package vip.thatiam.nwslookup;

import java.io.IOException;

public final class JsonDepthGuardTests {
    public static void main(String[] args) throws Exception {
        JsonDepthGuard.check("{\"properties\":{\"periods\":[{\"text\":\"Rain / snow #1; it's cold.\"}]}}", 64);
        JsonDepthGuard.check("{\"text\":\"" + "[{]}".repeat(12_000) + "\"}", 64);
        JsonDepthGuard.check("{\"text\":\"escaped \\\" quote and \\\\ slash\"}", 64);
        JsonDepthGuard.check(object(64), 64);
        JsonDepthGuard.check("[".repeat(64) + "0" + "]".repeat(64), 64);
        reject(object(65));
        reject(object(12_000));
        reject("[".repeat(65) + "0" + "]".repeat(65));
        reject("{\"unfinished\":");
        reject("{\"text\":\"unfinished}");
        reject("}");
        reject("{'singleQuoted':'extra syntax'}");
        reject("{/* comment */\"value\":0}");
        reject("{# comment\n\"value\":0}");
        String nearLimit = "{\"text\":\"" + "x".repeat(2 * 1024 * 1024 - 11) + "\"}";
        for (int i = 0; i < 200; i++) JsonDepthGuard.check(nearLimit, 64);
        System.out.println("JSON depth guard: PASS (200 near-2-MiB scans; depth, quote and malformed-input boundaries)");
    }

    private static String object(int levels) {
        return "{\"child\":".repeat(levels) + "0" + "}".repeat(levels);
    }

    private static void reject(String text) throws Exception {
        try {
            JsonDepthGuard.check(text, 64);
            throw new AssertionError("Expected malformed or overly nested JSON to be rejected");
        } catch (IOException expected) {
            if (expected.getMessage() == null) throw new AssertionError("Rejection needs an error message");
        }
    }
}
