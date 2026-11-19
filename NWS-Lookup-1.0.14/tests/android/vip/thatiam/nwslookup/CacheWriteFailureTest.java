package vip.thatiam.nwslookup;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.AtomicFile;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Injects actual write failures into the production private-cache writer. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, shadows = CacheWriteFailureTest.FailingAtomicFileShadow.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class CacheWriteFailureTest {
    private static final String NAME = "nws_afd_location_cache.json";
    private static int failAfter = -1;
    private static volatile CountDownLatch finishReached;
    private static volatile CountDownLatch resumeFinish;

    @Test
    public void interruptedWritesAlwaysKeepThePreviousOfficeList() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File file = app.getFileStreamPath(NAME);
        String old = "{\"locations\":[{\"locationId\":\"MTR\",\"locationName\":\"Old saved list\"}]}";
        byte[] oldBytes = old.getBytes(StandardCharsets.UTF_8);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), oldBytes);
        char[] padding = new char[20_000];
        Arrays.fill(padding, 'x');
        String replacement = "{\"locations\":[{\"locationId\":\"SGX\",\"locationName\":\""
                + new String(padding) + "\"}]}";
        Context faultContext = new ContextWrapper(app) {
            @Override
            public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
                return new FailingOutputStream(getFileStreamPath(name));
            }
        };
        int failures = 0;
        try {
            for (int cycle = 0; cycle < 50; cycle++) {
                for (int boundary : new int[]{1, 64, 8192}) {
                    failAfter = boundary;
                    try {
                        write(faultContext, replacement);
                        fail("an injected write failure must be reported");
                    } catch (IOException expected) {
                        failures++;
                    }
                    assertArrayEquals("failed update must preserve the complete prior office list",
                            oldBytes, Files.readAllBytes(file.toPath()));
                }
            }
        } finally {
            failAfter = -1;
        }
        write(app, replacement);
        assertEquals(replacement, new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        assertFalse("failed transactions must not accumulate scratch files", new File(file + ".new").exists());
        System.out.println("CACHE_WRITE_FAILURE injections=" + failures + " successAfterFailures=1");
    }

    @Test
    public void abandonedTemporaryWritesKeepTheLastCompleteOfficeList() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File file = app.getFileStreamPath(NAME);
        File pending = new File(file + ".new");
        String old = "{\"locations\":[{\"locationId\":\"MTR\",\"locationName\":\"Complete saved list\"}]}";
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), old.getBytes(StandardCharsets.UTF_8));
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create();
        try {
            for (int i = 0; i < 100; i++) {
                // Simulate a process disappearing with an unfinished AtomicFile transaction.
                Files.write(pending.toPath(), "{\"locations\":[".getBytes(StandardCharsets.UTF_8));
                assertEquals(old, read(controller.get()));
                assertFalse("abandoned transactions must not accumulate", pending.exists());
            }
        } finally {
            controller.destroy();
        }
        System.out.println("CACHE_ABANDONED_WRITES recoveries=100");
    }

    @Test
    public void interruptedLegacyWriteRestoresTheBackup() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File file = app.getFileStreamPath(NAME);
        File backup = new File(file + ".bak");
        String old = "{\"locations\":[{\"locationId\":\"MTR\",\"locationName\":\"Old Android saved list\"}]}";
        file.getParentFile().mkdirs();
        Files.write(backup.toPath(), old.getBytes(StandardCharsets.UTF_8));
        Files.write(file.toPath(), "{\"locations\":[".getBytes(StandardCharsets.UTF_8));
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create();
        try {
            assertEquals(old, read(controller.get()));
            assertFalse("recovery should consume the backup", backup.exists());
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void concurrentReadCannotRollBackAnActiveOfficeListWrite() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File file = app.getFileStreamPath(NAME);
        String old = "{\"locations\":[{\"locationId\":\"MTR\",\"locationName\":\"Previous list\"}]}";
        String replacement = "{\"locations\":[{\"locationId\":\"SGX\",\"locationName\":\"New complete list\"}]}";
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), old.getBytes(StandardCharsets.UTF_8));
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        finishReached = new CountDownLatch(1);
        resumeFinish = new CountDownLatch(1);
        CountDownLatch readerStarted = new CountDownLatch(1);
        CountDownLatch readerFinished = new CountDownLatch(1);
        try {
            Future<Void> writing = workers.submit(() -> { write(app, replacement); return null; });
            assertTrue("writer did not reach its commit boundary", finishReached.await(5, TimeUnit.SECONDS));
            Future<String> reading = workers.submit(() -> {
                readerStarted.countDown();
                try { return read(controller.get()); }
                finally { readerFinished.countDown(); }
            });
            assertTrue(readerStarted.await(5, TimeUnit.SECONDS));
            // Give an unguarded reader time to attempt AtomicFile recovery mid-transaction.
            boolean completedDuringWrite = readerFinished.await(250, TimeUnit.MILLISECONDS);
            resumeFinish.countDown();
            writing.get(5, TimeUnit.SECONDS);
            String observed = reading.get(5, TimeUnit.SECONDS);
            assertEquals("a concurrent reader must not discard a successful office-list update",
                    replacement, new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            assertFalse("a reader must wait for an active atomic transaction", completedDuringWrite);
            assertEquals(replacement, observed);
        } finally {
            resumeFinish.countDown();
            workers.shutdownNow();
            workers.awaitTermination(5, TimeUnit.SECONDS);
            finishReached = null;
            resumeFinish = null;
            controller.destroy();
        }
    }

    private static void write(Context context, String value) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(
                "writePrivateFile", Context.class, String.class, String.class);
        method.setAccessible(true);
        try {
            method.invoke(null, context, NAME, value);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
            if (e.getCause() instanceof Error) throw (Error) e.getCause();
            throw new AssertionError(e.getCause());
        }
    }

    private static String read(MainActivity activity) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod("readPrivateFile", String.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(activity, NAME);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
            if (e.getCause() instanceof Error) throw (Error) e.getCause();
            throw new AssertionError(e.getCause());
        }
    }

    private static class FailingOutputStream extends FileOutputStream {
        private int written;

        FailingOutputStream(File file) throws FileNotFoundException {
            super(file);
        }

        FailingOutputStream(FileDescriptor descriptor) {
            super(descriptor);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (failAfter < 0) {
                super.write(bytes, offset, length);
                return;
            }
            int accepted = Math.min(length, Math.max(0, failAfter - written));
            if (accepted > 0) {
                super.write(bytes, offset, accepted);
                written += accepted;
            }
            if (accepted < length || written >= failAfter) {
                throw new IOException("Injected interrupted cache write after " + written + " bytes");
            }
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            write(bytes, 0, bytes.length);
        }

        @Override
        public void write(int value) throws IOException {
            write(new byte[]{(byte) value}, 0, 1);
        }
    }

    @Implements(AtomicFile.class)
    public static class FailingAtomicFileShadow {
        @RealObject private AtomicFile real;

        @Implementation
        protected FileOutputStream startWrite() throws IOException {
            FileOutputStream output = Shadow.directlyOn(real, AtomicFile.class, "startWrite");
            return failAfter >= 0 ? new FailingOutputStream(output.getFD()) : output;
        }

        @Implementation
        protected void finishWrite(FileOutputStream output) {
            CountDownLatch reached = finishReached;
            CountDownLatch resume = resumeFinish;
            if (reached != null && resume != null) {
                reached.countDown();
                try {
                    if (!resume.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("test did not release the paused AtomicFile writer");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            }
            Shadow.directlyOn(real, AtomicFile.class, "finishWrite",
                    ClassParameter.from(FileOutputStream.class, output));
        }
    }
}
