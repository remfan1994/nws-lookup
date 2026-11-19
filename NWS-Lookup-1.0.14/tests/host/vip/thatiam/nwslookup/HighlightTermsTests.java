package vip.thatiam.nwslookup;

import java.util.Arrays;
import java.util.List;

/** Dependency-free checks for optional literal highlighting and bounded report work. */
public final class HighlightTermsTests {
    private static int assertions;

    private HighlightTermsTests() {
    }

    public static void main(String[] args) {
        emptyMeansNoHighlighting();
        normalizeEditableTerms();
        useLiteralWholeTerms();
        matchUnicodeCaseAndBoundaries();
        preferLongerPhrases();
        appendAndClearPresets();
        rejectOversizedListsWithoutTruncating();
        boundLargeReportMatches();
        System.out.println("NWS Lookup highlight terms: PASS (" + assertions + " assertions)");
    }

    private static void emptyMeansNoHighlighting() {
        require(HighlightTerms.empty().isEmpty(), "default policy must be empty");
        require(HighlightTerms.empty().normalizedText().isEmpty(), "default policy must contain no implicit preset");
        require(HighlightTerms.parse(null).isEmpty(), "missing saved terms must remain empty");
        require(HighlightTerms.parse(" , \r\n , \t\u00A0 ").isEmpty(), "empty entries must remain empty");
        require(HighlightTerms.empty().find("Rain, PWAT, wind, snow and heat.").isEmpty(), "empty defaults must highlight nothing");
        require(HighlightTerms.parse("rain").find(null).isEmpty(), "absent report must be harmless");
        require(HighlightTerms.parse("rain").find("").isEmpty(), "empty report must be harmless");
    }

    private static void normalizeEditableTerms() {
        String input = "  rain, wind\r\nPWAT, precipitable   water, RAIN, \n"
                + " rain,\tWind, PWATS\u2028snow\u2029heat\u0085hail ";
        HighlightTerms terms = HighlightTerms.parse(input);
        require("rain\nwind\nPWAT\nprecipitable water\nPWATS\nsnow\nheat\nhail".equals(terms.normalizedText()),
                "normalization must preserve first spelling and entry order while dropping duplicates");
        require("precipitable water".equals(HighlightTerms.parse("\u00A0precipitable\t\u2003water\u00A0").normalizedText()),
                "Unicode whitespace must trim and collapse within phrases");
        require(terms.normalizedText().equals(HighlightTerms.parse(terms.normalizedText()).normalizedText()),
                "saved normalized text must round-trip unchanged");
        require("ÉCLAIR\nΣ\nI".equals(HighlightTerms.parse("ÉCLAIR,éclair,Σ,σ,ς,I,i,İ,ı").normalizedText()),
                "Unicode case variants must deduplicate independently of locale");
        require("rain\nrains".equals(HighlightTerms.parse("rain,rains").normalizedText()),
                "deduplication must not stem distinct literal words");
    }

    private static void useLiteralWholeTerms() {
        assertMatches("rain", "rain RAIN terrain train rainfall raining rain2 2rain _rain rain_ (rain), rain-rain.",
                "rain", "RAIN", "rain", "rain", "rain");
        assertMatches("PWAT, PWATS", "PWAT PWATS pwat pwats PWATsX XPWAT PWATS2", "PWAT", "PWATS", "pwat", "pwats");
        assertMatches(".*", ".* abc rain .*", ".*", ".*");
        assertMatches("C++, [rain], a+b, (rain), 50%, $5, \\b", "C++ [rain] a+b (rain) 50% $5 \\b C++x xa+b", 
                "C++", "[rain]", "a+b", "(rain)", "50%", "$5", "\\b");
        assertMatches("rain?", "rain rain? rainy rain?", "rain?", "rain?");
        assertMatches("rain|snow", "rain snow rain|snow", "rain|snow");
        List<HighlightTerms.Range> immutable = HighlightTerms.parse("rain").find("rain");
        boolean protectedList = false;
        try {
            immutable.clear();
        } catch (UnsupportedOperationException expected) {
            protectedList = true;
        }
        require(protectedList, "callers must not mutate returned ranges");
    }

