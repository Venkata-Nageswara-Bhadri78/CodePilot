package com.developer.copilot.jobextraction.automatedjobextraction.service.impl;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import com.developer.copilot.auth.entity.User;
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
import com.developer.copilot.jobextraction.automatedjobextraction.security.SsrfProtectionService;
import com.developer.copilot.jobextraction.automatedjobextraction.service.AutomatedJobExtractionService;
import com.developer.copilot.jobextraction.manualextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.manualextraction.exception.EmailNotVerifiedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutomatedJobExtractionServiceImpl implements AutomatedJobExtractionService {

    private final CurrentUserService currentUserService;
    private final UrlNormalizationUtil urlNormalizationUtil;
    private final SsrfProtectionService ssrfProtectionService;
    private final JobPageFetcher jobPageFetcher;
    private final JobExtractionPipeline jobExtractionPipeline;
    private final ManualJobExtractionGateway manualJobExtractionGateway;
    private final ExtractedJobContentCache extractedJobContentCache;
    private final AutomatedJobExtractionMetrics metrics;

    @Override
    public JobExtractionResultResponse extractFromUrl(AutomatedJobExtractionRequest request) {
        Instant started = Instant.now();
        User currentUser = currentUserService.getCurrentUser();
        if (!Boolean.TRUE.equals(currentUser.getEmailVerified())) {
            throw new EmailNotVerifiedException();
        }

        String normalizedUrl;
        try {
            normalizedUrl = urlNormalizationUtil.normalizeStrict(request.getSourceUrl());
        } catch (InvalidJobUrlException ex) {
            metrics.recordInvalidUrl();
            throw ex;
        }

        try {
            ssrfProtectionService.validate(URI.create(normalizedUrl));
        } catch (IllegalArgumentException ex) {
            metrics.recordInvalidUrl();
            throw new InvalidAutomatedJobUrlException();
        } catch (InvalidAutomatedJobUrlException ex) {
            metrics.recordInvalidUrl();
            throw ex;
        }

        String urlHash = urlNormalizationUtil.sha256Hex(normalizedUrl);
        AtomicBoolean loaded = new AtomicBoolean(false);
        String extractedText;
        try {
            extractedText = extractedJobContentCache.computeIfAbsent(currentUser.getId(), urlHash, () -> {
                loaded.set(true);
                FetchedJobPage page = jobPageFetcher.fetch(normalizedUrl);
                return jobExtractionPipeline.extractJobText(normalizedUrl, page);
            });
        } catch (InvalidAutomatedJobUrlException ex) {
            metrics.recordInvalidUrl();
            throw ex;
        } catch (AutomatedJobPageFetchException ex) {
            metrics.recordFetchFailure();
            throw ex;
        }

        JobExtractionResultResponse result =
                manualJobExtractionGateway.parseExtractedContent(normalizedUrl, extractedText);

        if (!loaded.get()) {
            metrics.recordCacheHit();
            log.info("Automated job extraction cache hit for user {} and urlHash {}",
                    currentUser.getId(), urlHash);
        } else {
            metrics.recordSuccess(Duration.between(started, Instant.now()));
            log.info("Automated job extraction completed for user {} and urlHash {}",
                    currentUser.getId(), urlHash);
        }
        return result;
    }
}
