package vip.thatiam.nwslookup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class HostInvariantTests {
    private HostInvariantTests() {
    }

    public static void main(String[] args) throws Exception {
        testBoundedReaderEdges();
        stressBoundedReader();
        stressMaximumResponseReads();
        stressSingleFlightGate();
        testRotationListenerHandoff();
        System.out.println("NWS Lookup host invariants: PASS");
    }

    private static void testBoundedReaderEdges() throws Exception {
        String exact = "abcdefgh";
        String read = BoundedTextReader.read(bytes(exact), exact.length(), "exact");
        require(exact.equals(read), "exact-limit body changed");

        TrackingInputStream oversized = new TrackingInputStream(bytes(exact + "i"));
        boolean rejected = false;
        try {
            BoundedTextReader.read(oversized, exact.length(), "oversize");
        } catch (IOException expected) {
            rejected = expected.getMessage().contains("character limit");
        }
        require(rejected, "limit+1 body was not rejected");
        require(oversized.closed, "oversize rejection did not close its input stream");

        TrackingInputStream tracked = new TrackingInputStream(bytes("closed"));
        BoundedTextReader.read(tracked, 16, "close test");
        require(tracked.closed, "reader did not close its input stream");
        require("".equals(BoundedTextReader.read(null, 0, "null")), "null stream was not empty");
    }

    private static void stressBoundedReader() throws Exception {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < 250_000; i++) source.append((char) ('a' + (i % 26)));
        String expected = source.toString();

        for (int pass = 0; pass < 40; pass++) {
            InputStream chunked = new ChunkedInputStream(bytes(expected), 1 + (pass % 97));
            String actual = BoundedTextReader.read(chunked, expected.length(), "stress");
            require(expected.equals(actual), "chunked read mismatch at pass " + pass);
        }
    }

    private static void stressSingleFlightGate() throws Exception {
        final int contenders = 24;
        final int rounds = 250;
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        try {
            SingleFlightGate gate = new SingleFlightGate();
            for (int round = 0; round < rounds; round++) {
                CountDownLatch ready = new CountDownLatch(contenders);
                CountDownLatch start = new CountDownLatch(1);
                AtomicInteger winners = new AtomicInteger();
                AtomicLong winningToken = new AtomicLong();
                List<Future<?>> futures = new ArrayList<>();

                for (int i = 0; i < contenders; i++) {
                    futures.add(pool.submit(() -> {
                        ready.countDown();
                        await(start);
                        long token = gate.tryStart();
                        if (token != 0L) {
                            winners.incrementAndGet();
                            winningToken.set(token);
                        }
                    }));
                }

                require(ready.await(5, TimeUnit.SECONDS), "contenders did not become ready");
                start.countDown();
                for (Future<?> future : futures) future.get(5, TimeUnit.SECONDS);
                require(winners.get() == 1, "single-flight admitted " + winners.get() + " contenders");
                require(gate.isRunning(), "gate lost its in-flight state");
                require(gate.finish(winningToken.get()), "winning token could not finish");
                require(!gate.isRunning(), "gate stayed running after completion");
            }

            long oldToken = gate.tryStart();
            require(gate.finish(oldToken), "first token did not finish");
            long newToken = gate.tryStart();
            require(!gate.finish(oldToken), "stale token finished a newer operation");
            require(gate.isRunning(), "stale token cleared the newer operation");
            require(gate.finish(newToken), "new token did not finish");
        } finally {
            pool.shutdownNow();
        }
    }

    private static void stressMaximumResponseReads() throws Exception {
        final int limit = 2 * 1024 * 1024;
        for (int i = 0; i < 200; i++) {
            final boolean oversized = (i % 2) == 1;
            GeneratedInputStream stream = new GeneratedInputStream(limit + (oversized ? 1 : 0));
            try {
                String value = BoundedTextReader.read(stream, limit, "maximum response");
                require(!oversized, "maximum+1 response passed the size bound");
                require(value.length() == limit, "maximum response was truncated");
                require(value.charAt(0) == 'x' && value.charAt(limit - 1) == 'x', "maximum response changed");
            } catch (IOException expected) {
                require(oversized, "exact maximum response was rejected");
            }
            require(stream.closed, "maximum response stream leaked");
        }
        System.out.println("MAX_RESPONSE_SOAK: 200 exact/oversized 2-MiB reads under a 32-MiB JVM heap");
    }

    private static void testRotationListenerHandoff() {
        SingleFlightGate gate = new SingleFlightGate();
        WeakListenerSlot<Object> listeners = new WeakListenerSlot<>();
        Object oldActivity = new Object();
        Object currentActivity = new Object();

        listeners.attach(oldActivity);
        long operation = gate.tryStart();
        require(operation != 0L, "rotation test could not start operation");

        listeners.detach(oldActivity);
        listeners.attach(currentActivity);
        require(gate.tryStart() == 0L, "rotation admitted a duplicate operation");
        require(listeners.get() == currentActivity, "completion was not routed to the current listener");
        require(gate.finish(operation), "rotation operation could not complete");
        require(listeners.get() == currentActivity, "current listener was lost at completion");

        listeners.detach(currentActivity);
        require(listeners.get() == null, "detached Activity remained registered");
    }

    private static ByteArrayInputStream bytes(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class TrackingInputStream extends InputStream {
        private final InputStream delegate;
        private boolean closed;

        TrackingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            delegate.close();
        }
    }

    private static final class ChunkedInputStream extends InputStream {
        private final InputStream delegate;
        private final int chunkSize;

        ChunkedInputStream(InputStream delegate, int chunkSize) {
            this.delegate = delegate;
            this.chunkSize = chunkSize;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return delegate.read(buffer, offset, Math.min(length, chunkSize));
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static final class GeneratedInputStream extends InputStream {
        private int remaining;
        private boolean closed;

        GeneratedInputStream(int size) { remaining = size; }

        @Override public int read() {
            if (remaining == 0) return -1;
            remaining--;
            return 'x';
        }

        @Override public int read(byte[] buffer, int offset, int length) {
            if (length == 0) return 0;
            if (remaining == 0) return -1;
            int count = Math.min(length, remaining);
            java.util.Arrays.fill(buffer, offset, offset + count, (byte) 'x');
            remaining -= count;
            return count;
        }

        @Override public void close() { closed = true; }
    }
}
