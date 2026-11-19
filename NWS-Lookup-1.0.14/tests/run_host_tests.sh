#!/usr/bin/env bash
set -euo pipefail

NWS_PROJECT_ROOT=$(cd "$(dirname "$0")/.." && pwd)
NWS_TEST_TMP=$(mktemp -d)
trap 'rm -rf "$NWS_TEST_TMP"' EXIT

java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d "$NWS_TEST_TMP/classes" \
    "$NWS_PROJECT_ROOT/app/src/main/java/vip/thatiam/nwslookup/BoundedTextReader.java" \
    "$NWS_PROJECT_ROOT/app/src/main/java/vip/thatiam/nwslookup/ForecastCachePolicy.java" \
    "$NWS_PROJECT_ROOT/app/src/main/java/vip/thatiam/nwslookup/HighlightTerms.java" \
    "$NWS_PROJECT_ROOT/app/src/main/java/vip/thatiam/nwslookup/JsonDepthGuard.java" \
    "$NWS_PROJECT_ROOT/app/src/main/java/vip/thatiam/nwslookup/SingleFlightGate.java" \
    "$NWS_PROJECT_ROOT/app/src/main/java/vip/thatiam/nwslookup/WeakListenerSlot.java" \
    "$NWS_PROJECT_ROOT/tests/host/vip/thatiam/nwslookup/HostInvariantTests.java" \
    "$NWS_PROJECT_ROOT/tests/host/vip/thatiam/nwslookup/ForecastCachePolicyTests.java" \
    "$NWS_PROJECT_ROOT/tests/host/vip/thatiam/nwslookup/HighlightTermsTests.java" \
    "$NWS_PROJECT_ROOT/tests/host/vip/thatiam/nwslookup/JsonDepthGuardTests.java" \
    "$NWS_PROJECT_ROOT/tests/host/vip/thatiam/nwslookup/JavaSyntaxCheck.java"

java -Xmx32m -cp "$NWS_TEST_TMP/classes" vip.thatiam.nwslookup.ForecastCachePolicyTests
java -Xmx32m -cp "$NWS_TEST_TMP/classes" vip.thatiam.nwslookup.HighlightTermsTests
java -Xmx32m -cp "$NWS_TEST_TMP/classes" vip.thatiam.nwslookup.JsonDepthGuardTests
java -Xmx32m -cp "$NWS_TEST_TMP/classes" vip.thatiam.nwslookup.HostInvariantTests
java -cp "$NWS_TEST_TMP/classes" vip.thatiam.nwslookup.JavaSyntaxCheck \
    "$NWS_PROJECT_ROOT"/app/src/main/java/vip/thatiam/nwslookup/*.java

NWS_ACTIVITY="$NWS_PROJECT_ROOT/app/src/main/java/vip/thatiam/nwslookup/MainActivity.java"
rg -q 'private static final AppNetworkCoordinator NETWORK_COORDINATOR' "$NWS_ACTIVITY"
rg -q 'WeakListenerSlot<MainActivity>' "$NWS_ACTIVITY"
rg -q 'new WeakReference<>\(value\)' \
    "$NWS_PROJECT_ROOT/app/src/main/java/vip/thatiam/nwslookup/WeakListenerSlot.java"
rg -q 'getApplicationContext\(\)' "$NWS_ACTIVITY"
rg -q 'current = snapshotLocked\(\)' "$NWS_ACTIVITY"
rg -q 'putLong\(STATE_NETWORK_SESSION, handledNetworkSession\)' "$NWS_ACTIVITY"
rg -q 'putLong\(STATE_NETWORK_EVENT, handledNetworkEvent\)' "$NWS_ACTIVITY"
rg -q 'BoundedTextReader\.read\(stream, MAX_NETWORK_RESPONSE_CHARS' "$NWS_ACTIVITY"
rg -q 'BoundedTextReader\.read\(in, MAX_PRIVATE_FILE_CHARS' "$NWS_ACTIVITY"
rg -q 'cached\.length\(\) <= MAX_PRIVATE_FILE_CHARS' "$NWS_ACTIVITY"
rg -q '\.commit\(\);' "$NWS_ACTIVITY"
rg -q 'NETWORK_COORDINATOR\.startWeather' "$NWS_ACTIVITY"
rg -q 'NETWORK_COORDINATOR\.startStationUpdate' "$NWS_ACTIVITY"
if rg -q 'runOnUiThread' "$NWS_ACTIVITY"; then
    echo "Activity-owned worker callback remains" >&2
    exit 1
fi
NWS_WORKER_COUNT=$(rg -c 'Thread worker = new Thread' "$NWS_ACTIVITY")
test "$NWS_WORKER_COUNT" -eq 2

echo "NWS Lookup source invariants: PASS"
