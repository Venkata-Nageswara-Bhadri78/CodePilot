package com.developer.copilot.common.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import com.developer.copilot.common.exception.InvalidJobUrlException;

import lombok.extern.slf4j.Slf4j;

/**
 * Produces a single, stable canonical form for a job posting URL regardless of which
 * source (LinkedIn, Naukri, a company career site, a job board, etc.) it was copied from.
 * <p>
 * Two links pointing at the exact same job posting - but carrying different tracking
 * parameters, casing, "www." prefixes, or query parameter ordering - must normalize to
 * an identical string so that duplicate-detection and future "who else added this job"
 * style lookups can rely on simple equality/hash comparisons against {@code source_url}.
 */
@Slf4j
@Component
public class UrlNormalizationUtil {

    static final String INVALID_ABSOLUTE_HTTP_URL =
            "Job URL must be a valid absolute http or https link.";

    /**
     * Exact-match query parameter names (case-insensitive) that only carry tracking /
     * referral / marketing information and never identify the job itself.
     */
    private static final Set<String> TRACKING_PARAM_NAMES = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id", "utm_name",
            "fbclid", "gclid", "gclsrc", "dclid", "msclkid", "yclid", "twclid",
            "share_id", "shareid", "share", "shared_from",
            "ref", "ref_src", "ref_url", "referrer", "referral_code",
            "source", "src", "trk", "trkinfo", "trackingid", "tracking_id",
            "igshid", "mc_cid", "mc_eid", "spm", "si", "irclickid",
            "_hsenc", "_hsmi", "originalsubdomain", "position", "pagenumber", "prehotel"
    );

    /** Query parameter name prefixes that always indicate tracking metadata. */
    private static final String[] TRACKING_PARAM_PREFIXES = {"utm_"};

    /**
     * Strictly normalizes a job URL. Rejects anything that is not a valid absolute
     * http/https URL. Use this on the initial "extract" flow where a malformed URL
     * must be surfaced to the user immediately.
     *
     * @throws InvalidJobUrlException if the URL cannot be parsed into a valid absolute http/https URL
     */
    public String normalizeStrict(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidJobUrlException("Job URL must not be empty.");
        }
        return normalizeOrThrow(rawUrl.trim());
    }

    /**
     * Best-effort normalization that never throws. Falls back to the trimmed original
     * string if it cannot be parsed as a valid URL. Use this for the general job-creation
     * path so existing lenient behavior around {@code sourceUrl} is preserved while still
     * canonicalizing well-formed URLs for duplicate detection.
     */
    public String normalizeLenient(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        try {
            return normalizeOrThrow(trimmed);
        } catch (InvalidJobUrlException ex) {
            return trimmed;
        }
    }

    /**
     * Computes a stable SHA-256 hex digest of the given value, used as a fixed-length,
     * index-friendly uniqueness key for the (potentially very long) canonical URL.
     */
    public String sha256Hex(String value) {
        if (value == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available on this JVM.", ex);
        }
    }

    private String normalizeOrThrow(String trimmedUrl) {
        URI uri;
        try {
            uri = new URI(trimmedUrl);
        } catch (URISyntaxException ex) {
            throw invalidUrl(trimmedUrl);
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (scheme == null || host == null || host.isBlank()
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw invalidUrl(trimmedUrl);
        }

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.startsWith("www.") && normalizedHost.length() > 4) {
            normalizedHost = normalizedHost.substring(4);
        }

        int port = uri.getPort();
        boolean isDefaultPort = port == -1
                || (port == 80 && normalizedScheme.equals("http"))
                || (port == 443 && normalizedScheme.equals("https"));

        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        String canonicalQuery = buildCanonicalQuery(uri.getRawQuery());

        StringBuilder result = new StringBuilder();
        result.append(normalizedScheme).append("://").append(normalizedHost);
        if (!isDefaultPort) {
            result.append(':').append(port);
        }
        result.append(path);
        if (!canonicalQuery.isEmpty()) {
            result.append('?').append(canonicalQuery);
        }

        return result.toString();
    }

    private InvalidJobUrlException invalidUrl(String trimmedUrl) {
        if (log.isDebugEnabled()) {
            String preview = trimmedUrl.length() <= 80
                    ? trimmedUrl
                    : trimmedUrl.substring(0, 80) + "...";
            log.debug("Rejected job URL: {}", preview);
        }
        return new InvalidJobUrlException(INVALID_ABSOLUTE_HTTP_URL);
    }

    /**
     * Strips known tracking/marketing parameters and deterministically sorts whatever
     * remains, so query-parameter reordering alone never produces a different canonical
     * URL for the same job posting.
     */
    private String buildCanonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }

        Map<String, String> keptParams = new TreeMap<>();
        for (String pair : rawQuery.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int eqIndex = pair.indexOf('=');
            String key = eqIndex >= 0 ? pair.substring(0, eqIndex) : pair;
            String value = eqIndex >= 0 ? pair.substring(eqIndex + 1) : "";
            String lowerKey = key.toLowerCase(Locale.ROOT);

            if (TRACKING_PARAM_NAMES.contains(lowerKey) || hasTrackingPrefix(lowerKey)) {
                continue;
            }
            keptParams.put(key, value);
        }

        if (keptParams.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : keptParams.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(entry.getKey());
            if (!entry.getValue().isEmpty()) {
                sb.append('=').append(entry.getValue());
            }
        }
        return sb.toString();
    }

    private boolean hasTrackingPrefix(String lowerKey) {
        for (String prefix : TRACKING_PARAM_PREFIXES) {
            if (lowerKey.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
