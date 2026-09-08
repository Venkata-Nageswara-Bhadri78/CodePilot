package com.developer.copilot.jobextraction.automatedjobextraction.fetch;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Maps a public Workday career URL to the same-host CXS job JSON path the career
 * site itself uses. Listing URLs without {@code /job/} or {@code /details/} return null.
 */
public final class WorkdayCxsUrls {

    private static final Pattern LOCALE = Pattern.compile("^[a-z]{2}(?:-[a-z]{2,8})?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WD_NODE = Pattern.compile("^wd\\d+$", Pattern.CASE_INSENSITIVE);

    private WorkdayCxsUrls() {
    }

    public static boolean isWorkdayHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.contains("myworkdayjobs.com")
                || lower.contains("workdayjobs.com")
                || lower.contains("myworkdaysite.com");
    }

    public static URI toCxsJobUri(URI page) {
        if (page == null || !isWorkdayHost(page.getHost())) {
            return null;
        }
        String tenant = tenantFromHost(page.getHost());
        if (tenant == null) {
            return null;
        }
        String path = page.getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        List<String> segments = pathSegments(path);
        if (segments.size() >= 4
                && "wday".equalsIgnoreCase(segments.get(0))
                && "cxs".equalsIgnoreCase(segments.get(1))) {
            return page;
        }
        int index = 0;
        if (segments.size() >= 4 && LOCALE.matcher(segments.get(0)).matches()) {
            index = 1;
        }
        if (index + 1 >= segments.size()) {
            return null;
        }
        String site = segments.get(index);
        index++;
        String kind = segments.get(index);
        String jobSuffix;
        if ("job".equalsIgnoreCase(kind)) {
            jobSuffix = joinFrom(segments, index);
        } else if ("details".equalsIgnoreCase(kind)) {
            if (index + 1 >= segments.size()) {
                return null;
            }
            jobSuffix = "job/" + joinFrom(segments, index + 1);
        } else {
            return null;
        }
        try {
            return new URI(
                    page.getScheme(),
                    page.getRawAuthority() != null ? page.getRawAuthority() : page.getHost(),
                    "/wday/cxs/" + tenant + "/" + site + "/" + jobSuffix,
                    null,
                    null);
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    static boolean looksLikeSpaRedirect(String body) {
        if (body == null) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("\"externalspa\"") || lower.contains("\"widget\":\"redirect\"");
    }

    static boolean looksLikeJobJson(String body) {
        return body != null && body.contains("jobPostingInfo");
    }

    static boolean containsJobPostingSignal(String body) {
        if (body == null) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("jobposting") || body.contains("jobPostingInfo");
    }

    static String tenantFromHost(String host) {
        if (host == null) {
            return null;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        int dot = lower.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        String tenant = lower.substring(0, dot);
        if ("www".equals(tenant) || WD_NODE.matcher(tenant).matches()) {
            return null;
        }
        return tenant;
    }

    private static List<String> pathSegments(String path) {
        List<String> segments = new ArrayList<>();
        for (String part : path.split("/")) {
            if (!part.isBlank()) {
                segments.add(part);
            }
        }
        return segments;
    }

    private static String joinFrom(List<String> segments, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < segments.size(); i++) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(segments.get(i));
        }
        return sb.toString();
    }
}
