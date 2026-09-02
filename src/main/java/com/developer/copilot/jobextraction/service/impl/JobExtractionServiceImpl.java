package com.developer.copilot.jobextraction.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.developer.copilot.ai.dto.request.JobExtractionAiRequest;
import com.developer.copilot.ai.dto.response.JobExtractionAiResponse;
import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.ai.service.AiService;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.exception.InvalidJobUrlException;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.util.UrlNormalizationUtil;
import com.developer.copilot.jobextraction.cache.JobExtractionPreviewCache;
import com.developer.copilot.jobextraction.dto.request.JobExtractionRequest;
import com.developer.copilot.jobextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.exception.EmailNotVerifiedException;
import com.developer.copilot.jobextraction.exception.JobExtractionAiUnavailableException;
import com.developer.copilot.jobextraction.mapper.JobExtractionMapper;
import com.developer.copilot.jobextraction.metrics.JobExtractionMetrics;
import com.developer.copilot.jobextraction.resilience.JobExtractionAiGuard;
import com.developer.copilot.jobextraction.service.JobExtractionService;
import com.developer.copilot.jobs.exception.DuplicateJobException;
import com.developer.copilot.jobs.repository.JobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Production implementation of {@link JobExtractionService}. Depends directly on the
 * {@code jobs} module's repository (read-only duplicate pre-check) and the {@code ai}
 * module's service (structured extraction) - this module owns no persistent state of its own.
 * <p>
 * The duplicate exists query is a single Spring Data call (short-lived transaction). The
 * AI invocation is intentionally <strong>not</strong> wrapped in {@code @Transactional}
 * so a JDBC connection is not held for the model timeout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobExtractionServiceImpl implements JobExtractionService {

    private final UrlNormalizationUtil urlNormalizationUtil;
    private final JobRepository jobRepository;
    private final AiService aiService;
    private final JobExtractionMapper jobExtractionMapper;
    private final CurrentUserService currentUserService;
    private final JobExtractionPreviewCache previewCache;
    private final JobExtractionAiGuard aiGuard;
    private final JobExtractionMetrics metrics;

    @Override
    public JobExtractionResultResponse extractJobInfo(JobExtractionRequest request) {
        Instant started = Instant.now();
        User currentUser = currentUserService.getCurrentUser();

        if (!Boolean.TRUE.equals(currentUser.getEmailVerified())) {
            throw new EmailNotVerifiedException();
        }

        String normalizedUrl;
        try {
            normalizedUrl = urlNormalizationUtil.normalizeStrict(request.getSourceUrl());
        } catch (InvalidJobUrlException ex) {
            metrics.recordBadUrl();
            throw ex;
        }

        String urlHash = urlNormalizationUtil.sha256Hex(normalizedUrl);
        if (jobRepository.existsByUserIdAndSourceUrlHash(currentUser.getId(), urlHash)) {
            log.info("Rejected duplicate job extraction for user {} and urlHash {}", currentUser.getId(), urlHash);
            metrics.recordDuplicate();
            throw new DuplicateJobException("This post was already added to your records.");
        }

        Optional<JobExtractionResultResponse> cached = previewCache.get(currentUser.getId(), urlHash);
        if (cached.isPresent()) {
            metrics.recordCacheHit();
            log.info("Job extraction cache hit for user {} and urlHash {}", currentUser.getId(), urlHash);
            return cached.get();
        }

        JobExtractionAiRequest aiRequest = JobExtractionAiRequest.builder()
                .jobUrl(normalizedUrl)
                .rawJobText(request.getRawJobText())
                .build();

        JobExtractionAiResponse aiResponse;
        try {
            aiResponse = aiGuard.call(() -> aiService.extractJobInfo(aiRequest));
        } catch (AiServiceException | JobExtractionAiUnavailableException ex) {
            metrics.recordAiFailure();
            throw ex;
        }

        JobExtractionResultResponse result =
                jobExtractionMapper.toResultResponse(aiResponse, normalizedUrl, request.getRawJobText());

        previewCache.put(currentUser.getId(), urlHash, result);
        metrics.recordSuccess(Duration.between(started, Instant.now()));

        log.info("Job extraction completed for user {} and urlHash {}, manualReviewRequired={}",
                currentUser.getId(), urlHash, result.isRequiresManualReview());

        return result;
    }
}
