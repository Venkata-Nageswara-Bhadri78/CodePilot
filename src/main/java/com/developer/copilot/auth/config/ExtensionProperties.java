package com.developer.copilot.auth.config;

import java.util.Locale;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the single Chrome browser-extension client. The extension ID is used only
 * for CORS ({@code chrome-extension://&lt;id&gt;}). Authorization is the signed JWT
 * {@code cid} claim, not this origin.
 */
@ConfigurationProperties(prefix = "app.extension")
@Getter
@Setter
public class ExtensionProperties {

    /** Kill switch: minting and accepting extension JWTs. */
    private boolean enabled = true;

    /**
     * Chrome extension ID (not a URL). When set, {@code chrome-extension://&lt;id&gt;} is added
     * to the CORS allow-list. Blank means no extension origin is advertised.
     */
    private String id = "";

    /** Extension access JWT lifetime. Default 15 minutes, same as web access tokens. */
    private long accessExpiryMs = 900_000L;

    /** Per-IP and per-user mint limit for {@code POST /api/v1/auth/extension-token}. {@code <= 0} disables. */
    private int tokenRateLimitPerMinute = 10;

    public Optional<String> resolvedOrigin() {
        String extensionId = normalizedId();
        if (extensionId.isEmpty()) {
            return Optional.empty();
        }
        if (extensionId.contains("://") || extensionId.contains("/") || extensionId.contains("*")
                || extensionId.contains("\\") || extensionId.contains(" ")) {
            throw new IllegalStateException(
                    "app.extension.id must be the Chrome extension ID, not a URL, path, or wildcard.");
        }
        return Optional.of("chrome-extension://" + extensionId);
    }

    private String normalizedId() {
        if (id == null) {
            return "";
        }
        return id.trim().toLowerCase(Locale.ROOT);
    }
}
