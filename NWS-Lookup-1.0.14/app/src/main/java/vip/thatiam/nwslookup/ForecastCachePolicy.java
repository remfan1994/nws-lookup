package vip.thatiam.nwslookup;

import java.time.Duration;
import java.time.OffsetDateTime;

/** Rules for admitting forecast data and reusing a point-to-grid mapping. */
final class ForecastCachePolicy {
    private static final Duration MAX_MAPPING_AGE = Duration.ofHours(24);

    private ForecastCachePolicy() {
    }

    static boolean isMappingFresh(String resolvedAt, String now) {
        try {
            Duration age = Duration.between(OffsetDateTime.parse(resolvedAt), OffsetDateTime.parse(now));
            return !age.isNegative() && age.compareTo(MAX_MAPPING_AGE) < 0;
        } catch (Exception ignored) {
            // Old caches and invalid or future timestamps need a new /points lookup.
            return false;
        }
    }

    static boolean isUsableForecastPeriod(String startTime, Object temperature,
            String temperatureUnit, String shortForecast, String detailedForecast) {
        try {
            OffsetDateTime.parse(startTime);
        } catch (Exception ignored) {
            return false;
        }
        return hasTemperature(temperature, temperatureUnit)
                || hasText(shortForecast) || hasText(detailedForecast);
    }

    static String temperatureText(Object temperature, String unit) {
        return hasTemperature(temperature, unit) ? temperature + " " + unit : "unknown";
    }

    static boolean areAdjacentPeriods(String firstEnd, String nextStart) {
        try {
            return OffsetDateTime.parse(firstEnd).toInstant()
                    .equals(OffsetDateTime.parse(nextStart).toInstant());
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isValidPoint(String lat, String lon) {
        try {
            double latitude = Double.parseDouble(lat);
            double longitude = Double.parseDouble(lon);
            return Double.isFinite(latitude) && Double.isFinite(longitude)
                    && latitude >= -90 && latitude <= 90
                    && longitude >= -180 && longitude <= 180;
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean samePoint(String firstLat, String firstLon, String secondLat, String secondLon) {
        return isValidPoint(firstLat, firstLon) && isValidPoint(secondLat, secondLon)
                && Double.parseDouble(firstLat) == Double.parseDouble(secondLat)
                && Double.parseDouble(firstLon) == Double.parseDouble(secondLon);
    }

    private static boolean hasTemperature(Object temperature, String unit) {
        return temperature instanceof Number && Double.isFinite(((Number) temperature).doubleValue())
                && ("F".equals(unit) || "C".equals(unit));
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim());
    }
}
