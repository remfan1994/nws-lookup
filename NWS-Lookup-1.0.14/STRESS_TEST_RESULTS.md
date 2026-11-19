# NWS Lookup 1.0.13 stress-test results

Verification date: October 20, 2026 (UTC).

Historical record for 1.0.13. The 1.0.14 highlighting changes and their focused
checks are recorded in HIGHLIGHT_TEST_RESULTS.md and RELEASE_CHECKS.md.
The hashes and build output below identify the earlier 1.0.13 source.

Application ID: `vip.thatiam.nwslookup`. Version: `1.0.13`, code `14`.

## Scope and method

The tests execute the application's production Java classes. Android framework
behavior is supplied by Robolectric 4.14.1, using API 35 for the full suite and
API 26 for focused minimum-version regressions. These are Android JVM tests;
there was no attached phone, emulator, installation soak, or signed release.

Time-dependent callback tests accelerate Android's main-loop uptime. A year of
minute callbacks therefore means 525,600 actual callback executions under
simulated uptime, rather than a year of elapsed real-world use. Cache tests
separately exercise old/future timestamps, repeated serialization, fresh reads
from actual Android SharedPreferences disk files, and corrupted saved data.

Network tests invoke the actual HTTP/fetch/coordinator code. A scripted
HttpURLConnection transport supplies success, outage, redirect, truncated,
malformed, oversized, and deeply nested replies. No stress-test traffic is sent
to api.weather.gov. They verify application behavior around those responses;
they do not measure live NWS availability, cellular radios, TLS, or device power.

## Defects reproduced before repair

The exact saved 1.0.12 source archive used as the starting point had SHA-256:

    5cf297da1cf442e3d4993148fab09dcdc20646c9ad967482ef8a91a64a79a1a0

Eight distinct regressions failed against that original production source:

| Failure | Reproduction | Repair in 1.0.13 |
| --- | --- | --- |
| Old completion shown for a different office | Complete MTR, choose SGX, close and fresh-open the Activity in the same process | Capture the completion cursor before loading saved cache and require the completed result's office to match the selection |
| Unknown report age became falsely newer | Save/reload an explicitly empty Hourly/AFD fetch time beside a newer Daily time | Migrate the former shared timestamp only when the per-report key is absent |
| Invalid report values became visible weather | Store JSON null, numbers, booleans, arrays, or objects in report-text fields | Accept only actual text fields |
| Invalid saved font adjustment | Persist a font step outside -2 through 6 | Clamp the restored value to the supported range |
| Incompatible saved preference crashed startup | Store an integer where station/cache JSON should be a string, or a string where the font setting should be an integer | Recover from the incompatible type and use a safe default |
| Interrupted office-list write destroyed the old file | Inject an I/O failure after the first byte of a replacement | Atomic replacement and rollback preserve the complete prior office list |
| Small but deeply nested JSON overflowed the parser | Parse a 12,000-level document below the response-size limit | Check nesting before parsing, with a maximum of 64 levels |
| Failed weather save still exposed unsaved data | Force a real SharedPreferences disk commit to fail after it updates process memory | Restore the previous cache value before readers can observe the failed update |

During the AtomicFile repair, an additional concurrency regression was reproduced
against the intermediate implementation: a startup read could recover/roll back
an active writer. The final version uses one lock for office-list reads,
replacement, and recovery. This is recorded separately from the eight original
1.0.12 failures.

The stale-office repair also received a stronger UI regression: select SGX from
the actual office list while MTR work is pending, stop/start the screen, then
complete MTR. Rejecting the unrelated result initially left the old progress
message visible. The final guard restores the selected office's current view,
and all five affected lifecycle regressions passed afterward.

A final request-bound probe ran against the intermediate version's still-uncapped
representative-point fallback. One hundred approved stations without usable
geometry caused 101 requests. The final resolver stops after eight station
candidates. The corrected fixture used nine requests (one office plus eight
stations), and a valid eighth candidate still succeeded after 116 blank entries.
Redirects have their separate existing limit; nine is a fixture result rather
than a universal HTTP-connection ceiling. This probe is also recorded separately
from the original eight failures.

