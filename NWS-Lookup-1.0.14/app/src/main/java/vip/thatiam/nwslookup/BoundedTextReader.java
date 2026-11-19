package vip.thatiam.nwslookup;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

final class BoundedTextReader {
    private static final int BUFFER_CHARS = 8192;

    private BoundedTextReader() {
    }

    static String read(InputStream stream, int maxChars, String sourceLabel) throws IOException {
        if (stream == null) return "";
        if (maxChars < 0) throw new IllegalArgumentException("maxChars must not be negative");

        String source = sourceLabel == null || sourceLabel.trim().isEmpty()
                ? "text source"
                : sourceLabel.trim();
        StringBuilder out = new StringBuilder(Math.min(maxChars, BUFFER_CHARS));
        char[] buffer = new char[BUFFER_CHARS];

        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (count > maxChars - out.length()) {
                    throw new IOException(source + " exceeds the " + maxChars + " character limit.");
                }
                out.append(buffer, 0, count);
            }
        }
        return out.toString();
    }
}
