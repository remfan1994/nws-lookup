# NWS Lookup

Text-only Android app for National Weather Service weather lookup.

Current release: 1.0.14 (version code 15).

- Android application ID: vip.thatiam.nwslookup
- Gradle namespace and Java package: vip.thatiam.nwslookup
- About NWS Lookup is available from the Cache Age ... Details dialog.

The preceding 1.0.10 package correction updated the application ID, namespace, Java source/test package paths, and host-test runner together.

Android treats the former net.thatiam.nwslookup ID and the corrected vip.thatiam.nwslookup ID as separate applications. Installing a build from this source does not update the former installation in place or automatically transfer its private settings and cached weather. Both installations can coexist; the corrected installation starts with its own data.

Android identity and storage references:

- https://developer.android.com/build/configure-app-module
- https://developer.android.com/training/data-storage/app-specific

Feature set:

- App drawer name: NWS Lookup
- No ZIP lookup
- No GPS
- No Android location permission
- No third-party web service
- All in-app weather network requests are guarded so only https://api.weather.gov is allowed
- Main screen controls are AFD, Hourly, and Daily
- Forecast Discussion is the default AFD view
- Pressing AFD, Hourly, or Daily shows only that view
- The active AFD/Hourly/Daily button uses a reverse-video style
- Android Back returns from Hourly or Daily to AFD
- Keeps one forecast-office cache, with separately fetched AFD, Hourly, and Daily reports
- Keeps the last good saved cache when an empty response or failed request is received
- Restores the previous in-memory weather cache when a disk commit fails
- Replaces the downloaded office list atomically, with coordinated reads and interrupted-write recovery
- Changing forecast office does not erase the saved cache; returning to that office restores it until another office is fetched successfully
- Shows cached weather on startup, useful in airplane mode
- Shows Cache Age prominently between the controls and output
- Updates Cache Age on return to the app and once per minute while the app is visible, using only local time and saved data
- Identifies AFD as a regional office discussion and shows the actual representative coordinates for Hourly and Daily
- The ... button beside Cache Age opens a details/action popup
- The details popup shows the forecast office, view, representative point when applicable, Fetched time, and separate NWS issued/updated time
- The details popup contains Fetch forecast discussion now / Fetch hourly forecast now / Fetch daily forecast now depending on the active view, Change forecast office, Copy data to clipboard, Highlight terms, About NWS Lookup, and Close actions
- Each weather fetch requires a confirmation before any network request is made
- Fetch confirmations identify a one-time fetch, with a short Fetch now confirmation button
- Details and About content scroll when needed on smaller screens
- Details shows the vegetarian reminder prominently between its title and station information; About repeats it between its title and credits
- About links open the browser or email app only when tapped
- About includes a complete, selectable offline copy of the IALA license and identifies the independent publisher and NOAA/NWS weather-data source
- The Close action is placed underneath the other details-popup actions
- Update WFO list requires a confirmation before any network request is made
- Network-unavailable errors use a plain message asking the user to turn off airplane mode or open network connections
- Reflows hard-wrapped NWS Forecast Discussion text into readable paragraphs
- Hourly forecast output is limited to 48 hourly periods
- Forecast Discussion output is limited to Key Messages, Short Term, and Long Term
- Key Messages are displayed first when present
- Weather-term highlighting is off by default, including after upgrading from the former automatic rain highlighting
- Optional Highlight terms setting accepts comma-separated or newline-separated words and phrases, with editable presets
- Enabled matches use bold and underline, with whole-word, case-insensitive matching
- Uses system light/dark mode via values/ and values-night/ themes
- Applies system-bar, display-cutout, and keyboard insets on both main and forecast-office selection screens
- Uses theme-derived foreground/background colors for the active view button so light mode, dark mode, and color inversion remain usable
- Adds bottom content-font controls: increase size and decrease size

Personal highlighting:

