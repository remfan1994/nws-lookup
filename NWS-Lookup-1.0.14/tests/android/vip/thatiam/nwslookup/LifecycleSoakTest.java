package vip.thatiam.nwslookup;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.robolectric.shadows.ShadowAlertDialog;

/** Actual Android Activity/Handler tests with simulated time; never a device soak claim. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class LifecycleSoakTest {
    private static final String PREFS = "nws_lookup_cache";
    private static final String SELECTION = "selected_station_json";
    private static final String CACHE = "cache_json";
    private final List<ActivityController<? extends MainActivity>> open = new ArrayList<>();
    private SharedPreferences prefs;

    @Before
    public void resetState() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        assertTrue(prefs.edit().clear().commit());
        ConnectivityManager network = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(network).clearAllNetworks();
        shadowOf(network).setActiveNetworkInfo(null);
        Object coordinator = coordinator();
        ((Handler) field(coordinator, "mainHandler")).removeCallbacksAndMessages(null);
        call(coordinator, "clearCompletionLocked");
        set(coordinator, "eventId", 0L);
        set(coordinator, "activeOperation", 0);
        Object listeners = field(coordinator, "listener");
        Object previous = call(listeners, "get");
        if (previous != null) call(listeners, "detach", new Class<?>[]{Object.class}, previous);
        assertFalse(((SingleFlightGate) field(coordinator, "gate")).isRunning());
        seed("MTR", weatherJson("MTR", "MTR saved weather"));
    }

    @After
    public void closeActivities() {
        for (ActivityController<? extends MainActivity> controller : new ArrayList<>(open)) {
            controller.close();
        }
        open.clear();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void oneThousandActivityLifetimesDoNotAccumulateAppCallbacks() throws Exception {
        for (int generation = 0; generation < 1_000; generation++) {
            ActivityController<MainActivity> controller = launch();
            MainActivity activity = controller.get();
            assertEquals("MTR", field(field(activity, "selectedStation"), "stationIdentifier"));
            for (int visit = 0; visit < 10; visit++) {
                assertEquals("one foreground callback", 1, pendingStatusCallbacks(activity));
                controller.pause();
                assertEquals("paused callback retained", 0, pendingStatusCallbacks(activity));
                controller.resume().postResume();
                shadowOf(Looper.getMainLooper()).idle();
            }
            close(controller);
            assertEquals("destroyed callback retained", 0, pendingStatusCallbacks(activity));
            assertEquals(Boolean.FALSE, field(activity, "statusRefreshActive"));
            assertNull("coordinator retained a stopped Activity", call(field(coordinator(), "listener"), "get"));
            assertPassiveNetworkState();
        }
    }

    @Test
    public void destroyedActivityOwnersAreReleasedWithGcDiagnostics() throws Exception {
        List<WeakReference<MainActivity>> references = new ArrayList<>();
        for (int generation = 0; generation < 100; generation++) {
            references.add(createAndCloseForCollection());
        }
        // Collection timing is a JVM/framework diagnostic, not a deterministic pass gate.
        // Callback removal and coordinator detachment are asserted for every Activity.
        int collected = 0;
        for (int attempt = 0; attempt < 4; attempt++) {
            System.gc();
            System.runFinalization();
            shadowOf(Looper.getMainLooper()).idle();
            collected = 0;
            for (WeakReference<MainActivity> reference : references) {
                if (reference.get() == null) collected++;
            }
            if (collected == references.size()) break;
        }
        System.out.println("LIFECYCLE_GC_DIAGNOSTIC collected=" + collected
                + " total=" + references.size() + "; deterministic callback/listener checks passed");
        assertPassiveNetworkState();
    }

    @Test
    public void oneYearOfMinuteTicksKeepsOneCallbackAndDoesNotReplaceWeather() throws Exception {
        ActivityController<MainActivity> controller = launch();
        MainActivity activity = controller.get();
        TextView status = (TextView) field(activity, "statusView");
        TextView content = (TextView) field(activity, "contentView");
        String report = content.getText().toString();
        AtomicInteger changes = new AtomicInteger();
        status.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { changes.incrementAndGet(); }
            public void afterTextChanged(Editable s) { }
        });
        // Uptime is accelerated, not actual time installed or elapsed wall-clock years.
        for (int month = 0; month < 12; month++) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofDays(30));
            assertEquals(1, pendingStatusCallbacks(activity));
            assertEquals(report, content.getText().toString());
            assertPassiveNetworkState();
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofDays(5));
        assertEquals(365 * 24 * 60, changes.get());
        assertEquals(1, pendingStatusCallbacks(activity));
        assertEquals(report, content.getText().toString());
        controller.pause().stop();
        int stoppedChanges = changes.get();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofDays(365 * 5L));
        assertEquals("stopped activity kept updating", stoppedChanges, changes.get());
        assertEquals(0, pendingStatusCallbacks(activity));
        assertPassiveNetworkState();
    }

    @Test
    public void repeatedReadOnlyDialogsFiltersAndCanceledFetchesStayOffline() throws Exception {
        ActivityController<MainActivity> controller = launch();
        MainActivity activity = controller.get();
        String originalCache = prefs.getString(CACHE, null);
        for (int round = 0; round < 100; round++) {
            call(activity, "showView", new Class<?>[]{int.class}, round % 3);
            shadowOf(Looper.getMainLooper()).idle();
            call(activity, "showDetailsDialog");
            AlertDialog details = latestDialog();
            assertTrue(allText(details.getWindow().getDecorView()).contains(
                    "Not vegetarian?  www.bloodguiltcurse.net will change your mind."));
            details.dismiss();
            call(activity, "showAboutDialog");
            AlertDialog about = latestDialog();
            findButton(about.getWindow().getDecorView(), "Read license offline").performClick();
            AlertDialog license = latestDialog();
            String licenseText = allText(license.getWindow().getDecorView());
            assertTrue(licenseText.contains("Insha-Allah License Agreement"));
            assertTrue("license was truncated", licenseText.contains("8.3 Supremacy of Consciousness"));
            license.dismiss();
            about.dismiss();
            call(activity, "showStationScreen");
            EditText filter = (EditText) field(activity, "stationFilterInput");
            filter.setText("MTR");
            assertEquals(1, ((BaseAdapter) field(activity, "stationAdapter")).getCount());
            filter.setText("zzzz-not-an-office");
            assertEquals(0, ((BaseAdapter) field(activity, "stationAdapter")).getCount());
            filter.setText("");
            assertTrue(((BaseAdapter) field(activity, "stationAdapter")).getCount() > 100);
            call(activity, "confirmUpdateStationDataFromNws");
            latestDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
            activity.onBackPressed();
            call(activity, "confirmFetchWeather", new Class<?>[]{int.class, boolean.class}, round % 3, false);
            latestDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(originalCache, prefs.getString(CACHE, null));
            assertEquals(1, pendingStatusCallbacks(activity));
            assertPassiveNetworkState();
        }
    }

    @Test
    public void ageRefreshAndResumePreserveTheChosenReportAndScroll() throws Exception {
        JSONObject weather = weatherJson("MTR", "saved report");
        weather.put("dailyText", "Clear skies; moderate afternoon wind.\n".repeat(300));
        seed("MTR", weather);
        ActivityController<MainActivity> controller = launch();
        MainActivity activity = controller.get();
        call(activity, "showView", new Class<?>[]{int.class}, 2);
        shadowOf(Looper.getMainLooper()).idle();
        ScrollView scroll = (ScrollView) field(activity, "scrollView");
        scroll.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        scroll.layout(0, 0, 600, 400);
        scroll.scrollTo(0, 300);
        assertTrue("fixture must actually scroll", scroll.getScrollY() > 0);
        int position = scroll.getScrollY();
        String report = contentText(activity);
        for (int visit = 0; visit < 100; visit++) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));
            controller.pause().resume().postResume();
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(2, field(activity, "currentView"));
            assertEquals(report, contentText(activity));
            assertEquals("age refresh moved the reading position", position, scroll.getScrollY());
            assertEquals(1, pendingStatusCallbacks(activity));
            assertPassiveNetworkState();
        }
    }

    @Test
    public void coldReopenCannotReplayAnOldCompletionUnderAnotherOffice() throws Exception {
        ActivityController<MainActivity> first = launch();
        MainActivity activity = first.get();
        Object bundle = callStatic(nested("WeatherBundle"), "fromJson", new Class<?>[]{String.class},
                prefs.getString(CACHE, ""));
        SingleFlightGate gate = (SingleFlightGate) field(coordinator(), "gate");
        long token = gate.tryStart();
        assertNotEquals(0L, token);
        call(coordinator(), "completeWeather",
                new Class<?>[]{long.class, int.class, boolean.class, nested("WeatherBundle"), String.class},
                token, 0, false, bundle, "");
        shadowOf(Looper.getMainLooper()).idle();
        Object sgx = callStatic(nested("StationRecord"), "fromSelectionJson",
                new Class<?>[]{JSONObject.class}, selection("SGX"));
        call(activity, "selectStation", new Class<?>[]{nested("StationRecord")}, sgx);
        assertNull(field(activity, "currentBundle"));
        close(first);

        ActivityController<MainActivity> reopened = launch();
        assertEquals("SGX", field(field(reopened.get(), "selectedStation"), "stationIdentifier"));
        assertNull("old MTR completion was replayed under SGX", field(reopened.get(), "currentBundle"));
        assertFalse(((TextView) field(reopened.get(), "contentView")).getText().toString().contains("MTR saved weather"));
    }

    @Test
    public void completionAfterFreshCreateBeforeStartIsDelivered() throws Exception {
        // Exercise the production worker-completion boundary without starting an HTTP request.
        long oldToken = beginPendingWeather(0);
        finishWeather(oldToken, 0, weatherJson("MTR", "older completion"));
        shadowOf(Looper.getMainLooper()).idle();
        long token = beginPendingWeather(2);
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create();
        open.add(controller);
        MainActivity activity = controller.get();
        assertTrue(contentText(activity).contains("older completion"));

        finishWeather(token, 2, weatherJson("MTR", "completion during startup"));
        controller.start().resume().postResume().visible();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(2, field(activity, "currentView"));
        assertTrue(contentText(activity).contains("completion during startup"));
        assertEquals(field(coordinator(), "eventId"), field(activity, "handledNetworkEvent"));
        assertEquals(1, pendingStatusCallbacks(activity));
    }

    @Test
    public void completionDuringTheStartupCacheReadIsNotLost() throws Exception {
        long token = beginPendingWeather(2);
        ActivityController<CacheReadRaceActivity> controller = Robolectric.buildActivity(CacheReadRaceActivity.class);
        open.add(controller);
        MainActivity activity = controller.get();
        controller.get().afterCacheRead = () -> {
            try {
                finishWeather(token, 2, weatherJson("MTR", "completed during cache read"));
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        };
        controller.create();
        assertEquals(1L, field(coordinator(), "eventId"));
        assertTrue("fixture should have read the old cached report", contentText(activity).contains("MTR saved weather"));
        controller.start().resume().postResume().visible();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(2, field(activity, "currentView"));
        assertTrue("completion after the cursor was captured was lost",
                contentText(activity).contains("completed during cache read"));
        assertEquals(1L, field(activity, "handledNetworkEvent"));
    }

    @Test
    public void completionWhileStoppedIsDeliveredOnceOnRestart() throws Exception {
        ActivityController<MainActivity> controller = launch();
        MainActivity activity = controller.get();
        long token = beginPendingWeather(2);
        shadowOf(Looper.getMainLooper()).idle();
        controller.pause().stop();
        assertNull(call(field(coordinator(), "listener"), "get"));
        finishWeather(token, 2, weatherJson("MTR", "completed while stopped"));
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(contentText(activity).contains("completed while stopped"));

        controller.restart().start().resume().postResume();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(2, field(activity, "currentView"));
        assertTrue(contentText(activity).contains("completed while stopped"));
        call(activity, "showView", new Class<?>[]{int.class}, 1);
        shadowOf(Looper.getMainLooper()).idle();
        String chosenReport = contentText(activity);
        for (int visit = 0; visit < 100; visit++) {
            controller.pause().stop().restart().start().resume().postResume();
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals("completion was replayed on restart", 1, field(activity, "currentView"));
            assertEquals(chosenReport, contentText(activity));
            assertEquals(1, pendingStatusCallbacks(activity));
        }
        assertEquals(1L, field(coordinator(), "eventId"));
        assertFalse(((SingleFlightGate) field(coordinator(), "gate")).isRunning());
    }

    @Test
    public void inFlightCompletionFollowsFortyActivityRecreations() throws Exception {
        ActivityController<MainActivity> controller = launch();
        long token = beginPendingWeather(2);
        shadowOf(Looper.getMainLooper()).idle();
        for (int rotation = 0; rotation < 40; rotation++) {
            MainActivity previous = controller.get();
            controller.recreate();
            shadowOf(Looper.getMainLooper()).idle();
            MainActivity current = controller.get();
            assertNotSame(previous, current);
            assertEquals(Boolean.TRUE, field(previous, "destroyed"));
            assertEquals(0, pendingStatusCallbacks(previous));
            assertSame(current, call(field(coordinator(), "listener"), "get"));
            assertEquals(2, field(current, "currentView"));
            assertEquals(1, pendingStatusCallbacks(current));
            assertTrue(((SingleFlightGate) field(coordinator(), "gate")).isRunning());
        }
        finishWeather(token, 2, weatherJson("MTR", "completion after forty rotations"));
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(contentText(controller.get()).contains("completion after forty rotations"));
        assertEquals(2, field(controller.get(), "currentView"));
        assertEquals(1L, field(coordinator(), "eventId"));
        assertFalse(((SingleFlightGate) field(coordinator(), "gate")).isRunning());
    }

    @Test
    public void resultForDifferentOfficeCannotReplaceSelectedOffice() throws Exception {
        ActivityController<MainActivity> controller = launch();
        MainActivity activity = controller.get();
        call(activity, "showStationScreen");
        long token = beginPendingWeather(2);
        shadowOf(Looper.getMainLooper()).idle();
        // The actual office list remains available while an existing office fetch runs.
        EditText filter = (EditText) field(activity, "stationFilterInput");
        assertTrue(filter.isEnabled());
        filter.setText("SGX");
        shadowOf(Looper.getMainLooper()).idle();
        ListView offices = findView(activity.getWindow().getDecorView(), ListView.class);
        assertNotNull(offices);
        assertEquals(1, offices.getAdapter().getCount());
        assertTrue(offices.performItemClick(offices.getChildAt(0), 0, offices.getAdapter().getItemId(0)));
        assertNull(field(activity, "currentBundle"));
        assertEquals("SGX", field(field(activity, "selectedStation"), "stationIdentifier"));
        controller.pause().stop().restart().start().resume().postResume();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("fixture must show pending work", contentText(activity).startsWith("Fetching "));
        finishWeather(token, 2, weatherJson("MTR", "different office completion"));
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("SGX", field(field(activity, "selectedStation"), "stationIdentifier"));
        assertNull("mismatched office result replaced the selected office", field(activity, "currentBundle"));
        assertFalse(contentText(activity).contains("different office completion"));
        assertFalse("completed mismatched fetch left permanent progress text", contentText(activity).startsWith("Fetching "));
        assertFalse(((SingleFlightGate) field(coordinator(), "gate")).isRunning());
    }

    @Test
    public void clockCenturyBoundariesAndOldCachesRemainReadable() throws Exception {
        ActivityController<MainActivity> controller = launch();
        MainActivity activity = controller.get();
        assertEquals("none", call(activity, "cacheAge", new Class<?>[]{String.class}, ""));
        assertEquals("unknown", call(activity, "cacheAge", new Class<?>[]{String.class}, "not a timestamp"));
        for (String timestamp : new String[]{
                "1900-01-01T00:00:00Z", "2000-02-29T23:59:59-08:00",
                "2038-01-19T03:14:07Z", "2100-03-01T00:00:00+14:00",
                "+999999999-12-31T23:59:59Z", "-999999999-01-01T00:00:00Z"}) {
            String age = (String) call(activity, "cacheAge", new Class<?>[]{String.class}, timestamp);
            assertTrue("unreadable age for " + timestamp + ": " + age,
                    age.matches("[0-9]+m|[0-9]+h [0-9]+m|[0-9]+d [0-9]+h"));
        }
        Object bundle = field(activity, "currentBundle");
        set(bundle, "afdFetchedAt", OffsetDateTime.now().minusYears(10).toString());
        controller.pause().resume().postResume();
        shadowOf(Looper.getMainLooper()).idle();
        String status = ((TextView) field(activity, "statusView")).getText().toString();
        assertTrue("old cache age was not refreshed on resume", status.matches("Cache Age: [0-9]{4,}d [0-9]+h"));
        assertPassiveNetworkState();
    }

    private ActivityController<MainActivity> launch() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        open.add(controller);
        shadowOf(Looper.getMainLooper()).idle();
        return controller;
    }

    private WeakReference<MainActivity> createAndCloseForCollection() throws Exception {
        ActivityController<MainActivity> controller = launch();
        MainActivity activity = controller.get();
        WeakReference<MainActivity> reference = new WeakReference<>(activity);
        close(controller);
        assertEquals(0, pendingStatusCallbacks(activity));
        assertEquals(Boolean.FALSE, field(activity, "statusRefreshActive"));
        assertNull(call(field(coordinator(), "listener"), "get"));
        return reference;
    }

    private void close(ActivityController<? extends MainActivity> controller) {
        controller.close();
        open.remove(controller);
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** Inject only a deterministic completion boundary; inherit all real Activity logic. */
    public static class CacheReadRaceActivity extends MainActivity {
        Runnable afterCacheRead;

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            SharedPreferences delegate = super.getSharedPreferences(name, mode);
            if (!PREFS.equals(name)) return delegate;
            return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                    new Class<?>[]{SharedPreferences.class}, (proxy, method, arguments) -> {
                        try {
                            Object result = method.invoke(delegate, arguments);
                            if ("getString".equals(method.getName()) && CACHE.equals(arguments[0])
                                    && afterCacheRead != null) {
                                Runnable completion = afterCacheRead;
                                afterCacheRead = null;
                                completion.run();
                            }
                            return result;
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        }
    }

    private void seed(String office, JSONObject weather) throws Exception {
        assertTrue(prefs.edit().putString(SELECTION, selection(office).toString())
                .putString(CACHE, weather.toString()).commit());
    }

    private static long beginPendingWeather(int view) throws Exception {
        Object coordinator = coordinator();
        synchronized (coordinator) {
            long token = ((SingleFlightGate) field(coordinator, "gate")).tryStart();
            assertNotEquals(0L, token);
            call(coordinator, "clearCompletionLocked");
            set(coordinator, "activeOperation", 1);
            set(coordinator, "activeView", view);
            call(coordinator, "postSnapshot");
            return token;
        }
    }

    private void finishWeather(long token, int view, JSONObject weather) throws Exception {
        String json = weather.toString();
        assertTrue(prefs.edit().putString(CACHE, json).commit());
        Object bundle = callStatic(nested("WeatherBundle"), "fromJson", new Class<?>[]{String.class}, json);
        call(coordinator(), "completeWeather",
                new Class<?>[]{long.class, int.class, boolean.class, nested("WeatherBundle"), String.class},
                token, view, false, bundle, "");
    }

    private static String contentText(MainActivity activity) throws Exception {
        return ((TextView) field(activity, "contentView")).getText().toString();
    }

    private static JSONObject selection(String office) throws Exception {
        return new JSONObject().put("stationIdentifier", office)
                .put("name", "MTR".equals(office) ? "San Francisco Bay Area/Monterey" : "San Diego")
                .put("lat", "MTR".equals(office) ? "36.6002" : "32.7157")
                .put("lon", "MTR".equals(office) ? "-121.8947" : "-117.1611");
    }

    private static JSONObject weatherJson(String office, String marker) throws Exception {
        String timestamp = OffsetDateTime.now().minusHours(2).toString();
        return new JSONObject().put("stationIdentifier", office).put("stationName", "Monterey")
                .put("office", office).put("lat", "36.6002").put("lon", "-121.8947")
                .put("fetchedAt", timestamp).put("afdFetchedAt", timestamp)
                .put("hourlyFetchedAt", timestamp).put("dailyFetchedAt", timestamp)
                .put("afdUpdated", timestamp).put("hourlyUpdated", timestamp).put("dailyUpdated", timestamp)
                .put("hourlyLat", "36.6002").put("hourlyLon", "-121.8947")
                .put("dailyLat", "36.6002").put("dailyLon", "-121.8947")
                .put("hourlyLocationName", "Monterey, CA").put("dailyLocationName", "Monterey, CA")
                .put("hourlyPointOffice", office).put("dailyPointOffice", office)
                .put("afdText", "Key Messages\n------------\n" + marker + ": dry weather.\n")
                .put("hourlyText", marker + ": hourly clear skies.\n\n")
                .put("dailyText", marker + ": daily clear skies.\n\n");
    }

    private static void assertPassiveNetworkState() throws Exception {
        assertEquals("passive UI started a network operation", 0L, field(coordinator(), "eventId"));
        assertEquals(0, field(coordinator(), "activeOperation"));
        assertFalse(((SingleFlightGate) field(coordinator(), "gate")).isRunning());
    }

    private static int pendingStatusCallbacks(MainActivity activity) throws Exception {
        Handler handler = (Handler) field(activity, "statusHandler");
        Runnable callback = (Runnable) field(activity, "statusRefresh");
        Object next = field(Looper.getMainLooper().getQueue(), "mMessages");
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        int count = 0;
        while (next != null) {
            assertNull("message queue cycle", seen.put(next, Boolean.TRUE));
            Message message = (Message) next;
            if (message.getTarget() == handler && message.getCallback() == callback) count++;
            next = field(message, "next");
        }
        return count;
    }

    private static AlertDialog latestDialog() {
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        return dialog;
    }

    private static Button findButton(View view, String text) {
        if (view instanceof Button && text.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button result = findButton(group.getChildAt(i), text);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static <T extends View> T findView(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T result = findView(group.getChildAt(i), type);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static String allText(View view) {
        StringBuilder text = new StringBuilder();
        if (view instanceof TextView) text.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) text.append(allText(group.getChildAt(i)));
        }
        return text.toString();
    }

    private static Class<?> nested(String name) throws Exception {
        return Class.forName(MainActivity.class.getName() + "$" + name);
    }

    private static Object coordinator() throws Exception {
        return field(MainActivity.class, null, "NETWORK_COORDINATOR");
    }

    private static Object field(Object object, String name) throws Exception {
        return field(object.getClass(), object, name);
    }

    private static Object field(Class<?> type, Object object, String name) throws Exception {
        Field member = declaredField(type, name);
        return member.get(object);
    }

    private static Field declaredField(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field member = current.getDeclaredField(name);
                member.setAccessible(true);
                return member;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        declaredField(object.getClass(), name).set(object, value);
    }

    private static Object call(Object object, String name) throws Exception {
        return call(object, name, new Class<?>[0]);
    }

    private static Object call(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(object, args);
    }

    private static Object callStatic(Class<?> type, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = type.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
