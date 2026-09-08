package com.developer.copilot.jobextraction.automatedjobextraction.fetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.developer.copilot.jobextraction.automatedjobextraction.config.AutomatedJobExtractionHttpProperties;
import com.developer.copilot.jobextraction.automatedjobextraction.exception.AutomatedJobPageFetchException;
import com.developer.copilot.jobextraction.automatedjobextraction.exception.InvalidAutomatedJobUrlException;
import com.developer.copilot.jobextraction.automatedjobextraction.security.SsrfProtectionService;

@ExtendWith(MockitoExtension.class)
class JobPageFetcherTest {

    @Mock
    private JobPageHttpClient httpClient;

    private JobPageFetcher fetcher;

    @BeforeEach
    void setUp() {
        AutomatedJobExtractionHttpProperties properties = new AutomatedJobExtractionHttpProperties();
        properties.setMaxRedirects(2);
        properties.setMaxResponseBytes(10_000);
        SsrfProtectionService ssrf = new SsrfProtectionService(
                host -> new InetAddress[] {InetAddress.getByName("8.8.8.8")});
        fetcher = new JobPageFetcher(httpClient, ssrf, properties, new JobPageFetchGuard());
    }

    @Test
    void fetch_200Html_returnsBody() {
        when(httpClient.get(any(), any(), anyInt(), anyString())).thenReturn(
                new JobPageHttpResponse(200, null, "text/html",
                        "<html><body>Job Description responsibilities requirements</body></html>"
                                .getBytes(StandardCharsets.UTF_8)));

        FetchedJobPage page = fetcher.fetch("https://example.com/jobs/1");
        assertEquals("https://example.com/jobs/1", page.finalUrl());
        org.junit.jupiter.api.Assertions.assertTrue(page.body().contains("responsibilities"));
    }

    @Test
    void fetch_workdayWidgetJson_usesCxsJobJson() {
        String widget = "{\"widget\":\"redirect\",\"url\":\"/en-US/Visa/job/IN/Engineer_R1\",\"externalSpa\":true}";
        String cxsJson = "{\"jobPostingInfo\":{\"title\":\"Engineer\","
                + "\"jobDescription\":\"<p>Build APIs. Responsibilities include services.</p>\"}}";
        when(httpClient.get(any(), any(), anyInt(), anyString())).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            if (uri.getPath() != null && uri.getPath().contains("/wday/cxs/")) {
                return new JobPageHttpResponse(200, null, "application/json",
                        cxsJson.getBytes(StandardCharsets.UTF_8));
            }
            return new JobPageHttpResponse(200, null, "application/json",
                    widget.getBytes(StandardCharsets.UTF_8));
        });
        when(httpClient.get(any(), any(), anyInt(), anyString(), anyString())).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            if (uri.getPath() != null && uri.getPath().contains("/wday/cxs/")) {
                return new JobPageHttpResponse(200, null, "application/json",
                        cxsJson.getBytes(StandardCharsets.UTF_8));
            }
            return new JobPageHttpResponse(200, null, "application/json",
                    widget.getBytes(StandardCharsets.UTF_8));
        });

        FetchedJobPage page = fetcher.fetch(
                "https://visa.wd5.myworkdayjobs.com/en-US/Visa/job/IN/Engineer_R1");
        org.junit.jupiter.api.Assertions.assertTrue(page.body().contains("jobPostingInfo"));
        org.junit.jupiter.api.Assertions.assertTrue(page.body().contains("Engineer"));
    }

    @Test
    void fetch_404_isInvalidJobUrl() {
        when(httpClient.get(any(), any(), anyInt(), anyString())).thenReturn(
                new JobPageHttpResponse(404, null, "text/html", "gone".getBytes(StandardCharsets.UTF_8)));

        assertThrows(InvalidAutomatedJobUrlException.class, () -> fetcher.fetch("https://example.com/jobs/missing"));
    }

    @Test
    void fetch_401_isInvalidJobUrl() {
        when(httpClient.get(any(), any(), anyInt(), anyString())).thenReturn(
                new JobPageHttpResponse(401, null, "text/html", "login".getBytes(StandardCharsets.UTF_8)));

        assertThrows(InvalidAutomatedJobUrlException.class, () -> fetcher.fetch("https://example.com/jobs/1"));
    }

    @Test
    void fetch_500_isFetchFailure() {
        when(httpClient.get(any(), any(), anyInt(), anyString())).thenReturn(
                new JobPageHttpResponse(502, null, "text/html", "bad gateway".getBytes(StandardCharsets.UTF_8)));

        assertThrows(AutomatedJobPageFetchException.class, () -> fetcher.fetch("https://example.com/jobs/1"));
    }

    @Test
    void fetch_redirectToPublicHost_followed() {
        when(httpClient.get(any(), any(), anyInt(), anyString())).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            if (uri.toString().endsWith("/old")) {
                return new JobPageHttpResponse(302, "https://example.com/jobs/new", "text/html", new byte[0]);
            }
            return new JobPageHttpResponse(200, null, "text/html",
                    "Job Description responsibilities requirements".getBytes(StandardCharsets.UTF_8));
        });

        FetchedJobPage page = fetcher.fetch("https://example.com/jobs/old");
        assertEquals("https://example.com/jobs/new", page.finalUrl());
    }

    @Test
    void fetch_redirectToLocalhost_rejected() {
        when(httpClient.get(any(), any(), anyInt(), anyString())).thenReturn(
                new JobPageHttpResponse(302, "http://127.0.0.1/secret", "text/html", new byte[0]));

        assertThrows(InvalidAutomatedJobUrlException.class, () -> fetcher.fetch("https://example.com/jobs/1"));
    }

    @Test
    void fetch_tooManyRedirects_rejected() {
        when(httpClient.get(any(), any(), anyInt(), anyString())).thenReturn(
                new JobPageHttpResponse(302, "https://example.com/jobs/next", "text/html", new byte[0]));

        assertThrows(InvalidAutomatedJobUrlException.class, () -> fetcher.fetch("https://example.com/jobs/1"));
    }

    @Test
    void fetch_captchaWithoutJobContent_rejected() {
        when(httpClient.get(any(), any(), anyInt(), anyString())).thenReturn(
                new JobPageHttpResponse(200, null, "text/html",
                        "<html>please enable javascript captcha</html>".getBytes(StandardCharsets.UTF_8)));

        assertThrows(InvalidAutomatedJobUrlException.class, () -> fetcher.fetch("https://example.com/jobs/1"));
    }

    @Test
    void fetch_httpClientThrows_isFetchFailure() {
        when(httpClient.get(any(), any(), anyInt(), anyString())).thenThrow(new AutomatedJobPageFetchException());

        assertThrows(AutomatedJobPageFetchException.class, () -> fetcher.fetch("https://example.com/jobs/1"));
    }

    @Test
    void decodeBody_usesUtf8ByDefault() {
        assertEquals("hi", JobPageFetcher.decodeBody("hi".getBytes(StandardCharsets.UTF_8), "text/html"));
    }
}
