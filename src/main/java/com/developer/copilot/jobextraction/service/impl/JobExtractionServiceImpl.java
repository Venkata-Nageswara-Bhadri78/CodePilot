package com.developer.copilot.jobextraction.service.impl;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.ai.dto.request.JobExtractionAiRequest;
import com.developer.copilot.ai.dto.response.JobExtractionAiResponse;
import com.developer.copilot.ai.service.AiService;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.common.util.UrlNormalizationUtil;
import com.developer.copilot.jobextraction.dto.request.JobExtractionRequest;
import com.developer.copilot.jobextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.mapper.JobExtractionMapper;
import com.developer.copilot.jobextraction.service.JobExtractionService;
import com.developer.copilot.jobs.exception.DuplicateJobException;
import com.developer.copilot.jobs.repository.JobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Production implementation of {@link JobExtractionService}. Depends directly on the
 * {@code jobs} module's repository (read-only duplicate pre-check) and the {@code ai}
 * module's service (structured extraction) - this module owns no persistent state of its own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobExtractionServiceImpl implements JobExtractionService {

    private final UrlNormalizationUtil urlNormalizationUtil;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final AiService aiService;
    private final JobExtractionMapper jobExtractionMapper;

    @Override
    @Transactional(readOnly = true)
    public JobExtractionResultResponse extractJobInfo(JobExtractionRequest request) {
        User currentUser = getCurrentUser();

        // Strict: an invalid URL must be rejected before we spend an AI call on it.
        String normalizedUrl = urlNormalizationUtil.normalizeStrict(request.getSourceUrl());

        String urlHash = urlNormalizationUtil.sha256Hex(normalizedUrl);
        if (jobRepository.existsByUserIdAndSourceUrlHash(currentUser.getId(), urlHash)) {
            log.info("Rejected duplicate job extraction for user {} and URL {}", currentUser.getId(), normalizedUrl);
            throw new DuplicateJobException("This post was already added to your records.");
        }

        JobExtractionAiRequest aiRequest = JobExtractionAiRequest.builder()
                .jobUrl(normalizedUrl)
                .rawJobText(request.getRawJobText())
                .build();

        JobExtractionAiResponse aiResponse = aiService.extractJobInfo(aiRequest);

        return jobExtractionMapper.toResultResponse(aiResponse, normalizedUrl, request.getRawJobText());
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InvalidCredentialsException("User is not authenticated.");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("User account not found."));
    }
}
