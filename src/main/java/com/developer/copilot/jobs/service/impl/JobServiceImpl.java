package com.developer.copilot.jobs.service.impl;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.util.UrlNormalizationUtil;
import com.developer.copilot.jobs.dto.JobPatchRequest;
import com.developer.copilot.jobs.dto.JobRequest;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.dto.JobSummaryResponse;
import com.developer.copilot.jobs.dto.request.*;
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.jobs.entity.JobStatus;
import com.developer.copilot.jobs.exception.DuplicateJobException;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.jobs.exception.JobValidationException;
import com.developer.copilot.jobs.mapper.JobMapper;
import com.developer.copilot.jobs.repository.JobRepository;
import com.developer.copilot.jobs.service.JobResumeBindingService;
import com.developer.copilot.jobs.service.JobService;
import com.developer.copilot.jobs.util.JobQuerySupport;
import com.developer.copilot.jobs.util.ResumeToJobScoreSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private static final String DUPLICATE_JOB_MESSAGE = "This post was already added to your records.";
    private static final String DUPLICATE_CONSTRAINT = "uk_job_user_source_url_hash";

    private final JobRepository jobRepository;
    private final CurrentUserService currentUserService;
    private final JobMapper jobMapper;
    private final UrlNormalizationUtil urlNormalizationUtil;
    private final JobResumeBindingService jobResumeBindingService;

    @Override
    @Transactional
    public JobResponse createJob(JobRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity jobEntity = jobMapper.toEntity(request, currentUser);
        applySourceUrl(jobEntity, request.getSourceUrl(), currentUser.getId(), null);
        bindResumeOnCreate(jobEntity, currentUser, request);
        JobEntity savedJob = saveJob(jobEntity);
        return jobMapper.toJobResponse(savedJob);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JobSummaryResponse> getAllJobs(String search, Pageable pageable) {
        User currentUser = currentUserService.getCurrentUser();
        String preparedSearch = JobQuerySupport.prepareSearch(search);
        Page<JobEntity> jobPage;
        if (preparedSearch != null) {
            jobPage = jobRepository.searchJobsByUserId(currentUser.getId(), preparedSearch, pageable);
        } else {
            jobPage = jobRepository.findAllByUserId(currentUser.getId(), pageable);
        }
        return jobPage.map(jobMapper::toJobSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public JobResponse getJobById(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        return jobMapper.toJobResponse(job);
    }

    @Override
    @Transactional
    public JobResponse updateJob(Long id, JobRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        jobMapper.updateEntityFromRequest(job, request);
        applySourceUrl(job, request.getSourceUrl(), currentUser.getId(), job.getId());
        bindResumeIfRequested(job, currentUser, request.getResume(), request.getResumeToJobScore());
        JobEntity updatedJob = saveJob(job);
        return jobMapper.toJobResponse(updatedJob);
    }

    @Override
    @Transactional
    public JobResponse patchJob(Long id, JobPatchRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);

        // Mandatory fields are optional-by-absence in a patch, but never optional-by-blank
        // once the caller explicitly includes them.
        rejectIfBlank("Title", request.getTitle());
        rejectIfBlank("Company", request.getCompany());
        rejectIfBlank("Original description", request.getOriginalDescription());

        jobMapper.updateEntityFromPatch(job, request);

        if (request.getSourceUrl() != null) {
            applySourceUrl(job, request.getSourceUrl(), currentUser.getId(), job.getId());
        }
        if (request.getJobStatus() != null) {
            applyJobStatus(job, request.getJobStatus(), request.getCustomStatus());
        }
        if (request.getResume() != null) {
            bindResumeAndRescore(job, currentUser, request.getResume());
        }

        JobEntity updatedJob = saveJob(job);
        return jobMapper.toJobResponse(updatedJob);
    }

    @Override
    @Transactional
    public void deleteJob(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        jobRepository.delete(job);
    }

    @Override
    @Transactional
    public JobResponse updateLocation(Long id, UpdateLocationRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setLocation(request.getLocation());
        return jobMapper.toJobResponse(saveJob(job));
    }

    @Override
    @Transactional
    public JobResponse updateTitle(Long id, UpdateTitleRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setTitle(request.getTitle());
        return jobMapper.toJobResponse(saveJob(job));
    }

    @Override
    @Transactional
    public JobResponse updateCompany(Long id, UpdateCompanyRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setCompany(request.getCompany());
        return jobMapper.toJobResponse(saveJob(job));
    }

    @Override
    @Transactional
    public JobResponse updateEmploymentType(Long id, UpdateEmploymentTypeRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setEmploymentType(request.getEmploymentType());
        return jobMapper.toJobResponse(saveJob(job));
    }

    @Override
    @Transactional
    public JobResponse updateWorkMode(Long id, UpdateWorkModeRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setWorkMode(request.getWorkMode());
        return jobMapper.toJobResponse(saveJob(job));
    }

    @Override
    @Transactional
    public JobResponse updateExperience(Long id, UpdateExperienceRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setExperience(request.getExperience());
        return jobMapper.toJobResponse(saveJob(job));
    }

    @Override
    @Transactional
    public JobResponse updateSalary(Long id, UpdateSalaryRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setSalary(request.getSalary());
        return jobMapper.toJobResponse(saveJob(job));
    }

    @Override
    @Transactional
    public JobResponse updateEducation(Long id, UpdateEducationRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setEducation(request.getEducation());
        return jobMapper.toJobResponse(saveJob(job));
    }

    @Override
    @Transactional
    public JobResponse updateDepartment(Long id, UpdateDepartmentRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setDepartment(request.getDepartment());
        return jobMapper.toJobResponse(saveJob(job));
    }

    @Override
    @Transactional
    public JobResponse updateIndustry(Long id, UpdateIndustryRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setIndustry(request.getIndustry());
        return jobMapper.toJobResponse(saveJob(job));
    }

    @Override
    @Transactional
    public JobResponse updateSourcePlatform(Long id, UpdateSourcePlatformRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setSourcePlatform(request.getSourcePlatform());
        return jobMapper.toJobResponse(saveJob(job));
    }

    @Override
    @Transactional
    public JobResponse updateSourceUrl(Long id, UpdateSourceUrlRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        applySourceUrl(job, request.getSourceUrl(), currentUser.getId(), job.getId());
        return jobMapper.toJobResponse(saveJob(job));
    }

    @Override
    @Transactional
    public JobResponse updateSkills(Long id, UpdateSkillsRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setSkills(request.getSkills() != null ? request.getSkills() : "");
        return jobMapper.toJobResponse(saveJob(job));
    }

    @Override
    @Transactional
    public JobResponse updateDescription(Long id, UpdateDescriptionRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setDescription(request.getDescription());
        return jobMapper.toJobResponse(saveJob(job));
    }

    @Override
    @Transactional
    public JobResponse updateOriginalDescription(Long id, UpdateOriginalDescriptionRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setOriginalDescription(request.getOriginalDescription());
        return jobMapper.toJobResponse(saveJob(job));
    }

    @Override
    @Transactional
    public JobResponse updateResume(Long id, UpdateResumeRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        bindResumeAndRescore(job, currentUser, request.getResume());
        return jobMapper.toJobResponse(saveJob(job));
    }

    @Override
    @Transactional
    public JobResponse updateNotes(Long id, UpdateNotesRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setNotes(request.getNotes() != null ? request.getNotes() : "");
        return jobMapper.toJobResponse(saveJob(job));
    }

    @Override
    @Transactional
    public JobResponse updateJobStatus(Long id, UpdateJobStatusRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        applyJobStatus(job, request.getJobStatus(), request.getCustomStatus());
        return jobMapper.toJobResponse(saveJob(job));
    }

    private JobEntity getJobEntityForCurrentUser(Long id, User currentUser) {
        return jobRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new JobNotFoundException("Job not found with id: " + id));
    }

    /**
     * Normalizes the given raw source URL, computes its dedupe hash, verifies no other job
     * of this user already carries the same canonical URL, and applies both the normalized
     * URL and its hash onto the entity. This is the single owner of sourceUrl mutation for
     * every create/update path (full create, full update, single-field update, and patch).
     *
     * @param excludeJobId when updating an existing job, its own id (so re-saving the same
     *                     URL on itself is never flagged as a duplicate); {@code null} on create
     */
    private void applySourceUrl(JobEntity entity, String rawSourceUrl, Long currentUserId, Long excludeJobId) {
        if (rawSourceUrl == null || rawSourceUrl.isBlank()) {
            throw new JobValidationException("Source URL cannot be blank.");
        }

        String normalizedUrl = urlNormalizationUtil.normalizeStrict(rawSourceUrl);
        String urlHash = urlNormalizationUtil.sha256Hex(normalizedUrl);

        boolean duplicateExists = (excludeJobId == null)
                ? jobRepository.existsByUserIdAndSourceUrlHash(currentUserId, urlHash)
                : jobRepository.existsByUserIdAndSourceUrlHashAndIdNot(currentUserId, urlHash, excludeJobId);

        if (duplicateExists) {
            throw new DuplicateJobException(DUPLICATE_JOB_MESSAGE);
        }

        entity.setSourceUrl(normalizedUrl);
        entity.setSourceUrlHash(urlHash);
    }

    private JobEntity saveJob(JobEntity job) {
        try {
            return jobRepository.save(job);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateSourceUrlConstraint(ex)) {
                throw new DuplicateJobException(DUPLICATE_JOB_MESSAGE);
            }
            throw ex;
        }
    }

    private boolean isDuplicateSourceUrlConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String causeMessage = cause.getMessage() != null ? cause.getMessage() : "";
        String topMessage = ex.getMessage() != null ? ex.getMessage() : "";
        String combined = (causeMessage + " " + topMessage).toLowerCase(Locale.ROOT);
        return combined.contains(DUPLICATE_CONSTRAINT);
    }

    private void rejectIfBlank(String fieldName, String value) {
        if (value != null && value.isBlank()) {
            throw new JobValidationException(fieldName + " cannot be blank.");
        }
    }

    private void bindResumeOnCreate(JobEntity job, User currentUser, JobRequest request) {
        Long resumeId = jobResumeBindingService.resolveResumeId(currentUser, request.getResume());
        job.setResume(resumeId);
        if (resumeId != null && ResumeToJobScoreSupport.isValid(request.getResumeToJobScore())) {
            job.setResumeToJobScore(request.getResumeToJobScore());
        } else {
            job.setResumeToJobScore(jobResumeBindingService.scoreOrDefault(resumeId, job));
        }
    }

    private void bindResumeIfRequested(JobEntity job, User currentUser, Long requestedResume, Integer providedScore) {
        if (requestedResume == null) {
            return;
        }
        Long resumeId = jobResumeBindingService.resolveResumeId(currentUser, requestedResume);
        job.setResume(resumeId);
        if (resumeId != null && ResumeToJobScoreSupport.isValid(providedScore)) {
            job.setResumeToJobScore(providedScore);
            return;
        }
        job.setResumeToJobScore(jobResumeBindingService.scoreOrThrow(resumeId, job));
    }

    private void bindResumeAndRescore(JobEntity job, User currentUser, Long requestedResume) {
        Long resumeId = jobResumeBindingService.resolveResumeId(currentUser, requestedResume);
        job.setResume(resumeId);
        job.setResumeToJobScore(jobResumeBindingService.scoreOrThrow(resumeId, job));
    }

    private void applyJobStatus(JobEntity job, JobStatus status, String customStatus) {
        if (status == JobStatus.CUSTOM) {
            if (customStatus == null || customStatus.isBlank()) {
                throw new JobValidationException("Custom status text is required when jobStatus is CUSTOM.");
            }
            job.setJobStatus(JobStatus.CUSTOM);
            job.setCustomStatus(customStatus.trim());
            return;
        }
        job.setJobStatus(status);
        job.setCustomStatus(null);
    }
}
