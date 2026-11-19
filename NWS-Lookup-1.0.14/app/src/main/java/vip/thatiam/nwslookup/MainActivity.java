package vip.thatiam.nwslookup;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.text.TextWatcher;
import android.util.AtomicFile;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "nws_lookup_cache";
    private static final String KEY_SELECTED_STATION_JSON = "selected_station_json";
    private static final String KEY_CACHE_JSON = "cache_json";
    private static final String KEY_FONT_SIZE_STEP = "font_size_step";
    private static final String KEY_HIGHLIGHT_TERMS = "highlight_terms";
    private static final String OFFICE_CACHE_FILE = "nws_afd_location_cache.json";
    private static final String APP_VERSION = "1.0.14";
    private static final String USER_AGENT = "NWSLookup/" + APP_VERSION + " (personal-use)";
    private static final String NWS_HOST = "api.weather.gov";
    private static final int VIEW_AFD = 0;
    private static final int VIEW_HOURLY = 1;
    private static final int VIEW_DAILY = 2;
    private static final int FONT_STEP_SP = 2;
    private static final int MIN_FONT_SIZE_STEP = -2;
    private static final int MAX_FONT_SIZE_STEP = 6;
    private static final int MAX_HOURLY_PERIODS = 48;
    private static final int MAX_REPRESENTATIVE_STATION_ATTEMPTS = 8;
    private static final int MAX_NETWORK_RESPONSE_CHARS = 2 * 1024 * 1024;
    private static final int MAX_PRIVATE_FILE_CHARS = 4 * 1024 * 1024;
    private static final Object WEATHER_CACHE_LOCK = new Object();
    private static final Object PRIVATE_FILE_LOCK = new Object();
    private static final String STATE_NETWORK_SESSION = "network_session";
    private static final String STATE_NETWORK_EVENT = "network_event";
    private static final String STATE_HIGHLIGHT_EDITOR = "highlight_editor_open";
    private static final String STATE_HIGHLIGHT_DRAFT = "highlight_draft";
    private static final String STATE_HIGHLIGHT_SELECTION_START = "highlight_selection_start";
    private static final String STATE_HIGHLIGHT_SELECTION_END = "highlight_selection_end";
    private static final String STATE_HIGHLIGHT_REJECTED = "highlight_input_rejected";
    private static final String STATE_HIGHLIGHT_VIEW = "highlight_editor_report_view";
    private static final AppNetworkCoordinator NETWORK_COORDINATOR = new AppNetworkCoordinator();

    private static final String[] BUILTIN_OFFICES = new String[]{
            "ABQ|Albuquerque|35.0844|-106.6504",
            "ABR|Aberdeen|45.4647|-98.4865",
            "AFC|Anchorage|61.2181|-149.9003",
            "AFG|Fairbanks|64.8378|-147.7164",
            "AJK|Juneau|58.3019|-134.4197",
            "AKQ|Wakefield|36.9682|-77.0130",
            "ALY|Albany|42.6526|-73.7562",
            "AMA|Amarillo|35.2220|-101.8313",
            "APX|Gaylord|45.0275|-84.6748",
            "ARX|La Crosse|43.8138|-91.2519",
            "BGM|Binghamton|42.0987|-75.9180",
            "BIS|Bismarck|46.8083|-100.7837",
            "BMX|Birmingham|33.1029|-86.7536",
            "BOI|Boise|43.6150|-116.2023",
            "BOU|Denver/Boulder|40.0150|-105.2705",
            "BOX|Boston/Norton|41.9668|-71.1870",
            "BRO|Brownsville|25.9017|-97.4975",
            "BTV|Burlington|44.4759|-73.2121",
            "BUF|Buffalo|42.8864|-78.8784",
            "BYZ|Billings|45.7833|-108.5007",
            "CAE|Columbia|34.0007|-81.0348",
            "CAR|Caribou|46.8606|-68.0119",
            "CHS|Charleston SC|32.7765|-79.9311",
            "CLE|Cleveland|41.4993|-81.6944",
            "CRP|Corpus Christi|27.8006|-97.3964",
            "CTP|State College|40.7934|-77.8600",
            "CYS|Cheyenne|41.1400|-104.8202",
            "DDC|Dodge City|37.7528|-100.0171",
            "DLH|Duluth|46.7867|-92.1005",
            "DMX|Des Moines|41.5868|-93.6250",
            "DTX|Detroit/Pontiac|42.6389|-83.2910",
            "DVN|Davenport/Quad Cities|41.5236|-90.5776",
            "EAX|Kansas City/Pleasant Hill|38.7875|-94.2694",
            "EKA|Eureka|40.8021|-124.1637",
            "EPZ|El Paso|31.7619|-106.4850",
            "EWX|Austin/San Antonio|29.7030|-98.1245",
            "FFC|Peachtree City/Atlanta|33.3968|-84.5958",
            "FGF|Grand Forks|47.9253|-97.0329",
            "FGZ|Flagstaff|35.1983|-111.6513",
            "FSD|Sioux Falls|43.5446|-96.7311",
            "FWD|Fort Worth/Dallas|32.7555|-97.3308",
            "GGW|Glasgow|48.1969|-106.6367",
            "GID|Hastings|40.5861|-98.3884",
            "GJT|Grand Junction|39.0639|-108.5506",
            "GLD|Goodland|39.3508|-101.7102",
            "GRB|Green Bay|44.5133|-88.0133",
            "GRR|Grand Rapids|42.9634|-85.6681",
            "GSP|Greenville-Spartanburg|34.8957|-82.2189",
            "GUM|Guam|13.4443|144.7937",
            "GYX|Gray/Portland|43.8856|-70.3317",
            "HGX|Houston/Galveston|29.4608|-95.0513",
            "HFO|Honolulu|21.3069|-157.8583",
            "HNX|San Joaquin Valley/Hanford|36.3275|-119.6457",
            "HUN|Huntsville|34.7304|-86.5861",
            "ICT|Wichita|37.6872|-97.3301",
            "ILM|Wilmington NC|34.2104|-77.8868",
            "ILN|Wilmington OH|39.4453|-83.8285",
            "ILX|Lincoln|40.1484|-89.3648",
            "IND|Indianapolis|39.7684|-86.1581",
            "IWX|Northern Indiana|41.4278|-85.7525",
            "JAN|Jackson MS|32.2988|-90.1848",
            "JAX|Jacksonville|30.3322|-81.6557",
            "JKL|Jackson KY|37.5531|-83.3835",
            "KEY|Key West|24.5551|-81.7800",
            "LBF|North Platte|41.1403|-100.7601",
            "LCH|Lake Charles|30.2266|-93.2174",
            "LIX|New Orleans/Baton Rouge|30.2752|-89.7812",
            "LKN|Elko|40.8324|-115.7631",
            "LMK|Louisville|38.2527|-85.7585",
            "LOT|Chicago|41.6475|-88.0895",
            "LOX|Los Angeles/Oxnard|34.1975|-119.1771",
            "LSX|St. Louis|38.7881|-90.4974",
            "LUB|Lubbock|33.5779|-101.8552",
            "LWX|Baltimore/Washington|38.9847|-77.4565",
            "LZK|Little Rock|34.7465|-92.2896",
            "MAF|Midland/Odessa|31.9973|-102.0779",
            "MEG|Memphis|35.1495|-90.0490",
            "MFL|Miami|25.7617|-80.1918",
            "MFR|Medford|42.3265|-122.8756",
            "MHX|Newport/Morehead City|34.7865|-76.8591",
            "MKX|Milwaukee/Sullivan|43.0125|-88.5984",
            "MLB|Melbourne|28.0836|-80.6081",
            "MOB|Mobile/Pensacola|30.6954|-88.0399",
            "MPX|Chanhassen/Twin Cities|44.8619|-93.5308",
            "MQT|Marquette|46.5436|-87.3954",
            "MRX|Morristown/Knoxville|36.2140|-83.2949",
            "MSO|Missoula|46.8721|-113.9940",
            "MTR|San Francisco Bay Area/Monterey|36.6002|-121.8947",
            "OAX|Omaha/Valley|41.3128|-96.3461",
            "OHX|Nashville|36.2606|-86.7133",
            "OKX|New York/Upton|40.8695|-72.8868",
            "OUN|Norman/Oklahoma City|35.2226|-97.4395",
            "OTX|Spokane|47.6588|-117.4260",
            "PAH|Paducah|37.0834|-88.6000",
            "PBZ|Pittsburgh|40.4917|-80.2329",
            "PDT|Pendleton|45.6721|-118.7886",
            "PHI|Mount Holly/Philadelphia|39.9930|-74.7879",
            "PIH|Pocatello|42.8713|-112.4455",
            "PQR|Portland|45.5152|-122.6784",
            "PPG|Pago Pago|-14.2756|-170.7020",
            "PSR|Phoenix|33.4484|-112.0740",
            "PUB|Pueblo|38.2544|-104.6091",
            "RAH|Raleigh|35.7796|-78.6382",
            "REV|Reno|39.5296|-119.8138",
            "RIW|Riverton|43.0250|-108.3801",
            "RLX|Charleston WV|38.3498|-81.6326",
            "RNK|Blacksburg/Roanoke|37.2296|-80.4139",
            "SEW|Seattle|47.6062|-122.3321",
            "SGF|Springfield|37.2089|-93.2923",
            "SGX|San Diego|32.7157|-117.1611",
            "SHV|Shreveport|32.5252|-93.7502",
            "SJT|San Angelo|31.4638|-100.4370",
            "SJU|San Juan|18.4655|-66.1057",
            "SLC|Salt Lake City|40.7608|-111.8910",
            "STO|Sacramento|38.5816|-121.4944",
            "TAE|Tallahassee|30.4383|-84.2807",
            "TBW|Tampa|27.7209|-82.4332",
            "TFX|Great Falls|47.5053|-111.3008",
            "TOP|Topeka|39.0473|-95.6752",
            "TSA|Tulsa|36.1540|-95.9928",
            "TWC|Tucson|32.2226|-110.9747",
            "UNR|Rapid City|44.0805|-103.2310",
            "VEF|Las Vegas|36.1699|-115.1398"
    };

    private Button stationButton;
    private Button refreshButton;
    private Button afdButton;
    private Button hourlyButton;
    private Button dailyButton;
    private Button detailsButton;
    private TextView statusView;
    private TextView forecastPointView;
    private TextView contentView;
    private ScrollView scrollView;

    private EditText stationFilterInput;
    private Button updateStationsButton;
    private TextView stationStatusView;
    private StationAdapter stationAdapter;

    private SharedPreferences prefs;
    private StationRecord selectedStation;
    private WeatherBundle currentBundle;
    private int currentView = VIEW_AFD;
    private String currentStatusPrefix = "";
    private boolean stationScreenShowing = false;
    private int fontSizeStep = 0;
    private HighlightTerms highlightTerms = HighlightTerms.empty();
    private AlertDialog highlightDialog;
    private EditText highlightTermsInput;
    private boolean highlightInputRejected;
    private boolean destroyed = false;
    private long handledNetworkSession = 0L;
    private long handledNetworkEvent = 0L;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private boolean statusRefreshActive = false;
    private final Runnable statusRefresh = new Runnable() {
        @Override
        public void run() {
            if (!statusRefreshActive || !canUpdateUi()) return;
            if (!stationScreenShowing) renderStatus();
            statusHandler.postDelayed(this, 60_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        fontSizeStep = loadFontSizeStep();
        highlightTerms = loadHighlightTerms();
        if (savedInstanceState != null) {
            handledNetworkSession = savedInstanceState.getLong(STATE_NETWORK_SESSION, 0L);
            handledNetworkEvent = savedInstanceState.getLong(STATE_NETWORK_EVENT, 0L);
        } else {
            // Completed results are already saved. Capture before loading that cache so
            // a completion during startup is still delivered, without replaying old work.
            NETWORK_COORDINATOR.captureCursor(this);
        }
        selectedStation = loadSelectedStation();
        boolean restoreHighlightEditor = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_HIGHLIGHT_EDITOR, false);
        buildMainUi();
        loadCachedWeatherAtStartup();
        if (restoreHighlightEditor) {
            int savedView = savedInstanceState.getInt(STATE_HIGHLIGHT_VIEW, VIEW_AFD);
            if (savedView >= VIEW_AFD && savedView <= VIEW_DAILY && currentView != savedView) {
                currentView = savedView;
                renderCurrentView();
            }
            showHighlightTermsDialog(savedInstanceState.getString(STATE_HIGHLIGHT_DRAFT, ""),
                    savedInstanceState.getInt(STATE_HIGHLIGHT_SELECTION_START, -1),
                    savedInstanceState.getInt(STATE_HIGHLIGHT_SELECTION_END, -1),
                    savedInstanceState.getBoolean(STATE_HIGHLIGHT_REJECTED, false));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        NETWORK_COORDINATOR.attach(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        statusRefreshActive = true;
        statusHandler.removeCallbacks(statusRefresh);
        statusRefresh.run();
    }

    @Override
    protected void onPause() {
        stopStatusRefresh();
        super.onPause();
    }

    @Override
    protected void onStop() {
        stopStatusRefresh();
        NETWORK_COORDINATOR.detach(this);
        super.onStop();
    }

    private void stopStatusRefresh() {
        statusRefreshActive = false;
        statusHandler.removeCallbacks(statusRefresh);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putLong(STATE_NETWORK_SESSION, handledNetworkSession);
        outState.putLong(STATE_NETWORK_EVENT, handledNetworkEvent);
        if (highlightDialog != null && highlightDialog.isShowing() && highlightTermsInput != null) {
            outState.putBoolean(STATE_HIGHLIGHT_EDITOR, true);
            outState.putString(STATE_HIGHLIGHT_DRAFT, highlightTermsInput.getText().toString());
            outState.putInt(STATE_HIGHLIGHT_SELECTION_START, highlightTermsInput.getSelectionStart());
            outState.putInt(STATE_HIGHLIGHT_SELECTION_END, highlightTermsInput.getSelectionEnd());
            outState.putBoolean(STATE_HIGHLIGHT_REJECTED, highlightInputRejected);
            outState.putInt(STATE_HIGHLIGHT_VIEW, currentView);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        stopStatusRefresh();
        destroyed = true;
        if (highlightDialog != null) highlightDialog.dismiss();
        super.onDestroy();
    }

    private boolean canUpdateUi() {
        return !destroyed && !isFinishing() && !isDestroyed();
    }

    private boolean canUpdateStationScreen() {
        return canUpdateUi()
                && stationScreenShowing
                && stationAdapter != null
                && stationStatusView != null;
    }

    private void onNetworkSnapshot(NetworkSnapshot snapshot) {
        if (!canUpdateUi() || snapshot == null) return;

        setBusy(snapshot.busy);
        if (stationScreenShowing) {
            setStationBusy(snapshot.busy);
            if (snapshot.busy && snapshot.activeOperation == NetworkSnapshot.OP_STATIONS
                    && stationStatusView != null) {
                stationStatusView.setText("Updating WFO list from NWS...");
            }
        } else if (snapshot.busy && snapshot.activeOperation == NetworkSnapshot.OP_WEATHER
                && contentView != null) {
            currentView = snapshot.activeView;
            updateViewButtons();
            renderStatus();
            renderForecastPoint();
            contentView.setText(fetchProgressLabel(snapshot.activeView));
        }

        if (snapshot.eventId == 0L
                || (handledNetworkSession == snapshot.sessionId && handledNetworkEvent >= snapshot.eventId)) {
            return;
        }
        handledNetworkSession = snapshot.sessionId;
        handledNetworkEvent = snapshot.eventId;

        if (snapshot.completedOperation == NetworkSnapshot.OP_WEATHER) {
            if (snapshot.weatherBundle != null && (selectedStation == null
                    || !sameStation(snapshot.weatherBundle.stationIdentifier, selectedStation.stationIdentifier))) {
                if (!stationScreenShowing) renderCurrentView();
                return;
            }
            currentView = snapshot.completedView;
            if (snapshot.errorMessage.isEmpty() && snapshot.weatherBundle != null) {
                rememberResolvedStationPoint(snapshot.weatherBundle);
                if (snapshot.returnToMainAfterFetch || stationScreenShowing) buildMainUi();
                renderBundle(snapshot.weatherBundle, "");
            } else {
                String message = viewTitle(snapshot.completedView)
                        + " fetch failed. Existing cache was not changed. " + snapshot.errorMessage;
                if (stationScreenShowing && stationStatusView != null) {
                    stationStatusView.setText(message);
                } else {
                    currentStatusPrefix = "";
                    renderCurrentView();
                    if (contentView != null) contentView.setText(message);
                }
            }
            return;
        }

        if (snapshot.completedOperation == NetworkSnapshot.OP_STATIONS && canUpdateStationScreen()) {
            if (snapshot.errorMessage.isEmpty() && snapshot.stationCache != null) {
                stationAdapter.setRecords(snapshot.stationCache.records);
                if (stationFilterInput != null) stationAdapter.setFilter(stationFilterInput.getText().toString());
                stationStatusView.setText(stationListStatus(snapshot.stationCache));
            } else {
                stationStatusView.setText("WFO list update failed. Showing existing list. " + snapshot.errorMessage);
            }
        }
    }

    private void configureSystemBars() {
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attributes);
        }
    }

    private void setScreenContent(View root) {
        final int leftPadding = root.getPaddingLeft();
        final int topPadding = root.getPaddingTop();
        final int rightPadding = root.getPaddingRight();
        final int bottomPadding = root.getPaddingBottom();
        int background = themeColor(android.R.attr.colorBackground, Color.WHITE);
        root.setBackgroundColor(background);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                left = safe.left;
                top = safe.top;
                right = safe.right;
                bottom = safe.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= 28) {
                    DisplayCutout cutout = insets.getDisplayCutout();
                    if (cutout != null) {
                        left = Math.max(left, cutout.getSafeInsetLeft());
                        top = Math.max(top, cutout.getSafeInsetTop());
                        right = Math.max(right, cutout.getSafeInsetRight());
                        bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                    }
                }
            }
            view.setPadding(leftPadding + left, topPadding + top,
                    rightPadding + right, bottomPadding + bottom);
            return insets;
        });
        setContentView(root);
        boolean lightBackground = Color.luminance(background) > 0.5f;
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightBackground ? mask : 0, mask);
            }
        } else {
            View decor = getWindow().getDecorView();
            int mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            int flags = decor.getSystemUiVisibility() & ~mask;
            decor.setSystemUiVisibility(lightBackground ? flags | mask : flags);
        }
        root.requestApplyInsets();
    }

    private void buildMainUi() {
        stationScreenShowing = false;
        refreshButton = null;
        stationFilterInput = null;
        updateStationsButton = null;
        stationStatusView = null;
        stationAdapter = null;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setBaselineAligned(false);

        afdButton = makeTopButton("AFD");
        afdButton.setOnClickListener(view -> showView(VIEW_AFD));
        buttonRow.addView(afdButton, buttonRowParams(true));

        hourlyButton = makeTopButton("Hourly");
        hourlyButton.setOnClickListener(view -> showView(VIEW_HOURLY));
        buttonRow.addView(hourlyButton, buttonRowParams(true));

        dailyButton = makeTopButton("Daily");
        dailyButton.setOnClickListener(view -> showView(VIEW_DAILY));
        buttonRow.addView(dailyButton, buttonRowParams(false));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, 0);
        root.addView(buttonRow, rowParams);

        LinearLayout cacheRow = new LinearLayout(this);
        cacheRow.setOrientation(LinearLayout.HORIZONTAL);
        cacheRow.setBaselineAligned(false);

        statusView = new TextView(this);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, uiSizeSp(16));
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        statusView.setLineSpacing(0, 1.14f);
        statusView.setGravity(android.view.Gravity.CENTER_VERTICAL);
        cacheRow.addView(statusView, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        detailsButton = new Button(this);
        detailsButton.setText("...");
        detailsButton.setContentDescription("Details");
        detailsButton.setAllCaps(false);
        detailsButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, uiSizeSp(16));
        detailsButton.setMinHeight(dp(44));
        detailsButton.setMinWidth(dp(56));
        detailsButton.setOnClickListener(view -> showDetailsDialog());
        cacheRow.addView(detailsButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.setMargins(0, dp(8), 0, dp(10));
        root.addView(cacheRow, statusParams);

        forecastPointView = new TextView(this);
        forecastPointView.setTextSize(TypedValue.COMPLEX_UNIT_SP, uiSizeSp(14));
        forecastPointView.setTypeface(Typeface.DEFAULT_BOLD);
        forecastPointView.setLineSpacing(0, 1.12f);
        LinearLayout.LayoutParams pointParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pointParams.setMargins(0, 0, 0, dp(10));
        root.addView(forecastPointView, pointParams);

        contentView = new TextView(this);
        contentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, contentSizeSp(16));
        contentView.setLineSpacing(0, 1.18f);
        contentView.setTextIsSelectable(true);
        contentView.setIncludeFontPadding(true);

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.addView(contentView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        LinearLayout.LayoutParams fontParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        fontParams.setMargins(0, dp(8), 0, 0);
        root.addView(makeFontControls(), fontParams);

        setScreenContent(root);
        updateViewButtons();
    }

    private Button makeTopButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setMaxLines(2);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, uiSizeSp(16));
        button.setMinHeight(dp(48));
        return button;
    }

    private LinearLayout.LayoutParams buttonRowParams(boolean addRightMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        if (addRightMargin) params.setMargins(0, 0, dp(6), 0);
        return params;
    }

    private LinearLayout makeFontControls() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);

        Button increase = makeTopButton("increase size");
        increase.setOnClickListener(view -> adjustFontSize(1));
        row.addView(increase, buttonRowParams(true));

        Button decrease = makeTopButton("decrease size");
        decrease.setOnClickListener(view -> adjustFontSize(-1));
        row.addView(decrease, buttonRowParams(false));

        return row;
    }

    private void adjustFontSize(int delta) {
        int next = Math.max(MIN_FONT_SIZE_STEP, Math.min(MAX_FONT_SIZE_STEP, fontSizeStep + delta));
        if (next == fontSizeStep) return;
        fontSizeStep = next;
        prefs.edit().putInt(KEY_FONT_SIZE_STEP, fontSizeStep).apply();
        if (contentView != null) {
            contentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, contentSizeSp(16));
        }
    }

    private int contentSizeSp(int base) {
        return Math.max(10, base + (fontSizeStep * FONT_STEP_SP));
    }

    private int uiSizeSp(int base) {
        return base;
    }

    private void loadCachedWeatherAtStartup() {
        currentView = VIEW_AFD;
        String cached = boundedWeatherCacheString();
        if (selectedStation == null || cached == null || cached.trim().isEmpty()) {
            currentBundle = null;
            currentStatusPrefix = "";
            renderCurrentView();
            return;
        }

        try {
            WeatherBundle bundle = WeatherBundle.fromJson(cached);
            if (!sameStation(bundle.stationIdentifier, selectedStation.stationIdentifier)) {
                currentBundle = null;
                currentStatusPrefix = "";
                renderCurrentView();
                return;
            }
            renderBundle(bundle, "");
        } catch (Exception e) {
            currentBundle = null;
            currentStatusPrefix = "";
            renderCurrentView();
            contentView.setText(cleanError(e));
        }
    }

    private void confirmFetchWeather(int view, boolean returnToMainAfterFetch) {
        if (selectedStation == null) {
            new AlertDialog.Builder(this)
                    .setTitle("No forecast office selected")
                    .setMessage("Choose Change forecast office first, then fetch weather.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String label = fetchActionLabel(view);
        new AlertDialog.Builder(this)
                .setTitle(label + "?")
                .setMessage("This contacts api.weather.gov now for a one-time fetch of the "
                        + viewTitle(view).toLowerCase(Locale.US)
                        + ". The cached report is replaced only if the fetch succeeds.\n\n"
                        + (view == VIEW_AFD
                        ? "Regional discussion for the " + selectedStation.stationIdentifier + " office."
                        : nextForecastPointText()))
                .setPositiveButton("Fetch now", (dialog, which) -> fetchViewFromNetwork(view, returnToMainAfterFetch))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmUpdateStationDataFromNws() {
        new AlertDialog.Builder(this)
                .setTitle("Update WFO list?")
                .setMessage("This will contact api.weather.gov and replace the cached WFO list if the update succeeds.")
                .setPositiveButton("Update WFO list", (dialog, which) -> updateStationDataFromNws())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void fetchViewFromNetwork(int view, boolean returnToMainAfterFetch) {
        if (selectedStation == null) {
            if (stationScreenShowing && stationStatusView != null) {
                stationStatusView.setText("Select a station before fetching weather.");
            } else {
                currentStatusPrefix = "";
                renderCurrentView();
            }
            return;
        }

        StationRecord station = selectedStation.copy();
        WeatherBundle base = baseBundleForFetch(station);
        if (!NETWORK_COORDINATOR.startWeather(
                getApplicationContext(), station, base, view, returnToMainAfterFetch)) {
            setBusy(true);
            return;
        }

        currentView = view;
        setBusy(true);
        String progress = fetchProgressLabel(view);
        if (stationScreenShowing && stationStatusView != null) {
            stationStatusView.setText(progress);
        } else {
            currentStatusPrefix = "";
            renderStatus();
            if (contentView != null) contentView.setText(progress);
        }
    }

    private void setBusy(boolean busy) {
        if (refreshButton != null) {
            refreshButton.setEnabled(!busy && selectedStation != null);
            refreshButton.setText(busy ? "Fetching" : "Fetch weather now");
        }
        if (stationButton != null) stationButton.setEnabled(!busy);
        if (afdButton != null) afdButton.setEnabled(!busy);
        if (hourlyButton != null) hourlyButton.setEnabled(!busy);
        if (dailyButton != null) dailyButton.setEnabled(!busy);
        if (detailsButton != null) detailsButton.setEnabled(!busy);
        if (updateStationsButton != null) updateStationsButton.setEnabled(!busy);
    }

    private void rememberResolvedStationPoint(WeatherBundle bundle) {
        if (selectedStation == null || bundle == null) return;
        if (!sameStation(selectedStation.stationIdentifier, bundle.stationIdentifier)) return;
        if (selectedStation.hasCoordinates()) return;
        if (bundle.lat == null || bundle.lon == null || bundle.lat.trim().isEmpty() || bundle.lon.trim().isEmpty()) return;
        selectedStation.lat = bundle.lat;
        selectedStation.lon = bundle.lon;
        prefs.edit().putString(KEY_SELECTED_STATION_JSON, selectedStation.toSelectionJson().toString()).apply();
    }

    private void showView(int view) {
        currentView = view;
        renderCurrentView();
    }

    @Override
    public void onBackPressed() {
        if (stationScreenShowing) {
            buildMainUi();
            renderCurrentView();
        } else if (currentView != VIEW_AFD) {
            showView(VIEW_AFD);
        } else {
            super.onBackPressed();
        }
    }

    private void showStationScreen() {
        stationScreenShowing = true;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Select Forecast Office");
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, uiSizeSp(20));
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        updateStationsButton = makeTopButton("Update WFO list");
        updateStationsButton.setOnClickListener(view -> confirmUpdateStationDataFromNws());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        actionParams.setMargins(0, dp(8), 0, dp(8));
        root.addView(updateStationsButton, actionParams);

        TextView filterLabel = new TextView(this);
        filterLabel.setText("Filter");
        filterLabel.setTypeface(Typeface.DEFAULT_BOLD);
        filterLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, uiSizeSp(16));
        root.addView(filterLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        stationFilterInput = new EditText(this);
        stationFilterInput.setSingleLine(true);
        stationFilterInput.setSelectAllOnFocus(false);
        stationFilterInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, uiSizeSp(18));
        stationFilterInput.setHint("MTR, Monterey, San Diego...");
        stationFilterInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (stationAdapter != null) stationAdapter.setFilter(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        root.addView(stationFilterInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        stationStatusView = new TextView(this);
        stationStatusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, uiSizeSp(14));
        stationStatusView.setLineSpacing(0, 1.12f);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.setMargins(0, dp(8), 0, dp(8));
        root.addView(stationStatusView, statusParams);

        stationAdapter = new StationAdapter();
        ListView listView = new ListView(this);
        listView.setAdapter(stationAdapter);
        listView.setOnItemClickListener((parent, view, position, id) -> selectStation(stationAdapter.getItem(position)));
        root.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setScreenContent(root);
        loadStationListCacheIntoScreen();
    }

    private void loadStationListCacheIntoScreen() {
        if (stationAdapter == null || stationStatusView == null) return;
        try {
            StationListCache cache = readStationListCache();
            stationAdapter.setRecords(cache.records);
            stationStatusView.setText(stationListStatus(cache));
        } catch (FileNotFoundException e) {
            StationListCache cache = builtInStationListCache();
            stationAdapter.setRecords(cache.records);
            stationStatusView.setText(stationListStatus(cache));
        } catch (Exception e) {
            StationListCache cache = builtInStationListCache();
            stationAdapter.setRecords(cache.records);
            stationStatusView.setText("Cached station data could not be read; showing built-in station list. " + cleanError(e));
        }
    }

    private void updateStationDataFromNws() {
        if (!NETWORK_COORDINATOR.startStationUpdate(getApplicationContext())) {
            setStationBusy(true);
            return;
        }
        setStationBusy(true);
        if (stationStatusView != null) stationStatusView.setText("Updating WFO list from NWS...");
    }

    private void setStationBusy(boolean busy) {
        if (updateStationsButton != null) {
            updateStationsButton.setEnabled(!busy);
            updateStationsButton.setText(busy ? "Updating" : "Update WFO list");
        }
        if (refreshButton != null) refreshButton.setEnabled(!busy && selectedStation != null);
    }

    private void selectStation(StationRecord station) {
        if (station == null) return;

        selectedStation = station;
        prefs.edit().putString(KEY_SELECTED_STATION_JSON, station.toSelectionJson().toString()).apply();
        currentBundle = null;
        currentView = VIEW_AFD;
        currentStatusPrefix = "";
        buildMainUi();
        loadCachedWeatherAtStartup();
    }

    private String stationListStatus(StationListCache cache) {
        StringBuilder out = new StringBuilder();
        if (cache.fetchedAt != null && !cache.fetchedAt.trim().isEmpty()) {
            out.append("Office list updated: ").append(niceIso(cache.fetchedAt)).append('\n');
        } else {
            out.append("Office list: built-in fallback").append('\n');
        }
        out.append("Source: ").append(cache.sourceLabel == null || cache.sourceLabel.trim().isEmpty() ? "built-in" : cache.sourceLabel.trim()).append('\n');
        out.append("Offices shown: ").append(cache.records.size()).append('\n');
        out.append("Tap a forecast office to select it. Filter by code or name if needed.");
        return out.toString();
    }

    private static StationListCache fetchStationListFromNws(Context context) throws Exception {
        String url = "https://api.weather.gov/products/types/AFD/locations";
        JSONObject root = getJson(context, url);
        ArrayList<StationRecord> records = stationRecordsFromAfdLocationJson(root);
        if (records.isEmpty()) {
            throw new IOException("NWS returned no AFD locations.");
        }

        StationListCache cache = new StationListCache();
        cache.fetchedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        cache.sourceUrl = url;
        cache.sourceLabel = "NWS AFD locations";
        cache.rawLocations = firstLocationArray(root);
        cache.records = records;
        return cache;
    }

    private StationListCache readStationListCache() throws IOException, JSONException {
        String text = readPrivateFile(OFFICE_CACHE_FILE);
        JSONObject json = parseJsonObject(text);
        StationListCache cache = new StationListCache();
        cache.fetchedAt = json.optString("fetchedAt", "");
        cache.sourceUrl = json.optString("sourceUrl", "");
        cache.sourceLabel = json.optString("sourceLabel", "NWS AFD locations");
        JSONArray locations = json.optJSONArray("locations");
        if (locations == null) locations = new JSONArray();
        cache.rawLocations = locations;
        cache.records = stationRecordsFromAfdLocationArray(locations);
        if (cache.records.isEmpty()) throw new IOException("Cached station list is empty.");
        return cache;
    }

    private StationListCache builtInStationListCache() {
        StationListCache cache = new StationListCache();
        cache.sourceLabel = "built-in WFO list";
        cache.records = defaultStationRecords();
        cache.rawLocations = new JSONArray();
        for (StationRecord r : cache.records) cache.rawLocations.put(r.toLocationJson());
        return cache;
    }

    private static ArrayList<StationRecord> stationRecordsFromAfdLocationJson(JSONObject root) throws JSONException {
        return stationRecordsFromAfdLocationArray(firstLocationArray(root));
    }

    private static JSONArray firstLocationArray(JSONObject root) {
        if (root == null) return new JSONArray();
        JSONArray graph = root.optJSONArray("@graph");
        if (graph != null) return graph;
        JSONArray locations = root.optJSONArray("locations");
        if (locations != null) return locations;
        JSONArray features = root.optJSONArray("features");
        if (features != null) return features;

        JSONObject locationObject = root.optJSONObject("locations");
        JSONArray out = new JSONArray();
        if (locationObject != null) {
            Iterator<String> keys = locationObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = locationObject.opt(key);
                JSONObject entry = new JSONObject();
                try {
                    entry.put("locationId", key);
                    if (value instanceof JSONObject) {
                        JSONObject v = (JSONObject) value;
                        Iterator<String> vkeys = v.keys();
                        while (vkeys.hasNext()) {
                            String vk = vkeys.next();
                            entry.put(vk, v.opt(vk));
                        }
                    } else if (value != null && value != JSONObject.NULL) {
                        entry.put("locationName", String.valueOf(value));
                    }
                } catch (JSONException ignored) {
                }
                out.put(entry);
            }
        }
        return out;
    }

    private static ArrayList<StationRecord> stationRecordsFromAfdLocationArray(JSONArray locations) throws JSONException {
        HashMap<String, StationRecord> defaults = defaultStationMap();
        LinkedHashMap<String, StationRecord> merged = new LinkedHashMap<>();

        for (int i = 0; i < locations.length(); i++) {
            Object item = locations.opt(i);
            StationRecord record = StationRecord.fromAfdLocationItem(item, defaults);
            if (record != null && !record.stationIdentifier.trim().isEmpty()) {
                merged.put(record.stationIdentifier.toUpperCase(Locale.US), record);
            }
        }

        ArrayList<StationRecord> records = new ArrayList<>(merged.values());
        sortStationRecords(records);
        return records;
    }

    private static ArrayList<StationRecord> defaultStationRecords() {
        ArrayList<StationRecord> records = new ArrayList<>();
        for (String line : BUILTIN_OFFICES) {
            StationRecord record = StationRecord.fromBuiltInLine(line);
            if (record != null) records.add(record);
        }
        sortStationRecords(records);
        return records;
    }

    private static HashMap<String, StationRecord> defaultStationMap() {
        HashMap<String, StationRecord> map = new HashMap<>();
        for (StationRecord record : defaultStationRecords()) {
            map.put(record.stationIdentifier.toUpperCase(Locale.US), record);
        }
        return map;
    }

    private static void sortStationRecords(ArrayList<StationRecord> records) {
        Collections.sort(records, new Comparator<StationRecord>() {
            @Override
            public int compare(StationRecord a, StationRecord b) {
                return a.stationIdentifier.compareToIgnoreCase(b.stationIdentifier);
            }
        });
    }

    private WeatherBundle baseBundleForFetch(StationRecord station) {
        WeatherBundle base = null;
        if (currentBundle != null && sameStation(currentBundle.stationIdentifier, station.stationIdentifier)) {
            base = currentBundle.copy();
        } else {
            String cached = boundedWeatherCacheString();
            if (cached != null && !cached.trim().isEmpty()) {
                try {
                    WeatherBundle fromCache = WeatherBundle.fromJson(cached);
                    if (sameStation(fromCache.stationIdentifier, station.stationIdentifier)) base = fromCache;
                } catch (Exception ignored) {
                }
            }
        }

        if (base == null) base = new WeatherBundle();
        applyStationBasics(base, station);
        return base;
    }

    private static void applyStationBasics(WeatherBundle bundle, StationRecord station) {
        if (bundle == null || station == null) return;
        bundle.stationIdentifier = station.stationIdentifier == null ? "" : station.stationIdentifier.trim().toUpperCase(Locale.US);
        bundle.stationName = station.name == null ? "" : station.name.trim();
        if ((bundle.lat == null || bundle.lat.trim().isEmpty()) && station.lat != null) bundle.lat = station.lat.trim();
        if ((bundle.lon == null || bundle.lon.trim().isEmpty()) && station.lon != null) bundle.lon = station.lon.trim();
        if (bundle.office == null || bundle.office.trim().isEmpty()) bundle.office = bundle.stationIdentifier;
        if (bundle.locationName == null || bundle.locationName.trim().isEmpty()) bundle.locationName = bundle.stationName;
    }

    private static WeatherBundle fetchWeatherPart(Context context, StationRecord station, WeatherBundle base, int view) throws Exception {
        WeatherBundle bundle = base == null ? new WeatherBundle() : base.copy();
        applyStationBasics(bundle, station);

        if (view == VIEW_AFD) {
            String office = firstNonEmpty(bundle.office, station.stationIdentifier).toUpperCase(Locale.US);
            Product afd = fetchLatestAfd(context, office);
            String report = formatAfd(afd.text);
            bundle.office = office;
            bundle.afdUpdated = afd.issuedAt;
            bundle.afdText = report;
            bundle.afdFetchedAt = nowIso();
            bundle.fetchedAt = bundle.afdFetchedAt;
            return bundle;
        }

        PointData pointData = pointDataForForecast(context, station, bundle, view, false);
        JSONObject forecast;
        try {
            forecast = getJson(context, forecastUrlForView(pointData, view));
        } catch (NwsHttpException e) {
            if (!pointData.fromCache || (e.statusCode != 404 && e.statusCode != 410)) throw e;
            // A grid can move before the normal expiry. Refresh and retry only once,
            // inside this same confirmed fetch; outages and rate limits are not retried.
            pointData = pointDataForForecast(context, station, bundle, view, true);
            forecast = getJson(context, forecastUrlForView(pointData, view));
        }

        String report = view == VIEW_HOURLY ? formatHourly(forecast) : formatDaily(forecast);
        String now = nowIso();
        applyPointData(bundle, pointData);
        if (view == VIEW_HOURLY) {
            bundle.hourlyUpdated = forecastUpdated(forecast);
            bundle.hourlyText = report;
            bundle.hourlyFetchedAt = now;
            bundle.hourlyLat = pointData.lat;
            bundle.hourlyLon = pointData.lon;
            bundle.hourlyLocationName = pointData.locationName;
            bundle.hourlyPointOffice = pointData.pointOffice;
        } else {
            bundle.dailyUpdated = forecastUpdated(forecast);
            bundle.dailyText = report;
            bundle.dailyFetchedAt = now;
            bundle.dailyLat = pointData.lat;
            bundle.dailyLon = pointData.lon;
            bundle.dailyLocationName = pointData.locationName;
            bundle.dailyPointOffice = pointData.pointOffice;
        }
        bundle.fetchedAt = now;
        return bundle;
    }

    private static String nowIso() {
        return OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static String forecastUrlForView(PointData pointData, int view) throws IOException {
        String url = view == VIEW_HOURLY ? pointData.hourlyUrl : pointData.forecastUrl;
        if (isBlank(url)) throw new IOException("NWS did not provide a URL for this forecast.");
        return url;
    }

    private static PointData pointDataForForecast(Context context, StationRecord station,
            WeatherBundle bundle, int view, boolean forceRefresh) throws Exception {
        if (!forceRefresh && bundle != null
                && ForecastCachePolicy.isMappingFresh(bundle.pointResolvedAt, nowIso())
                && ForecastCachePolicy.isValidPoint(bundle.lat, bundle.lon)
                && (!station.hasCoordinates() || ForecastCachePolicy.samePoint(
                        station.lat, station.lon, bundle.lat, bundle.lon))
                && !isBlank(view == VIEW_HOURLY ? bundle.hourlyUrl : bundle.forecastUrl)) {
            return PointData.fromBundle(bundle);
        }

        StationRecord pointStation = station.hasCoordinates() ? station : resolveRepresentativePointFromNws(context, station);
        if (!pointStation.hasCoordinates()) {
            throw new IOException("No representative point is available for " + station.stationIdentifier + ".");
        }

        JSONObject points = getJson(context, "https://api.weather.gov/points/" + pointStation.lat + "," + pointStation.lon);
        JSONObject pointProps = points.getJSONObject("properties");

        PointData data = new PointData();
        data.lat = pointStation.lat;
        data.lon = pointStation.lon;
        data.pointOffice = jsonText(pointProps, "cwa");
        data.office = firstNonEmpty(station.stationIdentifier, data.pointOffice).toUpperCase(Locale.US);
        data.forecastUrl = jsonText(pointProps, "forecast");
        data.hourlyUrl = jsonText(pointProps, "forecastHourly");
        data.locationName = locationNameFromPoint(pointProps, "");
        data.resolvedAt = nowIso();
        forecastUrlForView(data, view);
        return data;
    }

    private static void applyPointData(WeatherBundle bundle, PointData data) {
        if (bundle == null || data == null) return;
        if (!isBlank(data.lat)) bundle.lat = data.lat;
        if (!isBlank(data.lon)) bundle.lon = data.lon;
        if (!isBlank(data.office)) bundle.office = data.office;
        bundle.pointOffice = data.pointOffice;
        bundle.forecastUrl = data.forecastUrl;
        bundle.hourlyUrl = data.hourlyUrl;
        bundle.locationName = data.locationName;
        bundle.pointResolvedAt = data.resolvedAt;
    }

    private static StationRecord resolveRepresentativePointFromNws(Context context, StationRecord office) throws Exception {
        if (office == null) throw new IOException("No station selected.");
        String code = office.stationIdentifier == null ? "" : office.stationIdentifier.trim().toUpperCase(Locale.US);
        if (!code.matches("[A-Z]{3}")) {
            throw new IOException("Selected station is not a forecast-office code: " + code);
        }

        JSONObject officeJson = getJson(context, "https://api.weather.gov/offices/" + code);
        StationRecord fromGeometry = office.withCoordinatesFromGeometry(officeJson);
        if (fromGeometry.hasCoordinates()) return fromGeometry;

        JSONArray approved = findArrayRecursive(officeJson, "approvedObservationStations");
        if (approved == null || approved.length() == 0) {
            throw new IOException("NWS office metadata did not provide approved observation stations for " + code + ".");
        }

        int attempts = 0;
        for (int i = 0; i < approved.length() && attempts < MAX_REPRESENTATIVE_STATION_ATTEMPTS; i++) {
            String stationUrl = approved.optString(i, "").trim();
            if (stationUrl.isEmpty()) continue;
            if (stationUrl.startsWith("/")) stationUrl = "https://" + NWS_HOST + stationUrl;
            attempts++;
            JSONObject stationJson = getJson(context, stationUrl);
            StationRecord fromStation = office.withCoordinatesFromGeometry(stationJson);
            if (fromStation.hasCoordinates()) return fromStation;
        }

        throw new IOException("No usable representative point was found for " + code + ".");
    }

    private static JSONArray findArrayRecursive(JSONObject object, String key) {
        if (object == null || key == null) return null;
        JSONArray direct = object.optJSONArray(key);
        if (direct != null) return direct;
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String childKey = keys.next();
            JSONObject child = object.optJSONObject(childKey);
            if (child != null) {
                JSONArray found = findArrayRecursive(child, key);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Product fetchLatestAfd(Context context, String office) throws Exception {
        if (office == null || office.trim().isEmpty()) {
            throw new IOException("No forecast office was returned by NWS.");
        }

        JSONObject list = getJson(context, "https://api.weather.gov/products/types/AFD/locations/" + office.toUpperCase(Locale.US));
        JSONArray graph = list.optJSONArray("@graph");
        if (graph == null) graph = list.optJSONArray("products");
        if (graph == null || graph.length() == 0) {
            throw new IOException("No Area Forecast Discussion was returned for office " + office + ".");
        }

        JSONObject latest = graph.getJSONObject(0);
        String productUrl = firstNonEmpty(jsonText(latest, "@id"), jsonText(latest, "id"));
        String listIssuedAt = firstNonEmpty(jsonText(latest, "issuanceTime"), jsonText(latest, "issueTime"));
        if (productUrl.trim().isEmpty()) {
            throw new IOException("NWS did not provide an Area Forecast Discussion product URL.");
        }

        JSONObject productJson = getJson(context, productUrl);
        JSONObject props = productJson.optJSONObject("properties");

        Product product = new Product();
        if (props != null) {
            product.issuedAt = firstNonEmpty(jsonText(props, "issuanceTime"), listIssuedAt);
            product.text = jsonText(props, "productText");
        } else {
            product.issuedAt = firstNonEmpty(jsonText(productJson, "issuanceTime"), listIssuedAt);
            product.text = jsonText(productJson, "productText");
        }
        if (product.text.trim().isEmpty()) {
            throw new IOException("NWS returned no Area Forecast Discussion text.");
        }
        return product;
    }

    private static String jsonText(JSONObject object, String name) {
        Object value = object == null ? null : object.opt(name);
        return value instanceof String && !"null".equalsIgnoreCase(((String) value).trim())
                ? ((String) value).trim() : "";
    }

    private static JSONObject getJson(Context context, String url) throws IOException, JSONException {
        return parseJsonObject(getText(context, url));
    }

    private static JSONObject parseJsonObject(String text) throws JSONException {
        try {
            JsonDepthGuard.check(text, 64);
        } catch (IOException e) {
            throw new JSONException(e.getMessage());
        }
        return new JSONObject(text);
    }

    private static boolean hasNetworkConnection(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return true;
            Network network = manager.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (SecurityException ignored) {
            return true;
        } catch (Exception ignored) {
            return true;
        }
    }

    private static String offlineMessage() {
        return "Network connection is not available. Turn off airplane mode or open network connections, then try again.";
    }

    private static String getText(Context context, String urlString) throws IOException {
        return getText(context, urlString, 0);
    }

    private static String getText(Context context, String urlString, int redirectCount) throws IOException {
        if (!hasNetworkConnection(context)) {
            throw new IOException(offlineMessage());
        }

        if (redirectCount > 5) {
            throw new IOException("Too many redirects from " + safeUrl(urlString));
        }

        URL parsed = new URL(urlString);
        if (!"https".equalsIgnoreCase(parsed.getProtocol()) || !NWS_HOST.equalsIgnoreCase(parsed.getHost())) {
            throw new IOException("Blocked non-NWS URL: " + safeUrl(urlString));
        }

        HttpURLConnection connection = (HttpURLConnection) parsed.openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/geo+json, application/json, application/ld+json");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);

            int code = connection.getResponseCode();
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_NETWORK_RESPONSE_CHARS) {
                throw new IOException("NWS response exceeds the " + MAX_NETWORK_RESPONSE_CHARS + " character limit.");
            }
            if (code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER
                    || code == 307
                    || code == 308) {
                String location = connection.getHeaderField("Location");
                if (location == null || location.trim().isEmpty()) {
                    throw new IOException("Redirect without Location from " + safeUrl(urlString));
                }
                URL redirected = new URL(parsed, location);
                connection.disconnect();
                return getText(context, redirected.toString(), redirectCount + 1);
            }

            InputStream stream = (code >= 200 && code <= 299) ? connection.getInputStream() : connection.getErrorStream();
            String body = BoundedTextReader.read(stream, MAX_NETWORK_RESPONSE_CHARS, "NWS response");

            if (code < 200 || code > 299) {
                throw new NwsHttpException(code,
                        "HTTP " + code + " from " + safeUrl(urlString) + " " + firstLine(body));
            }
            return body;
        } finally {
            connection.disconnect();
        }
    }

    private String readPrivateFile(String fileName) throws IOException {
        synchronized (PRIVATE_FILE_LOCK) {
            AtomicFile file = new AtomicFile(getFileStreamPath(fileName));
            try (InputStream in = file.openRead()) {
                return BoundedTextReader.read(in, MAX_PRIVATE_FILE_CHARS, "private cache file");
            }
        }
    }

    private static void writePrivateFile(Context context, String fileName, String text) throws IOException {
        String value = text == null ? "" : text;
        if (value.length() > MAX_PRIVATE_FILE_CHARS) {
            throw new IOException("Private cache file exceeds the " + MAX_PRIVATE_FILE_CHARS + " character limit.");
        }
        // AtomicFile recovery must not race an active writer in another Activity/worker.
        synchronized (PRIVATE_FILE_LOCK) {
            AtomicFile file = new AtomicFile(context.getFileStreamPath(fileName));
            FileOutputStream output = null;
            try {
                output = file.startWrite();
                OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
                writer.write(value);
                writer.flush();
                file.finishWrite(output);
            } catch (IOException | RuntimeException failure) {
                file.failWrite(output);
                throw failure;
            }
        }
    }

    private static String safeUrl(String url) {
        try {
            URL parsed = new URL(url);
            return parsed.getProtocol() + "://" + parsed.getHost() + parsed.getPath();
        } catch (Exception ignored) {
            return url;
        }
    }

    private static String firstLine(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        int newline = trimmed.indexOf('\n');
        String first = newline >= 0 ? trimmed.substring(0, newline) : trimmed;
        return first.length() > 180 ? first.substring(0, 180) : first;
    }

    private static String locationNameFromPoint(JSONObject pointProps, String fallback) {
        JSONObject relative = pointProps.optJSONObject("relativeLocation");
        if (relative != null) {
            JSONObject props = relative.optJSONObject("properties");
            if (props != null) {
                String city = jsonText(props, "city");
                String state = jsonText(props, "state");
                String joined = joinLocation(city, state);
                if (!joined.isEmpty()) return joined;
            }
        }
        return fallback == null ? "" : fallback;
    }

    private static String joinLocation(String city, String state) {
        String c = city == null ? "" : city.trim();
        String s = state == null ? "" : state.trim();
        if (!c.isEmpty() && !s.isEmpty()) return c + ", " + s;
        if (!c.isEmpty()) return c;
        return s;
    }

    private static String forecastUpdated(JSONObject forecast) {
        JSONObject props = forecast.optJSONObject("properties");
        return props == null ? "" : jsonText(props, "updated");
    }

    private static String formatAfd(String productText) throws IOException {
        if (productText == null || productText.trim().isEmpty()) {
            throw new IOException("NWS returned no Area Forecast Discussion text.");
        }

        Map<String, StringBuilder> sections = new LinkedHashMap<>();
        String current = null;
        boolean sawRequestedSection = false;

        String normalized = productText.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        for (String rawLine : lines) {
            String line = rawLine.trim();
            String upper = line.toUpperCase(Locale.US);
            if (upper.contains("WATCHES/WARNINGS/ADVISORIES")) break;

            String section = afdSectionName(line);
            if (section != null) {
                current = wantedAfdSection(section);
                if (current != null) {
                    StringBuilder existing = sections.get(current);
                    if (existing == null) {
                        sections.put(current, new StringBuilder());
                    } else if (existing.length() > 0) {
                        existing.append('\n');
                    }
                    sawRequestedSection = true;
                }
                continue;
            }

            if (current != null) sections.get(current).append(rawLine).append('\n');
        }

        if (!sawRequestedSection) {
            throw new IOException("No Key Messages, Short Term, or Long Term section was found in this Area Forecast Discussion.");
        }

        List<String> order = Arrays.asList("Key Messages", "Short Term", "Long Term");
        StringBuilder out = new StringBuilder();
        for (String name : order) appendAfdSection(out, name, sections.remove(name));

        String result = out.toString().trim();
        if (result.isEmpty()) {
            throw new IOException("No readable Key Messages, Short Term, or Long Term text was found in this Area Forecast Discussion.");
        }
        return result;
    }

    private static void appendAfdSection(StringBuilder out, String name, StringBuilder body) {
        if (body == null) return;
        String reflowed = reflowParagraphs(body.toString());
        if (reflowed.trim().isEmpty()) return;
        out.append(name).append('\n');
        out.append(repeat('-', name.length())).append('\n');
        out.append(reflowed).append("\n\n");
    }

    private static String afdSectionName(String line) {
        if (line == null) return null;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.equals("&&") || trimmed.equals("$$")) return null;

        String upper = trimmed.toUpperCase(Locale.US);
        String compact = upper.replaceAll("^[. ]+", "");

        if (compact.startsWith("KEY MESSAGE")) return "Key Messages";
        if (compact.startsWith("SHORT TERM")) return "Short Term";
        if (compact.startsWith("NEAR TERM")) return "Near Term";
        if (compact.startsWith("LONG TERM")) return "Long Term";

        if (!trimmed.startsWith(".") || trimmed.startsWith("...")) return null;
        if (upper.contains("MARINE")) return "Marine";
        if (upper.contains("AVIATION")) return "Aviation";
        if (upper.contains("BEACH")) return "Beaches";
        if (upper.contains("TIDE")) return "Tides";
        if (upper.contains("COASTAL")) return "Coastal";
        if (upper.contains("SYNOPSIS")) return "Synopsis";
        if (upper.contains("FIRE WEATHER")) return "Fire Weather";
        if (upper.contains("HYDROLOGY")) return "Hydrology";
        if (upper.contains("CLIMATE")) return "Climate";
        if (upper.contains("UPDATE")) return "Update";
        if (upper.contains("DISCUSSION")) return "Discussion";

        if (upper.matches("^\\.[A-Z0-9 /&(),-]+(\\.\\.\\.|/|$).*")) {
            return "Other";
        }
        return null;
    }

    private static String wantedAfdSection(String section) {
        if ("Key Messages".equals(section)) return "Key Messages";
        if ("Short Term".equals(section) || "Near Term".equals(section)) return "Short Term";
        if ("Long Term".equals(section)) return "Long Term";
        return null;
    }

    private static String reflowParagraphs(String text) {
        StringBuilder result = new StringBuilder();
        StringBuilder paragraph = new StringBuilder();

        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (shouldSkipAfdLine(line)) {
                flushParagraph(result, paragraph);
                continue;
            }
            if (line.isEmpty()) {
                flushParagraph(result, paragraph);
                continue;
            }
            if (isBulletStart(line)) {
                flushParagraph(result, paragraph);
                paragraph.append(cleanSpaces(line));
                continue;
            }
            appendReflowLine(paragraph, line);
        }
        flushParagraph(result, paragraph);
        return cleanReflow(result.toString().trim());
    }

    private static boolean shouldSkipAfdLine(String line) {
        if (line == null || line.isEmpty()) return false;
        String upper = line.toUpperCase(Locale.US);
        if (line.equals("&&") || line.equals("$$") || line.equals("...")) return true;
        if (line.startsWith("$$")) return true;
        if (upper.startsWith("ISSUED AT ")) return true;
        if (upper.matches("^[A-Z]{3,6}$")) return true;
        if (upper.matches("^\\.\\.\\.NEW .+\\.\\.\\.$")) return true;
        return false;
    }

    private static boolean isBulletStart(String line) {
        return line.matches("^[-*][ \\t].+") || line.matches("^[0-9]+[.)][ \\t].+");
    }

    private static void appendReflowLine(StringBuilder paragraph, String line) {
        String cleaned = cleanSpaces(line);
        if (paragraph.length() == 0) {
            paragraph.append(cleaned);
        } else if (paragraph.charAt(paragraph.length() - 1) == '-') {
            paragraph.deleteCharAt(paragraph.length() - 1);
            paragraph.append(cleaned);
        } else {
            paragraph.append(' ').append(cleaned);
        }
    }

    private static void flushParagraph(StringBuilder result, StringBuilder paragraph) {
        if (paragraph.length() == 0) return;
        if (result.length() > 0) result.append("\n\n");
        result.append(cleanSpaces(paragraph.toString()));
        paragraph.setLength(0);
    }

    private static String cleanSpaces(String text) {
        return text.replaceAll("[ \\t]+", " ").trim();
    }

    private static String cleanReflow(String text) {
        return text
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" \\.", ".")
                .replaceAll(" ,", ",")
                .replaceAll(" ;", ";")
                .replaceAll(" :", ":")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private static JSONArray usableForecastPeriods(JSONObject forecast, int maxPeriods) throws IOException {
        JSONObject properties = forecast.optJSONObject("properties");
        JSONArray received = properties == null ? null : properties.optJSONArray("periods");
        JSONArray usable = new JSONArray();
        if (received != null) {
            for (int i = 0; i < received.length() && usable.length() < maxPeriods; i++) {
                JSONObject period = received.optJSONObject(i);
                if (period != null && ForecastCachePolicy.isUsableForecastPeriod(
                        jsonText(period, "startTime"), period.opt("temperature"),
                        jsonText(period, "temperatureUnit"), jsonText(period, "shortForecast"),
                        jsonText(period, "detailedForecast"))) {
                    usable.put(period);
                }
            }
        }
        if (usable.length() == 0) throw new IOException("NWS returned no usable forecast periods.");
        return usable;
    }

    private static String formatHourly(JSONObject hourlyJson) throws JSONException, IOException {
        JSONArray periods = usableForecastPeriods(hourlyJson, MAX_HOURLY_PERIODS);
        StringBuilder out = new StringBuilder();

        int limit = Math.min(periods.length(), MAX_HOURLY_PERIODS);
        for (int i = 0; i < limit; i++) {
            JSONObject p = periods.getJSONObject(i);
            String time = shortHour(jsonText(p, "startTime"));
            String temp = ForecastCachePolicy.temperatureText(p.opt("temperature"), jsonText(p, "temperatureUnit"));
            String pop = precipitationPercent(p);
            String forecast = firstNonEmpty(jsonText(p, "shortForecast"), jsonText(p, "detailedForecast"));

            out.append(time).append("  ").append(temp).append("  ").append(pop).append(" precipitation");
            if (!forecast.isEmpty()) out.append("  ").append(forecast);
            out.append("\n\n");
        }
        return out.toString().trim();
    }

    private static String formatDaily(JSONObject dailyJson) throws JSONException, IOException {
        JSONArray periods = usableForecastPeriods(dailyJson, Integer.MAX_VALUE);
        StringBuilder out = new StringBuilder();
        int i = 0;

        while (i < periods.length()) {
            JSONObject first = periods.getJSONObject(i);
            boolean firstIsDay = first.optBoolean("isDaytime", false);

            if (firstIsDay && i + 1 < periods.length()) {
                JSONObject second = periods.getJSONObject(i + 1);
                if (!second.optBoolean("isDaytime", true)
                        && ForecastCachePolicy.areAdjacentPeriods(
                                jsonText(first, "endTime"), jsonText(second, "startTime"))) {
                    appendDailyPair(out, first, second);
                    i += 2;
                    continue;
                }
            }

            appendSingleDailyPeriod(out, first);
            i++;
        }
        return out.toString().trim();
    }

    private static void appendDailyPair(StringBuilder out, JSONObject day, JSONObject night) {
        String name = day.optString("name", shortDay(day.optString("startTime", "")));
        out.append(name).append('\n');
        appendTemperatureBlock(out, "High", day, "Day precipitation");
        appendForecastText(out, day);
        out.append('\n');
        appendTemperatureBlock(out, "Low", night, "Night precipitation");
        appendForecastText(out, night);
        out.append("\n\n");
    }

    private static void appendSingleDailyPeriod(StringBuilder out, JSONObject period) {
        String name = period.optString("name", shortDay(period.optString("startTime", "")));
        String label = period.optBoolean("isDaytime", false) ? "High" : "Low";
        out.append(name).append('\n');
        appendTemperatureBlock(out, label, period, "Precipitation");
        appendForecastText(out, period);
        out.append("\n\n");
    }

    private static void appendTemperatureBlock(StringBuilder out, String label, JSONObject period, String precipLabel) {
        out.append(label)
                .append(' ')
                .append(ForecastCachePolicy.temperatureText(period.opt("temperature"), jsonText(period, "temperatureUnit")))
                .append('\n');
        out.append(precipLabel).append(": ").append(precipitationPercent(period)).append('\n');
    }

    private static void appendForecastText(StringBuilder out, JSONObject period) {
        String detailed = jsonText(period, "detailedForecast");
        String shortForecast = jsonText(period, "shortForecast");
        if (!detailed.isEmpty()) {
            out.append(cleanSpaces(detailed)).append('\n');
        } else if (!shortForecast.isEmpty()) {
            out.append(cleanSpaces(shortForecast)).append('\n');
        }
    }

    private static String precipitationPercent(JSONObject period) {
        JSONObject pop = period.optJSONObject("probabilityOfPrecipitation");
        if (pop == null || pop.isNull("value")) return "--%";
        Object value = pop.opt("value");
        if (value == null || value == JSONObject.NULL) return "--%";
        if (value instanceof Number) return Math.round(((Number) value).doubleValue()) + "%";
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "--%" : text + "%";
    }

    private void renderBundle(WeatherBundle bundle, String prefix) {
        currentBundle = bundle;
        currentStatusPrefix = prefix == null ? "" : prefix;
        currentBundle.afdText = filterCachedAfdText(currentBundle.afdText);
        currentBundle.hourlyText = limitCachedHourlyText(currentBundle.hourlyText);
        renderStatus();
        renderCurrentView();
    }

    private void renderStatus() {
        if (statusView == null) return;

        String fetchedAt = cacheTimestampForView(currentBundle, currentView);
        if (!hasReportForDisplay(currentBundle, currentView)) {
            statusView.setText("Cache Age: none");
        } else if (isBlank(fetchedAt)) {
            statusView.setText("Cache Age: unknown");
        } else {
            statusView.setText("Cache Age: " + cacheAge(fetchedAt));
        }
    }

    private boolean hasReportForDisplay(WeatherBundle bundle, int view) {
        if (bundle == null) return false;
        if (view == VIEW_HOURLY) return !isBlank(bundle.hourlyText);
        if (view == VIEW_DAILY) return !isBlank(bundle.dailyText);
        return !isBlank(bundle.afdText);
    }

    private String reportTimestampForView(WeatherBundle bundle, int view) {
        if (!hasReportForDisplay(bundle, view)) return "";
        if (view == VIEW_HOURLY) return firstNonEmpty(bundle.hourlyUpdated);
        if (view == VIEW_DAILY) return firstNonEmpty(bundle.dailyUpdated);
        return firstNonEmpty(bundle.afdUpdated);
    }

    private String detailedTimestamp(String value) {
        if (isBlank(value)) return "not available";
        try {
            return OffsetDateTime.parse(value).format(
                    DateTimeFormatter.ofPattern("EEE MMM d, uuuu h:mm a xxx", Locale.US));
        } catch (Exception ignored) {
            return value;
        }
    }

    private String forecastPointText(WeatherBundle bundle, int view) {
        if (hasReportForDisplay(bundle, view)) {
            String lat = view == VIEW_HOURLY ? bundle.hourlyLat : bundle.dailyLat;
            String lon = view == VIEW_HOURLY ? bundle.hourlyLon : bundle.dailyLon;
            String location = view == VIEW_HOURLY
                    ? bundle.hourlyLocationName : bundle.dailyLocationName;
            if (isBlank(lat) || isBlank(lon)) {
                return "Representative point: unknown for this cached report";
            }
            // A legacy cache may carry the broad office label instead of an NWS place name.
            boolean namedPlace = !isBlank(location) && !location.equals(bundle.stationName);
            return "Representative point: "
                    + (namedPlace ? location.trim() + "\n" : "")
                    + lat.trim() + ", " + lon.trim();
        }
        return nextForecastPointText();
    }

    private String nextForecastPointText() {
        if (selectedStation == null) return "No forecast office selected";
        if (selectedStation.hasCoordinates()) {
            return "Next fetch point: " + selectedStation.lat.trim() + ", "
                    + selectedStation.lon.trim() + " (representative)";
        }
        return "Representative point: resolved from NWS when fetched";
    }

    private void renderForecastPoint() {
        if (forecastPointView == null || stationScreenShowing) return;
        if (currentView == VIEW_AFD) {
            forecastPointView.setVisibility(selectedStation == null ? View.GONE : View.VISIBLE);
            forecastPointView.setText(selectedStation == null ? ""
                    : "Regional discussion: " + selectedStation.stationIdentifier + " office");
        } else {
            forecastPointView.setVisibility(View.VISIBLE);
            forecastPointView.setText(forecastPointText(currentBundle, currentView));
        }
    }

    private void showDetailsDialog() {
        StringBuilder details = new StringBuilder();
        details.append("Forecast office: ");
        details.append(selectedStation == null ? "none" : selectedStation.summary());
        details.append('\n');

        details.append("View: ").append(viewTitle(currentView)).append('\n');
        if (currentView == VIEW_AFD) {
            details.append("Coverage: regional discussion for the selected office.\n");
            details.append("Hourly and daily forecasts describe one representative point.\n");
        } else {
            details.append(forecastPointText(currentBundle, currentView)).append('\n');
            if (hasReportForDisplay(currentBundle, currentView)) {
                String pointOffice = currentView == VIEW_HOURLY
                        ? currentBundle.hourlyPointOffice : currentBundle.dailyPointOffice;
                if (!isBlank(pointOffice)) {
                    details.append("Point forecast office: ").append(pointOffice.trim()).append('\n');
                }
            }
        }

        details.append("Fetched: ").append(detailedTimestamp(
                cacheTimestampForView(currentBundle, currentView))).append('\n');
        details.append(currentView == VIEW_AFD ? "NWS issued: " : "NWS updated: ")
                .append(detailedTimestamp(reportTimestampForView(currentBundle, currentView)));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(8);
        root.setPadding(pad, pad, pad, 0);
        root.addView(makeVegetarianReminder());

        TextView detailsView = new TextView(this);
        detailsView.setText(details.toString());
        detailsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, uiSizeSp(16));
        detailsView.setLineSpacing(0, 1.14f);
        root.addView(detailsView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        Button fetchWeatherButton = makeDialogButton(fetchActionLabel(currentView));
        Button changeStationButton = makeDialogButton("Change forecast office");
        Button copyButton = makeDialogButton("Copy data to clipboard");
        Button highlightButton = makeDialogButton(getString(R.string.highlight_title));
        Button aboutButton = makeDialogButton(getString(R.string.about_title));
        Button closeButton = makeDialogButton("Close");

        LinearLayout.LayoutParams actionParams = dialogButtonParams(false);
        root.addView(fetchWeatherButton, actionParams);
        root.addView(changeStationButton, dialogButtonParams(false));
        root.addView(copyButton, dialogButtonParams(false));
        root.addView(highlightButton, dialogButtonParams(false));
        root.addView(aboutButton, dialogButtonParams(false));
        root.addView(closeButton, dialogButtonParams(true));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Details")
                .setView(scroll)
                .create();

        fetchWeatherButton.setEnabled(selectedStation != null);
        fetchWeatherButton.setOnClickListener(v -> {
            dialog.dismiss();
            confirmFetchWeather(currentView, false);
        });

        changeStationButton.setOnClickListener(v -> {
            dialog.dismiss();
            showStationScreen();
        });

        copyButton.setEnabled(currentBundle != null);
        copyButton.setOnClickListener(v -> {
            copyDataToClipboard();
            dialog.dismiss();
        });

        highlightButton.setOnClickListener(v -> {
            dialog.dismiss();
            showHighlightTermsDialog();
        });

        aboutButton.setOnClickListener(v -> {
            dialog.dismiss();
            showAboutDialog();
        });

        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private HighlightTerms loadHighlightTerms() {
        try {
            return HighlightTerms.parse(stringPreference(KEY_HIGHLIGHT_TERMS, ""));
        } catch (IllegalArgumentException invalidSavedTerms) {
            prefs.edit().remove(KEY_HIGHLIGHT_TERMS).apply();
            return HighlightTerms.empty();
        }
    }

    private void showHighlightTermsDialog() {
        showHighlightTermsDialog(highlightTerms.normalizedText(), -1, -1, false);
    }

    private void showHighlightTermsDialog(String draft, int selectionStart, int selectionEnd,
            boolean rejectedInput) {
        if (highlightDialog != null && highlightDialog.isShowing()) return;
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = makeAboutText(getString(R.string.highlight_prompt));
        label.setLabelFor(R.id.highlight_terms_input);
        header.addView(label, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button presets = new Button(this);
        presets.setText("...");
        presets.setAllCaps(false);
        presets.setContentDescription(getString(R.string.highlight_presets_description));
        presets.setMinWidth(dp(48));
        presets.setMinHeight(dp(48));
        header.addView(presets, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        body.addView(header);

        EditText input = new EditText(this);
        input.setId(R.id.highlight_terms_input);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, uiSizeSp(16));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(4);
        input.setMaxLines(8);
        input.setHorizontallyScrolling(false);
        input.setHint(R.string.highlight_hint);
        input.setFilters(new InputFilter[]{(source, start, end, dest, dstart, dend) -> {
            int length = dest.length() - (dend - dstart) + (end - start);
            if (length > HighlightTerms.MAX_INPUT_CHARS) {
                highlightInputRejected = true;
                input.setError(getString(R.string.highlight_input_limit, HighlightTerms.MAX_INPUT_CHARS));
                // Reject the whole edit, retaining the selected text instead of truncating a term.
                return dest.subSequence(dstart, dend);
            }
            highlightInputRejected = false;
            return null;
        }});
        input.setText(draft == null ? "" : draft);
        if (rejectedInput) {
            highlightInputRejected = true;
            input.setError(getString(R.string.highlight_input_limit, HighlightTerms.MAX_INPUT_CHARS));
        }
        int length = input.length();
        if (selectionStart >= 0 && selectionEnd >= 0) {
            input.setSelection(Math.min(selectionStart, length), Math.min(selectionEnd, length));
        } else {
            input.setSelection(length);
        }
        body.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        body.addView(makeAboutText(getString(R.string.highlight_help)));
        presets.setOnClickListener(v -> showHighlightPresets(presets, input));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.highlight_title)
                .setView(scroll)
                .setPositiveButton(R.string.highlight_save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.highlight_clear, null)
                .create();
        highlightDialog = dialog;
        highlightTermsInput = input;
        dialog.setOnDismissListener(ignored -> {
            if (highlightDialog == dialog) {
                highlightDialog = null;
                highlightTermsInput = null;
                highlightInputRejected = false;
            }
        });
        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            input.setText("");
            highlightInputRejected = false;
            input.setError(null);
        });
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (highlightInputRejected) {
                input.setError(getString(R.string.highlight_input_limit, HighlightTerms.MAX_INPUT_CHARS));
                return;
            }
            try {
                HighlightTerms selected = HighlightTerms.parse(input.getText().toString());
                prefs.edit().putString(KEY_HIGHLIGHT_TERMS, selected.normalizedText()).apply();
                highlightTerms = selected;
                refreshDisplayedReportHighlights();
                dialog.dismiss();
            } catch (IllegalArgumentException invalidTerms) {
                input.setError(invalidTerms.getMessage());
            }
        });
    }

    private void showHighlightPresets(View anchor, EditText input) {
        PopupMenu menu = new PopupMenu(this, anchor);
        int[] labels = {R.string.highlight_preset_rain, R.string.highlight_preset_wind,
                R.string.highlight_preset_winter, R.string.highlight_preset_heat};
        HighlightTerms.Preset[] presets = HighlightTerms.Preset.values();
        for (int i = 0; i < presets.length; i++) menu.getMenu().add(0, i, i, labels[i]);
        menu.setOnMenuItemClickListener(item -> {
            try {
                String added = HighlightTerms.addPreset(input.getText().toString(), presets[item.getItemId()]);
                input.setText(added);
                input.setSelection(input.length());
                input.setError(null);
            } catch (IllegalArgumentException invalidTerms) {
                input.setError(invalidTerms.getMessage());
            }
            return true;
        });
        menu.show();
    }

    private void refreshDisplayedReportHighlights() {
        if (stationScreenShowing || contentView == null || scrollView == null
                || !hasReportForDisplay(currentBundle, currentView)) return;
        String report = displayTextForView(currentBundle, currentView);
        // A fetch progress/error message may currently occupy the report area.
        if (!report.contentEquals(contentView.getText())) return;
        TextView reportView = contentView;
        ScrollView reportScroll = scrollView;
        int x = reportScroll.getScrollX();
        int y = reportScroll.getScrollY();
        int start = reportView.getSelectionStart();
        int end = reportView.getSelectionEnd();
        reportView.setText(highlightReportTerms(report));
        if (start >= 0 && end >= 0 && reportView.getText() instanceof Spannable) {
            Selection.setSelection((Spannable) reportView.getText(), start, end);
        }
        reportScroll.post(() -> {
            if (canUpdateUi() && scrollView == reportScroll && contentView == reportView) {
                reportScroll.scrollTo(x, y);
            }
        });
    }

    private void showAboutDialog() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(12), dp(24), dp(8));
        body.addView(makeVegetarianReminder());

        TextView byline = makeAboutText(getString(R.string.about_byline));
        byline.setTypeface(byline.getTypeface(), Typeface.BOLD);
        body.addView(byline);
        body.addView(makeAboutLink(getString(R.string.about_website),
                getString(R.string.about_website), getString(R.string.about_website_url)));

        TextView author = makeAboutText(getString(R.string.about_author));
        author.setPadding(0, dp(12), 0, 0);
        body.addView(author);
        String email = getString(R.string.about_email);
        body.addView(makeAboutLink(email, email, "mailto:" + email));

        String license = getString(R.string.about_license);
        body.addView(makeAboutLink(license, license, getString(R.string.about_license_url)));
        Button readLicense = makeDialogButton("Read license offline");
        readLicense.setOnClickListener(view -> showBundledLicense());
        body.addView(readLicense, dialogButtonParams(false));

        TextView attribution = makeAboutText("Weather data: NOAA / National Weather Service. "
                + "NWS Lookup is independently published by ThatIAm.vip and is not affiliated "
                + "with or endorsed by NOAA or the National Weather Service.");
        attribution.setPadding(0, dp(16), 0, 0);
        body.addView(attribution);
        TextView version = makeAboutText(getString(R.string.about_version, APP_VERSION));
        version.setPadding(0, dp(12), 0, 0);
        body.addView(version);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    private void showBundledLicense() {
        final String text;
        try (InputStream input = getAssets().open("LICENSE.md")) {
            text = BoundedTextReader.read(input, 128 * 1024, "bundled license");
        } catch (IOException e) {
            Toast.makeText(this, "The bundled license could not be opened.", Toast.LENGTH_LONG).show();
            return;
        }
        TextView license = makeAboutText(text);
        license.setTextIsSelectable(true);
        license.setPadding(dp(24), dp(12), dp(24), dp(12));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(license);
        new AlertDialog.Builder(this)
                .setTitle("IALA license")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    private TextView makeVegetarianReminder() {
        TextView reminder = makeAboutLink(
                getString(R.string.vegetarian_reminder),
                getString(R.string.vegetarian_website),
                getString(R.string.vegetarian_url));
        reminder.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        reminder.setTypeface(reminder.getTypeface(), Typeface.BOLD);
        reminder.setPadding(0, 0, 0, dp(20));
        return reminder;
    }

    private TextView makeAboutText(CharSequence text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setLineSpacing(0, 1.14f);
        return view;
    }

    private TextView makeAboutLink(String text, String linkText, String target) {
        SpannableStringBuilder linked = new SpannableStringBuilder(text);
        int start = text.indexOf(linkText);
        if (start >= 0) {
            linked.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    openAboutLink(target);
                }
            }, start, start + linkText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView view = makeAboutText(linked);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setMinHeight(dp(48));
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private void openAboutLink(String target) {
        Uri uri = Uri.parse(target);
        Intent intent = new Intent("mailto".equals(uri.getScheme())
                ? Intent.ACTION_SENDTO : Intent.ACTION_VIEW, uri);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.link_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private Button makeDialogButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, uiSizeSp(16));
        button.setMinHeight(dp(48));
        return button;
    }

    private LinearLayout.LayoutParams dialogButtonParams(boolean closeButton) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, closeButton ? dp(12) : dp(8), 0, 0);
        return params;
    }

    private void copyDataToClipboard() {
        String text = clipboardDataText();
        if (text.trim().isEmpty()) {
            Toast.makeText(this, "No weather data to copy.", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "Clipboard is not available.", Toast.LENGTH_SHORT).show();
            return;
        }

        clipboard.setPrimaryClip(ClipData.newPlainText("NWS Lookup data", text));
        Toast.makeText(this, "Copied weather data to clipboard.", Toast.LENGTH_SHORT).show();
    }

    private String clipboardDataText() {
        if (currentBundle == null) return "";

        StringBuilder out = new StringBuilder();
        out.append("NWS Lookup").append("\n\n");
        out.append("Forecast office: ").append(selectedStation == null
                ? fallback(currentBundle.stationIdentifier, "unknown") : selectedStation.summary())
                .append("\n\n");
        appendClipboardReport(out, VIEW_AFD);
        appendClipboardReport(out, VIEW_HOURLY);
        appendClipboardReport(out, VIEW_DAILY);
        return out.toString().trim();
    }

    private void appendClipboardReport(StringBuilder out, int view) {
        String title = viewTitle(view);
        out.append(title).append('\n').append(repeat('=', title.length())).append('\n');
        if (view == VIEW_AFD) {
            out.append("Coverage: regional discussion for the selected office.\n");
        } else if (hasReportForDisplay(currentBundle, view)) {
            out.append(forecastPointText(currentBundle, view)).append('\n');
            String pointOffice = view == VIEW_HOURLY
                    ? currentBundle.hourlyPointOffice : currentBundle.dailyPointOffice;
            if (!isBlank(pointOffice)) {
                out.append("Point forecast office: ").append(pointOffice.trim()).append('\n');
            }
        }
        String fetchedAt = cacheTimestampForView(currentBundle, view);
        out.append("Fetched: ").append(detailedTimestamp(fetchedAt)).append('\n');
        out.append(view == VIEW_AFD ? "NWS issued: " : "NWS updated: ")
                .append(detailedTimestamp(reportTimestampForView(currentBundle, view))).append('\n');
        out.append("Cache Age: ").append(!hasReportForDisplay(currentBundle, view) ? "none"
                : isBlank(fetchedAt) ? "unknown" : cacheAge(fetchedAt)).append("\n\n");
        out.append(displayTextForView(currentBundle, view)).append("\n\n");
    }

    private void renderCurrentView() {
        updateViewButtons();
        renderStatus();
        renderForecastPoint();

        String text;
        if (currentBundle == null) {
            text = emptyDisplayTextForView(currentView);
        } else {
            text = displayTextForView(currentBundle, currentView);
        }
        if (contentView != null) contentView.setText(hasReportForDisplay(currentBundle, currentView)
                ? highlightReportTerms(text) : text);
        resetScrollToTop();
    }

    private void updateViewButtons() {
        applyViewButtonStyle(afdButton, "AFD", currentView == VIEW_AFD);
        applyViewButtonStyle(hourlyButton, "Hourly", currentView == VIEW_HOURLY);
        applyViewButtonStyle(dailyButton, "Daily", currentView == VIEW_DAILY);
    }

    private void applyViewButtonStyle(Button button, String label, boolean active) {
        if (button == null) return;
        int foreground = themeColor(android.R.attr.textColorPrimary, Color.BLACK);
        int background = themeColor(android.R.attr.colorBackground, Color.WHITE);

        button.setText(active ? "[" + label + "]" : label);
        button.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        button.setSelected(active);
        button.setTextColor(active ? background : foreground);
        button.setBackground(viewButtonBackground(foreground, background, active));
    }

    private GradientDrawable viewButtonBackground(int foreground, int background, boolean active) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(active ? foreground : background);
        drawable.setStroke(dp(1), foreground);
        drawable.setCornerRadius(dp(3));
        return drawable;
    }

    private int themeColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (!getTheme().resolveAttribute(attr, value, true)) return fallback;
        if (value.resourceId != 0) {
            try {
                return getResources().getColor(value.resourceId, getTheme());
            } catch (Exception ignored) {
            }
        }
        return value.data == 0 ? fallback : value.data;
    }

    private void resetScrollToTop() {
        if (scrollView == null) return;
        scrollView.post(() -> scrollView.scrollTo(0, 0));
    }

    private String emptyDisplayTextForView(int view) {
        if (selectedStation == null) {
            return "No forecast office selected.";
        }
        return "No cached " + viewTitle(view).toLowerCase(Locale.US) + " for this office yet.";
    }

    private String limitCachedHourlyText(String text) {
        if (text == null) return text;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return trimmed;

        String[] blocks = trimmed.split("\\n\\s*\\n");
        if (blocks.length <= MAX_HOURLY_PERIODS) return trimmed;

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < MAX_HOURLY_PERIODS; i++) {
            String block = blocks[i] == null ? "" : blocks[i].trim();
            if (block.isEmpty()) continue;
            if (out.length() > 0) out.append("\n\n");
            out.append(block);
        }
        return out.toString().trim();
    }

    private String displayTextForView(WeatherBundle bundle, int view) {
        if (view == VIEW_HOURLY) {
            return fallback(limitCachedHourlyText(bundle.hourlyText), "No hourly forecast was loaded.");
        }
        if (view == VIEW_DAILY) {
            return fallback(bundle.dailyText, "No daily forecast was loaded.");
        }
        return fallback(bundle.afdText, "No Area Forecast Discussion was loaded.");
    }

    private String sectionText(String title, String updated, String body) {
        StringBuilder out = new StringBuilder();
        out.append(title).append('\n');
        out.append(repeat('-', title.length())).append("\n");
        if (updated != null && !updated.trim().isEmpty()) {
            out.append("updated ").append(niceIso(updated)).append("\n\n");
        } else {
            out.append('\n');
        }
        out.append(body == null || body.trim().isEmpty() ? "No cached weather yet." : body.trim());
        return out.toString().trim();
    }

    private String fallback(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    private String viewTitle(int view) {
        if (view == VIEW_HOURLY) return "Hourly Forecast";
        if (view == VIEW_DAILY) return "Daily Forecast";
        return "Forecast Discussion";
    }

    private String fetchActionLabel(int view) {
        return "Fetch " + viewTitle(view).toLowerCase(Locale.US) + " now";
    }

    private String fetchProgressLabel(int view) {
        return "Fetching " + viewTitle(view).toLowerCase(Locale.US) + " from NWS...";
    }

    private String cacheTimestampForView(WeatherBundle bundle, int view) {
        if (bundle == null) return "";
        if (view == VIEW_HOURLY) {
            if (isBlank(bundle.hourlyText)) return "";
            return firstNonEmpty(bundle.hourlyFetchedAt);
        }
        if (view == VIEW_DAILY) {
            if (isBlank(bundle.dailyText)) return "";
            return firstNonEmpty(bundle.dailyFetchedAt);
        }
        if (isBlank(bundle.afdText)) return "";
        return firstNonEmpty(bundle.afdFetchedAt);
    }

    private String latestCacheTimestamp(WeatherBundle bundle) {
        if (bundle == null) return "";
        return firstNonEmpty(bundle.fetchedAt, bundle.afdFetchedAt, bundle.hourlyFetchedAt, bundle.dailyFetchedAt);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private String filterCachedAfdText(String text) {
        if (text == null || text.trim().isEmpty()) return text;

        Map<String, StringBuilder> sections = new LinkedHashMap<>();
        String current = null;
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String maybeHeading = lines[i].trim();
            if (i + 1 < lines.length && isUnderline(lines[i + 1].trim())) {
                current = wantedAfdSection(maybeHeading);
                if (current != null && !sections.containsKey(current)) {
                    sections.put(current, new StringBuilder());
                }
                i++;
                continue;
            }

            if (current != null) sections.get(current).append(lines[i]).append('\n');
        }

        if (sections.isEmpty()) return text;

        StringBuilder out = new StringBuilder();
        appendAfdSection(out, "Key Messages", sections.get("Key Messages"));
        appendAfdSection(out, "Short Term", sections.get("Short Term"));
        appendAfdSection(out, "Long Term", sections.get("Long Term"));
        String filtered = out.toString().trim();
        return filtered.isEmpty()
                ? "No Key Messages, Short Term, or Long Term section was found in this Area Forecast Discussion."
                : filtered;
    }

    private boolean isUnderline(String line) {
        return line != null && line.matches("^-{3,}$");
    }

    private CharSequence highlightReportTerms(String text) {
        if (highlightTerms.isEmpty()) return text;
        List<HighlightTerms.Range> matches = highlightTerms.find(text);
        if (matches.isEmpty()) return text;
        SpannableStringBuilder spans = new SpannableStringBuilder(text);
        for (HighlightTerms.Range match : matches) {
            spans.setSpan(new StyleSpan(Typeface.BOLD), match.start, match.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spans.setSpan(new UnderlineSpan(), match.start, match.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spans;
    }

    private static String niceIso(String iso) {
        if (iso == null || iso.trim().isEmpty()) return "";
        try {
            OffsetDateTime t = OffsetDateTime.parse(iso);
            return t.format(DateTimeFormatter.ofPattern("EEE MMM d h:mm a", Locale.getDefault()));
        } catch (Exception ignored) {
            return iso;
        }
    }

    private String cacheAge(String iso) {
        if (iso == null || iso.trim().isEmpty()) return "none";
        try {
            OffsetDateTime t = OffsetDateTime.parse(iso);
            Duration d = Duration.between(t, OffsetDateTime.now());
            long minutes = Math.max(0, d.toMinutes());
            long days = minutes / (24 * 60);
            long hours = (minutes % (24 * 60)) / 60;
            long mins = minutes % 60;
            if (days > 0) return days + "d " + hours + "h";
            if (hours > 0) return hours + "h " + mins + "m";
            return mins + "m";
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static String shortHour(String iso) {
        if (iso == null || iso.trim().isEmpty()) return "?";
        try {
            OffsetDateTime t = OffsetDateTime.parse(iso);
            return t.format(DateTimeFormatter.ofPattern("EEE h a", Locale.getDefault()));
        } catch (Exception ignored) {
            return iso;
        }
    }

    private static String shortDay(String iso) {
        if (iso == null || iso.trim().isEmpty()) return "?";
        try {
            OffsetDateTime t = OffsetDateTime.parse(iso);
            return t.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()));
        } catch (Exception ignored) {
            return iso;
        }
    }

    private static String repeat(char c, int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) out.append(c);
        return out.toString();
    }

    private static String cleanError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) return e.getClass().getSimpleName();

        String oneLine = message.replace('\n', ' ').trim();
        String lower = oneLine.toLowerCase(Locale.US);
        if (lower.contains("network connection is not available")
                || lower.contains("unable to resolve host")
                || lower.contains("no address associated")
                || lower.contains("network is unreachable")
                || lower.contains("no route to host")
                || lower.contains("failed to connect to")
                || lower.contains("connection refused")) {
            return offlineMessage();
        }
        return oneLine;
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private StationRecord loadSelectedStation() {
        String text = stringPreference(KEY_SELECTED_STATION_JSON, "");
        if (text == null || text.trim().isEmpty()) return null;
        try {
            StationRecord record = StationRecord.fromSelectionJson(parseJsonObject(text));
            return record.isForecastOfficeCode() ? record : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String boundedWeatherCacheString() {
        synchronized (WEATHER_CACHE_LOCK) {
            String cached = stringPreference(KEY_CACHE_JSON, null);
            if (cached == null || cached.length() <= MAX_PRIVATE_FILE_CHARS) return cached;
            prefs.edit().remove(KEY_CACHE_JSON).apply();
            return null;
        }
    }

    private String stringPreference(String key, String fallback) {
        try {
            return prefs.getString(key, fallback);
        } catch (ClassCastException incompatibleSavedValue) {
            prefs.edit().remove(key).apply();
            return fallback;
        }
    }

    private int loadFontSizeStep() {
        int saved;
        try {
            saved = prefs.getInt(KEY_FONT_SIZE_STEP, 0);
        } catch (ClassCastException incompatibleSavedValue) {
            prefs.edit().remove(KEY_FONT_SIZE_STEP).apply();
            return 0;
        }
        int bounded = Math.max(MIN_FONT_SIZE_STEP, Math.min(MAX_FONT_SIZE_STEP, saved));
        if (bounded != saved) prefs.edit().putInt(KEY_FONT_SIZE_STEP, bounded).apply();
        return bounded;
    }

    private boolean sameStation(String a, String b) {
        String aa = a == null ? "" : a.trim();
        String bb = b == null ? "" : b.trim();
        return aa.equalsIgnoreCase(bb);
    }

    private static final class AppNetworkCoordinator {
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private final SingleFlightGate gate = new SingleFlightGate();
        private final long sessionId = nonZeroSessionId();

        private final WeakListenerSlot<MainActivity> listener = new WeakListenerSlot<>();
        private int activeOperation = NetworkSnapshot.OP_NONE;
        private int activeView = VIEW_AFD;
        private long eventId;
        private int completedOperation = NetworkSnapshot.OP_NONE;
        private int completedView = VIEW_AFD;
        private boolean returnToMainAfterFetch;
        private WeatherBundle weatherResult;
        private StationListCache stationResult;
        private String errorMessage = "";

        synchronized void captureCursor(MainActivity activity) {
            activity.handledNetworkSession = sessionId;
            activity.handledNetworkEvent = eventId;
        }

        void attach(MainActivity activity) {
            synchronized (this) {
                listener.attach(activity);
            }
            postSnapshot();
        }

        synchronized void detach(MainActivity activity) {
            listener.detach(activity);
        }

        boolean startWeather(
                Context context,
                StationRecord station,
                WeatherBundle base,
                int view,
                boolean returnToMain) {
            final Context appContext = context.getApplicationContext();
            final long token;
            synchronized (this) {
                token = gate.tryStart();
                if (token == 0L) return false;
                clearCompletionLocked();
                activeOperation = NetworkSnapshot.OP_WEATHER;
                activeView = view;
            }
            postSnapshot();

            StationRecord taskStation = station.copy();
            WeatherBundle taskBase = base == null ? new WeatherBundle() : base.copy();
            Thread worker = new Thread(() -> {
                WeatherBundle result = null;
                String error = "";
                try {
                    result = fetchWeatherPart(appContext, taskStation, taskBase, view);
                    String cacheJson = result.toJson().toString();
                    if (cacheJson.length() > MAX_PRIVATE_FILE_CHARS) {
                        throw new IOException("Weather cache exceeds the "
                                + MAX_PRIVATE_FILE_CHARS + " character limit.");
                    }
                    synchronized (WEATHER_CACHE_LOCK) {
                        SharedPreferences cachePrefs = appContext
                                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                        Object previous = cachePrefs.getAll().get(KEY_CACHE_JSON);
                        boolean saved = cachePrefs.edit().putString(KEY_CACHE_JSON, cacheJson).commit();
                        if (!saved) {
                            // A failed commit can still replace the in-memory value.
                            // Restore it before a recreated Activity can read the cache.
                            SharedPreferences.Editor rollback = cachePrefs.edit();
                            if (previous instanceof String) {
                                rollback.putString(KEY_CACHE_JSON, (String) previous);
                            } else {
                                rollback.remove(KEY_CACHE_JSON);
                            }
                            rollback.commit();
                            throw new IOException("Weather cache could not be saved.");
                        }
                    }
                } catch (Exception e) {
                    result = null;
                    error = cleanError(e);
                }
                completeWeather(token, view, returnToMain, result, error);
            }, "NWSLookup-weather");
            worker.start();
            return true;
        }

        boolean startStationUpdate(Context context) {
            final Context appContext = context.getApplicationContext();
            final long token;
            synchronized (this) {
                token = gate.tryStart();
                if (token == 0L) return false;
                clearCompletionLocked();
                activeOperation = NetworkSnapshot.OP_STATIONS;
            }
            postSnapshot();

            Thread worker = new Thread(() -> {
                StationListCache result = null;
                String error = "";
                try {
                    result = fetchStationListFromNws(appContext);
                    writePrivateFile(appContext, OFFICE_CACHE_FILE, result.toJson().toString());
                } catch (Exception e) {
                    result = null;
                    error = cleanError(e);
                }
                completeStations(token, result, error);
            }, "NWSLookup-stations");
            worker.start();
            return true;
        }

        private void completeWeather(
                long token,
                int view,
                boolean returnToMain,
                WeatherBundle result,
                String error) {
            synchronized (this) {
                if (!gate.finish(token)) return;
                activeOperation = NetworkSnapshot.OP_NONE;
                completedOperation = NetworkSnapshot.OP_WEATHER;
                completedView = view;
                returnToMainAfterFetch = returnToMain;
                weatherResult = result == null ? null : result.copy();
                stationResult = null;
                errorMessage = error == null ? "" : error;
                eventId++;
            }
            postSnapshot();
        }

        private void completeStations(long token, StationListCache result, String error) {
            synchronized (this) {
                if (!gate.finish(token)) return;
                activeOperation = NetworkSnapshot.OP_NONE;
                completedOperation = NetworkSnapshot.OP_STATIONS;
                completedView = VIEW_AFD;
                returnToMainAfterFetch = false;
                weatherResult = null;
                stationResult = result == null ? null : result.copyForUi();
                errorMessage = error == null ? "" : error;
                eventId++;
            }
            postSnapshot();
        }

        private void postSnapshot() {
            mainHandler.post(() -> {
                MainActivity activity;
                NetworkSnapshot current;
                synchronized (AppNetworkCoordinator.this) {
                    activity = listener.get();
                    current = snapshotLocked();
                }
                if (activity != null) activity.onNetworkSnapshot(current);
            });
        }

        private NetworkSnapshot snapshotLocked() {
            return new NetworkSnapshot(
                    sessionId,
                    gate.isRunning(),
                    activeOperation,
                    activeView,
                    eventId,
                    completedOperation,
                    completedView,
                    returnToMainAfterFetch,
                    weatherResult,
                    stationResult,
                    errorMessage
            );
        }

        private void clearCompletionLocked() {
            completedOperation = NetworkSnapshot.OP_NONE;
            returnToMainAfterFetch = false;
            weatherResult = null;
            stationResult = null;
            errorMessage = "";
        }

        private static long nonZeroSessionId() {
            long value = System.nanoTime() ^ System.currentTimeMillis();
            return value == 0L ? 1L : value;
        }
    }

    private static final class NetworkSnapshot {
        static final int OP_NONE = 0;
        static final int OP_WEATHER = 1;
        static final int OP_STATIONS = 2;

        final long sessionId;
        final boolean busy;
        final int activeOperation;
        final int activeView;
        final long eventId;
        final int completedOperation;
        final int completedView;
        final boolean returnToMainAfterFetch;
        final WeatherBundle weatherBundle;
        final StationListCache stationCache;
        final String errorMessage;

        NetworkSnapshot(
                long sessionId,
                boolean busy,
                int activeOperation,
                int activeView,
                long eventId,
                int completedOperation,
                int completedView,
                boolean returnToMainAfterFetch,
                WeatherBundle weatherBundle,
                StationListCache stationCache,
                String errorMessage) {
            this.sessionId = sessionId;
            this.busy = busy;
            this.activeOperation = activeOperation;
            this.activeView = activeView;
            this.eventId = eventId;
            this.completedOperation = completedOperation;
            this.completedView = completedView;
            this.returnToMainAfterFetch = returnToMainAfterFetch;
            this.weatherBundle = weatherBundle == null ? null : weatherBundle.copy();
            this.stationCache = stationCache == null ? null : stationCache.copyForUi();
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }
    }

    private static final class NwsHttpException extends IOException {
        final int statusCode;

        NwsHttpException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }

    private static class Product {
        String issuedAt = "";
        String text = "";
    }

    private static class PointData {
        String lat = "";
        String lon = "";
        String office = "";
        String pointOffice = "";
        String forecastUrl = "";
        String hourlyUrl = "";
        String locationName = "";
        String resolvedAt = "";
        boolean fromCache = false;

        static PointData fromBundle(WeatherBundle bundle) {
            PointData data = new PointData();
            if (bundle == null) return data;
            data.lat = bundle.lat;
            data.lon = bundle.lon;
            data.office = bundle.office;
            data.pointOffice = bundle.pointOffice;
            data.forecastUrl = bundle.forecastUrl;
            data.hourlyUrl = bundle.hourlyUrl;
            data.locationName = bundle.locationName;
            data.resolvedAt = bundle.pointResolvedAt;
            data.fromCache = true;
            return data;
        }
    }

    private static class StationListCache {
        String fetchedAt = "";
        String sourceUrl = "";
        String sourceLabel = "";
        JSONArray rawLocations = new JSONArray();
        ArrayList<StationRecord> records = new ArrayList<>();

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("fetchedAt", fetchedAt);
            json.put("sourceUrl", sourceUrl);
            json.put("sourceLabel", sourceLabel);
            json.put("locations", rawLocations == null ? new JSONArray() : rawLocations);
            return json;
        }

        StationListCache copyForUi() {
            StationListCache copy = new StationListCache();
            copy.fetchedAt = fetchedAt;
            copy.sourceUrl = sourceUrl;
            copy.sourceLabel = sourceLabel;
            for (StationRecord record : records) copy.records.add(record.copy());
            return copy;
        }
    }

    private static class StationRecord {
        String stationIdentifier = "";
        String name = "";
        String lat = "";
        String lon = "";
        String rawJson = "";

        static StationRecord fromBuiltInLine(String line) {
            if (line == null) return null;
            String[] parts = line.split("\\|", -1);
            if (parts.length < 4) return null;
            StationRecord r = new StationRecord();
            r.stationIdentifier = normalizeCode(parts[0]);
            r.name = parts[1].trim();
            r.lat = parts[2].trim();
            r.lon = parts[3].trim();
            r.rawJson = r.toLocationJson().toString();
            return r.stationIdentifier.isEmpty() ? null : r;
        }

        static StationRecord fromAfdLocationItem(Object item, HashMap<String, StationRecord> defaults) throws JSONException {
            String code = "";
            String name = "";
            String raw = "";

            if (item instanceof JSONObject) {
                JSONObject obj = (JSONObject) item;
                raw = obj.toString(2);
                JSONObject props = obj.optJSONObject("properties");
                code = firstNonEmpty(
                        obj.optString("locationId", ""),
                        obj.optString("locationIdentifier", ""),
                        obj.optString("id", ""),
                        obj.optString("@id", ""),
                        obj.optString("code", ""),
                        props == null ? "" : props.optString("locationId", ""),
                        props == null ? "" : props.optString("locationIdentifier", ""),
                        props == null ? "" : props.optString("id", ""),
                        props == null ? "" : props.optString("@id", ""),
                        props == null ? "" : props.optString("code", "")
                );
                name = firstNonEmpty(
                        obj.optString("locationName", ""),
                        obj.optString("name", ""),
                        obj.optString("title", ""),
                        obj.optString("officeName", ""),
                        props == null ? "" : props.optString("locationName", ""),
                        props == null ? "" : props.optString("name", ""),
                        props == null ? "" : props.optString("title", ""),
                        props == null ? "" : props.optString("officeName", "")
                );
            } else if (item != null && item != JSONObject.NULL) {
                raw = String.valueOf(item);
                code = raw;
            }

            code = normalizeCode(code);
            if (code.isEmpty()) return null;

            StationRecord r = new StationRecord();
            r.stationIdentifier = code;
            r.name = cleanDisplayName(name);
            r.rawJson = raw;

            StationRecord def = defaults == null ? null : defaults.get(code.toUpperCase(Locale.US));
            if (def != null) {
                if (r.name.isEmpty()) r.name = def.name;
                r.lat = def.lat;
                r.lon = def.lon;
            }
            if (r.name.isEmpty()) r.name = code;
            return r;
        }

        static StationRecord fromSelectionJson(JSONObject json) {
            StationRecord r = new StationRecord();
            r.stationIdentifier = normalizeCode(json.optString("stationIdentifier", ""));
            r.name = json.optString("name", "");
            r.lat = json.optString("lat", "");
            r.lon = json.optString("lon", "");
            r.rawJson = json.optString("rawJson", "");
            return r;
        }

        private static String firstNonEmpty(String... values) {
            if (values == null) return "";
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) return value.trim();
            }
            return "";
        }

        private static String normalizeCode(String value) {
            if (value == null) return "";
            String text = value.trim().toUpperCase(Locale.US);
            if (text.contains("/")) {
                int slash = text.lastIndexOf('/');
                text = slash >= 0 ? text.substring(slash + 1) : text;
            }
            text = text.replaceAll("[^A-Z0-9]", "");
            if (text.length() == 4 && text.startsWith("K")) text = text.substring(1);
            Matcher exact = Pattern.compile("^[A-Z]{3}$").matcher(text);
            if (exact.find()) return text;
            Matcher trailing = Pattern.compile("([A-Z]{3})$").matcher(text);
            if (trailing.find()) return trailing.group(1);
            Matcher any = Pattern.compile("[A-Z]{3}").matcher(text);
            return any.find() ? any.group() : "";
        }

        private static String cleanDisplayName(String value) {
            if (value == null) return "";
            return value.replaceAll("\\s+", " ").trim();
        }

        JSONObject toSelectionJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("stationIdentifier", stationIdentifier);
                json.put("name", name);
                json.put("lat", lat);
                json.put("lon", lon);
                json.put("rawJson", rawJson);
            } catch (JSONException ignored) {
            }
            return json;
        }

        JSONObject toLocationJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("locationId", stationIdentifier);
                json.put("locationName", name);
            } catch (JSONException ignored) {
            }
            return json;
        }

        boolean hasCoordinates() {
            return ForecastCachePolicy.isValidPoint(lat, lon);
        }

        boolean isForecastOfficeCode() {
            return stationIdentifier != null && stationIdentifier.trim().matches("[A-Za-z]{3}");
        }

        StationRecord copy() {
            StationRecord r = new StationRecord();
            r.stationIdentifier = stationIdentifier;
            r.name = name;
            r.lat = lat;
            r.lon = lon;
            r.rawJson = rawJson;
            return r;
        }

        StationRecord withCoordinatesFromGeometry(JSONObject json) {
            StationRecord r = copy();
            JSONArray coords = findGeometryCoordinates(json);
            if (coords != null && coords.length() >= 2 && !coords.isNull(0) && !coords.isNull(1)) {
                double rawLon = coords.optDouble(0);
                double rawLat = coords.optDouble(1);
                r.lat = String.format(Locale.US, "%.4f", rawLat);
                r.lon = String.format(Locale.US, "%.4f", rawLon);
            }
            return r;
        }

        private static JSONArray findGeometryCoordinates(JSONObject object) {
            if (object == null) return null;
            JSONObject geometry = object.optJSONObject("geometry");
            if (geometry != null) {
                JSONArray coords = geometry.optJSONArray("coordinates");
                if (coords != null) return coords;
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject child = object.optJSONObject(key);
                if (child != null) {
                    JSONArray found = findGeometryCoordinates(child);
                    if (found != null) return found;
                }
            }
            return null;
        }

        String summary() {
            String id = stationIdentifier == null ? "" : stationIdentifier.trim();
            String n = name == null ? "" : name.trim();
            if (!id.isEmpty() && !n.isEmpty() && !id.equalsIgnoreCase(n)) return id + " - " + n;
            if (!id.isEmpty()) return id;
            if (!n.isEmpty()) return n;
            return "selected station";
        }

        String rowText() {
            String text = summary();
            if (!hasCoordinates()) text += "\nrepresentative point will be resolved from NWS when fetched";
            return text;
        }

        String searchText() {
            return (stationIdentifier + " " + name).toLowerCase(Locale.US);
        }
    }

    private class StationAdapter extends BaseAdapter {
        private ArrayList<StationRecord> allRecords = new ArrayList<>();
        private ArrayList<StationRecord> records = new ArrayList<>();
        private String filter = "";

        void setRecords(ArrayList<StationRecord> records) {
            this.allRecords = records == null ? new ArrayList<>() : records;
            applyFilter();
        }

        void setFilter(String filter) {
            this.filter = filter == null ? "" : filter.trim().toLowerCase(Locale.US);
            applyFilter();
        }

        private void applyFilter() {
            records = new ArrayList<>();
            if (filter.isEmpty()) {
                records.addAll(allRecords);
            } else {
                for (StationRecord record : allRecords) {
                    if (record.searchText().contains(filter)) records.add(record);
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return records.size();
        }

        @Override
        public StationRecord getItem(int position) {
            return records.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView row;
            if (convertView instanceof TextView) {
                row = (TextView) convertView;
            } else {
                row = new TextView(MainActivity.this);
                row.setTextSize(TypedValue.COMPLEX_UNIT_SP, uiSizeSp(17));
                row.setLineSpacing(0, 1.12f);
                row.setPadding(dp(8), dp(10), dp(8), dp(10));
                row.setMinHeight(dp(58));
            }
            row.setText(records.get(position).rowText());
            return row;
        }
    }

    private static class WeatherBundle {
        String stationIdentifier = "";
        String stationName = "";
        String locationName = "";
        String lat = "";
        String lon = "";
        String office = "";
        String pointOffice = "";
        String forecastUrl = "";
        String hourlyUrl = "";
        String pointResolvedAt = "";
        String hourlyLat = "";
        String hourlyLon = "";
        String hourlyLocationName = "";
        String hourlyPointOffice = "";
        String dailyLat = "";
        String dailyLon = "";
        String dailyLocationName = "";
        String dailyPointOffice = "";
        String fetchedAt = "";
        String afdFetchedAt = "";
        String dailyFetchedAt = "";
        String hourlyFetchedAt = "";
        String afdUpdated = "";
        String dailyUpdated = "";
        String hourlyUpdated = "";
        String afdText = "";
        String hourlyText = "";
        String dailyText = "";

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("stationIdentifier", stationIdentifier);
            json.put("stationName", stationName);
            json.put("locationName", locationName);
            json.put("lat", lat);
            json.put("lon", lon);
            json.put("office", office);
            json.put("pointOffice", pointOffice);
            json.put("forecastUrl", forecastUrl);
            json.put("hourlyUrl", hourlyUrl);
            json.put("pointResolvedAt", pointResolvedAt);
            json.put("hourlyLat", hourlyLat);
            json.put("hourlyLon", hourlyLon);
            json.put("hourlyLocationName", hourlyLocationName);
            json.put("hourlyPointOffice", hourlyPointOffice);
            json.put("dailyLat", dailyLat);
            json.put("dailyLon", dailyLon);
            json.put("dailyLocationName", dailyLocationName);
            json.put("dailyPointOffice", dailyPointOffice);
            json.put("fetchedAt", fetchedAt);
            json.put("afdFetchedAt", afdFetchedAt);
            json.put("dailyFetchedAt", dailyFetchedAt);
            json.put("hourlyFetchedAt", hourlyFetchedAt);
            json.put("afdUpdated", afdUpdated);
            json.put("dailyUpdated", dailyUpdated);
            json.put("hourlyUpdated", hourlyUpdated);
            json.put("afdText", afdText);
            json.put("hourlyText", hourlyText);
            json.put("dailyText", dailyText);
            return json;
        }

        static WeatherBundle fromJson(String text) throws JSONException {
            JSONObject json = parseJsonObject(text);
            WeatherBundle b = new WeatherBundle();
            b.stationIdentifier = jsonText(json, "stationIdentifier");
            b.stationName = jsonText(json, "stationName");
            b.locationName = jsonText(json, "locationName");
            b.lat = jsonText(json, "lat");
            b.lon = jsonText(json, "lon");
            b.office = jsonText(json, "office");
            b.pointOffice = jsonText(json, "pointOffice");
            b.forecastUrl = jsonText(json, "forecastUrl");
            b.hourlyUrl = jsonText(json, "hourlyUrl");
            b.pointResolvedAt = jsonText(json, "pointResolvedAt");
            b.hourlyLat = jsonText(json, "hourlyLat");
            b.hourlyLon = jsonText(json, "hourlyLon");
            b.hourlyLocationName = jsonText(json, "hourlyLocationName");
            b.hourlyPointOffice = jsonText(json, "hourlyPointOffice");
            b.dailyLat = jsonText(json, "dailyLat");
            b.dailyLon = jsonText(json, "dailyLon");
            b.dailyLocationName = jsonText(json, "dailyLocationName");
            b.dailyPointOffice = jsonText(json, "dailyPointOffice");
            b.fetchedAt = jsonText(json, "fetchedAt");
            b.afdFetchedAt = jsonText(json, "afdFetchedAt");
            b.dailyFetchedAt = jsonText(json, "dailyFetchedAt");
            b.hourlyFetchedAt = jsonText(json, "hourlyFetchedAt");
            b.afdUpdated = jsonText(json, "afdUpdated");
            b.dailyUpdated = jsonText(json, "dailyUpdated");
            b.hourlyUpdated = jsonText(json, "hourlyUpdated");
            b.afdText = jsonText(json, "afdText");
            b.hourlyText = jsonText(json, "hourlyText");
            b.dailyText = jsonText(json, "dailyText");

            // Before 1.0.12 the point identity was shared. Migrate it once for each
            // existing report, then keep the two reports' identities independent.
            if (!json.has("hourlyLat") && !isBlank(b.hourlyText)) {
                b.hourlyLat = b.lat;
                b.hourlyLon = b.lon;
                b.hourlyLocationName = b.locationName;
                b.hourlyPointOffice = b.pointOffice;
            }
            if (!json.has("dailyLat") && !isBlank(b.dailyText)) {
                b.dailyLat = b.lat;
                b.dailyLon = b.lon;
                b.dailyLocationName = b.locationName;
                b.dailyPointOffice = b.pointOffice;
            }

            // Older caches used one timestamp. Only absent keys need migration;
            // a present empty value means that report's fetch time is unknown.
            if (!b.fetchedAt.trim().isEmpty()) {
                if (!json.has("afdFetchedAt") && !b.afdText.trim().isEmpty()) b.afdFetchedAt = b.fetchedAt;
                if (!json.has("hourlyFetchedAt") && !b.hourlyText.trim().isEmpty()) b.hourlyFetchedAt = b.fetchedAt;
                if (!json.has("dailyFetchedAt") && !b.dailyText.trim().isEmpty()) b.dailyFetchedAt = b.fetchedAt;
            }
            return b;
        }

        WeatherBundle copy() {
            WeatherBundle b = new WeatherBundle();
            b.stationIdentifier = stationIdentifier;
            b.stationName = stationName;
            b.locationName = locationName;
            b.lat = lat;
            b.lon = lon;
            b.office = office;
            b.pointOffice = pointOffice;
            b.forecastUrl = forecastUrl;
            b.hourlyUrl = hourlyUrl;
            b.pointResolvedAt = pointResolvedAt;
            b.hourlyLat = hourlyLat;
            b.hourlyLon = hourlyLon;
            b.hourlyLocationName = hourlyLocationName;
            b.hourlyPointOffice = hourlyPointOffice;
            b.dailyLat = dailyLat;
            b.dailyLon = dailyLon;
            b.dailyLocationName = dailyLocationName;
            b.dailyPointOffice = dailyPointOffice;
            b.fetchedAt = fetchedAt;
            b.afdFetchedAt = afdFetchedAt;
            b.dailyFetchedAt = dailyFetchedAt;
            b.hourlyFetchedAt = hourlyFetchedAt;
            b.afdUpdated = afdUpdated;
            b.dailyUpdated = dailyUpdated;
            b.hourlyUpdated = hourlyUpdated;
            b.afdText = afdText;
            b.hourlyText = hourlyText;
            b.dailyText = dailyText;
            return b;
        }
    }
}