- Open the ... Details menu and select Highlight terms.
- The dialog shows Highlight these terms: with a multiline input field.
- Separate terms with commas or new lines. Spaces within a term form a phrase.
- The ... button beside the prompt offers Rain and precipitation, Wind, Snow and ice, and Heat presets.
- Presets add editable terms to the current draft; duplicate terms are removed. Custom entries are retained.
- The rain preset includes PWAT, PWATS, and precipitable water.
- Save applies the list. Cancel or Back discards edits. Clear empties the draft; Save then turns highlighting off.
- The empty default gives normal report text on fresh installations and upgrades. No automatic rain list is carried forward.
- Matching ignores capitalization and avoids word fragments: rain does not match terrain or train. Terms are literal text, not wildcard expressions.
- When terms overlap at the same position, the longest matching phrase is highlighted. Phrase whitespace can span wrapped lines.
- Editing changes only display emphasis; forecast text, cache timestamps, and copied data remain unchanged. Settings do not fetch weather.
- Saving preserves the current report and reading position. An open draft and its cursor survive Activity recreation.
- Limits: 100 distinct terms, 80 characters per term, and 8,192 characters of input. Overlong edits are rejected as a whole with an explanation.
- For exceptionally repetitive reports, only the first 2,000 matches receive emphasis. The full report text remains available.

Forecast office selection:

- Open the Cache Age ... popup, then choose Change forecast office.
- The selector shows Weather Forecast Office / AFD issuance-location style entries, for example:

    MTR - San Francisco Bay Area/Monterey
    SGX - San Diego
    LOX - Los Angeles/Oxnard
    STO - Sacramento
    HNX - San Joaquin Valley/Hanford

- The selector includes a Filter field, so typing a code or city name narrows the list.
- The selector uses a built-in WFO list first, so it is usable without downloading station data.
- Press Update WFO list to manually fetch current AFD issuance locations from NWS:

    https://api.weather.gov/products/types/AFD/locations

- Update WFO list shows a confirmation popup before contacting NWS.
- Station data is updated only when Update WFO list is pressed and confirmed.
- The downloaded WFO list is cached in app-private storage.
- The selector lists forecast offices rather than individual observation stations.
- Raw station JSON is not displayed in the selection list.

Weather fetch flow:

- The selected station is an AFD/WFO office code such as MTR, SGX, LOX, STO, or HNX.
- The app uses the selected office code for Area Forecast Discussion products:

    https://api.weather.gov/products/types/AFD/locations/{office}

- Hourly and daily forecasts still require a point coordinate. Known office codes have a small bundled representative point.
- If a manually updated NWS office code has no bundled point, the app tries to resolve a representative point through NWS office metadata and up to eight approved observation-station candidates. Empty metadata entries do not consume the candidate budget.
- The point is passed to the NWS points endpoint:

    https://api.weather.gov/points/{lat},{lon}

- The app follows the forecast and forecastHourly URLs returned by the NWS points response.
- A cached point-to-grid mapping is reused for less than 24 hours. On the next confirmed hourly/daily fetch, an older, missing, invalid, or future-dated mapping is resolved again. NWS recommends checking the mapping periodically because its office/grid identifiers can change: https://www.weather.gov/documentation/services-web-api
- If a reused forecast URL returns HTTP 404 or 410, that same manual fetch refreshes the mapping and retries the forecast once. Other errors are reported without an automatic retry.
- Mapping checks never run from a timer, app startup, or opening Details/About.
- Each saved hourly/daily report retains the point identity that produced it. Fetching one view cannot relabel the other view's saved forecast.
- Empty product lists, missing AFD text, AFDs with no readable selected sections, and forecast responses with no usable periods fail without replacing saved weather.
- A usable forecast period has a valid start time and either a finite temperature with a recognized unit or readable forecast text. Unusable periods are skipped; daily day/night pairing also requires adjoining time boundaries.
- Fetched time records receipt of a usable report. NWS issued/updated time is stored separately. An unknown fetch time is not inferred from the NWS issue time.
- View-specific fetch actions are only available from the ... details/action popup and require confirmation.
- Copy data to clipboard copies the cached AFD, limited hourly forecast, daily forecast, and cache metadata.

Implementation notes:

