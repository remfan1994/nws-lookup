package vip.thatiam.nwslookup;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Executes the application's actual cache/model/startup code on Android's JVM runtime. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class CacheLongevityTest {
    private static final String PREFS = "nws_lookup_cache";
    private static final String CACHE = "cache_json";
    private static final String SELECTED = "selected_station_json";
    private static final String FONT = "font_size_step";
    private static final int CACHE_LIMIT = 4 * 1024 * 1024;
    private final List<ActivityController<MainActivity>> activities = new ArrayList<>();
    private Application app;
    private SharedPreferences prefs;

    @Before
    public void before() {
        app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        assertTrue(prefs.edit().clear().commit());
    }

    @After
    public void after() {
        for (ActivityController<MainActivity> controller : activities) {
            controller.pause().stop().destroy();
        }
        activities.clear();
        ShadowLooper.idleMainLooper();
        prefs.edit().clear().commit();
    }

    @Test
    public void tenThousandSerializationCyclesKeepEachReportIdentityAndBoundedSize() throws Exception {
        Object bundle = parse(bundleJson());
        String stableDaily = stringField(bundle, "dailyText");
        String stableDailyTime = stringField(bundle, "dailyFetchedAt");
        String stableDailyPoint = stringField(bundle, "dailyLat") + "," + stringField(bundle, "dailyLon");
        int largest = 0;
        int smallest = Integer.MAX_VALUE;
        for (int i = 0; i < 10_000; i++) {
            Object previous = bundle;
            Object copy = call(bundle, "copy", new Class<?>[0]);
            assertNotSame(previous, copy);
            String timestamp = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i * 86400L).toString();
            String marker = String.format(Locale.ROOT, "Hourly sequence %08d", i);
            set(copy, "hourlyText", marker);
            set(copy, "hourlyFetchedAt", timestamp);
            set(copy, "fetchedAt", timestamp);
            set(copy, "hourlyLat", i % 2 == 0 ? "36.6002" : "36.7002");
            String encoded = json(copy).toString();
            largest = Math.max(largest, encoded.length());
            smallest = Math.min(smallest, encoded.length());
            bundle = parse(encoded);
            assertEquals(marker, stringField(bundle, "hourlyText"));
            assertEquals(timestamp, stringField(bundle, "hourlyFetchedAt"));
            assertEquals(stableDaily, stringField(bundle, "dailyText"));
            assertEquals(stableDailyTime, stringField(bundle, "dailyFetchedAt"));
            assertEquals(stableDailyPoint,
                    stringField(bundle, "dailyLat") + "," + stringField(bundle, "dailyLon"));
            assertEquals("Daily point", stringField(bundle, "dailyLocationName"));
            assertEquals(stableDailyTime, stringField(previous, "dailyFetchedAt"));
        }
        assertTrue("serializing must not append history", largest - smallest <= 16);
        assertTrue("one cache must remain bounded", largest < 8192);
        System.out.println("CACHE_SERIALIZATION cycles=10000 minChars=" + smallest + " maxChars=" + largest);
    }

    @Test
    public void modernUnknownFetchTimesAreNotReplacedByAnotherViewsNewerFetchTime() throws Exception {
        JSONObject wire = bundleJson();
        wire.put("hourlyFetchedAt", "");
        wire.put("afdFetchedAt", "");
        wire.put("fetchedAt", "2040-02-01T12:00:00Z");
        wire.put("dailyFetchedAt", "2040-02-01T12:00:00Z");
        for (int i = 0; i < 100; i++) {
            Object reloaded = parse(wire);
            assertEquals("an explicitly unknown hourly fetch time must stay unknown", "",
                    stringField(reloaded, "hourlyFetchedAt"));
            assertEquals("an explicitly unknown AFD fetch time must stay unknown", "",
                    stringField(reloaded, "afdFetchedAt"));
            wire = json(reloaded);
        }
    }

    @Test
    public void legacyMigrationRunsOnceAndDoesNotRelabelOlderReports() throws Exception {
        JSONObject legacy = bundleJson();
        for (String key : Arrays.asList("hourlyFetchedAt", "dailyFetchedAt", "afdFetchedAt",
                "hourlyLat", "hourlyLon", "hourlyLocationName", "hourlyPointOffice",
                "dailyLat", "dailyLon", "dailyLocationName", "dailyPointOffice")) {
            legacy.remove(key);
        }
        String legacyTime = "2018-01-01T00:00:00Z";
        legacy.put("fetchedAt", legacyTime);
        Object migrated = parse(legacy);
        assertEquals(legacyTime, stringField(migrated, "hourlyFetchedAt"));
        assertEquals(legacyTime, stringField(migrated, "dailyFetchedAt"));
        assertEquals("36.6002", stringField(migrated, "hourlyLat"));
        assertEquals("36.6002", stringField(migrated, "dailyLat"));
        set(migrated, "fetchedAt", "2042-08-01T00:00:00Z");
        set(migrated, "dailyFetchedAt", "2042-08-01T00:00:00Z");
        set(migrated, "dailyLat", "37.0010");
        set(migrated, "lat", "37.0010");
        for (int i = 0; i < 100; i++) {
            migrated = parse(json(migrated));
            assertEquals(legacyTime, stringField(migrated, "hourlyFetchedAt"));
            assertEquals("36.6002", stringField(migrated, "hourlyLat"));
            assertEquals("37.0010", stringField(migrated, "dailyLat"));
        }
    }

    @Test
    public void wrongTypeReportFieldsDoNotBecomeWeatherText() throws Exception {
        Object[] invalid = {JSONObject.NULL, 42, true, new JSONArray().put("rain"),
                new JSONObject().put("message", "not a report")};
        for (Object value : invalid) {
            JSONObject wire = bundleJson();
            wire.put("hourlyText", value);
            wire.put("dailyText", value);
            wire.put("afdText", value);
            Object reloaded = parse(wire);
            assertEquals("wrong-type hourly text", "", stringField(reloaded, "hourlyText"));
            assertEquals("wrong-type daily text", "", stringField(reloaded, "dailyText"));
            assertEquals("wrong-type AFD text", "", stringField(reloaded, "afdText"));
        }
    }

    @Test
    public void fiftyDiskReloadsDoNotGrowTheSavedCache() throws Exception {
        File file = new File(app.getApplicationInfo().dataDir,
                "shared_prefs/nws_longevity_disk.xml");
        file.getParentFile().mkdirs();
        SharedPreferences disk = newDiskPreferences(file);
        Object model = parse(bundleJson());
        long largest = 0L;
        for (int i = 0; i < 50; i++) {
            String marker = String.format(Locale.ROOT, "Stored hourly %06d", i);
            set(model, "hourlyText", marker);
            assertTrue(disk.edit().putString(CACHE, json(model).toString()).commit());
            assertTrue("Android preferences must have reached disk", file.isFile());
            largest = Math.max(largest, file.length());
            disk = newDiskPreferences(file);
            model = parse(disk.getString(CACHE, ""));
            assertEquals(marker, stringField(model, "hourlyText"));
            assertEquals("Daily point", stringField(model, "dailyLocationName"));
        }
        assertTrue("disk cache must replace prior state rather than append it", largest < 16384);
        System.out.println("CACHE_DISK_RELOAD cycles=50 maxBytes=" + largest);
    }

    @Test
    public void wrongTypePreferencesDoNotCrashColdActivityStartup() throws Exception {
        for (String key : Arrays.asList(SELECTED, CACHE, FONT)) {
            assertTrue(prefs.edit().clear().putString(SELECTED, stationJson("MTR").toString()).commit());
            SharedPreferences.Editor corrupt = prefs.edit();
            if (FONT.equals(key)) corrupt.putString(key, "old incompatible value");
            else corrupt.putInt(key, 42);
            assertTrue(corrupt.commit());
            MainActivity activity = launch();
            assertNotNull(activity);
            closeLast();
        }
    }

    @Test
    public void persistedFontStepIsClampedOnStartup() throws Exception {
        for (int stored : new int[]{-1000, -2, 0, 6, 1000}) {
            assertTrue(prefs.edit().clear().putInt(FONT, stored).commit());
            MainActivity activity = launch();
            int step = (Integer) field(activity, "fontSizeStep");
            assertTrue("font preference below supported bound", step >= -2);
            assertTrue("font preference above supported bound", step <= 6);
            closeLast();
        }
    }

    @Test
    public void malformedAndOversizedCacheCannotStrandStartup() throws Exception {
        String oversized = repeat('x', CACHE_LIMIT + 1);
        for (String wire : new String[]{"{", "null", "[]", "{\"stationIdentifier\":\"MTR\",", oversized}) {
            assertTrue(prefs.edit().clear().putString(SELECTED, stationJson("MTR").toString())
                    .putString(CACHE, wire).commit());
            MainActivity activity = launch();
            assertNull("unreadable cache must not become a report", field(activity, "currentBundle"));
            closeLast();
        }
        assertFalse("oversized legacy cache must be removed", prefs.contains(CACHE));
    }

    @Test
    public void deeplyNestedSavedJsonCannotCrashStartupOrOfficeSelection() throws Exception {
        String deep = "{\"child\":".repeat(12_000) + "0" + "}".repeat(12_000);
        assertTrue(prefs.edit().putString(SELECTED, deep).commit());
        MainActivity activity = launch();
        assertNull("unreadable selection should fall back safely", field(activity, "selectedStation"));
        closeLast();

        assertTrue(prefs.edit().putString(SELECTED, stationJson("MTR").toString())
                .putString(CACHE, deep).commit());
        activity = launch();
        assertNull("unreadable weather must not become a report", field(activity, "currentBundle"));
        File officeCache = app.getFileStreamPath("nws_afd_location_cache.json");
        officeCache.getParentFile().mkdirs();
        Files.write(officeCache.toPath(), deep.getBytes(StandardCharsets.UTF_8));
        call(activity, "showStationScreen", new Class<?>[0]);
        android.widget.BaseAdapter adapter = (android.widget.BaseAdapter) field(activity, "stationAdapter");
        assertTrue("the built-in office fallback remains available", adapter.getCount() > 100);
    }

    @Test
    public void repeatedOfficeSwitchesRestoreOnlyTheMatchingSavedReports() throws Exception {
        String wire = bundleJson().toString();
        assertTrue(prefs.edit().putString(CACHE, wire).putString(SELECTED, stationJson("MTR").toString()).commit());
        MainActivity activity = launch();
        for (int i = 0; i < 200; i++) {
            select(activity, "SGX");
            assertNull("MTR weather must not be displayed as SGX weather", field(activity, "currentBundle"));
            assertEquals("selection must not delete the last successful report", wire, prefs.getString(CACHE, ""));
            select(activity, "MTR");
            Object restored = field(activity, "currentBundle");
            assertNotNull(restored);
            assertEquals("Hourly point", stringField(restored, "hourlyLocationName"));
            assertEquals("Daily point", stringField(restored, "dailyLocationName"));
        }
        System.out.println("CACHE_OFFICE_SWITCH cycles=200 switches=400");
    }

    private MainActivity launch() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        controller.create().start().resume();
        activities.add(controller);
        ShadowLooper.idleMainLooper();
        return controller.get();
    }

    private void closeLast() {
        ActivityController<MainActivity> controller = activities.remove(activities.size() - 1);
        controller.pause().stop().destroy();
        ShadowLooper.idleMainLooper();
    }

    private static void select(MainActivity activity, String code) throws Exception {
        Class<?> station = nested("StationRecord");
        Object record = callStatic(station, "fromSelectionJson", new Class<?>[]{JSONObject.class}, stationJson(code));
        call(activity, "selectStation", new Class<?>[]{station}, record);
    }

    private static JSONObject stationJson(String code) throws JSONException {
        return new JSONObject().put("stationIdentifier", code).put("name", code + " office")
                .put("lat", "MTR".equals(code) ? "36.6002" : "32.7157")
                .put("lon", "MTR".equals(code) ? "-121.8947" : "-117.1611");
    }

    private static JSONObject bundleJson() throws JSONException {
        return new JSONObject()
                .put("stationIdentifier", "MTR").put("stationName", "MTR office")
                .put("lat", "36.6002").put("lon", "-121.8947")
                .put("office", "MTR").put("pointOffice", "MTR").put("locationName", "Monterey, CA")
                .put("forecastUrl", "https://api.weather.gov/gridpoints/MTR/1,2/forecast")
                .put("hourlyUrl", "https://api.weather.gov/gridpoints/MTR/1,2/forecast/hourly")
                .put("pointResolvedAt", "2026-10-19T12:00:00Z")
                .put("fetchedAt", "2026-10-19T12:02:00Z")
                .put("afdFetchedAt", "2026-10-19T12:00:00Z")
                .put("hourlyFetchedAt", "2026-10-19T12:01:00Z")
                .put("dailyFetchedAt", "2026-10-19T12:02:00Z")
                .put("afdUpdated", "2026-10-19T11:00:00Z")
                .put("hourlyUpdated", "2026-10-19T11:30:00Z")
                .put("dailyUpdated", "2026-10-19T11:45:00Z")
                .put("hourlyLat", "36.6002").put("hourlyLon", "-121.8947")
                .put("hourlyLocationName", "Hourly point").put("hourlyPointOffice", "MTR")
                .put("dailyLat", "36.8002").put("dailyLon", "-121.7947")
                .put("dailyLocationName", "Daily point").put("dailyPointOffice", "MTR")
                .put("afdText", "Key Messages\n------------\nRain is possible later today.")
                .put("hourlyText", "Noon  60 F  20% precipitation  Cloudy")
                .put("dailyText", "Tuesday\nHigh 65 F\nPartly cloudy.");
    }

    private static Class<?> nested(String name) throws ClassNotFoundException {
        return Class.forName("vip.thatiam.nwslookup.MainActivity$" + name);
    }

    private static Object parse(JSONObject object) throws Exception {
        return parse(object.toString());
    }

    private static Object parse(String text) throws Exception {
        return callStatic(nested("WeatherBundle"), "fromJson", new Class<?>[]{String.class}, text);
    }

    private static JSONObject json(Object object) throws Exception {
        return (JSONObject) call(object, "toJson", new Class<?>[0]);
    }

    private static Object callStatic(Class<?> type, String name, Class<?>[] parameters, Object... args) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return invoke(method, null, args);
    }

    private static Object call(Object target, String name, Class<?>[] parameters, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return invoke(method, target, args);
    }

    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new AssertionError(cause);
        }
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static String stringField(Object object, String name) throws Exception {
        return (String) field(object, name);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static SharedPreferences newDiskPreferences(File file) throws Exception {
        Class<?> type = Class.forName("android.app.SharedPreferencesImpl");
        Constructor<?> constructor = type.getDeclaredConstructor(File.class, int.class);
        constructor.setAccessible(true);
        return (SharedPreferences) constructor.newInstance(file, Context.MODE_PRIVATE);
    }

    private static String repeat(char value, int length) {
        char[] chars = new char[length];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
