package vip.thatiam.nwslookup;

import java.io.IOException;

/** Bounds parser stack use before handing bounded text to the JSON parser. */
final class JsonDepthGuard {
    private JsonDepthGuard() {
    }

    static void check(String text, int maxDepth) throws IOException {
        if (maxDepth < 1) throw new IllegalArgumentException("maxDepth must be positive");
        if (text == null) throw new IOException("JSON text is missing.");
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                if (++depth > maxDepth) {
                    throw new IOException("JSON exceeds the " + maxDepth + " level nesting limit.");
                }
            } else if (c == '}' || c == ']') {
                if (--depth < 0) throw new IOException("JSON has unbalanced containers.");
            } else if (c == '\'' || c == '/' || c == '#') {
                // Require standard JSON quoting: extra quote/comment syntax must
                // not conceal containers from this scan before lenient parsing.
                throw new IOException("JSON contains unsupported quote or comment syntax.");
            }
        }
        if (inString || depth != 0) throw new IOException("JSON text is incomplete.");
    }
}
