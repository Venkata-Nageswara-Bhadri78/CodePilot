package com.developer.copilot.jobextraction.automatedjobextraction.fetch;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.developer.copilot.jobextraction.automatedjobextraction.config.AutomatedJobExtractionHttpProperties;
import com.developer.copilot.jobextraction.automatedjobextraction.exception.AutomatedJobPageFetchException;
import com.developer.copilot.jobextraction.automatedjobextraction.exception.InvalidAutomatedJobUrlException;
import com.developer.copilot.jobextraction.automatedjobextraction.security.SsrfProtectionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fetches a job page over http(s) with SSRF checks on the original URL and every redirect.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobPageFetcher {

    private final JobPageHttpClient httpClient;
    private final SsrfProtectionService ssrfProtectionService;
    private final AutomatedJobExtractionHttpProperties httpProperties;
    private final JobPageFetchGuard fetchGuard;

    public FetchedJobPage fetch(String canonicalUrl) {
        URI uri = parseUri(canonicalUrl);
        ssrfProtectionService.validate(uri);
        return fetchGuard.call(() -> follow(uri, 0));
    }

    private FetchedJobPage follow(URI uri, int depth) {
        ssrfProtectionService.validate(uri);
        Duration timeout = Duration.ofMillis(Math.max(1, httpProperties.getRequestTimeoutMs()));
        JobPageHttpResponse response = httpClient.get(
                uri,
                timeout,
                Math.max(1, httpProperties.getMaxResponseBytes()),
                httpProperties.getUserAgent());

        int status = response.statusCode();
        if (isRedirect(status)) {
            int maxRedirects = Math.max(0, httpProperties.getMaxRedirects());
            if (depth >= maxRedirects) {
                throw new InvalidAutomatedJobUrlException();
            }
            URI next = resolveRedirect(uri, response.location());
            return follow(next, depth + 1);
        }
        if (status == 401 || status == 403 || status == 404 || status == 407 || status == 410
                || status == 451) {
            throw new InvalidAutomatedJobUrlException();
        }
        if (status < 200 || status >= 300) {
            if (status >= 400 && status < 500) {
                throw new InvalidAutomatedJobUrlException();
            }
            throw new AutomatedJobPageFetchException();
        }
        String contentType = response.contentType() == null ? "" : response.contentType();
        String body = decodeBody(response.body(), contentType);
        FetchedJobPage workdayJson = maybeFetchWorkdayJobJson(uri, timeout, body);
        if (workdayJson != null) {
            body = workdayJson.body();
            contentType = workdayJson.contentType();
        }
        if (looksLikeAccessChallenge(body) && !looksLikeJob(body)) {
            throw new InvalidAutomatedJobUrlException();
        }
        return new FetchedJobPage(uri.toString(), contentType, body);
    }

    private FetchedJobPage maybeFetchWorkdayJobJson(URI pageUri, Duration timeout, String pageBody) {
        if (!WorkdayCxsUrls.isWorkdayHost(pageUri.getHost())) {
            return null;
        }
        boolean widget = WorkdayCxsUrls.looksLikeSpaRedirect(pageBody);
        boolean hasJobPosting = WorkdayCxsUrls.containsJobPostingSignal(pageBody);
        boolean alreadyJobJson = WorkdayCxsUrls.looksLikeJobJson(pageBody);
        if (alreadyJobJson || (!widget && hasJobPosting)) {
            return null;
        }
        URI cxs = WorkdayCxsUrls.toCxsJobUri(pageUri);
        if (cxs == null) {
            return null;
        }
        try {
            ssrfProtectionService.validate(cxs);
            JobPageHttpResponse cxsResponse = httpClient.get(
                    cxs,
                    timeout,
                    Math.max(1, httpProperties.getMaxResponseBytes()),
                    httpProperties.getUserAgent(),
                    JdkJobPageHttpClient.JSON_ACCEPT);
            String cxsBody = decodeBody(cxsResponse.body(), cxsResponse.contentType());
            boolean ok = cxsResponse.statusCode() == 200 && WorkdayCxsUrls.looksLikeJobJson(cxsBody);
            if (!ok) {
                return null;
            }
            String cxsType = cxsResponse.contentType() == null || cxsResponse.contentType().isBlank()
                    ? "application/json"
                    : cxsResponse.contentType();
            return new FetchedJobPage(pageUri.toString(), cxsType, cxsBody);
        } catch (InvalidAutomatedJobUrlException | AutomatedJobPageFetchException ex) {
            log.debug("Workday CXS enrichment skipped");
            return null;
        }
    }

    private URI resolveRedirect(URI current, String location) {
        if (location == null || location.isBlank()) {
            throw new InvalidAutomatedJobUrlException();
        }
        URI next;
        try {
            next = current.resolve(location.trim());
        } catch (IllegalArgumentException ex) {
            throw new InvalidAutomatedJobUrlException();
        }
        String scheme = next.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidAutomatedJobUrlException();
        }
        return next;
    }

    private static URI parseUri(String canonicalUrl) {
        try {
            return URI.create(canonicalUrl);
        } catch (IllegalArgumentException ex) {
            throw new InvalidAutomatedJobUrlException();
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    static String decodeBody(byte[] body, String contentType) {
        if (body == null || body.length == 0) {
            return "";
        }
        Charset charset = charsetFrom(contentType);
        return new String(body, charset);
    }

    private static Charset charsetFrom(String contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        int charsetAt = lower.indexOf("charset=");
        if (charsetAt < 0) {
            return StandardCharsets.UTF_8;
        }
        String name = lower.substring(charsetAt + 8).trim();
        int semi = name.indexOf(';');
        if (semi >= 0) {
            name = name.substring(0, semi).trim();
        }
        name = name.replace("\"", "");
        try {
            return Charset.forName(name);
        } catch (Exception ex) {
            return StandardCharsets.UTF_8;
        }
    }

    static boolean looksLikeAccessChallenge(String body) {
        if (body == null || body.isBlank()) {
            return true;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("captcha")
                || lower.contains("recaptcha")
                || lower.contains("hcaptcha")
                || lower.contains("cf-challenge")
                || lower.contains("verify you are human")
                || lower.contains("access denied")
                || lower.contains("please enable javascript")
                || lower.contains("just a moment");
    }

    static boolean looksLikeJob(String body) {
        if (body == null) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("jobposting")
                || lower.contains("job description")
                || lower.contains("responsibilities")
                || lower.contains("requirements")
                || lower.contains("qualifications");
    }
}
