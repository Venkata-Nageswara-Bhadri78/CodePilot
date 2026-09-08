package com.developer.copilot.jobextraction.automatedjobextraction.fetch;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.developer.copilot.jobextraction.automatedjobextraction.exception.AutomatedJobPageFetchException;

/**
 * JDK {@link HttpClient} adapter. Redirects are never followed here — {@link JobPageFetcher}
 * validates each hop against SSRF rules first.
 */
public class JdkJobPageHttpClient implements JobPageHttpClient {

    static final String HTML_ACCEPT =
            "text/html,application/xhtml+xml,application/ld+json;q=0.8,*/*;q=0.1";

    static final String JSON_ACCEPT = "application/json";

    private final HttpClient httpClient;

    public JdkJobPageHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public JobPageHttpResponse get(URI uri, Duration requestTimeout, int maxResponseBytes, String userAgent) {
        return get(uri, requestTimeout, maxResponseBytes, userAgent, HTML_ACCEPT);
    }

    @Override
    public JobPageHttpResponse get(
            URI uri, Duration requestTimeout, int maxResponseBytes, String userAgent, String accept) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("User-Agent", userAgent == null || userAgent.isBlank()
                        ? "CopilotJobExtraction/1.0"
                        : userAgent)
                .header("Accept", accept == null || accept.isBlank() ? HTML_ACCEPT : accept)
                .header("Accept-Language", "en-US,en;q=0.8")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body = readLimited(response.body(), maxResponseBytes);
            String location = response.headers().firstValue("Location").orElse(null);
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            return new JobPageHttpResponse(response.statusCode(), location, contentType, body);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AutomatedJobPageFetchException(ex);
        } catch (IOException | IllegalArgumentException | AutomatedJobPageFetchException ex) {
            if (ex instanceof AutomatedJobPageFetchException fetchEx) {
                throw fetchEx;
            }
            throw new AutomatedJobPageFetchException(ex);
        }
    }

    static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        if (input == null) {
            return new byte[0];
        }
        int limit = Math.max(1, maxBytes);
        byte[] buffer = new byte[Math.min(8192, limit)];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int read;
        int total = 0;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new AutomatedJobPageFetchException();
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
