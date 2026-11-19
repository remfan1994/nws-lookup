# NWS Lookup 1.0.14 highlighting verification

Verification date: October 23, 2026 (America/Los_Angeles).

Release: 1.0.14, version code 15. Application ID: vip.thatiam.nwslookup.

## Product decision

Highlighting is optional and off by default. The previous fixed precipitation
word list has been removed from the rendering path. A fresh installation or an
upgrade without a saved personal term list displays reports without automatic
term emphasis.

Details > Highlight terms opens a native dialog with the prompt Highlight these
terms:, a multiline field, an adjacent ... preset menu, and Save/Cancel/Clear.
Commas and line breaks separate entries. Save applies the list; Cancel or Back
discards edits; Clear empties the draft and Save then disables highlighting.

Presets are ordinary editable terms appended to the current draft. They retain
custom entries and remove duplicates. The four choices are Rain and
precipitation, Wind, Snow and ice, and Heat. The rain preset includes PWAT,
PWATS, and precipitable water. No preset is automatically selected.

Matches use bold and underline. Matching is literal, ignores capitalization,
and observes word boundaries. For example, rain matches RAIN but not terrain,
train, or rainfall unless rainfall is also listed. Phrases accept runs of
whitespace, including wrapped lines. The longest matching phrase at a given
position takes precedence. User text is never compiled as a regular expression.

## Bounds and persistence

The accepted setting is limited to 8,192 input characters, 100 distinct terms,
and 80 characters per term. An overlong edit or paste is rejected as a whole;
it does not silently truncate a term or replace the previous draft. Invalid
saved settings fall back to the empty list and remain editable.

Rendering adds at most 2,000 matching ranges per report. Reports themselves are
never truncated by highlighting. Empty lists return the original report text
without creating highlight spans. Settings are stored independently of the
weather content, and no highlight operation requests weather or changes the
cache. The current view and reading position are preserved on Save; an open
editor's unsaved draft, cursor, and selected report survive Activity recreation.
Progress, error, and empty-state messages are not highlighted.

## Host checks

The complete host runner passed. The focused term-policy suite passed 48,194
assertions, including parsing, case folding, phrase overlap, word boundaries,
literal punctuation, Unicode offsets, preset composition, invalid input, and
span limits. Its bounded-input exercise used 24 repeated 2,097,151-character
reports plus late-match and no-match scans under a 32-MiB JVM heap. In this run
that exercise took 165 ms; that is a host measurement, not a phone latency
promise. Dense inputs stop scanning when the 2,000-match limit is reached.

Existing forecast-cache policy, response-size/depth, concurrency, syntax, and
source-invariant checks also passed. The seven production Java source files
parsed successfully. Raw host output is in verification/1.0.14/host-tests.txt.

## Android framework checks

The primary API 35 run passed 22 tests: all 15 new highlighting cases and seven
existing dialog, scrolling, startup, and Activity-recreation regressions. The
new suite covers:

- Default-off behavior on both fresh installs and existing cached installations.
- Details entry, multiline editor, adjacent preset menu, labels and accessibility.
- Save normalization and matching across AFD, Hourly, and Daily reports.
- Cancel and Back preserving the active saved setting.
- Clear remaining an editable draft until Save; empty lists disabling emphasis.
- Editable additive presets, duplicate removal, and PWAT/PWATS coverage.
- Persistence through recreation and a cold Activity reopen.
- Unsaved and cleared drafts, cursor, and selected report surviving rotation.
- Invalid input retaining the editor and saved setting; whole-edit paste rejection.
- Recovery from corrupt or incompatible preference types.
- Current report, scroll position, clipboard source, and cache identity preservation.
- Repeated rendering without accumulated spans and a maximum of 2,000 matches.
- Saving during progress/error messages without replacing or styling those messages.

Every test also checks that non-highlight preferences stay unchanged and no
network worker or coordinator completion starts from this UI. Fixtures are
local; no test traffic is sent to NWS.

The same 15 new-feature cases also passed on the Android 13 / API 33 framework,
with an explicit API-level assertion, zero failures, and zero skips. The recorded
API 35 run took 76 seconds including compilation; API 33 took 29 seconds.
Together they executed 37 successful test cases. Test-execution time was 49.297
seconds on API 35 and 16.178 seconds on API 33; the remainder was build/runtime
setup. Compact per-case records and production source hashes are included in
verification/1.0.14/android-api35.json and android-api33.json. These are Robolectric JVM tests of the real app and Android framework
classes. No physical-device layout, keyboard, battery, signing, or installation
claim is made.

## Release build and packaged output

Normal release assembly and lint passed in 24 seconds. The package is
vip.thatiam.nwslookup, version 1.0.14/code 15, min API 26 and target API 35.
All 20 production/resource/build inputs match the inspected artifact. The APK
contains the new settings/presets, the complete license and notice, and the
requested About/reminder text. Test dependencies are absent from the release.

Lint returned zero errors and 18 warnings. Seventeen are the existing warnings;
one new PluralsCandidate recommendation concerns quantity localization of the
fixed 8,192-character input-limit message. This does not change current matching
or validation behavior. The full warning report is included.

The temporary unsigned APK was 62,085 bytes with SHA-256
d6bb4010c4a7dd63663d8075aab2352f6d629eb6f983b56f2685d0e61fc113d4.
ZIP integrity, alignment, and expected unsigned state were checked. No signing,
publishing, or device installation was performed. Build metadata and source
hashes are in verification/1.0.14/build-results.json and source-manifest.sha256.

## Reproduce

Host checks:

    bash tests/run_host_tests.sh

Primary highlighting UI suite:

    ./gradlew -PandroidStress -Pandroid.useAndroidX=true :app:testDebugUnitTest --tests vip.thatiam.nwslookup.HighlightSettingsTest

Android 13 / API 33 compatibility suite:

    ./gradlew -PandroidStress -PandroidStressApi33 -Pandroid.useAndroidX=true :app:testDebugUnitTest --tests vip.thatiam.nwslookup.Api33HighlightSettingsTest

Normal release assembly and lint:

    ./gradlew :app:assembleRelease :app:lintRelease

Use JDK 17, Android SDK Platform 35, and Build Tools 34.0.0. The standard Gradle
8.9 wrapper is included. The optional test configuration uses Robolectric 4.14.1
and JUnit 4.13.2; neither is included in the normal release app. First builds may
download the required dependencies. The previous duration/stress record is
preserved separately as STRESS_TEST_RESULTS.md for version 1.0.13; its year-long
simulation was not rerun or relabeled as testing this UI revision.
