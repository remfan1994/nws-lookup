package vip.thatiam.nwslookup;

/** Dependency-free regression cases for admitting data before a cache commit. */
public final class ForecastCachePolicyTests {
    private static final String PERIOD_START = "2026-10-20T08:00:00-07:00";
    private static final String NOW = "2026-10-20T12:00:00Z";
    private static int assertions;

    // Models JSONObject.NULL without adding an Android or JSON dependency.
    private static final Object JSON_NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    private ForecastCachePolicyTests() {
    }

    public static void main(String[] args) {
        rejectEmptyAndIncompletePeriods();
        acceptUsablePeriods();
        renderOnlyAvailableTemperatures();
        pairOnlyAdjacentForecastPeriods();
        enforceMappingAgeBoundaries();
        compareOnlyValidPoints();
        System.out.println("NWS Lookup forecast cache policy: PASS (" + assertions + " assertions)");
    }

    private static void rejectEmptyAndIncompletePeriods() {
        require(!usable(null, null, null, null, null), "empty period must not be admitted to replace a report");
        require(!usable(PERIOD_START, null, "", "", ""), "timestamp alone is not a usable forecast");
        require(!usable(PERIOD_START, JSON_NULL, "C", "", ""), "JSON null must not become a zero-degree forecast");
        require(!usable(PERIOD_START, JSON_NULL, "F", "  null  ", "NULL"), "null text is not weather content");
        require(!usable(PERIOD_START, null, "F", " \t ", "\n"), "whitespace is not weather content");
        require(!usable(PERIOD_START, "0", "C", "", ""), "a temperature string is not a numeric JSON value");
        require(!usable(PERIOD_START, Double.NaN, "C", "", ""), "NaN is not a forecast temperature");
        require(!usable(PERIOD_START, Double.POSITIVE_INFINITY, "F", "", ""), "infinite temperature must be rejected");
        require(!usable(PERIOD_START, 12, "K", "", ""), "unsupported temperature unit must be rejected");
        require(!usable(PERIOD_START, 12, null, "", ""), "temperature without units must be rejected");
        require(!usable("", 0, "C", "Clear", ""), "missing timestamp must reject an otherwise usable period");
        require(!usable("not a timestamp", 0, "C", "Clear", ""), "malformed timestamp must be rejected");
        require(!usable("2026-10-20T08:00:00", 0, "C", "Clear", ""), "timestamp needs its UTC offset");
    }

    private static void acceptUsablePeriods() {
        require(usable(PERIOD_START, 0, "C", "", ""), "zero Celsius is a valid measurement");
        require(usable(PERIOD_START, -4.5, "C", null, null), "finite negative temperatures must be accepted");
        require(usable("2026-10-20T15:00:00Z", 32, "F", "", ""), "UTC forecast timestamp must be accepted");
        require(usable(PERIOD_START, JSON_NULL, "F", "Rain likely", ""), "weather text remains usable without temperature");
        require(usable(PERIOD_START, null, null, "", "A chance of rain after midnight."), "detailed weather text alone is usable");
        require(usable(PERIOD_START, Double.NaN, "C", "Cloudy", ""), "unavailable temperature must not discard valid weather text");
    }

    private static void renderOnlyAvailableTemperatures() {
        require("0 C".equals(ForecastCachePolicy.temperatureText(0, "C")), "zero Celsius must render as zero");
        require("32 F".equals(ForecastCachePolicy.temperatureText(32, "F")), "numeric Fahrenheit value must render with units");
        require("unknown".equals(ForecastCachePolicy.temperatureText(null, "F")), "absent temperature must render as unknown");
        require("unknown".equals(ForecastCachePolicy.temperatureText(JSON_NULL, "F")), "JSON null must render as unknown");
        require("unknown".equals(ForecastCachePolicy.temperatureText("0", "C")), "numeric-looking strings must not be rendered as temperatures");
        require("unknown".equals(ForecastCachePolicy.temperatureText(Double.NaN, "C")), "NaN must render as unknown");
        require("unknown".equals(ForecastCachePolicy.temperatureText(Double.NEGATIVE_INFINITY, "F")), "infinity must render as unknown");
        require("unknown".equals(ForecastCachePolicy.temperatureText(12, "K")), "unsupported units must render as unknown");
        require("unknown".equals(ForecastCachePolicy.temperatureText(12, null)), "missing units must render as unknown");
    }