    private static void matchUnicodeCaseAndBoundaries() {
        assertMatches("éclair, Σ", "ÉCLAIR éclair Σ σ ς xéclair éclairx", "ÉCLAIR", "éclair", "Σ", "σ", "ς");
        assertMatches("rain", "érain rainé rain\u0301 \u0301rain rain\u203F rain² rain\u200D rain", "rain");
        String upper = new String(Character.toChars(0x10400));
        String lower = new String(Character.toChars(0x10428));
        String text = "🌧 " + upper + " " + lower + " 🌧";
        List<HighlightTerms.Range> ranges = HighlightTerms.parse(upper + "," + lower).find(text);
        require(ranges.size() == 2, "supplementary Unicode case variants must match");
        require(ranges.get(0).start == 3 && ranges.get(0).end == 5, "offsets must remain original UTF-16 indices after emoji");
        require(ranges.get(1).start == 6 && ranges.get(1).end == 8, "supplementary letters must not split surrogate pairs");
        require(upper.equals(HighlightTerms.parse(upper + "," + lower).normalizedText()), "supplementary case variants must deduplicate");
    }

    private static void preferLongerPhrases() {
        String report = "Rain showers; rain; precipitable\t\nwater; FREEZING\u00A0RAIN. "
                + "rain\t\tshowers expected; rain showering.";
        String[] expected = {"Rain showers", "rain", "precipitable\t\nwater", "FREEZING\u00A0RAIN",
                "rain\t\tshowers expected", "rain"};
        assertMatches("rain, rain showers, rain showers expected, precipitable water, freezing rain", report, expected);
        assertMatches("freezing rain, precipitable water, rain showers expected, rain showers, rain", report, expected);
        assertMatches("water, precipitable water", "precipitable water; water", "precipitable water", "water");
        assertMatches("rain showers", "rain\r\n\t\u2003showers rain\n\nshowers", "rain\r\n\t\u2003showers", "rain\n\nshowers");
        assertMatches("rain showers", "rain\t\t rainwater rain");
    }

    private static void appendAndClearPresets() {
        String appended = HighlightTerms.addPreset("custom phrase, RAIN", HighlightTerms.Preset.RAIN);
        require(appended.startsWith("custom phrase\nRAIN\nrainfall\n"), "preset append must keep custom terms and first spelling");
        require(appended.equals(HighlightTerms.addPreset(appended, HighlightTerms.Preset.RAIN)), "appending the same preset must be idempotent");
        assertMatches(appended, "custom phrase, rain, PWAT, PWATS, precipitable water, QPF, PoP, PoPs",
                "custom phrase", "rain", "PWAT", "PWATS", "precipitable water", "QPF", "PoP", "PoPs");
        String rain = HighlightTerms.presetText(HighlightTerms.Preset.RAIN);
        for (String required : new String[] {"rain", "rainfall", "precipitation", "precip", "shower", "showers",
                "thunderstorm", "thunderstorms", "drizzle", "PWAT", "PWATS", "precipitable water", "QPF", "PoP", "PoPs"}) {
            require(HighlightTerms.parse(rain).find(required).size() == 1, "rain preset lacks " + required);
        }
        String combined = "custom phrase";
        for (HighlightTerms.Preset preset : HighlightTerms.Preset.values()) {
            require(!HighlightTerms.parse(HighlightTerms.presetText(preset)).isEmpty(), "preset must contain editable terms: " + preset);
            combined = HighlightTerms.addPreset(combined, preset);
        }
        require(combined.split("\n").length == 42, "four compact presets must combine within term limit with custom content");
        require(combined.length() < HighlightTerms.MAX_INPUT_CHARS, "combined presets must fit the input limit");
        require(HighlightTerms.parse("").isEmpty(), "clearing a populated editor must restore an empty policy");
        require(HighlightTerms.parse("").find("rain snow wind heat").isEmpty(), "clear must remove all matches");
        rejects(() -> HighlightTerms.addPreset("custom", null), "missing preset must be rejected");
    }

