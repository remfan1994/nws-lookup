# NWS Lookup 1.0.13 release checks

Verification date: October 20, 2026 (UTC).

Release assembly, release lint, host tests, and Android JVM stress tests are
recorded below and in STRESS_TEST_RESULTS.md. The release APK produced for these checks is unsigned.
Signing and a real phone/emulator smoke test remain to be performed before
distributing an installable APK.

## Release identity

| Item | Verified value |
| --- | --- |
| App name | NWS Lookup |
| Application ID | vip.thatiam.nwslookup |
| Java package / Gradle namespace | vip.thatiam.nwslookup |
| Version | 1.0.13 |
| Version code | 14 |
| Minimum Android API | 26 |
| Compile / target Android API | 35 / 35 |
| Launcher activity | vip.thatiam.nwslookup.MainActivity |

The former net.thatiam.nwslookup installation is a separate Android application.
Installing this corrected package does not migrate the former application's
private settings or weather cache. Updates within the corrected package require
the appropriate existing signing key.

Android reference: https://developer.android.com/build/configure-app-module

## Completed build and static checks

- Full Gradle tasks `:app:assembleRelease :app:lintRelease`: passed.
- Toolchain: Gradle 8.9, Android Gradle Plugin 8.6.1, JDK 17, Android SDK 35,
  Build Tools 34.0.0.
- Build dependencies were cached in an isolated temporary toolchain; no SDK,
  test runtime, or machine-specific local.properties is included in the source.
- Lint: zero errors, 17 warnings. These comprise 13 text/localization notices,
  two missing monochrome launcher-icon variants, one redundant v26 qualifier,
  and one recommendation to use apply() instead of rollback.commit(). That
  synchronous rollback is deliberate, runs on the weather worker, and passed
  the failed-storage tests.
  Deprecated Android API usage is also reported by the compiler; compatibility
  branches remain in place for API 26 and newer.
- The compiled manifest has only INTERNET and ACCESS_NETWORK_STATE permissions.
- No services, receivers, providers, native libraries, or debuggable flag are
  present in the release APK.
- Binary manifest identity, version, minimum/target SDK, and launcher were checked.
- APK ZIP integrity and zip alignment passed.
- APK signature inspection confirmed the expected unsigned state.
- About credits, website/email/license resources, and the reminder were checked
  in the compiled resource table, including exactly two spaces after `?`.
- The compiled license and notice assets match their source documents byte for
  byte. The Gradle Wrapper's original Apache license and script headers remain
  intact.

Unsigned verification APK:

    app/build/outputs/apk/release/app-release-unsigned.apk

Final unsigned-build size, SHA-256, binary metadata, and validation results are
recorded in `verification/build-results.json`. The source archive includes
source and verification records. It does not include an unsigned APK or a
private signing key. Signing or rebuilding produces a separate artifact whose
signature and metadata must be checked before distribution.

## Regression checks and source review

- The full API 35 Android JVM run passed 32 tests, including 525,600 minute
  callbacks over a simulated year, 1,000 Activity lifetimes, 10,000 pause/resume
  pairs, 10,000 cache serialization cycles, and injected I/O/network failures.
- Focused follow-up runs verify the final office-selection progress fix and
  representative-point request bound. Minimum-API checks use API 26 explicitly.
  Exact run counts and source hashes are in STRESS_TEST_RESULTS.md and the
  `verification/` records.
- The host suite passed 68 forecast/cache policy assertions, 6,000 concurrent
  single-flight contenders, maximum-size response and JSON scans under a 32-MiB
  JVM heap, stream cleanup, stale-token, and listener-handoff checks.
- All six production Java files passed syntax checks and the Android compiler.
- Wrong-type saved preferences, unsupported font adjustments, unknown report
  timestamps, malformed/deep saved JSON, interrupted office-list writes, and
  failed weather commits have regression coverage.
- Reads and writes share the appropriate cache lock; Activity startup captures
  its completion cursor before loading cache. Results are accepted for the
  current office only, including through Activity recreation.
- The local minute callback leaves one pending callback while resumed and none
  when paused/stopped/destroyed. Five additional simulated years while stopped
  caused no further refresh work. Passive UI actions started no network operation.
- The requested reminder and full offline license are exercised repeatedly by
  the Android JVM UI tests. Real phone layout, link dispatch, and keyboard/system
  bar behavior still require the checks below.

To repeat the included host checks:

    bash tests/run_host_tests.sh

To build and run release lint with your configured Android SDK:

    ./gradlew :app:assembleRelease :app:lintRelease

NWS mapping reference: https://www.weather.gov/documentation/services-web-api

Android inset reference:
https://developer.android.com/develop/ui/views/layout/edge-to-edge

## Phone smoke test before releasing an APK

No phone or emulator was attached during this verification. These checks have
not been represented as completed:

1. Install a build signed with your release key. Confirm NWS Lookup starts and
   its package is vip.thatiam.nwslookup. Check portrait and landscape layouts,
   light/dark mode, large system text, and gesture or button navigation. Buttons
   and report text should stay clear of system bars and any display cutout.
2. Open the forecast-office selector, type in its filter, and check that the
   keyboard leaves the list and controls usable. Select an office such as MTR.
3. Fetch AFD, Hourly, and Daily separately. Each must require confirmation. AFD
   should be identified as a regional discussion; Hourly/Daily should show their
   representative coordinates and, where available, the NWS relative place name.
4. Inspect Details and copied data. Fetched time must be distinct from the NWS
   issued/updated time, and the correct point must stay attached to each report.
5. Leave the app visible for a minute, then background and reopen it. Cache Age
   should advance without automatically fetching weather or changing the report.
6. Enable airplane mode and reopen the app. Saved reports should remain readable.
   A requested fetch should give a clear error while preserving the cache.
7. With a good cache for one office, choose another office and cancel or fail its
   fetch, then return to the original office. Its saved reports should reappear.
8. Open Details, About, and Read license offline. Verify scrolling, the exact
   prominent vegetarian reminder, and the complete offline license. Check website
   links and the email composer when online, including Back navigation.
9. Rotate or background the app during a manual fetch. It should receive the
   result after returning without starting a duplicate fetch or showing the wrong
   active view.

## Signing and publication

Use Android Studio's Generate Signed Bundle / APK flow with your release key.
Keep that key and its passwords backed up securely and outside the published
source. For an existing vip.thatiam.nwslookup installation, use its corresponding
signing identity. Verify the final signed artifact with `apksigner verify` and
repeat the phone smoke test on that artifact.

This maintenance pass retains target SDK 35. If the intended distribution is
Google Play, its target-API and privacy-policy/Data Safety requirements are an
additional publishing gate; this source has not been certified as Play-ready.
Check the current requirements before making a Play submission:

- https://support.google.com/googleplay/android-developer/answer/11926878
- https://support.google.com/googleplay/android-developer/answer/10144311

LICENSE.md and NOTICE.md accompany the source, and identical documents are in
the APK assets. NOTICE.md identifies the exact upstream IALA revision and keeps
the application license distinct from NOAA/NWS weather data and Gradle tooling.
