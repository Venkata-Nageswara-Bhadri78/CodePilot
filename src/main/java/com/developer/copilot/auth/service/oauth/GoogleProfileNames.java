package com.developer.copilot.auth.service.oauth;

public final class GoogleProfileNames {

    private GoogleProfileNames() {
    }

    /**
     * Display name for {@code users.full_name}. This is never used as a username.
     * A missing or short Google name falls back to the email local-part, padded with
     * {@code user} when that local-part is shorter than 3 characters.
     */
    public static String fullName(String googleName, String normalizedEmail) {
        String trimmed = googleName == null ? "" : googleName.trim();
        if (trimmed.length() >= 3) {
            return limit(trimmed);
        }
        String local = normalizedEmail == null ? "" : normalizedEmail;
        int at = local.indexOf('@');
        if (at >= 0) {
            local = local.substring(0, at);
        }
        local = local.trim();
        if (local.length() < 3) {
            local = local + "user";
        }
        if (local.length() < 3) {
            local = "user";
        }
        return limit(local);
    }

    private static String limit(String value) {
        return value.length() > 100 ? value.substring(0, 100) : value;
    }
}
