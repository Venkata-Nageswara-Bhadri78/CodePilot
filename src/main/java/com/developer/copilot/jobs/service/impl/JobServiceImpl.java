package com.developer.copilot.jobs.service.impl;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.common.util.UrlNormalizationUtil;
import com.developer.copilot.jobs.dto.JobPatchRequest;
import com.developer.copilot.jobs.dto.JobRequest;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.dto.JobSummaryResponse;
import com.developer.copilot.jobs.dto.request.*;
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.jobs.exception.DuplicateJobException;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.jobs.exception.JobValidationException;
import com.developer.copilot.jobs.mapper.JobMapper;
import com.developer.copilot.jobs.repository.JobRepository;
import com.developer.copilot.jobs.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final JobMapper jobMapper;
    private final UrlNormalizationUtil urlNormalizationUtil;

    @Override
    @Transactional
    public JobResponse createJob(JobRequest request) {
        User currentUser = getCurrentUser();
        JobEntity jobEntity = jobMapper.toEntity(request, currentUser);
        applySourceUrl(jobEntity, request.getSourceUrl(), currentUser.getId(), null);
        JobEntity savedJob = jobRepository.save(jobEntity);
        return jobMapper.toJobResponse(savedJob);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JobSummaryResponse> getAllJobs(String search, Pageable pageable) {
        User currentUser = getCurrentUser();
        Page<JobEntity> jobPage;
        if (search != null && !search.trim().isEmpty()) {
            jobPage = jobRepository.searchJobsByUserId(currentUser.getId(), search.trim(), pageable);
        } else {
            jobPage = jobRepository.findAllByUserId(currentUser.getId(), pageable);
        }
        return jobPage.map(jobMapper::toJobSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public JobResponse getJobById(Long id) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        return jobMapper.toJobResponse(job);
    }

    @Override
    @Transactional
    public JobResponse updateJob(Long id, JobRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        jobMapper.updateEntityFromRequest(job, request);
        applySourceUrl(job, request.getSourceUrl(), currentUser.getId(), job.getId());
        JobEntity updatedJob = jobRepository.save(job);
        return jobMapper.toJobResponse(updatedJob);
    }

    @Override
    @Transactional
    public JobResponse patchJob(Long id, JobPatchRequest request) {
        User currentUser = getCurrentUser();
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

        JobEntity updatedJob = jobRepository.save(job);
        return jobMapper.toJobResponse(updatedJob);
    }

    @Override
    @Transactional
    public void deleteJob(Long id) {
        User currentUser = getCurrentUser();
        if (!jobRepository.existsByIdAndUserId(id, currentUser.getId())) {
            throw new JobNotFoundException("Job not found with id: " + id);
        }
        jobRepository.deleteByIdAndUserId(id, currentUser.getId());
    }

    @Override
    @Transactional
    public JobResponse updateLocation(Long id, UpdateLocationRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setLocation(request.getLocation());
        return jobMapper.toJobResponse(jobRepository.save(job));
    }

    @Override
    @Transactional
    public JobResponse updateTitle(Long id, UpdateTitleRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setTitle(request.getTitle());
        return jobMapper.toJobResponse(jobRepository.save(job));
    }

    @Override
    @Transactional
    public JobResponse updateCompany(Long id, UpdateCompanyRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setCompany(request.getCompany());
        return jobMapper.toJobResponse(jobRepository.save(job));
    }

    @Override
    @Transactional
    public JobResponse updateEmploymentType(Long id, UpdateEmploymentTypeRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setEmploymentType(request.getEmploymentType());
        return jobMapper.toJobResponse(jobRepository.save(job));
    }

    @Override
    @Transactional
    public JobResponse updateWorkMode(Long id, UpdateWorkModeRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setWorkMode(request.getWorkMode());
        return jobMapper.toJobResponse(jobRepository.save(job));
    }

    @Override
    @Transactional
    public JobResponse updateExperience(Long id, UpdateExperienceRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setExperience(request.getExperience());
        return jobMapper.toJobResponse(jobRepository.save(job));
    }

    @Override
    @Transactional
    public JobResponse updateSalary(Long id, UpdateSalaryRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setSalary(request.getSalary());
        return jobMapper.toJobResponse(jobRepository.save(job));
    }

    @Override
    @Transactional
    public JobResponse updateEducation(Long id, UpdateEducationRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setEducation(request.getEducation());
        return jobMapper.toJobResponse(jobRepository.save(job));
    }

    @Override
    @Transactional
    public JobResponse updateDepartment(Long id, UpdateDepartmentRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setDepartment(request.getDepartment());
        return jobMapper.toJobResponse(jobRepository.save(job));
    }

    @Override
    @Transactional
    public JobResponse updateIndustry(Long id, UpdateIndustryRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setIndustry(request.getIndustry());
        return jobMapper.toJobResponse(jobRepository.save(job));
    }

    @Override
    @Transactional
    public JobResponse updateSourcePlatform(Long id, UpdateSourcePlatformRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setSourcePlatform(request.getSourcePlatform());
        return jobMapper.toJobResponse(jobRepository.save(job));
    }

    @Override
    @Transactional
    public JobResponse updateSourceUrl(Long id, UpdateSourceUrlRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        applySourceUrl(job, request.getSourceUrl(), currentUser.getId(), job.getId());
        return jobMapper.toJobResponse(jobRepository.save(job));
    }

    @Override
    @Transactional
    public JobResponse updateSkills(Long id, UpdateSkillsRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setSkills(request.getSkills() != null ? new ArrayList<>(request.getSkills()) : new ArrayList<>());
        return jobMapper.toJobResponse(jobRepository.save(job));
    }

    @Override
    @Transactional
    public JobResponse updateDescription(Long id, UpdateDescriptionRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setDescription(request.getDescription());
        return jobMapper.toJobResponse(jobRepository.save(job));
    }

    @Override
    @Transactional
    public JobResponse updateOriginalDescription(Long id, UpdateOriginalDescriptionRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobEntityForCurrentUser(id, currentUser);
        job.setOriginalDescription(request.getOriginalDescription());
        return jobMapper.toJobResponse(jobRepository.save(job));
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

        String normalizedUrl = urlNormalizationUtil.normalizeLenient(rawSourceUrl);
        String urlHash = urlNormalizationUtil.sha256Hex(normalizedUrl);

        boolean duplicateExists = (excludeJobId == null)
                ? jobRepository.existsByUserIdAndSourceUrlHash(currentUserId, urlHash)
                : jobRepository.existsByUserIdAndSourceUrlHashAndIdNot(currentUserId, urlHash, excludeJobId);

        if (duplicateExists) {
            throw new DuplicateJobException("This post was already added to your records.");
        }

        entity.setSourceUrl(normalizedUrl);
        entity.setSourceUrlHash(urlHash);
    }

    private void rejectIfBlank(String fieldName, String value) {
        if (value != null && value.isBlank()) {
            throw new JobValidationException(fieldName + " cannot be blank.");
        }
    }
}