## Executed coverage

All recorded corrected runs passed, with zero failures, errors, or skips:

| Recorded run | Passed | Gradle elapsed time | MainActivity source revision |
| --- | --- | --- | --- |
| API 35 full duration/cache/UI/network suite | 32/32 | 69 seconds | After initial fixes; before final progress/candidate-bound repairs |
| API 35 affected lifecycle follow-up | 5/5 | 16 seconds | Includes final progress repair |
| API 35 complete network follow-up | 8/8 | 12 seconds | Final production source |
| API 26 focused compatibility suite | 18/18 | 23 seconds | Final production source |

The API 35 runs cover 34 distinct test methods across the full run and
follow-ups; the two candidate-bound methods were added after the initial
32-test run. The final-source API 26 checks cover the legacy AtomicFile behavior,
startup corruption recovery, font/timestamp bounds, seven lifecycle cases,
failed weather commits, deep network JSON, and both candidate boundaries. Each
minimum-version test subclass explicitly asserts API 26 at runtime.

The full duration run was preserved and the affected code paths were rerun after
the final small changes. Exact source hashes, test names, timings, and failure
messages are in `verification/1.0.13/test-runs.json` and the individual run files.
`verification/1.0.13/source-manifest.sha256` identifies the final production/build/test
files. Workload counts below describe each completed workload without adding
repeated runs together.

| Area | Executed workload | Result |
| --- | --- | --- |
| Activity lifetime | 1,000 create/start/use/destroy lifetimes and 10,000 pause/resume pairs | One foreground age callback; none when paused/destroyed; coordinator listener detached |
| Long visible duration | 365 simulated days, executing 525,600 minute callbacks | One scheduled callback, report unchanged, no network operation started |
| Long background duration | Five further simulated years while stopped | No additional age callbacks or network operations |
| Rotation and races | 40 Activity recreations during one pending fetch; completion during startup/read and while stopped | Result delivered once to the current Activity; old-office results rejected |
| Memory diagnostic | 100 additional closed Activities observed through WeakReference | 100/100 collected after explicit GC; callback/listener checks also passed |
| Passive UI | 100 rounds of Details, About, full offline license, filtering, and canceled fetch dialogs | Cache unchanged; no coordinator network operation; one visible callback |
| Reading position | 100 minute-refresh/resume rounds with an actually scrolled Daily report | View, report text, and scroll offset preserved |
| Cache serialization | 10,000 copy/save/parse cycles | Independent report identities/times preserved; fixture remained 985 JSON characters |
| Disk persistence | 50 fresh actual Android preference-file loads | Last saved report retained; fixture XML at most 1,651 bytes |
| Office selection | 400 office changes | Only matching reports displayed; original saved cache retained until replacement |
| HTTP resource cleanup | 1,500 success/error/redirect/header-failure/partial-read operations, using 1,800 connections | Every opened connection disconnected; every opened response stream closed |
| Fetch coordinator | 600 manual fetch operations: 150 successes and 450 injected failures | Failed responses preserved saved cache and released the single-flight gate |
| Mapping lifetime | 200 expiry/HTTP 404/410/outage cases | Fresh mapping used where needed; retry request counts stayed bounded |
| Representative-point fallback | 100 geometry-less candidates; a valid eighth candidate after 116 blanks | Stops after eight station candidates; the eighth usable candidate is accepted |
| Network size/depth | 40 oversized replies, plus a 12,000-level JSON document | Rejected with handled errors and closed resources |
| Failed weather saves | 25 actual Android SharedPreferences commit failures | Prior report retained both in memory and on a fresh disk reload |
| Failed office-list saves | 150 injected partial writes at 1, 64, and 8,192 bytes | Complete previous file retained; later successful replacement worked |
| Office-list recovery | 100 abandoned temporary writes, legacy backup recovery, and simultaneous read/write | Last complete file recovered; reader did not roll back the active writer |
| Saved-data compatibility | Wrong types, out-of-range font settings, malformed/oversized/deep JSON, and legacy formats | Startup remained usable; appropriate cache/selection fallback |
| Date boundaries | Very old/future dates, leap-day/century/2038 boundaries, and old-cache resume | Age formatting remained readable; no implicit network fetch |

