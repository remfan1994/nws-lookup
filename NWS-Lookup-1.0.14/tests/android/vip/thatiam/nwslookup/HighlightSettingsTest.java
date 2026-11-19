package vip.thatiam.nwslookup;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
import org.robolectric.shadows.ShadowPopupMenu;

/** Focused real-Activity tests. All weather is a local fixture; no HTTP request is started. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class HighlightSettingsTest {
    private static final String PREFS = "nws_lookup_cache";
    private static final String TERMS = "highlight_terms";
    private static final String CACHE = "cache_json";
    private static final String SELECTION = "selected_station_json";
    private static final String WEATHER_TEXT = "RAIN, rain; wind. PWAT and PWATS: precipitable water.\n"
            + "Train, brain, rainfall, snow, heat and dryness.\n";
    private static final List<String> PRESET_LABELS = Arrays.asList(
            "Rain and precipitation", "Wind", "Snow and ice", "Heat");

    private final List<ActivityController<MainActivity>> open = new ArrayList<>();
    private SharedPreferences prefs;
    private Map<String, ?> unchangedPreferences;

    @Before
    public void seedOfflineWeather() throws Exception {
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
        Object listener = field(coordinator, "listener");
        Object previous = call(listener, "get");
        if (previous != null) call(listener, "detach", new Class<?>[]{Object.class}, previous);
        assertFalse(((SingleFlightGate) field(coordinator, "gate")).isRunning());
        seedWeather(WEATHER_TEXT);
    }

    @After
    public void closeAndCheckOfflineInvariants() throws Exception {
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        PopupMenu popup = ShadowPopupMenu.getLatestPopupMenu();
        if (popup != null) popup.dismiss();
        for (ActivityController<MainActivity> controller : new ArrayList<>(open)) close(controller);
        idle();
        Map<String, ?> actual = new HashMap<>(prefs.getAll());
        actual.remove(TERMS);
        assertEquals("highlight settings changed cached weather or unrelated preferences",
                unchangedPreferences, actual);
        assertEquals("passive highlight UI produced a network completion", 0L, field(coordinator(), "eventId"));
        assertEquals("passive highlight UI started a network operation", 0, field(coordinator(), "activeOperation"));
        assertFalse("passive highlight UI started a worker",
                ((SingleFlightGate) field(coordinator(), "gate")).isRunning());
    }

    @Test
    public void anExistingInstallWithNoHighlightPreferenceHasNoAutomaticPrecipitationEmphasis() throws Exception {
        assertFalse(prefs.contains(TERMS));
        MainActivity activity = launch().get();
        for (int view = 0; view < 3; view++) {
            showView(activity, view);
            assertTrue(content(activity).getText().toString().contains("RAIN"));
            assertNoHighlights(content(activity));
        }
        AlertDialog editor = openEditor(activity);
        assertEquals("", input(editor).getText().toString());
        click(editor, "Cancel");
        assertFalse("opening settings must not opt in", prefs.contains(TERMS));
    }

    @Test
    public void aFreshInstallAndAnEmptyReportStayUnstyled() throws Exception {
        assertTrue(prefs.edit().remove(CACHE).remove(SELECTION).commit());
        rememberUnchangedPreferences();
        MainActivity activity = launch().get();
        assertNoHighlights(content(activity));
        AlertDialog editor = openEditor(activity);
        assertEquals("", input(editor).getText().toString());
        input(editor).setText("select, forecast, office, fetch, weather, no, cached, report");
        click(editor, "Save");
        for (int view = 0; view < 3; view++) {
            showView(activity, view);
            assertNoHighlights(content(activity));
        }
    }

    @Test
    public void detailsOpensAnAccessibleMultilineEditorWithAdjacentPresetButton() throws Exception {
        MainActivity activity = launch().get();
        Button detailsButton = (Button) field(activity, "detailsButton");
        assertTrue(detailsButton.performClick());
        AlertDialog details = latestDialog();
        Button entry = findButton(details.getWindow().getDecorView(), "Highlight terms");
        assertNotNull("Details must contain Highlight terms", entry);
        assertTrue(entry.performClick());
        idle();
        assertFalse(details.isShowing());
        AlertDialog editor = latestDialog();
        EditText text = input(editor);
        TextView header = findText(editor.getWindow().getDecorView(), "Highlight these terms:");
        Button presets = findButton(editor.getWindow().getDecorView(), "...");
        assertNotNull(header);
        assertNotNull("preset label must be the ASCII three dots", presets);
        assertSame("header and preset button must be adjacent", header.getParent(), presets.getParent());
        assertTrue(header.getParent() instanceof LinearLayout);
        assertEquals(LinearLayout.HORIZONTAL, ((LinearLayout) header.getParent()).getOrientation());
        assertEquals(text.getId(), header.getLabelFor());
        assertEquals("Add highlight preset", presets.getContentDescription());
        assertNotEquals(0, text.getInputType() & InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        for (String label : Arrays.asList("Save", "Cancel", "Clear")) {
            assertNotNull("missing editor action " + label, findButton(editor.getWindow().getDecorView(), label));
        }
        click(editor, "Cancel");
    }

    @Test
    public void saveNormalizesTermsAndStylesOnlyMatchingWordsInAllThreeReports() throws Exception {
        MainActivity activity = launch().get();
        AlertDialog editor = openEditor(activity);
        input(editor).setText("  rain, wind\nPWAT, precipitable   water, RAIN, \n");
        click(editor, "Save");
        assertFalse(editor.isShowing());
        assertEquals("rain\nwind\nPWAT\nprecipitable water", prefs.getString(TERMS, null));
        for (int view = 0; view < 3; view++) {
            showView(activity, view);
            assertHighlightedTexts(content(activity), "RAIN", "rain", "wind", "PWAT", "precipitable water");
            assertNoHighlights((TextView) field(activity, "statusView"));
            assertNoHighlights((TextView) field(activity, "forecastPointView"));
        }
    }

    @Test
    public void cancelAndBackLeaveTheSavedSettingAndCurrentReportUnchanged() throws Exception {
        MainActivity activity = launch().get();
        saveTerms(activity, "rain");
        String report = content(activity).getText().toString();
        AlertDialog editor = openEditor(activity);
        input(editor).setText("wind, heat");
        click(editor, "Cancel");
        assertEquals("rain", prefs.getString(TERMS, null));
        assertHighlightedTexts(content(activity), "RAIN", "rain");
        editor = openEditor(activity);
        input(editor).setText("PWATS");
        editor.onBackPressed();
        idle();
        assertFalse(editor.isShowing());
        assertEquals("rain", prefs.getString(TERMS, null));
        assertEquals(report, content(activity).getText().toString());
        assertHighlightedTexts(content(activity), "RAIN", "rain");
    }

    @Test
    public void clearIsAnEditableDraftAndSavingAnEmptyListTurnsHighlightsOff() throws Exception {
        MainActivity activity = launch().get();
        saveTerms(activity, "rain");
        AlertDialog editor = openEditor(activity);
        click(editor, "Clear");
        assertTrue("Clear must keep the editor open", editor.isShowing());
        assertEquals("", input(editor).getText().toString());
        assertEquals("Clear must wait for Save", "rain", prefs.getString(TERMS, null));
        assertHighlightedTexts(content(activity), "RAIN", "rain");
        click(editor, "Cancel");
        assertEquals("rain", prefs.getString(TERMS, null));

        editor = openEditor(activity);
        click(editor, "Clear");
        click(editor, "Save");
        assertEquals("", prefs.getString(TERMS, ""));
        assertNoHighlights(content(activity));

        saveTerms(activity, "wind");
        saveTerms(activity, " , \n\r\n ,   ");
        assertEquals("", prefs.getString(TERMS, ""));
        assertNoHighlights(content(activity));
    }

    @Test
    public void presetsAppendEditableTermsAndRepeatedSelectionDoesNotDuplicateThem() throws Exception {
        MainActivity activity = launch().get();
        AlertDialog editor = openEditor(activity);
        input(editor).setText("My custom phrase, RAIN");
        PopupMenu popup = openPresetMenu(editor);
        List<String> menuLabels = new ArrayList<>();
        for (int i = 0; i < popup.getMenu().size(); i++) {
            menuLabels.add(popup.getMenu().getItem(i).getTitle().toString());
        }
        assertEquals(PRESET_LABELS, menuLabels);
        choosePreset(popup, "Rain and precipitation");
        String once = input(editor).getText().toString();
        List<String> terms = draftTerms(once);
        assertEquals("My custom phrase", terms.get(0));
        assertEquals("RAIN", terms.get(1));
        assertContainsIgnoreCase(terms, "PWAT");
        assertContainsIgnoreCase(terms, "PWATS");
        assertContainsIgnoreCase(terms, "precipitable water");
        choosePreset(openPresetMenu(editor), "Rain and precipitation");
        assertEquals("repeated preset selection must be idempotent", once, input(editor).getText().toString());
        for (String preset : PRESET_LABELS.subList(1, PRESET_LABELS.size())) {
            choosePreset(openPresetMenu(editor), preset);
        }
        terms = draftTerms(input(editor).getText().toString());
        assertContainsIgnoreCase(terms, "My custom phrase");
        assertContainsIgnoreCase(terms, "wind");
        assertContainsIgnoreCase(terms, "snow");
        assertContainsIgnoreCase(terms, "heat");
        Set<String> unique = new LinkedHashSet<>();
        for (String term : terms) assertTrue("duplicate preset term " + term, unique.add(term.toLowerCase(Locale.ROOT)));
        assertFalse("presets must remain drafts until Save", prefs.contains(TERMS));
        assertNoHighlights(content(activity));

        // A preset is ordinary editable text; removing its PWATS entry is honored on Save.
        terms.removeIf(term -> "PWATS".equalsIgnoreCase(term));
        input(editor).setText(String.join("\n", terms));
        click(editor, "Save");
        assertFalse(draftTerms(prefs.getString(TERMS, "")).stream().anyMatch("PWATS"::equalsIgnoreCase));
        assertFalse(highlightedTexts(content(activity)).contains("PWATS"));
        assertTrue(highlightedTexts(content(activity)).contains("PWAT"));
    }

    @Test
    public void savedTermsSurviveActivityRecreationAndAColdReopen() throws Exception {
        ActivityController<MainActivity> controller = launch();
        saveTerms(controller.get(), "rain\nPWATS");
        controller.recreate();
        idle();
        assertHighlightedTexts(content(controller.get()), "RAIN", "rain", "PWATS");
        AlertDialog editor = openEditor(controller.get());
        assertEquals("rain\nPWATS", input(editor).getText().toString());
        click(editor, "Cancel");
        close(controller);
        MainActivity reopened = launch().get();
        assertHighlightedTexts(content(reopened), "RAIN", "rain", "PWATS");
        assertEquals("rain\nPWATS", prefs.getString(TERMS, null));
    }

    @Test
    public void unsavedAndClearedDraftsSurviveRotationWithoutApplyingThemselves() throws Exception {
        ActivityController<MainActivity> controller = launch();
        saveTerms(controller.get(), "rain");
        showView(controller.get(), 1);
        String originalReport = content(controller.get()).getText().toString();
        for (String draft : Arrays.asList("wind, heat\nPWATS", "")) {
            AlertDialog previous = openEditor(controller.get());
            input(previous).setText(draft);
            int cursor = Math.min(4, draft.length());
            input(previous).setSelection(cursor);
            controller.recreate();
            idle();
            AlertDialog restored = latestDialog();
            assertNotSame("rotation must attach a new dialog to the new Activity", previous, restored);
            assertEquals(draft, input(restored).getText().toString());
            assertEquals(cursor, input(restored).getSelectionStart());
            assertEquals("rain", prefs.getString(TERMS, null));
            assertEquals(1, field(controller.get(), "currentView"));
            assertEquals(originalReport, content(controller.get()).getText().toString());
            assertHighlightedTexts(content(controller.get()), "RAIN", "rain");
            click(restored, "Cancel");
        }
    }

    @Test
    public void invalidDraftsKeepTheEditorOpenAndPreserveTheSavedSetting() throws Exception {
        MainActivity activity = launch().get();
        saveTerms(activity, "rain");
        List<String> distinctTerms = new ArrayList<>();
        for (int i = 0; i <= HighlightTerms.MAX_TERMS; i++) distinctTerms.add("term" + i);
        String tooMany = String.join(",", distinctTerms);
        String longTerm = "x".repeat(HighlightTerms.MAX_TERM_CHARS + 1);
        String oversized = " ".repeat(HighlightTerms.MAX_INPUT_CHARS + 1);
        for (String invalid : Arrays.asList(tooMany, longTerm, oversized)) {
            AlertDialog editor = openEditor(activity);
            EditText text = input(editor);
            String previousDraft = text.getText().toString();
            text.getText().replace(0, text.length(), invalid);
            if (invalid.length() > HighlightTerms.MAX_INPUT_CHARS) {
                assertEquals("an oversized paste must preserve the previous draft as a whole",
                        previousDraft, text.getText().toString());
            }
            click(editor, "Save");
            assertTrue("invalid input must not dismiss the editor", editor.isShowing());
            assertNotNull("validation must explain the problem beside the editor", input(editor).getError());
            assertEquals("rain", prefs.getString(TERMS, null));
            assertHighlightedTexts(content(activity), "RAIN", "rain");
            input(editor).setText("wind");
            click(editor, "Save");
            assertFalse(editor.isShowing());
            assertEquals("wind", prefs.getString(TERMS, null));
            saveTerms(activity, "rain");
        }
    }

    @Test
    public void malformedStoredHighlightPreferencesFailClosedAndCanBeRepaired() throws Exception {
        for (Object corrupt : Arrays.asList(17, "x".repeat(HighlightTerms.MAX_TERM_CHARS + 1),
                " ".repeat(HighlightTerms.MAX_INPUT_CHARS + 1))) {
            SharedPreferences.Editor write = prefs.edit();
            if (corrupt instanceof Integer) write.putInt(TERMS, (Integer) corrupt);
            else write.putString(TERMS, (String) corrupt);
            assertTrue(write.commit());
            ActivityController<MainActivity> controller = launch();
            assertNoHighlights(content(controller.get()));
            AlertDialog editor = openEditor(controller.get());
            assertEquals("", input(editor).getText().toString());
            input(editor).setText("wind");
            click(editor, "Save");
            assertEquals("wind", prefs.getString(TERMS, null));
            assertHighlightedTexts(content(controller.get()), "wind");
            close(controller);
        }
    }

    @Test
    public void applyingAndDisablingHighlightsPreservesTheChosenViewTextAndReadingPosition() throws Exception {
        seedWeather(WEATHER_TEXT.repeat(150));
        MainActivity activity = launch().get();
        showView(activity, 2);
        ScrollView scroll = (ScrollView) field(activity, "scrollView");
        TextView originalView = content(activity);
        Object originalBundle = field(activity, "currentBundle");
        String originalText = originalView.getText().toString();
        String clipboardText = (String) call(activity, "clipboardDataText");
        layoutScroll(scroll);
        scroll.scrollTo(0, 600);
        int position = scroll.getScrollY();
        assertTrue("fixture must actually have a reading position", position > 0);

        for (String terms : Arrays.asList("rain, wind", "")) {
            saveTerms(activity, terms);
            layoutScroll(scroll);
            idle();
            assertEquals(2, field(activity, "currentView"));
            assertSame(originalView, content(activity));
            assertSame("cosmetic setting replaced the weather bundle", originalBundle, field(activity, "currentBundle"));
            assertEquals(originalText, content(activity).getText().toString());
            assertEquals("applying highlights moved the reading position", position, scroll.getScrollY());
            assertEquals("cosmetic setting modified copied weather", clipboardText, call(activity, "clipboardDataText"));
        }
        assertNoHighlights(content(activity));
    }

    @Test
    public void repeatedReportRendersNeverAccumulateSpans() throws Exception {
        MainActivity activity = launch().get();
        saveTerms(activity, "rain");
        for (int render = 0; render < 30; render++) {
            showView(activity, render % 3);
            assertHighlightedTexts(content(activity), "RAIN", "rain");
        }
        saveTerms(activity, "wind");
        assertHighlightedTexts(content(activity), "wind");
        saveTerms(activity, "");
        assertNoHighlights(content(activity));
        showView(activity, 0);
        assertNoHighlights(content(activity));
    }

    @Test
    public void reportSpanLimitStaysBoundedAcrossRepeatedRendering() throws Exception {
        seedWeather("rain ".repeat(HighlightTerms.MAX_SPANS + 50));
        MainActivity activity = launch().get();
        List<String> originalReports = new ArrayList<>();
        for (int view = 0; view < 3; view++) {
            showView(activity, view);
            originalReports.add(content(activity).getText().toString());
        }
        saveTerms(activity, "rain");
        for (int view = 0; view < 3; view++) {
            showView(activity, view);
            assertEquals(HighlightTerms.MAX_SPANS, highlightedTexts(content(activity)).size());
            assertEquals("highlighting changed report characters", originalReports.get(view),
                    content(activity).getText().toString());
        }
    }

    @Test
    public void savingWhileFetchProgressOrAnErrorIsVisibleDoesNotReplaceOrStyleTheMessage() throws Exception {
        MainActivity activity = launch().get();
        deliverWeatherSnapshot(activity, true, "");
        String progress = content(activity).getText().toString();
        assertTrue(progress.startsWith("Fetching "));
        saveTerms(activity, "Fetching, forecast, weather, hourly, rain");
        assertEquals(progress, content(activity).getText().toString());
        assertNoHighlights(content(activity));

        deliverWeatherSnapshot(activity, false, "rain service unavailable");
        String failure = content(activity).getText().toString();
        assertTrue(failure.contains("fetch failed"));
        saveTerms(activity, "failed, rain, cache");
        assertEquals(failure, content(activity).getText().toString());
        assertNoHighlights(content(activity));
    }

    private ActivityController<MainActivity> launch() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        open.add(controller);
        idle();
        return controller;
    }

    private void close(ActivityController<MainActivity> controller) {
        controller.close();
        open.remove(controller);
        idle();
    }

    private static AlertDialog openEditor(MainActivity activity) throws Exception {
        assertTrue(((Button) field(activity, "detailsButton")).performClick());
        AlertDialog details = latestDialog();
        click(details, "Highlight terms");
        assertFalse(details.isShowing());
        return latestDialog();
    }

    private static void saveTerms(MainActivity activity, String terms) throws Exception {
        AlertDialog editor = openEditor(activity);
        input(editor).setText(terms);
        click(editor, "Save");
        assertFalse("valid setting did not save", editor.isShowing());
    }

    private static EditText input(AlertDialog editor) {
        EditText input = editor.findViewById(R.id.highlight_terms_input);
        assertNotNull("missing multiline terms editor", input);
        return input;
    }

    private static void click(AlertDialog dialog, String label) {
        Button button = findButton(dialog.getWindow().getDecorView(), label);
        assertNotNull("missing button " + label, button);
        assertTrue(button.performClick());
        idle();
    }

    private static PopupMenu openPresetMenu(AlertDialog editor) {
        click(editor, "...");
        PopupMenu popup = ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull("preset button must open a menu", popup);
        return popup;
    }

    private static void choosePreset(PopupMenu popup, String title) {
        Menu menu = popup.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (title.contentEquals(item.getTitle())) {
                assertTrue("preset menu did not handle selection", menu.performIdentifierAction(item.getItemId(), 0));
                popup.dismiss();
                idle();
                return;
            }
        }
        fail("missing preset " + title);
    }

    private static List<String> draftTerms(String value) {
        List<String> result = new ArrayList<>();
        for (String term : value.split("[,\\r\\n]")) if (!term.trim().isEmpty()) result.add(term.trim());
        return result;
    }

    private static void assertContainsIgnoreCase(List<String> values, String expected) {
        assertTrue("missing preset term " + expected, values.stream().anyMatch(expected::equalsIgnoreCase));
    }

    private static void assertNoHighlights(TextView view) {
        assertTrue("unexpected highlight spans", highlightedTexts(view).isEmpty());
    }

    private static void assertHighlightedTexts(TextView view, String... expected) {
        assertEquals(Arrays.asList(expected), highlightedTexts(view));
    }

    private static List<String> highlightedTexts(TextView view) {
        CharSequence value = view.getText();
        List<String> highlights = new ArrayList<>();
        if (!(value instanceof Spanned)) return highlights;
        Spanned spans = (Spanned) value;
        StyleSpan[] styles = spans.getSpans(0, spans.length(), StyleSpan.class);
        UnderlineSpan[] underlines = spans.getSpans(0, spans.length(), UnderlineSpan.class);
        assertEquals("every highlighted range must have exactly bold and underline", styles.length, underlines.length);
        Arrays.sort(styles, (first, second) -> Integer.compare(spans.getSpanStart(first), spans.getSpanStart(second)));
        Set<String> ranges = new LinkedHashSet<>();
        for (StyleSpan style : styles) {
            int start = spans.getSpanStart(style);
            int end = spans.getSpanEnd(style);
            assertEquals(Typeface.BOLD, style.getStyle());
            assertTrue("highlight must have a nonempty range", start >= 0 && end > start && end <= spans.length());
            assertTrue("duplicated style range", ranges.add(start + ":" + end));
            int paired = 0;
            for (UnderlineSpan underline : spans.getSpans(start, end, UnderlineSpan.class)) {
                if (spans.getSpanStart(underline) == start && spans.getSpanEnd(underline) == end) paired++;
            }
            assertEquals("bold and underline must cover the same text", 1, paired);
            highlights.add(value.subSequence(start, end).toString());
        }
        return highlights;
    }

    private static TextView content(MainActivity activity) throws Exception {
        return (TextView) field(activity, "contentView");
    }

    private static void showView(MainActivity activity, int view) throws Exception {
        call(activity, "showView", new Class<?>[]{int.class}, view);
        idle();
    }

    private static void layoutScroll(ScrollView scroll) {
        scroll.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        scroll.layout(0, 0, 600, 400);
        idle();
    }

    private void seedWeather(String report) throws Exception {
        String timestamp = OffsetDateTime.now().minusHours(2).toString();
        JSONObject selection = new JSONObject().put("stationIdentifier", "MTR")
                .put("name", "San Francisco Bay Area/Monterey").put("lat", "36.6002").put("lon", "-121.8947");
        JSONObject weather = new JSONObject().put("stationIdentifier", "MTR").put("stationName", "Monterey")
                .put("office", "MTR").put("lat", "36.6002").put("lon", "-121.8947")
                .put("fetchedAt", timestamp).put("afdFetchedAt", timestamp)
                .put("hourlyFetchedAt", timestamp).put("dailyFetchedAt", timestamp)
                .put("afdUpdated", timestamp).put("hourlyUpdated", timestamp).put("dailyUpdated", timestamp)
                .put("hourlyLat", "36.6002").put("hourlyLon", "-121.8947")
                .put("dailyLat", "36.6002").put("dailyLon", "-121.8947")
                .put("hourlyLocationName", "Monterey, CA").put("dailyLocationName", "Monterey, CA")
                .put("hourlyPointOffice", "MTR").put("dailyPointOffice", "MTR")
                .put("afdText", "Key Messages\n------------\n" + report)
                .put("hourlyText", report).put("dailyText", report);
        assertTrue(prefs.edit().putString(SELECTION, selection.toString()).putString(CACHE, weather.toString()).commit());
        rememberUnchangedPreferences();
    }

    private void rememberUnchangedPreferences() {
        Map<String, ?> expected = new HashMap<>(prefs.getAll());
        expected.remove(TERMS);
        unchangedPreferences = expected;
    }

    /** Deliver the actual UI boundary without starting a worker or altering cached weather. */
    private static void deliverWeatherSnapshot(MainActivity activity, boolean busy, String error) throws Exception {
        Class<?> type = nested("NetworkSnapshot");
        Constructor<?> constructor = type.getDeclaredConstructor(long.class, boolean.class, int.class, int.class,
                long.class, int.class, int.class, boolean.class, nested("WeatherBundle"), nested("StationListCache"), String.class);
        constructor.setAccessible(true);
        Object snapshot = constructor.newInstance((Long) field(coordinator(), "sessionId"), busy, busy ? 1 : 0, 1,
                busy ? 0L : 1L, busy ? 0 : 1, 1, false, null, null, error);
        call(activity, "onNetworkSnapshot", new Class<?>[]{type}, snapshot);
        idle();
    }

    private static AlertDialog latestDialog() {
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        return dialog;
    }

    private static Button findButton(View view, String text) {
        TextView found = findText(view, text);
        return found instanceof Button ? (Button) found : null;
    }

    private static TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView result = findText(group.getChildAt(i), text);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
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
        return declaredField(type, name).get(object);
    }

    private static Field declaredField(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        declaredField(object.getClass(), name).set(object, value);
    }

    private static Object call(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(object, args);
    }

    private static Object call(Object object, String name) throws Exception {
        return call(object, name, new Class<?>[0]);
    }
}
