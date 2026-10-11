package com.developer.copilot.jobs.service.impl;

import java.util.Locale;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.util.UrlNormalizationUtil;
import com.developer.copilot.jobs.dto.JobDraftResponse;
import com.developer.copilot.jobs.dto.JobDraftUpdateRequest;
import com.developer.copilot.jobs.dto.JobDraftWrite;
import com.developer.copilot.jobs.dto.JobRequest;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.entity.JobDraftEntity;
import com.developer.copilot.jobs.exception.DuplicateJobDraftException;
import com.developer.copilot.jobs.exception.JobDraftNotFoundException;
import com.developer.copilot.jobs.exception.JobDraftPersistenceException;
import com.developer.copilot.jobs.exception.JobValidationException;
import com.developer.copilot.jobs.mapper.JobDraftMapper;
import com.developer.copilot.jobs.repository.JobDraftRepository;
import com.developer.copilot.jobs.service.JobDraftService;
import com.developer.copilot.jobs.service.JobDraftStore;
import com.developer.copilot.jobs.service.JobService;
import com.developer.copilot.jobs.util.ResumeToJobScoreSupport;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobDraftServiceImpl implements JobDraftService {

    private final JobDraftRepository jobDraftRepository;
    private final JobDraftStore jobDraftStore;
    private final JobDraftMapper jobDraftMapper;
    private final JobService jobService;
    private final CurrentUserService currentUserService;
    private final UrlNormalizationUtil urlNormalizationUtil;
    private final Validator validator;

    @Override
    public JobDraftResponse saveFallback(JobDraftWrite write) {
        try {
            return jobDraftMapper.toResponse(jobDraftStore.upsert(write));
        } catch (DataIntegrityViolationException ex) {
            if (!isDuplicateDraftConstraint(ex)) {
                throw new JobDraftPersistenceException(ex);
            }
        }
        try {
            return jobDraftMapper.toResponse(jobDraftStore.upsert(write));
        } catch (RuntimeException ex) {
            throw new JobDraftPersistenceException(ex);
        }
    }

    @Override
    public void deleteMatchingQuietly(String canonicalSourceUrl) {
        if (canonicalSourceUrl == null || canonicalSourceUrl.isBlank()) {
            return;
        }
        try {
            jobDraftStore.deleteByCanonicalUrl(canonicalSourceUrl);
        } catch (RuntimeException ex) {
            log.warn("Job was saved but the matching draft could not be deleted: {}", ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JobDraftResponse> list(Pageable pageable) {
        User user = currentUserService.getCurrentUser();
        return jobDraftRepository.findAllByUserId(user.getId(), pageable).map(jobDraftMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public JobDraftResponse get(Long id) {
        return jobDraftMapper.toResponse(findOwned(id));
    }

    @Override
    @Transactional
    public JobDraftResponse update(Long id, JobDraftUpdateRequest request) {
        if (request.getResumeToJobScore() != null
                && !ResumeToJobScoreSupport.isValid(request.getResumeToJobScore())) {
            throw new JobValidationException(
                    "resumeToJobScore must be an integer between 0 and 100.");
        }
        User user = currentUserService.getCurrentUser();
        JobDraftEntity draft = findOwned(id);
        jobDraftMapper.applyUpdate(draft, request);
        applySourceUrl(draft, request.getSourceUrl(), user.getId());
        try {
            return jobDraftMapper.toResponse(jobDraftRepository.saveAndFlush(draft));
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateDraftConstraint(ex)) {
                throw new DuplicateJobDraftException();
            }
            throw ex;
        }
    }

    @Override
    @Transactional
    public JobResponse saveAsJob(Long id) {
        JobDraftEntity draft = findOwned(id);
        JobRequest jobRequest = jobDraftMapper.toJobRequest(draft);
        String violations = violationMessage(validator.validate(jobRequest));
        if (!violations.isBlank()) {
            throw new JobValidationException(violations);
        }
        JobResponse saved = jobService.createJob(jobRequest);
        jobDraftRepository.delete(draft);
        return saved;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        jobDraftRepository.delete(findOwned(id));
    }

    private JobDraftEntity findOwned(Long id) {
        User user = currentUserService.getCurrentUser();
        return jobDraftRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new JobDraftNotFoundException("Job draft not found with id: " + id));
    }

    private void applySourceUrl(JobDraftEntity draft, String rawSourceUrl, Long userId) {
        if (rawSourceUrl == null || rawSourceUrl.isBlank()) {
            draft.setSourceUrl(null);
            draft.setSourceUrlHash(null);
            return;
        }
        String normalized = urlNormalizationUtil.normalizeStrict(rawSourceUrl);
        String hash = urlNormalizationUtil.sha256Hex(normalized);
        if (jobDraftRepository.existsByUserIdAndSourceUrlHashAndIdNot(userId, hash, draft.getId())) {
            throw new DuplicateJobDraftException();
        }
        draft.setSourceUrl(normalized);
        draft.setSourceUrlHash(hash);
    }

    private static String violationMessage(Set<ConstraintViolation<JobRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    static boolean isDuplicateDraftConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String causeMessage = cause.getMessage() != null ? cause.getMessage() : "";
        String topMessage = ex.getMessage() != null ? ex.getMessage() : "";
        String combined = (causeMessage + " " + topMessage).toLowerCase(Locale.ROOT);
        return combined.contains(JobDraftEntity.UNIQUE_USER_URL_HASH);
    }
}