The cache sizes above describe the repeated test fixture, not a maximum expected
real forecast size. The production limits remain 2 MiB of decoded characters per
network response and 4 MiB of characters for weather cache and private office-list files, with JSON
nesting limited to 64. Character limits are distinct from byte counts and heap
usage.

The separate host suite passed 68 forecast/cache policy assertions, 6,000
concurrent single-flight contenders, chunked-read and stale-token checks, 200
exact/oversized 2-MiB response reads, and 200 near-2-MiB JSON-depth scans. The
reader and depth stress processes ran with `-Xmx32m`. All six production Java
files passed syntax checks. This heap cap applies to the host components, not
the Android UI process.

Garbage collection is diagnostic: the deterministic lifetime tests check the
application's own references and callbacks independently. Collection behavior
under a particular device runtime can differ from this JVM test environment.

## Release verification

Normal release assembly and lint passed on the final source in 14 seconds.
The compiled package is vip.thatiam.nwslookup, version 1.0.13/code 14, with
minimum API 26 and target API 35. It contains the two expected network
permissions, one Activity, no background services/receivers/providers, and no
test framework code. The complete license/notice assets and requested About
strings were checked in the built APK.

Lint reported zero errors and 17 warnings: 13 existing text/localization
warnings, two existing monochrome-icon warnings, one existing redundant v26
qualifier, and a recommendation to replace rollback.commit() with apply(). The
last warning is intentionally retained: the worker completes rollback before
making the prior cache available to readers.

The temporary unsigned verification APK was 53,689 bytes; its SHA-256 is
44fa65618fbdbd2dbaf556a91b42dea40bea81ba9bd54d03d4aee4be8052a898.
ZIP integrity and alignment passed. Signature inspection returned the expected
unsigned result. `verification/1.0.13/build-results.json` records these checks and
confirms that the inspected production/resources/build configuration match the
provided source. Signing and phone installation remain separate steps.

## Repeat the tests

Prerequisites: JDK 17, Android SDK Platform 35 and Build Tools 34.0.0. The included
Gradle 8.9 wrapper verifies its downloaded distribution checksum. The first run
may download Gradle, Android build dependencies, JUnit, Robolectric, and the
Android framework test runtime. These test dependencies are not added to the
normal release APK.

Host checks, including a 32-MiB JVM heap for the reader/depth stress cases:

    bash tests/run_host_tests.sh

Full API 35 Android JVM suite:

    ./gradlew -PandroidStress -Pandroid.useAndroidX=true :app:testDebugUnitTest

Focused API 26 suite:

    sh tests/run-android-min26.sh

Release build and lint:

    ./gradlew :app:assembleRelease :app:lintRelease

An optional `-ProbolectricSdkDir=/absolute/path/to/framework-jars` selects
pre-downloaded framework JARs and sets Robolectric offline mode. Gradle's own
`--offline` option additionally requires previously cached build/test
dependencies. The .new recovery case is run on API 35; the API 26 script selects
the applicable legacy backup, write-failure, read/write overlap, startup, and
coordinator regressions explicitly.

## Remaining device verification

The reproducible checks substantially improve confidence in saved-state,
resource cleanup, repeated lifecycle transitions, and failure recovery. They
cannot certify arbitrary years of installation or every device/OS/storage fault.

Before distributing a signed build, perform the phone checks in
RELEASE_CHECKS.md. For duration-specific coverage, retain a useful cache, use the
app through a normal day, leave it backgrounded overnight, then reopen it in
airplane mode. Check that the saved reports, correct office/point, unknown or old
ages, and font setting survive. Repeat after a reboot and an app update signed
with the same key. During ordinary online use, rotate/background the app during
manual fetches and verify that it settles back to an idle state with one result.

No device battery, radio/network, real process-kill, full-disk, power-loss, or
multi-day installation measurements were performed. Injected I/O and Android
framework recovery tests provide narrower, repeatable evidence for those code
paths.
