# NWS Lookup 1.0.14 release checks

Verification date: October 23, 2026 (America/Los_Angeles).

Application: NWS Lookup
Application ID and namespace: vip.thatiam.nwslookup
Version: 1.0.14, code 15
Minimum SDK: 26; compile/target SDK: 35/35

## Changes and test scope

The former automatic precipitation emphasis is replaced with default-off custom
highlight terms. Details > Highlight terms provides a comma/newline-separated
editor and optional editable presets. The rain preset includes PWAT, PWATS,
and precipitable water. Saved settings and editing are local display options.

HIGHLIGHT_TEST_RESULTS.md describes the behavior, bounds, exact tests, and
repeat commands. Verification records for this revision are in
verification/1.0.14/. The complete 1.0.13 stress record is retained separately
under verification/1.0.13/ and STRESS_TEST_RESULTS.md.

Host checks passed, along with 22 Android API 35 tests and 15 asserted API 33
compatibility tests. No selected test failed or was skipped.

## Completed release verification

- Normal assembleRelease and lintRelease tasks passed in 24 seconds.
- Compiled identity: vip.thatiam.nwslookup, version 1.0.14/code 15, min API 26,
  target API 35. About and User-Agent use the same version.
- Twenty production/resource/build inputs were compared with the inspected
  build and matched byte for byte.
- Only INTERNET and ACCESS_NETWORK_STATE permissions are present. The APK has
  one Activity and no services, receivers, providers, or native libraries.
- No old namespace or test-framework code is packaged in the normal release.
- New highlight labels/presets and the requested About/vegetarian/identity
  strings are present in compiled resources. License and notice assets match.
- APK ZIP integrity and alignment passed; the expected unsigned state was
  confirmed. No signing or publishing was performed.
- Lint: zero errors and 18 warnings. The 17 earlier warnings remain; the new
  PluralsCandidate warning recommends a quantity resource for the fixed
  8,192-character input-limit message. Its current English wording is correct
  for the configured limit. These are nonblocking lint warnings.

Unsigned inspection APK: 62,085 bytes.
SHA-256: d6bb4010c4a7dd63663d8075aab2352f6d629eb6f983b56f2685d0e61fc113d4

This source package includes verification records, not the unsigned APK or any
signing credentials. Rebuilding/signing creates a separate artifact to verify.
Detailed metadata is in verification/1.0.14/build-results.json.

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
9. Open Highlight terms. Confirm no terms are enabled by default, add a custom
   term and the rain preset, and check PWAT/PWATS emphasis. Cancel should discard
   edits; Clear followed by Save should remove all term emphasis. Rotate while
   editing and check that the draft survives. Check the keyboard and scrolling
   with large system text.
10. Rotate or background the app during a manual fetch. It should receive the
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
