package com.developer.copilot.jobextraction.persistence;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.common.exception.InvalidJobUrlException;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.util.UrlNormalizationUtil;
import com.developer.copilot.jobextraction.automatedjobextraction.dto.request.AutomatedJobExtractionRequest;
import com.developer.copilot.jobextraction.automatedjobextraction.exception.RecoverableAutomatedExtractionException;
import com.developer.copilot.jobextraction.automatedjobextraction.service.AutomatedJobExtractionService;
import com.developer.copilot.jobextraction.manualextraction.dto.request.JobExtractionRequest;
import com.developer.copilot.jobextraction.manualextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.manualextraction.exception.EmailNotVerifiedException;
import com.developer.copilot.jobextraction.manualextraction.exception.JobExtractionAiUnavailableException;
import com.developer.copilot.jobextraction.manualextraction.service.JobExtractionService;
import com.developer.copilot.jobs.dto.JobDraftResponse;
import com.developer.copilot.jobs.dto.JobDraftWrite;
import com.developer.copilot.jobs.dto.JobRequest;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.entity.JobDraftFailureCategory;
import com.developer.copilot.jobs.exception.DuplicateJobException;
import com.developer.copilot.jobs.exception.JobDraftPersistenceException;
import com.developer.copilot.jobs.exception.JobValidationException;
import com.developer.copilot.jobs.service.JobDraftService;
import com.developer.copilot.jobs.service.JobService;
import com.developer.copilot.jobs.util.JobDraftTexts;
import com.developer.copilot.jobs.util.JobLimits;
import com.developer.copilot.user.exception.ResumeNotFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns an extraction into a saved job, or a draft when that save cannot succeed.
 * This class is not transactional. Job creation and draft upsert use separate transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobExtractionAutoPersistenceService {

    static final String EXTERNAL_REASON =
            "The job could not be structured automatically. Review the saved text and complete the missing fields.";
    static final String PERSISTENCE_REASON =
            "The job could not be saved because it conflicts with stored data.";
    static final String UNEXPECTED_REASON = "The job could not be saved.";

    private final JobExtractionService jobExtractionService;
    private final AutomatedJobExtractionService automatedJobExtractionService;
    private final JobService jobService;
    private final JobDraftService jobDraftService;
    private final Validator validator;
    private final UrlNormalizationUtil urlNormalizationUtil;
    private final CurrentUserService currentUserService;

    public JobAutoPersistResponse persistManual(JobExtractionRequest request) {
        try {
            return persistResult(jobExtractionService.extractJobInfo(request));
        } catch (AiServiceException | JobExtractionAiUnavailableException ex) {
            return draftAfterAiFailure(request.getSourceUrl(), request.getRawJobText(), request.getResume(), ex);
        }
    }

    public JobAutoPersistResponse persistAutomated(AutomatedJobExtractionRequest request) {
        try {
            return persistResult(automatedJobExtractionService.extractForAutoSave(request));
        } catch (RecoverableAutomatedExtractionException ex) {
            return draftAfterAiFailure(
                    ex.getNormalizedUrl(), ex.getExtractedText(), ex.getResumeId(), ex.aiFailure());
        }
    }

    private JobAutoPersistResponse persistResult(JobExtractionResultResponse result) {
        JobRequest jobRequest = toJobRequest(result);
        String violations = violationMessage(validator.validate(jobRequest));
        if (!violations.isBlank()) {
            return draftStructured(result, JobDraftFailureCategory.VALIDATION, violations);
        }
        try {
            JobResponse saved = jobService.createJob(jobRequest);
            jobDraftService.deleteMatchingQuietly(result.getSourceUrl());
            log.info("Auto-saved job {} for user {}", saved.getId(), currentUserId());
            return JobAutoPersistResponse.saved(saved);
        } catch (DuplicateJobException | ResumeNotFoundException | InvalidJobUrlException
                | EmailNotVerifiedException | InvalidCredentialsException ex) {
            throw ex;
        } catch (JobValidationException ex) {
            return draftStructured(result, JobDraftFailureCategory.VALIDATION, ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            log.warn("Auto-save persistence conflict for user {}", currentUserId());
            return draftStructured(result, JobDraftFailureCategory.PERSISTENCE, PERSISTENCE_REASON);
        } catch (RuntimeException ex) {
            log.error("Auto-save failed for user {}", currentUserId(), ex);
            return draftStructured(result, JobDraftFailureCategory.UNEXPECTED, UNEXPECTED_REASON);
        }
    }

    private JobAutoPersistResponse draftAfterAiFailure(
            String sourceUrl,
            String rawText,
            Long resumeId,
            RuntimeException aiFailure) {
        log.warn("Automatic job structuring failed for user {}: {}", currentUserId(), aiFailure.toString());
        String canonical;
        try {
            canonical = urlNormalizationUtil.normalizeStrict(sourceUrl);
        } catch (RuntimeException urlEx) {
            throw aiFailure;
        }
        JobDraftWrite write = JobDraftWrite.builder()
                .sourceUrl(canonical)
                .originalDescription(rawText)
                .resume(resumeId)
                .failureCategory(JobDraftFailureCategory.EXTERNAL)
                .failureReason(EXTERNAL_REASON)
                .build();
        try {
            return JobAutoPersistResponse.drafted(jobDraftService.saveFallback(write));
        } catch (RuntimeException draftEx) {
            log.error("Draft was not saved after extraction failure for user {}", currentUserId(), draftEx);
            throw aiFailure;
        }
    }

    private JobAutoPersistResponse draftStructured(
            JobExtractionResultResponse result,
            JobDraftFailureCategory category,
            String reason) {
        JobDraftWrite write = JobDraftWrite.builder()
                .sourceUrl(result.getSourceUrl())
                .originalDescription(result.getOriginalDescription())
                .description(result.getDescription())
                .title(result.getTitle())
                .company(result.getCompany())
                .location(result.getLocation())
                .employmentType(result.getEmploymentType())
                .workMode(result.getWorkMode())
                .experience(result.getExperience())
                .salary(result.getSalary())
                .education(result.getEducation())
                .department(result.getDepartment())
                .industry(result.getIndustry())
                .sourcePlatform(result.getSourcePlatform())
                .skills(result.getSkills())
                .resume(result.getResume())
                .resumeToJobScore(JobDraftTexts.validScoreOrNull(result.getResumeToJobScore()))
                .failureCategory(category)
                .failureReason(reason)
                .build();
        try {
            JobDraftResponse draft = jobDraftService.saveFallback(write);
            log.info("Stored job draft {} for user {} category {}", draft.getId(), currentUserId(), category);
            return JobAutoPersistResponse.drafted(draft);
        } catch (JobDraftPersistenceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Draft was not saved after job save failure for user {}", currentUserId(), ex);
            throw new JobDraftPersistenceException(ex);
        }
    }

    private JobRequest toJobRequest(JobExtractionResultResponse result) {
        return JobRequest.builder()
                .sourceUrl(result.getSourceUrl())
                .originalDescription(JobDraftTexts.clip(result.getOriginalDescription(), JobLimits.MAX_DESCRIPTION_LENGTH))
                .description(JobDraftTexts.clip(result.getDescription(), JobLimits.MAX_DESCRIPTION_LENGTH))
                .title(JobDraftTexts.clip(result.getTitle(), 255))
                .company(JobDraftTexts.clip(result.getCompany(), 255))
                .location(JobDraftTexts.clip(result.getLocation(), 255))
                .employmentType(JobDraftTexts.clip(result.getEmploymentType(), 100))
                .workMode(JobDraftTexts.clip(result.getWorkMode(), 50))
                .experience(JobDraftTexts.clip(result.getExperience(), 100))
                .salary(JobDraftTexts.clip(result.getSalary(), 100))
                .education(JobDraftTexts.clip(result.getEducation(), 255))
                .department(JobDraftTexts.clip(result.getDepartment(), 100))
                .industry(JobDraftTexts.clip(result.getIndustry(), 100))
                .sourcePlatform(JobDraftTexts.clip(result.getSourcePlatform(), 50))
                .skills(JobDraftTexts.clip(result.getSkills(), JobLimits.MAX_SKILLS_LENGTH))
                .resume(result.getResume())
                .resumeToJobScore(JobDraftTexts.validScoreOrNull(result.getResumeToJobScore()))
                .build();
    }

    private static String violationMessage(Set<ConstraintViolation<JobRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private Long currentUserId() {
        try {
            return currentUserService.getCurrentUser().getId();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
