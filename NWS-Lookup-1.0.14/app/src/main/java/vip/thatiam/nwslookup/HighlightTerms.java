package vip.thatiam.nwslookup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Immutable, bounded literal terms for optional report highlighting. */
final class HighlightTerms {
    static final int MAX_INPUT_CHARS = 8192;
    static final int MAX_TERMS = 100;
    static final int MAX_TERM_CHARS = 80;
    static final int MAX_SPANS = 2000;

    enum Preset { RAIN, WIND, WINTER, HEAT }

    private static final HighlightTerms EMPTY = new HighlightTerms(new ArrayList<>());

    private final String normalizedText;
    private final int[] initialCodePoints;
    private final Term[][] termGroups;

    private HighlightTerms(List<String> values) {
        StringBuilder normalized = new StringBuilder();
        TreeMap<Integer, List<Term>> groups = new TreeMap<>();
        for (String value : values) {
            if (normalized.length() > 0) normalized.append('\n');
            normalized.append(value);
            Term term = new Term(value);
            List<Term> group = groups.get(term.points[0]);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(term.points[0], group);
            }
            group.add(term);
        }
        normalizedText = normalized.toString();
        initialCodePoints = new int[groups.size()];
        termGroups = new Term[groups.size()][];
        int groupIndex = 0;
        for (Map.Entry<Integer, List<Term>> entry : groups.entrySet()) {
            initialCodePoints[groupIndex] = entry.getKey();
            termGroups[groupIndex] = entry.getValue().toArray(new Term[0]);
            groupIndex++;
        }
    }

    static HighlightTerms empty() {
        return EMPTY;
    }

    /** Commas and line breaks separate terms; blank entries are harmless. */
    static HighlightTerms parse(String input) {
        if (input == null || input.isEmpty()) return empty();
        if (input.length() > MAX_INPUT_CHARS) {
            throw new IllegalArgumentException("Use at most " + MAX_INPUT_CHARS + " characters in the term list.");
        }

        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        StringBuilder term = new StringBuilder();
        boolean pendingSpace = false;
        for (int index = 0; index < input.length();) {
            int point = input.codePointAt(index);
            index += Character.charCount(point);
            if (isSeparator(point)) {
                addTerm(unique, term.toString());
                term.setLength(0);
                pendingSpace = false;
            } else if (isWhitespace(point)) {
                pendingSpace = term.length() > 0;
            } else {
                if (Character.isISOControl(point)
                        || (point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE)) {
                    throw new IllegalArgumentException("Terms cannot contain control characters or incomplete Unicode characters.");
                }
                if (pendingSpace) term.append(' ');
                term.appendCodePoint(point);
                pendingSpace = false;
                if (term.length() > MAX_TERM_CHARS) {
                    throw new IllegalArgumentException("Each term can contain at most " + MAX_TERM_CHARS + " characters.");
                }
            }
        }
        addTerm(unique, term.toString());
        return unique.isEmpty() ? empty() : new HighlightTerms(new ArrayList<>(unique.values()));
    }

    String normalizedText() {
        return normalizedText;
    }

    boolean isEmpty() {
        return termGroups.length == 0;
    }

    static String presetText(Preset preset) {
        if (preset == null) throw new IllegalArgumentException("Choose a term preset.");
        switch (preset) {
            case RAIN:
                return "rain\nrainfall\nprecipitation\nprecip\nshower\nshowers\n"
                        + "thunderstorm\nthunderstorms\ndrizzle\nPWAT\nPWATS\nprecipitable water\nQPF\nPoP\nPoPs";
            case WIND:
                return "wind\nwinds\nwindy\ngust\ngusts\ngusty\nbreezy\nwind advisory\nhigh wind";
            case WINTER:
                return "snow\nsnowfall\nflurry\nflurries\nsleet\nfreezing rain\nice\nicy\nblizzard\nwind chill";
            case HEAT:
                return "heat\nhot\nheat index\nheat advisory\nexcessive heat\nhumidity\nhumid";
            default:
                throw new IllegalArgumentException("Choose a term preset.");
        }
    }

    /** Appends editable preset terms without replacing or silently dropping custom terms. */
    static String addPreset(String input, Preset preset) {
        HighlightTerms current = parse(input);
        String addition = presetText(preset);
        return parse(current.isEmpty() ? addition : current.normalizedText + '\n' + addition).normalizedText;
    }

    /**
     * Returns original UTF-16 offsets in reading order, with no overlapping spans.
     * At each start, the longest matching phrase wins regardless of term order.
     * Words use Unicode letters, marks, numbers, and connector characters; punctuation
     * remains literal. Matching uses Unicode simple case folding, not stemming or regex.
     * The first MAX_SPANS matches are returned; the supplied report is never changed.
     */
    List<Range> find(String text) {
        if (isEmpty() || text == null || text.isEmpty()) return Collections.emptyList();
        List<Range> ranges = new ArrayList<>();
        for (int index = 0; index < text.length() && ranges.size() < MAX_SPANS;) {
            int point = text.codePointAt(index);
            if (index == 0 || !isWord(text.codePointBefore(index))) {
                int groupIndex = Arrays.binarySearch(initialCodePoints, fold(point));
                if (groupIndex >= 0) {
                    int longestEnd = -1;
                    for (Term term : termGroups[groupIndex]) {
                        int end = matchEnd(term, text, index);
                        if (end > longestEnd) longestEnd = end;
                    }
                    if (longestEnd >= 0) {
                        ranges.add(new Range(index, longestEnd));
                        index = longestEnd;
                        continue;
                    }
                }
            }
            index += Character.charCount(point);
        }
        return ranges.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(ranges);
    }

    private static int matchEnd(Term term, String text, int index) {
        for (int expected : term.points) {
            if (index >= text.length()) return -1;
            int actual = text.codePointAt(index);
            if (expected == ' ') {
                if (!isWhitespace(actual)) return -1;
                do {
                    index += Character.charCount(actual);
                    if (index >= text.length()) return -1; // Normalized phrases never end in a space.
                    actual = text.codePointAt(index);
                } while (isWhitespace(actual));
            } else {
                if (fold(actual) != expected) return -1;
                index += Character.charCount(actual);
            }
        }
        return index == text.length() || !isWord(text.codePointAt(index)) ? index : -1;
    }

    private static void addTerm(LinkedHashMap<String, String> unique, String term) {
        if (term.isEmpty()) return;
        String key = folded(term);
        if (!unique.containsKey(key)) {
            if (unique.size() >= MAX_TERMS) {
                throw new IllegalArgumentException("Use at most " + MAX_TERMS + " different terms.");
            }
            unique.put(key, term);
        }
    }

    private static String folded(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int index = 0; index < text.length();) {
            int point = text.codePointAt(index);
            result.appendCodePoint(fold(point));
            index += Character.charCount(point);
        }
        return result.toString();
    }

    private static int fold(int point) {
        return Character.toLowerCase(Character.toUpperCase(point));
    }

    private static boolean isSeparator(int point) {
        return point == ',' || point == '\n' || point == '\r' || point == 0x85
                || point == 0x2028 || point == 0x2029;
    }

    private static boolean isWhitespace(int point) {
        return Character.isWhitespace(point) || Character.isSpaceChar(point) || point == 0x85;
    }

    private static boolean isWord(int point) {
        int type = Character.getType(point);
        return Character.isLetterOrDigit(point) || type == Character.LETTER_NUMBER
                || type == Character.OTHER_NUMBER || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK || type == Character.ENCLOSING_MARK
                || type == Character.CONNECTOR_PUNCTUATION || point == 0x200C || point == 0x200D;
    }

    static final class Range {
        final int start;
        final int end;

        private Range(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class Term {
        final int[] points;

        Term(String text) {
            points = new int[text.codePointCount(0, text.length())];
            int pointIndex = 0;
            for (int index = 0; index < text.length();) {
                int point = text.codePointAt(index);
                points[pointIndex++] = fold(point);
                index += Character.charCount(point);
            }
        }
    }
}