    private static void pairOnlyAdjacentForecastPeriods() {
        String end = "2026-10-20T18:00:00-07:00";
        require(ForecastCachePolicy.areAdjacentPeriods(end, end), "matching end/start times must be adjacent");
        require(ForecastCachePolicy.areAdjacentPeriods(end, "2026-10-21T01:00:00Z"), "equivalent offset boundaries must be adjacent");
        require(!ForecastCachePolicy.areAdjacentPeriods(null, end), "missing first end must prevent pairing");
        require(!ForecastCachePolicy.areAdjacentPeriods(end, ""), "missing next start must prevent pairing");
        require(!ForecastCachePolicy.areAdjacentPeriods("not a time", end), "malformed first end must prevent pairing");
        require(!ForecastCachePolicy.areAdjacentPeriods(end, "2026-10-20T18:00:00"), "next start without an offset must prevent pairing");
        require(!ForecastCachePolicy.areAdjacentPeriods(end, "2026-10-21T18:00:00-07:00"), "a later unrelated night after a skipped period must not pair with this day");
        require(!ForecastCachePolicy.areAdjacentPeriods(end, "2026-10-20T17:59:59-07:00"), "overlapping periods are not adjacent");
    }

    private static void enforceMappingAgeBoundaries() {
        require(ForecastCachePolicy.isMappingFresh(NOW, NOW), "mapping resolved now must be fresh");
        require(ForecastCachePolicy.isMappingFresh("2026-10-19T12:00:00.000000001Z", NOW), "just under 24 hours must remain fresh");
        require(!ForecastCachePolicy.isMappingFresh("2026-10-19T12:00:00Z", NOW), "exactly 24 hours must expire");
        require(!ForecastCachePolicy.isMappingFresh("2026-10-19T11:59:59Z", NOW), "over 24 hours must expire");
        require(ForecastCachePolicy.isMappingFresh("2026-10-20T05:00:00-07:00", NOW), "equivalent instants with different offsets must be fresh");
        require(!ForecastCachePolicy.isMappingFresh("2026-10-19T05:00:00-07:00", NOW), "24-hour expiry must respect equivalent offsets");
        require(!ForecastCachePolicy.isMappingFresh("2026-10-20T12:00:00.000000001Z", NOW), "future resolution time must be rejected");
        require(!ForecastCachePolicy.isMappingFresh("", NOW), "legacy empty mapping time must expire");
        require(!ForecastCachePolicy.isMappingFresh(null, NOW), "missing mapping time must expire");
        require(!ForecastCachePolicy.isMappingFresh("yesterday", NOW), "malformed mapping time must expire");
        require(!ForecastCachePolicy.isMappingFresh(NOW, "not a time"), "malformed current time must not establish freshness");
        require(!ForecastCachePolicy.isMappingFresh(NOW, null), "missing current time must not establish freshness");
    }

    private static void compareOnlyValidPoints() {
        require(ForecastCachePolicy.isValidPoint("37.7749", "-122.4194"), "ordinary coordinates must be valid");
        require(ForecastCachePolicy.isValidPoint("90", "180"), "positive coordinate boundaries must be valid");
        require(ForecastCachePolicy.isValidPoint("-90", "-180"), "negative coordinate boundaries must be valid");
        require(!ForecastCachePolicy.isValidPoint("NaN", "0"), "NaN latitude must be rejected");
        require(!ForecastCachePolicy.isValidPoint("0", "NaN"), "NaN longitude must be rejected");
        require(!ForecastCachePolicy.isValidPoint("Infinity", "0"), "infinite latitude must be rejected");
        require(!ForecastCachePolicy.isValidPoint("0", "-Infinity"), "infinite longitude must be rejected");
        require(!ForecastCachePolicy.isValidPoint("90.0001", "0"), "latitude above its upper bound must be rejected");
        require(!ForecastCachePolicy.isValidPoint("-90.0001", "0"), "latitude below its lower bound must be rejected");
        require(!ForecastCachePolicy.isValidPoint("0", "180.0001"), "longitude above its upper bound must be rejected");
        require(!ForecastCachePolicy.isValidPoint("0", "-180.0001"), "longitude below its lower bound must be rejected");
        require(!ForecastCachePolicy.isValidPoint(null, "0"), "missing coordinate must be rejected");
        require(!ForecastCachePolicy.isValidPoint("", "0"), "empty coordinate must be rejected");
        require(!ForecastCachePolicy.isValidPoint("north", "west"), "nonnumeric coordinates must be rejected");
        require(ForecastCachePolicy.samePoint("37.77490", "-122.41940", "37.7749", "-122.4194"), "equivalent numeric formatting must reuse the same point");
        require(ForecastCachePolicy.samePoint("+0.0", "-0.0", "0", "0"), "signed zero denotes the same point");
        require(!ForecastCachePolicy.samePoint("37.7749", "-122.4194", "37.7750", "-122.4194"), "a different latitude must not reuse the old point");
        require(!ForecastCachePolicy.samePoint("37.7749", "-122.4194", "37.7749", "-122.4193"), "a different longitude must not reuse the old point");
        require(!ForecastCachePolicy.samePoint("91", "0", "91", "0"), "identical invalid coordinates are not a reusable point");
        require(!ForecastCachePolicy.samePoint("NaN", "0", "NaN", "0"), "identical NaN text is not a reusable point");
    }

    private static boolean usable(String time, Object temperature, String unit, String shortText, String detailedText) {
        return ForecastCachePolicy.isUsableForecastPeriod(time, temperature, unit, shortText, detailedText);
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
