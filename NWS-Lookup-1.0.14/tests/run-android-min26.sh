#!/bin/sh
set -eu
cd "$(dirname "$0")/.."

# Focused minimum-version regressions. API35-only .new recovery stays in the main suite.
exec "${NWS_GRADLE_BIN:-./gradlew}" -PandroidStress -Pandroid.useAndroidX=true -PandroidStressMin26 \
    :app:testDebugUnitTest \
    --tests 'vip.thatiam.nwslookup.Min26CacheTest.wrongTypePreferencesDoNotCrashColdActivityStartup' \
    --tests 'vip.thatiam.nwslookup.Min26CacheTest.persistedFontStepIsClampedOnStartup' \
    --tests 'vip.thatiam.nwslookup.Min26CacheTest.deeplyNestedSavedJsonCannotCrashStartupOrOfficeSelection' \
    --tests 'vip.thatiam.nwslookup.Min26CacheTest.modernUnknownFetchTimesAreNotReplacedByAnotherViewsNewerFetchTime' \
    --tests 'vip.thatiam.nwslookup.Min26CacheWriteTest.interruptedWritesAlwaysKeepThePreviousOfficeList' \
    --tests 'vip.thatiam.nwslookup.Min26CacheWriteTest.interruptedLegacyWriteRestoresTheBackup' \
    --tests 'vip.thatiam.nwslookup.Min26CacheWriteTest.concurrentReadCannotRollBackAnActiveOfficeListWrite' \
    --tests 'vip.thatiam.nwslookup.Min26LifecycleTest.coldReopenCannotReplayAnOldCompletionUnderAnotherOffice' \
    --tests 'vip.thatiam.nwslookup.Min26LifecycleTest.resultForDifferentOfficeCannotReplaceSelectedOffice' \
    --tests 'vip.thatiam.nwslookup.Min26LifecycleTest.completionAfterFreshCreateBeforeStartIsDelivered' \
    --tests 'vip.thatiam.nwslookup.Min26LifecycleTest.completionDuringTheStartupCacheReadIsNotLost' \
    --tests 'vip.thatiam.nwslookup.Min26LifecycleTest.completionWhileStoppedIsDeliveredOnceOnRestart' \
    --tests 'vip.thatiam.nwslookup.Min26LifecycleTest.inFlightCompletionFollowsFortyActivityRecreations' \
    --tests 'vip.thatiam.nwslookup.Min26LifecycleTest.ageRefreshAndResumePreserveTheChosenReportAndScroll' \
    --tests 'vip.thatiam.nwslookup.Min26NetworkTest.deeplyNestedResponseFailsWithoutStackOverflow' \
    --tests 'vip.thatiam.nwslookup.Min26NetworkTest.failedWeatherCommitPreservesThePreviouslySavedReport' \
    --tests 'vip.thatiam.nwslookup.Min26NetworkTest.representativePointLookupStopsAfterEightStationRequests' \
    --tests 'vip.thatiam.nwslookup.Min26NetworkTest.eighthRepresentativeStationCanSucceedAfterEmptyEntries' \
    "$@"
