package com.developer.copilot.jobextraction.automatedjobextraction.service;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.enums.Role;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.common.exception.InvalidJobUrlException;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.util.UrlNormalizationUtil;
import com.developer.copilot.jobextraction.automatedjobextraction.cache.ExtractedJobContentCache;
import com.developer.copilot.jobextraction.automatedjobextraction.dto.request.AutomatedJobExtractionRequest;
import com.developer.copilot.jobextraction.automatedjobextraction.exception.AutomatedJobPageFetchException;
import com.developer.copilot.jobextraction.automatedjobextraction.exception.InvalidAutomatedJobUrlException;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.JobExtractionPipeline;
import com.developer.copilot.jobextraction.automatedjobextraction.fetch.FetchedJobPage;
import com.developer.copilot.jobextraction.automatedjobextraction.fetch.JobPageFetcher;
import com.developer.copilot.jobextraction.automatedjobextraction.integration.ManualJobExtractionGateway;
import com.developer.copilot.jobextraction.automatedjobextraction.metrics.AutomatedJobExtractionMetrics;
import com.developer.copilot.jobextraction.automatedjobextraction.security.HostnameResolver;
import com.developer.copilot.jobextraction.automatedjobextraction.security.SsrfProtectionService;
import com.developer.copilot.jobextraction.automatedjobextraction.service.impl.AutomatedJobExtractionServiceImpl;
import com.developer.copilot.jobextraction.manualextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.manualextraction.exception.EmailNotVerifiedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomatedJobExtractionServiceImplTest {

    @Spy
    private UrlNormalizationUtil urlNormalizationUtil = new UrlNormalizationUtil();

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private JobPageFetcher jobPageFetcher;

    @Mock
    private JobExtractionPipeline jobExtractionPipeline;

    @Mock
    private ManualJobExtractionGateway manualJobExtractionGateway;

    private AutomatedJobExtractionServiceImpl service;
    private User testUser;
    private SsrfProtectionService ssrfProtectionService;

    @BeforeEach
    void setUp() throws Exception {
        HostnameResolver resolver = host -> new InetAddress[] {InetAddress.getByName("8.8.8.8")};
        ssrfProtectionService = new SsrfProtectionService(resolver);
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setEmailVerified(true);
        testUser.setEnabled(true);
        testUser.setRole(Role.USER);
        service = new AutomatedJobExtractionServiceImpl(
                currentUserService,
                urlNormalizationUtil,
                ssrfProtectionService,
                jobPageFetcher,
                jobExtractionPipeline,
                manualJobExtractionGateway,
                new ExtractedJobContentCache(null),
                new AutomatedJobExtractionMetrics());
    }

    private void mockUser() {
        when(currentUserService.getCurrentUser()).thenReturn(testUser);
    }

    @Test
    void extractFromUrl_happyPath_callsManualGatewayWithExtractedText() {
        mockUser();
        FetchedJobPage page = new FetchedJobPage(
                "https://example.com/jobs/123", "text/html", "<html></html>");
        when(jobPageFetcher.fetch("https://example.com/jobs/123")).thenReturn(page);
        when(jobExtractionPipeline.extractJobText(eq("https://example.com/jobs/123"), eq(page)))
                .thenReturn("Job Title: Engineer\nJob Description:\nBuild things.");
        when(manualJobExtractionGateway.parseExtractedContent(eq("https://example.com/jobs/123"), any()))
                .thenReturn(JobExtractionResultResponse.builder()
                        .sourceUrl("https://example.com/jobs/123")
                        .title("Engineer")
                        .company("Acme")
                        .build());

        JobExtractionResultResponse result = service.extractFromUrl(AutomatedJobExtractionRequest.builder()
                .sourceUrl("https://www.example.com/jobs/123?utm_source=x")
                .build());

        assertEquals("Engineer", result.getTitle());
        verify(manualJobExtractionGateway).parseExtractedContent(
                "https://example.com/jobs/123",
                "Job Title: Engineer\nJob Description:\nBuild things.");
    }

    @Test
    void extractFromUrl_secondCall_usesExtractedTextCache() {
        mockUser();
        FetchedJobPage page = new FetchedJobPage(
                "https://example.com/jobs/1", "text/html", "<html></html>");
        when(jobPageFetcher.fetch("https://example.com/jobs/1")).thenReturn(page);
        when(jobExtractionPipeline.extractJobText(any(), any())).thenReturn("Job Title: T\n".repeat(20));
        when(manualJobExtractionGateway.parseExtractedContent(any(), any()))
                .thenReturn(JobExtractionResultResponse.builder().title("T").company("C").build());

        AutomatedJobExtractionRequest request = AutomatedJobExtractionRequest.builder()
                .sourceUrl("https://example.com/jobs/1")
                .build();
        service.extractFromUrl(request);
        service.extractFromUrl(request);

        verify(jobPageFetcher, org.mockito.Mockito.times(1)).fetch(any());
        verify(manualJobExtractionGateway, org.mockito.Mockito.times(2)).parseExtractedContent(any(), any());
    }

    @Test
    void extractFromUrl_unverifiedEmail_rejectedBeforeFetch() {
        testUser.setEmailVerified(false);
        mockUser();

        assertThrows(EmailNotVerifiedException.class, () -> service.extractFromUrl(
                AutomatedJobExtractionRequest.builder().sourceUrl("https://example.com/jobs/1").build()));
        verify(jobPageFetcher, never()).fetch(any());
    }

    @Test
    void extractFromUrl_unauthenticated_rejected() {
        when(currentUserService.getCurrentUser())
                .thenThrow(new InvalidCredentialsException("User is not authenticated."));

        assertThrows(InvalidCredentialsException.class, () -> service.extractFromUrl(
                AutomatedJobExtractionRequest.builder().sourceUrl("https://example.com/jobs/1").build()));
        verify(jobPageFetcher, never()).fetch(any());
    }

    @Test
    void extractFromUrl_javascriptUrl_rejectedAsInvalidFormat() {
        mockUser();
        assertThrows(InvalidJobUrlException.class, () -> service.extractFromUrl(
                AutomatedJobExtractionRequest.builder().sourceUrl("javascript:alert(1)").build()));
        verify(jobPageFetcher, never()).fetch(any());
    }

    @Test
    void extractFromUrl_localhost_rejectedAsInvalidJobUrl() {
        mockUser();
        assertThrows(InvalidAutomatedJobUrlException.class, () -> service.extractFromUrl(
                AutomatedJobExtractionRequest.builder().sourceUrl("http://localhost/jobs/1").build()));
        verify(jobPageFetcher, never()).fetch(any());
    }

    @Test
    void extractFromUrl_pipelineRejects_doesNotCallManual() {
        mockUser();
        when(jobPageFetcher.fetch(any())).thenReturn(new FetchedJobPage(
                "https://example.com/careers", "text/html", "<html>careers</html>"));
        when(jobExtractionPipeline.extractJobText(any(), any()))
                .thenThrow(new InvalidAutomatedJobUrlException());

        assertThrows(InvalidAutomatedJobUrlException.class, () -> service.extractFromUrl(
                AutomatedJobExtractionRequest.builder().sourceUrl("https://example.com/careers").build()));
        verify(manualJobExtractionGateway, never()).parseExtractedContent(any(), any());
    }

    @Test
    void extractFromUrl_fetchFailure_propagates() {
        mockUser();
        when(jobPageFetcher.fetch(any())).thenThrow(new AutomatedJobPageFetchException());

        assertThrows(AutomatedJobPageFetchException.class, () -> service.extractFromUrl(
                AutomatedJobExtractionRequest.builder().sourceUrl("https://example.com/jobs/1").build()));
        verify(manualJobExtractionGateway, never()).parseExtractedContent(any(), any());
    }

    @Test
    void extractFromUrl_dnsToPrivate_rejected() throws Exception {
        HostnameResolver privateResolver = host -> new InetAddress[] {InetAddress.getByName("127.0.0.1")};
        service = new AutomatedJobExtractionServiceImpl(
                currentUserService,
                urlNormalizationUtil,
                new SsrfProtectionService(privateResolver),
                jobPageFetcher,
                jobExtractionPipeline,
                manualJobExtractionGateway,
                new ExtractedJobContentCache(null),
                new AutomatedJobExtractionMetrics());
        mockUser();

        assertThrows(InvalidAutomatedJobUrlException.class, () -> service.extractFromUrl(
                AutomatedJobExtractionRequest.builder().sourceUrl("https://example.com/jobs/1").build()));
        verify(jobPageFetcher, never()).fetch(any());
    }

    @Test
    void extractFromUrl_unknownHost_rejected() {
        HostnameResolver failing = host -> {
            throw new UnknownHostException(host);
        };
        service = new AutomatedJobExtractionServiceImpl(
                currentUserService,
                urlNormalizationUtil,
                new SsrfProtectionService(failing),
                jobPageFetcher,
                jobExtractionPipeline,
                manualJobExtractionGateway,
                new ExtractedJobContentCache(null),
                new AutomatedJobExtractionMetrics());
        mockUser();

        assertThrows(InvalidAutomatedJobUrlException.class, () -> service.extractFromUrl(
                AutomatedJobExtractionRequest.builder().sourceUrl("https://no-such-host.example/jobs/1").build()));
    }
}
