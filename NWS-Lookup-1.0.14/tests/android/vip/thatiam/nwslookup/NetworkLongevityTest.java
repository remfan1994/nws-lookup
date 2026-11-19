package vip.thatiam.nwslookup;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/** Scripted HTTP at the URL boundary: the actual app fetch and coordinator code runs. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class NetworkLongevityTest {
    private static final String OLD_URL = "https://api.weather.gov/gridpoints/MTR/1,1/forecast/hourly";
    private static final String NEW_URL = "https://api.weather.gov/gridpoints/MTR/2,2/forecast/hourly";
    private static final String POINT_URL = "https://api.weather.gov/points/36.6002,-121.8947";
    private static final Deque<Reply> REPLIES = new ArrayDeque<>();
    private static final List<FakeConnection> CONNECTIONS = new ArrayList<>();
    private Context context;

    @BeforeClass public static void interceptHttp() {
        // This class runs in its own Gradle test JVM (forkEvery = 1). Never send test traffic to NWS.
        URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) throws IOException {
                synchronized (REPLIES) {
                    Reply reply = REPLIES.pollFirst();
                    if (reply == null) throw new IOException("Unscripted test connection: " + url);
                    if (!url.toString().equals(reply.url)) {
                        throw new IOException("Expected " + reply.url + ", received " + url);
                    }
                    FakeConnection connection = new FakeConnection(url, reply);
                    CONNECTIONS.add(connection);
                    return connection;
                }
            }
        } : null);
    }

    @Before public void setUp() {
        clearNetwork();
        context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public Context getApplicationContext() { return this; }
            @Override public Object getSystemService(String name) {
                return CONNECTIVITY_SERVICE.equals(name) ? null : super.getSystemService(name);
            }
        };
        preferences().edit().clear().commit();
    }

    @After public void tearDown() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertClosed();
        clearNetwork();
    }

    @Test public void repeatedHttpSuccessAndFailuresCloseAllConnections() throws Exception {
        long connections = 0;
        for (int i = 0; i < 1500; i++) {
            clearNetwork();
            int mode = i % 5;
            Reply first = new Reply(OLD_URL, mode == 1 ? 503 : 200, "body " + i);
            if (mode == 2) first.failHeaders = true;
            if (mode == 3) first.failReadAfter = 2;
            if (mode == 4) { first.status = 302; first.location = NEW_URL; }
            enqueue(first);
            if (mode == 4) enqueue(new Reply(NEW_URL, 200, "body " + i));
            try {
                String actual = (String) invoke(null, MainActivity.class, "getText",
                        new Class<?>[]{Context.class, String.class}, context, OLD_URL);
                assertTrue("An injected failure became success", mode == 0 || mode == 4);
                assertEquals("body " + i, actual);
            } catch (IOException expected) {
                assertTrue("A success fixture failed: " + expected, mode >= 1 && mode <= 3);
            }
            assertClosed();
            synchronized (REPLIES) {
                assertTrue("Unconsumed scripted request", REPLIES.isEmpty());
                connections += CONNECTIONS.size();
            }
        }
        assertEquals(1800, connections);
        System.out.println("NETWORK_SOAK: 1500 operations, 1800 connections, all closed");
    }

    @Test public void failedFetchesPreserveCacheAndReleaseCoordinator() throws Exception {
        Object station = station();
        Object current = bundle(OffsetDateTime.now().toString());
        String saved = json(current);
        preferences().edit().putString("cache_json", saved).commit();
        Object coordinator = construct(nested("AppNetworkCoordinator"));
        int accepted = 0;
        for (int i = 0; i < 600; i++) {
            clearNetwork();
            int mode = i % 4;
            String body = mode == 0 ? forecast("Cycle " + i)
                    : mode == 1 ? "{\"properties\":{\"periods\":[]}}"
                    : mode == 2 ? "NWS unavailable" : "{\"properties\":";
            enqueue(new Reply(OLD_URL, mode == 2 ? 503 : 200, body));
            assertTrue((Boolean) invoke(coordinator, coordinator.getClass(), "startWeather",
                    new Class<?>[]{Context.class, nested("StationRecord"), nested("WeatherBundle"), int.class, boolean.class},
                    context, station, current, 1, false));
            waitUntilComplete(coordinator);
            Object result;
            String error;
            synchronized (coordinator) {
                result = field(coordinator, "weatherResult");
                error = (String) field(coordinator, "errorMessage");
            }
            String after = preferences().getString("cache_json", null);
            if (mode == 0) {
                assertEquals("Successful fetch has an error", "", error);
                assertNotNull(result);
                assertTrue(((String) field(result, "hourlyText")).contains("Cycle " + i));
                current = result;
                saved = json(result);
                assertEquals(saved, after);
                accepted++;
            } else {
                assertNull("Failed fetch returned a replacement", result);
                assertFalse("Failed fetch lost its error", error.isEmpty());
                assertEquals("Failed fetch changed durable cache", saved, after);
            }
            assertClosed();
            synchronized (REPLIES) { assertTrue(REPLIES.isEmpty()); }
        }
        assertEquals(150, accepted);
        System.out.println("COORDINATOR_SOAK: 600 fetches, 150 successes, 450 preserved-cache failures");
    }

    @Test public void mappingExpiryAndRetiredUrlRetryStayBounded() throws Exception {
        for (int i = 0; i < 200; i++) {
            clearNetwork();
            int mode = i % 4;
            Object base = bundle((mode == 0 ? OffsetDateTime.now().minusDays(2)
                    : OffsetDateTime.now()).toString());
            if (mode == 0) {
                enqueue(new Reply(POINT_URL, 200, point()));
                enqueue(new Reply(NEW_URL, 200, forecast("mapped")));
            } else if (mode == 1 || mode == 2) {
                enqueue(new Reply(OLD_URL, mode == 1 ? 404 : 410, "retired"));
                enqueue(new Reply(POINT_URL, 200, point()));
                enqueue(new Reply(NEW_URL, 200, forecast("mapped")));
            } else {
                enqueue(new Reply(OLD_URL, 503, "outage"));
            }
            String unchanged = json(base);
            try {
                Object result = fetch(station(), base);
                assertTrue("An outage was silently retried", mode < 3);
                assertTrue(((String) field(result, "hourlyText")).contains("mapped"));
                assertEquals("Monterey, CA", field(result, "hourlyLocationName"));
            } catch (IOException expected) {
                assertEquals("A mapping refresh unexpectedly failed: " + expected, 3, mode);
            }
            assertEquals("Fetch modified its input bundle", unchanged, json(base));
            assertClosed();
            synchronized (REPLIES) {
                assertTrue(REPLIES.isEmpty());
                assertEquals(mode == 0 ? 2 : mode == 3 ? 1 : 3, CONNECTIONS.size());
            }
        }
        System.out.println("MAPPING_SOAK: 200 expiry/404/410/outage cases, request bounds held");
    }

    @Test public void deeplyNestedResponseFailsWithoutStackOverflow() throws Exception {
        String body = "{\"child\":".repeat(12000) + "0" + "}".repeat(12000);
        enqueue(new Reply(OLD_URL, 200, body));
        try {
            invoke(null, MainActivity.class, "getJson", new Class<?>[]{Context.class, String.class}, context, OLD_URL);
            fail("Excessively nested network JSON was accepted");
        } catch (Exception expected) {
            assertTrue(expected instanceof IOException || expected instanceof org.json.JSONException);
        }
    }

    @Test public void oversizedAndPartialResponsesCannotBypassLimits() throws Exception {
        for (int i = 0; i < 40; i++) {
            clearNetwork();
            Reply reply = new Reply(OLD_URL, 200, "x".repeat(2 * 1024 * 1024 + 1));
            reply.unknownLength = (i % 2) == 0;
            enqueue(reply);
            try {
                invoke(null, MainActivity.class, "getText", new Class<?>[]{Context.class, String.class}, context, OLD_URL);
                fail("Oversized response was accepted");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("character limit"));
            }
            assertClosed();
        }
        System.out.println("RESPONSE_LIMIT_SOAK: 40 oversized responses rejected and closed");
    }

    @Test public void failedWeatherCommitPreservesThePreviouslySavedReport() throws Exception {
        File directory = new File(context.getCacheDir(), "weather-commit-failure");
        File savedDirectory = new File(context.getCacheDir(), "weather-commit-failure.saved");
        assertTrue(directory.mkdirs());
        File xml = new File(directory, "cache.xml");
        SharedPreferences disk = newDiskPreferences(xml);
        Object current = bundle(OffsetDateTime.now().toString());
        String previous = json(current);
        assertTrue(disk.edit().putString("cache_json", previous).commit());
        assertTrue("fixture must use real Android preference disk writes", xml.isFile());
        Context failingContext = new ContextWrapper(context) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return "nws_lookup_cache".equals(name) ? disk : super.getSharedPreferences(name, mode);
            }
        };
        Object coordinator = construct(nested("AppNetworkCoordinator"));
        Files.move(directory.toPath(), savedDirectory.toPath());
        Files.write(directory.toPath(), new byte[]{1});
        try {
            // A regular file at the parent path forces actual disk failure even when run as root.
            for (int i = 0; i < 25; i++) {
                clearNetwork();
                enqueue(new Reply(OLD_URL, 200, forecast("Unsaved report " + i)));
                assertTrue((Boolean) invoke(coordinator, coordinator.getClass(), "startWeather",
                        new Class<?>[]{Context.class, nested("StationRecord"), nested("WeatherBundle"), int.class, boolean.class},
                        failingContext, station(), current, 1, false));
                waitUntilComplete(coordinator);
                assertNull("an unsaved report must not be delivered as success", field(coordinator, "weatherResult"));
                assertTrue(((String) field(coordinator, "errorMessage")).contains("could not be saved"));
                assertEquals("failed commit must not replace the cache seen by a recreated Activity",
                        previous, disk.getString("cache_json", null));
                assertClosed();
            }
        } finally {
            Files.delete(directory.toPath());
            Files.move(savedDirectory.toPath(), directory.toPath());
        }
        assertEquals("the last durable report survives a fresh disk reload", previous,
                newDiskPreferences(xml).getString("cache_json", null));
        System.out.println("WEATHER_WRITE_FAILURE injections=25 priorCachePreservedOnDiskAndInMemory=true");
    }

    @Test public void representativePointLookupStopsAfterEightStationRequests() throws Exception {
        JSONArray approved = new JSONArray();
        for (int i = 0; i < 100; i++) {
            approved.put("https://api.weather.gov/stations/TEST" + i);
        }
        enqueue(new Reply("https://api.weather.gov/offices/MTR", 200,
                new JSONObject().put("approvedObservationStations", approved).toString()));
        for (int i = 0; i < 100; i++) {
            enqueue(new Reply("https://api.weather.gov/stations/TEST" + i, 200, "{}"));
        }
        try {
            resolvePoint(officeWithoutPoint());
            fail("Geometry-less observation stations must not become a representative point");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("No usable representative point"));
        }
        synchronized (REPLIES) {
            System.out.println("REPRESENTATIVE_POINT_LIMIT listed=100 requests=" + CONNECTIONS.size());
            assertEquals("one office request plus at most eight observation-station attempts",
                    9, CONNECTIONS.size());
            assertEquals("stations outside the attempt limit must not be fetched", 92, REPLIES.size());
        }
    }

    @Test public void eighthRepresentativeStationCanSucceedAfterEmptyEntries() throws Exception {
        JSONArray approved = new JSONArray();
        for (int i = 0; i < 100; i++) approved.put(" ");
        for (int i = 1; i <= 9; i++) {
            approved.put("").put(" \t ").put("/stations/TEST" + i);
        }
        enqueue(new Reply("https://api.weather.gov/offices/MTR", 200,
                new JSONObject().put("approvedObservationStations", approved).toString()));
        for (int i = 1; i <= 9; i++) {
            String body = i == 8 ? new JSONObject().put("geometry", new JSONObject()
                    .put("type", "Point").put("coordinates", new JSONArray()
                            .put(-121.8947).put(36.6002))).toString() : "{}";
            enqueue(new Reply("https://api.weather.gov/stations/TEST" + i, 200, body));
        }
        Object office = officeWithoutPoint();
        Object result = resolvePoint(office);
        assertEquals("MTR", field(result, "stationIdentifier"));
        assertEquals("36.6002", field(result, "lat"));
        assertEquals("-121.8947", field(result, "lon"));
        assertEquals("resolution must not change the saved office selection", "", field(office, "lat"));
        synchronized (REPLIES) {
            assertEquals("empty entries must not consume the station request budget", 9, CONNECTIONS.size());
            assertEquals("a successful eighth station ends the lookup", 1, REPLIES.size());
        }
        System.out.println("REPRESENTATIVE_POINT_EIGHTH requests=9 ignoredEmptyEntries=116");
    }

    private Object resolvePoint(Object office) throws Exception {
        return invoke(null, MainActivity.class, "resolveRepresentativePointFromNws",
                new Class<?>[]{Context.class, nested("StationRecord")}, context, office);
    }

    private static Object officeWithoutPoint() throws Exception {
        return invoke(null, nested("StationRecord"), "fromSelectionJson", new Class<?>[]{JSONObject.class},
                new JSONObject().put("stationIdentifier", "MTR").put("name", "MTR office"));
    }

    private static SharedPreferences newDiskPreferences(File file) throws Exception {
        Constructor<?> constructor = Class.forName("android.app.SharedPreferencesImpl")
                .getDeclaredConstructor(File.class, int.class);
        constructor.setAccessible(true);
        return (SharedPreferences) constructor.newInstance(file, Context.MODE_PRIVATE);
    }

    private Object fetch(Object station, Object base) throws Exception {
        return invoke(null, MainActivity.class, "fetchWeatherPart",
                new Class<?>[]{Context.class, nested("StationRecord"), nested("WeatherBundle"), int.class},
                context, station, base, 1);
    }

    private static Object station() throws Exception {
        return invoke(null, nested("StationRecord"), "fromBuiltInLine", new Class<?>[]{String.class},
                "MTR|San Francisco Bay Area/Monterey|36.6002|-121.8947");
    }

    private static Object bundle(String resolvedAt) throws Exception {
        JSONObject json = new JSONObject();
        json.put("stationIdentifier", "MTR"); json.put("stationName", "San Francisco Bay Area/Monterey");
        json.put("office", "MTR"); json.put("pointOffice", "MTR");
        json.put("lat", "36.6002"); json.put("lon", "-121.8947");
        json.put("hourlyLat", "36.6002"); json.put("hourlyLon", "-121.8947");
        json.put("hourlyLocationName", "Monterey, CA"); json.put("hourlyPointOffice", "MTR");
        json.put("locationName", "Monterey, CA"); json.put("pointResolvedAt", resolvedAt);
        json.put("hourlyUrl", OLD_URL); json.put("hourlyText", "Original saved weather");
        json.put("hourlyFetchedAt", "2026-01-01T00:00:00Z"); json.put("fetchedAt", "2026-01-01T00:00:00Z");
        return invoke(null, nested("WeatherBundle"), "fromJson", new Class<?>[]{String.class}, json.toString());
    }

    private static String forecast(String text) throws Exception {
        JSONObject period = new JSONObject();
        period.put("startTime", "2026-10-20T12:00:00-07:00");
        period.put("endTime", "2026-10-20T13:00:00-07:00");
        period.put("temperature", 0); period.put("temperatureUnit", "C");
        period.put("shortForecast", text);
        JSONObject properties = new JSONObject();
        properties.put("updated", "2026-10-20T11:30:00-07:00");
        properties.put("periods", new JSONArray().put(period));
        return new JSONObject().put("properties", properties).toString();
    }

    private static String point() throws Exception {
        JSONObject location = new JSONObject().put("city", "Monterey").put("state", "CA");
        JSONObject properties = new JSONObject().put("cwa", "MTR").put("forecastHourly", NEW_URL)
                .put("forecast", "https://api.weather.gov/gridpoints/MTR/2,2/forecast")
                .put("relativeLocation", new JSONObject().put("properties", location));
        return new JSONObject().put("properties", properties).toString();
    }

    private SharedPreferences preferences() { return context.getSharedPreferences("nws_lookup_cache", Context.MODE_PRIVATE); }
    private static Class<?> nested(String name) throws Exception { return Class.forName(MainActivity.class.getName() + "$" + name); }
    private static Object construct(Class<?> type) throws Exception { Constructor<?> c = type.getDeclaredConstructor(); c.setAccessible(true); return c.newInstance(); }
    private static Object field(Object object, String name) throws Exception { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
    private static String json(Object bundle) throws Exception { return invoke(bundle, bundle.getClass(), "toJson", new Class<?>[0]).toString(); }
    private static Object invoke(Object target, Class<?> type, String name, Class<?>[] params, Object... args) throws Exception {
        Method m = type.getDeclaredMethod(name, params); m.setAccessible(true);
        try { return m.invoke(target, args); }
        catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof Exception) throw (Exception) cause;
            throw e;
        }
    }
    private static void waitUntilComplete(Object coordinator) throws Exception {
        SingleFlightGate gate = (SingleFlightGate) field(coordinator, "gate");
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (gate.isRunning() && System.nanoTime() < deadline) LockSupport.parkNanos(500_000L);
        assertFalse("Coordinator stayed busy after fetch failure", gate.isRunning());
        synchronized (coordinator) { /* Wait for the completion snapshot to be published. */ }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private static void enqueue(Reply reply) { synchronized (REPLIES) { REPLIES.addLast(reply); } }
    private static void clearNetwork() { synchronized (REPLIES) { REPLIES.clear(); CONNECTIONS.clear(); } }
    private static void assertClosed() {
        synchronized (REPLIES) {
            for (FakeConnection c : CONNECTIONS) {
                assertTrue("Connection leaked: " + c.getURL(), c.disconnected);
                assertTrue("Response stream leaked: " + c.getURL(), c.stream == null || c.stream.closed);
            }
        }
    }

    private static class Reply {
        final String url;
        int status;
        final byte[] body;
        String location;
        boolean failHeaders;
        boolean unknownLength;
        int failReadAfter = -1;
        Reply(String url, int status, String body) { this.url = url; this.status = status; this.body = body.getBytes(StandardCharsets.UTF_8); }
    }
    private static class FakeConnection extends HttpURLConnection {
        final Reply reply;
        boolean disconnected;
        TrackingStream stream;
        FakeConnection(URL url, Reply reply) { super(url); this.reply = reply; }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { connected = true; }
        @Override public int getResponseCode() throws IOException { if (reply.failHeaders) throw new IOException("Injected header failure"); return reply.status; }
        @Override public long getContentLengthLong() { return reply.unknownLength ? -1 : reply.body.length; }
        @Override public String getHeaderField(String name) { return "Location".equalsIgnoreCase(name) ? reply.location : null; }
        @Override public InputStream getInputStream() { return openStream(); }
        @Override public InputStream getErrorStream() { return openStream(); }
        private InputStream openStream() { stream = new TrackingStream(reply); return stream; }
    }
    private static class TrackingStream extends InputStream {
        final Reply reply;
        final ByteArrayInputStream data;
        int read;
        boolean closed;
        TrackingStream(Reply reply) { this.reply = reply; data = new ByteArrayInputStream(reply.body); }
        @Override public int read() throws IOException { byte[] b = new byte[1]; int n = read(b, 0, 1); return n < 0 ? -1 : b[0] & 255; }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (reply.failReadAfter >= 0 && read >= reply.failReadAfter) throw new IOException("Injected partial-read failure");
            if (reply.failReadAfter >= 0) len = Math.min(len, reply.failReadAfter - read);
            int n = data.read(b, off, len); if (n > 0) read += n; return n;
        }
        @Override public void close() throws IOException { closed = true; data.close(); }
    }
}