The app intentionally avoids ZIP databases, geocoders, GPS, and non-NWS services. The built-in station list exists only to make the selector usable immediately and to provide representative coordinates for hourly and daily forecasts. The manual Update WFO list button fetches the visible code/name list from NWS AFD issuance locations, but it does not run on its own and requires confirmation. The manual fetch action fetches only the active view (AFD, Hourly, or Daily), is hidden behind the Cache Age ... popup, and requires confirmation.

The weather cache remains a single most-recent lookup. Old observation-station selections from previous experimental builds are ignored at startup unless they are valid three-letter forecast-office codes.

The User-Agent is set in MainActivity.java as USER_AGENT. It currently contains no email address.

Build:

Open this folder in Android Studio and let Gradle sync. Build and run the app module. The source includes the standard Gradle 8.9 wrapper with its official distribution checksum pinned; Android Gradle Plugin remains 8.6.1.

Use JDK 17 with Android SDK Platform 35 and Build Tools 34.0.0. With the SDK configured locally, command-line builds can use:

    ./gradlew :app:assembleDebug

On Windows, use gradlew.bat :app:assembleDebug. The first sync/build may download Gradle and Android build dependencies.

To assemble and check the unsigned release:

    ./gradlew :app:assembleRelease :app:lintRelease

For an installable publication build, use Android Studio's Generate Signed Bundle / APK flow with your own signing key. This source contains no signing credentials. See RELEASE_CHECKS.md for the completed checks and the remaining phone/signing checklist.

Stress tests:

The source includes repeatable host tests and opt-in Android JVM tests using
Robolectric 4.14.1. The Android tests exercise the actual Activity, Handler,
SharedPreferences, AtomicFile, formatters, and network coordinator. Scripted
HTTP responses replace the transport boundary so the stress run sends no traffic
to NWS. Test dependencies are excluded from normal release builds.

    bash tests/run_host_tests.sh
    ./gradlew -PandroidStress -Pandroid.useAndroidX=true :app:testDebugUnitTest

See STRESS_TEST_RESULTS.md for the simulated-duration coverage, faults reproduced
before repair, exact results, minimum-API commands, and remaining device checks.

About NWS Lookup:

Both Details and About begin with this exact reminder in larger bold text, immediately below their titles:

    Not vegetarian?  www.bloodguiltcurse.net will change your mind.

Below the reminder it shows:

- by ThatIAm.vip
- www.thatiam.vip (https://www.thatiam.vip)
- remfan1994
- r94@thatiam.vip (mailto:r94@thatiam.vip)
- IALA (open source) (https://remfan1994.github.io/insha-allah-license-agreement/)
- Read license offline
- Weather data: NOAA / National Weather Service, with independent publisher attribution
- Version 1.0.14

The reminder's website is also clickable. Website links open externally using HTTPS; the email link opens a mail composer. Opening Details or About itself makes no network request. The existing weather confirmation and NWS host guard remain in place.

LICENSE.md contains the complete IALA text from its identified upstream revision. NOTICE.md records the immutable source, date, checksum, weather-data attribution, and Gradle Wrapper notices. Identical license and notice files are also included in the APK assets. About's Read license offline action opens the license directly from the installed app without contacting a website.

Revision 1.0.14
---------------

- Replaced mandatory precipitation highlighting with optional user-selected terms, disabled by default.
- Added Details > Highlight terms, an editable comma/newline-separated list, and four additive presets including PWAT/PWATS/precipitable water.
- Added Save, Cancel, and Clear behavior, saved settings, draft restoration during rotation, and report-only restyling that preserves the reading position.
- Used bounded literal matching and bounded input/spans, with no regex interpretation of user text.
- Added focused host and Android framework UI tests for defaults, editing, presets, persistence, rotation, validation, and passive-network/cache invariants.
- Updated app metadata, About version, and User-Agent to 1.0.14/code 15; application ID remains vip.thatiam.nwslookup.

Current verification is recorded in HIGHLIGHT_TEST_RESULTS.md and RELEASE_CHECKS.md.
The 1.0.13 duration/stress record is retained in STRESS_TEST_RESULTS.md with its original versioned evidence.

Revision 1.0.13
---------------

- Fixed replay of an old completed fetch after changing offices and reopening the app. Results completed during startup or rotation still reach the correct Activity.
- Preserved explicitly unknown per-report fetch times across reloads; only genuinely older cache formats inherit the former shared timestamp.
- Treated wrong-type saved report fields as absent, recovered safely from incompatible preference types, and clamped saved font adjustments to supported bounds.
- Preserved the previous office list through interrupted writes and serialized file reads with atomic replacement/recovery.
- Restored the previous in-memory weather cache after a failed disk commit, including while a recreated Activity tries to load the cache.
- Bounded JSON nesting at 64 levels before parsing network responses and saved JSON, preventing a small but deeply nested document from overflowing the parser stack.
- Bounded representative-point fallback to eight station candidates when office metadata has no usable geometry.
- Added accelerated lifecycle, cache, network, resource-limit, storage-failure, and minimum-API regression tests.
- Updated release metadata, About version, and User-Agent to 1.0.13/code 14. Application ID remains vip.thatiam.nwslookup.

Historical validation for 1.0.13 is recorded in STRESS_TEST_RESULTS.md and verification/1.0.13/RELEASE_CHECKS.md.

Revision 1.0.12
---------------

- Made regional discussion coverage and representative-point forecast coverage explicit. Saved Hourly and Daily reports keep separate location snapshots.
- Protected the last good cache from empty or unusable NWS responses, and from changing offices before a successful replacement fetch.
- Separated receipt time from NWS issue/update time, and made the visible cache-age label refresh using a foreground-only local callback.
- Added a 24-hour lifetime for point-to-grid mappings, plus one bounded refresh/retry for a retired cached forecast URL during a confirmed fetch.
- Added system-bar, cutout, and keyboard spacing without adding an Android UI dependency.
- Bundled the complete, pinned IALA license and provenance notices; added an offline license viewer and NOAA/NWS attribution.
- Retained the exact vegetarian reminder, requested About credits/links, manual fetch confirmations, NWS-only weather host guard, memory limits, and single-flight coordinator.
- Updated release metadata and User-Agent revision to 1.0.12/code 13; application ID remains vip.thatiam.nwslookup.

The earlier 1.0.12 publication review passed release assembly, lint, and host checks.
The later 1.0.13 stress review above adds Android runtime regression coverage.

Revision 1.0.11
---------------

- Added the vegetarian reminder beneath the Details title, plus About NWS Lookup with the reminder, author, website, email, and IALA link.
- Clarified immediate fetch actions and their confirmations; progress and error messages use separate wording.
- Made Details and About scrollable and added a Details accessibility label to the ... button.
- Included the standard Gradle wrapper for Android Studio and command-line builds.
- Updated release metadata and User-Agent revision to 1.0.11/code 12; application ID remains vip.thatiam.nwslookup.

Validation for 1.0.11:

- Existing host invariant/stress tests and production Java syntax checks passed.
- Android AAPT2 compiled resources and linked a binary manifest against the official Android API 35 library.
- JDK 17 compiled all four production Java files and generated R.java against Android API 35.
- Android D8 converted the compiled classes to DEX for minimum SDK 26.
- The linked binary manifest reports vip.thatiam.nwslookup, version 1.0.11, code 12.
- The compiled resource table preserves the exact About text and the two spaces after the reminder's question mark.
- The final source was checked for obsolete package references and exact placement of the reminder before the content of both dialogs.

The subsequent publication review also completed full Gradle/AGP release assembly and release lint for 1.0.11, with zero lint errors. Signing and device UI checks were not performed in that review.



Revision 1.0.10
---------------

- Moved network-operation ownership into an application-scoped, single-flight coordinator so rotation cannot start a duplicate fetch or strand a result in the old Activity.
- Kept only a weak registration for the current Activity and delivered the latest state at callback time, preventing stale Activity retention and stale queued state.
- Persisted successful weather results from the application context before publishing completion, so a recreated Activity loads or receives the result.
- Guaranteed that each HTTP connection is disconnected even when connection setup, response reading, or JSON retrieval fails.
- Limited each decoded NWS response to 2 MiB of characters and private/cache state to 4 MiB, including legacy SharedPreferences weather-cache validation.
- Added host stress tests for exact/oversized/chunked reads, stream cleanup, 6,000 concurrent single-flight contenders, stale tokens, and Activity-listener handoff.


Revision 1.0.9
--------------

- Font-size controls now affect only the weather report text.
- UI controls, selector rows, dialogs, and the increase/decrease buttons keep fixed UI font sizes so the controls do not become cramped.
- The saved font-size preference is still loaded on startup and remembered between sessions.
- The details popup fetch action is now view-specific: Fetch AFD, Fetch hourly, or Fetch daily.
- Fetch AFD updates only the cached AFD text.
- Fetch hourly updates only the cached hourly forecast.
- Fetch daily updates only the cached daily forecast.
- Hourly and daily fetches reuse cached NWS forecast URLs when available, avoiding the /points request after the first successful point-based fetch.
- Cache Age is now calculated for the active view instead of pretending all views were fetched at the same time.

Revision 1.0.8
--------------

- Limited the Hourly view to 48 hourly periods instead of every hourly period returned by NWS.
- Added a display-side limiter so old cached hourly text from prior versions is also capped.
- Removed the instructional placeholder text telling the user to press ... beside Cache Age.
- Added Copy data to clipboard to the Cache Age ... details/action popup.
- Changed the details/action popup to use in-dialog buttons: Fetch weather, Change station, Copy data to clipboard, then Close underneath.
- Bumped User-Agent and version metadata to 1.0.8.

Revision 1.0.7
--------------

- Removed the large Change Station / Refresh button from the main screen.
- Moved Change station and Fetch weather into the Cache Age ... popup.
- Added confirmation before Fetch weather contacts NWS.
- Added confirmation before Update WFO list contacts NWS.
- Renamed weather refresh wording to Fetch weather.
- Added bottom font-size controls: increase size and decrease size.
- Stored the local app font-size adjustment in SharedPreferences.
- Added ACCESS_NETWORK_STATE and a network availability check.
- Improved offline/airplane-mode error text.

Revision 1.0.6
--------------

- Removed Refresh from the main screen.
- Added AFD as a peer button with Hourly and Daily.
- Moved weather refresh to the station/configuration screen as Refresh weather.
- Renamed station-list refresh to Update station list.
- Simplified the main status area to Cache Age plus a details button.
- Added a Details popup showing station, office, and last updated.
- Removed the current-view metadata line.
- Added reverse-video style for the active AFD/Hourly/Daily button.
- Removed the section title and per-section updated line from the forecast output area so the weather text starts immediately under the status row.

Revision 1.0.5
--------------

- Replaced observation-station selection with a readable WFO / AFD office-code selector.
- Added built-in station list with code, name, and representative point for known offices.
- Added selector Filter field.
- Changed Update data to use NWS AFD issuance locations instead of observation stations.
- Removed state-code station lookup and raw JSON station rows.
- Kept manual-only station-list update behavior.
- Kept network guard restricting calls to api.weather.gov.

Revision 1.0.4
--------------

- Replaced the unbounded all-stations update with state/territory-filtered station updates.
- Update data fetched only one bounded NWS station page and did not follow pagination automatically.
- Changed the station-list cache file name so oversized all-stations caches from 1.0.3 were ignored.

Revision 1.0.3
--------------

- Replaced ZIP lookup with NWS-only station selection.
- Added full-screen station selection.
- Added manual station-list Update data button.
- Removed Zippopotam.us and all non-NWS network traffic.
- Added host guard so network calls to non-api.weather.gov URLs are rejected.
- Hourly forecast shows every period returned by NWS.
- Daily forecast shows every period returned by NWS.
- Replaced the previous notice area with prominent Last Updated and Cache Age status.
