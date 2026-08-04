package com.developer.copilot.jobs.service;

import com.developer.copilot.jobs.dto.JobPatchRequest;
import com.developer.copilot.jobs.dto.JobRequest;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.dto.JobSummaryResponse;
import com.developer.copilot.jobs.dto.request.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface JobService {

    JobResponse createJob(JobRequest request);

    Page<JobSummaryResponse> getAllJobs(String search, Pageable pageable);

    JobResponse getJobById(Long id);

    JobResponse updateJob(Long id, JobRequest request);

    JobResponse patchJob(Long id, JobPatchRequest request);

    void deleteJob(Long id);

    JobResponse updateLocation(Long id, UpdateLocationRequest request);

    JobResponse updateTitle(Long id, UpdateTitleRequest request);

    JobResponse updateCompany(Long id, UpdateCompanyRequest request);

    JobResponse updateEmploymentType(Long id, UpdateEmploymentTypeRequest request);

    JobResponse updateWorkMode(Long id, UpdateWorkModeRequest request);

    JobResponse updateExperience(Long id, UpdateExperienceRequest request);

    JobResponse updateSalary(Long id, UpdateSalaryRequest request);

    JobResponse updateEducation(Long id, UpdateEducationRequest request);

    JobResponse updateDepartment(Long id, UpdateDepartmentRequest request);

    JobResponse updateIndustry(Long id, UpdateIndustryRequest request);

    JobResponse updateSourcePlatform(Long id, UpdateSourcePlatformRequest request);

    JobResponse updateSourceUrl(Long id, UpdateSourceUrlRequest request);

    JobResponse updateSkills(Long id, UpdateSkillsRequest request);

    JobResponse updateDescription(Long id, UpdateDescriptionRequest request);

    JobResponse updateOriginalDescription(Long id, UpdateOriginalDescriptionRequest request);
}