    private static void rejectOversizedListsWithoutTruncating() {
        String exactTerm = repeat('x', HighlightTerms.MAX_TERM_CHARS);
        require(exactTerm.equals(HighlightTerms.parse(exactTerm).normalizedText()), "exact term limit must be accepted intact");
        rejects(() -> HighlightTerms.parse(exactTerm + "x"), "term limit + 1 must be rejected, not truncated");
        String exactInput = repeat(' ', HighlightTerms.MAX_INPUT_CHARS - 4) + "rain";
        require("rain".equals(HighlightTerms.parse(exactInput).normalizedText()), "exact raw input limit must be accepted");
        rejects(() -> HighlightTerms.parse(exactInput + " "), "raw input limit + 1 must be rejected");
        StringBuilder many = new StringBuilder();
        for (int index = 0; index < HighlightTerms.MAX_TERMS; index++) {
            if (index > 0) many.append(',');
            many.append("term").append(index);
        }
        String hundred = many.toString();
        require(HighlightTerms.parse(hundred).normalizedText().split("\n").length == HighlightTerms.MAX_TERMS,
                "exact distinct-term limit must retain every term");
        require(HighlightTerms.parse(hundred + ",TERM0,term0").normalizedText().split("\n").length == HighlightTerms.MAX_TERMS,
                "duplicates must not consume the distinct-term allowance");
        rejects(() -> HighlightTerms.parse(hundred + ",extra"), "term count + 1 must be rejected, not truncated");
        rejects(() -> HighlightTerms.addPreset(hundred, HighlightTerms.Preset.RAIN), "preset append must respect limits without dropping custom terms");
        rejects(() -> HighlightTerms.parse("rain\u0000wind"), "embedded control characters must be rejected");
        rejects(() -> HighlightTerms.parse("rain\uD800"), "incomplete Unicode input must be rejected");
        require(HighlightTerms.parse(hundred).normalizedText().contains("term99"), "a failed append must not mutate a previously accepted list");
    }

    private static void boundLargeReportMatches() {
        final int length = 2 * 1024 * 1024 - 1;
        String repeated = "rain PWATS precipitable\twater rain showers. ";
        StringBuilder builder = new StringBuilder(length);
        while (builder.length() + repeated.length() <= length) builder.append(repeated);
        while (builder.length() < length) builder.append(' ');
        String report = builder.toString();
        builder = null;
        HighlightTerms terms = HighlightTerms.parse("rain,PWATS,precipitable water,rain showers");
        long started = System.nanoTime();
        for (int pass = 0; pass < 24; pass++) {
            List<HighlightTerms.Range> ranges = terms.find(report);
            require(ranges.size() == HighlightTerms.MAX_SPANS, "large repeated report must stop at the span limit");
            require(ranges.get(0).start == 0 && ranges.get(0).end == 4, "bounded matching must retain the first report match");
            int previousEnd = -1;
            for (HighlightTerms.Range range : ranges) {
                require(range.start >= previousEnd && range.start < range.end && range.end <= report.length(),
                        "ranges must remain ordered, nonoverlapping, and inside the original report");
                previousEnd = range.end;
            }
            require("rain showers".equals(report.substring(ranges.get(3).start, ranges.get(3).end)),
                    "a phrase must stay intact during capped matching");
        }
        require(report.length() == length && report.startsWith(repeated), "highlight matching must leave the original report unchanged");
        char[] lateMatch = new char[length];
        Arrays.fill(lateMatch, 'x');
        lateMatch[length - 5] = ' ';
        lateMatch[length - 4] = 'r';
        lateMatch[length - 3] = 'a';
        lateMatch[length - 2] = 'i';
        lateMatch[length - 1] = 'n';
        String lateReport = new String(lateMatch);
        lateMatch = null;
        List<HighlightTerms.Range> lateRanges = terms.find(lateReport);
        require(lateRanges.size() == 1 && lateRanges.get(0).start == length - 4 && lateRanges.get(0).end == length,
                "the span cap must not impose an early cutoff on a long report with a late match");
        require(HighlightTerms.parse("snow").find(report).isEmpty(), "a maximum-size report with no matching term must scan safely");
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        System.out.println("HIGHLIGHT_BOUND: 24 repeated " + length + "-character reports; at most "
                + HighlightTerms.MAX_SPANS + " spans; late/no-match scans; " + elapsedMs + " ms; max heap "
                + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MiB");
    }

    private static void assertMatches(String terms, String report, String... expected) {
        List<HighlightTerms.Range> ranges = HighlightTerms.parse(terms).find(report);
        require(ranges.size() == expected.length, "expected " + expected.length + " literal matches but found "
                + ranges.size() + " in: " + report);
        for (int index = 0; index < expected.length; index++) {
            HighlightTerms.Range range = ranges.get(index);
            require(expected[index].equals(report.substring(range.start, range.end)), "unexpected match at " + index + " in: " + report);
        }
    }

    private static String repeat(char value, int count) {
        char[] text = new char[count];
        Arrays.fill(text, value);
        return new String(text);
    }

    private static void rejects(Runnable action, String message) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage() != null && !expected.getMessage().isEmpty();
        }
        require(rejected, message);
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
