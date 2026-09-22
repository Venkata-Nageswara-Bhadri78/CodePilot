package com.developer.copilot.jobs.mapper;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.jobs.dto.JobPatchRequest;
import com.developer.copilot.jobs.dto.JobRequest;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.dto.JobSummaryResponse;
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.jobs.entity.JobStatus;
import org.springframework.stereotype.Component;

@Component
public class JobMapper {

    public JobEntity toEntity(JobRequest request, User user) {
        if (request == null) {
            return null;
        }

        // Note: sourceUrl/sourceUrlHash are deliberately NOT set here - JobServiceImpl owns
        // normalization, hashing, and duplicate-checking for sourceUrl exclusively.
        return JobEntity.builder()
                .user(user)
                .originalDescription(request.getOriginalDescription())
                .description(request.getDescription())
                .title(request.getTitle())
                .company(request.getCompany())
                .location(request.getLocation())
                .employmentType(request.getEmploymentType())
                .workMode(request.getWorkMode())
                .experience(request.getExperience())
                .salary(request.getSalary())
                .education(request.getEducation())
                .department(request.getDepartment())
                .industry(request.getIndustry())
                .sourcePlatform(request.getSourcePlatform())
                .skills(emptyIfNull(request.getSkills()))
                .notes("")
                .resumeToJobScore(0)
                .jobStatus(JobStatus.APPLIED)
                .build();
    }

    public void updateEntityFromRequest(JobEntity entity, JobRequest request) {
        if (entity == null || request == null) {
            return;
        }

        // sourceUrl is handled exclusively by JobServiceImpl (normalization + dedupe check).
        entity.setOriginalDescription(request.getOriginalDescription());
        entity.setDescription(request.getDescription());
        entity.setTitle(request.getTitle());
        entity.setCompany(request.getCompany());
        entity.setLocation(request.getLocation());
        entity.setEmploymentType(request.getEmploymentType());
        entity.setWorkMode(request.getWorkMode());
        entity.setExperience(request.getExperience());
        entity.setSalary(request.getSalary());
        entity.setEducation(request.getEducation());
        entity.setDepartment(request.getDepartment());
        entity.setIndustry(request.getIndustry());
        entity.setSourcePlatform(request.getSourcePlatform());
        entity.setSkills(emptyIfNull(request.getSkills()));
    }

    public void updateEntityFromPatch(JobEntity entity, JobPatchRequest patch) {
        if (entity == null || patch == null) {
            return;
        }

        // sourceUrl is handled exclusively by JobServiceImpl (normalization + dedupe check).
        if (patch.getOriginalDescription() != null) entity.setOriginalDescription(patch.getOriginalDescription());
        if (patch.getDescription() != null) entity.setDescription(patch.getDescription());
        if (patch.getTitle() != null) entity.setTitle(patch.getTitle());
        if (patch.getCompany() != null) entity.setCompany(patch.getCompany());
        if (patch.getLocation() != null) entity.setLocation(patch.getLocation());
        if (patch.getEmploymentType() != null) entity.setEmploymentType(patch.getEmploymentType());
        if (patch.getWorkMode() != null) entity.setWorkMode(patch.getWorkMode());
        if (patch.getExperience() != null) entity.setExperience(patch.getExperience());
        if (patch.getSalary() != null) entity.setSalary(patch.getSalary());
        if (patch.getEducation() != null) entity.setEducation(patch.getEducation());
        if (patch.getDepartment() != null) entity.setDepartment(patch.getDepartment());
        if (patch.getIndustry() != null) entity.setIndustry(patch.getIndustry());
        if (patch.getSourcePlatform() != null) entity.setSourcePlatform(patch.getSourcePlatform());
        if (patch.getSkills() != null) {
            entity.setSkills(patch.getSkills());
        }
        if (patch.getNotes() != null) {
            entity.setNotes(patch.getNotes());
        }
    }

    public JobResponse toJobResponse(JobEntity entity) {
        if (entity == null) {
            return null;
        }

        return JobResponse.builder()
                .id(entity.getId())
                .sourceUrl(entity.getSourceUrl())
                .originalDescription(entity.getOriginalDescription())
                .description(entity.getDescription())
                .title(entity.getTitle())
                .company(entity.getCompany())
                .location(entity.getLocation())
                .employmentType(entity.getEmploymentType())
                .workMode(entity.getWorkMode())
                .experience(entity.getExperience())
                .salary(entity.getSalary())
                .education(entity.getEducation())
                .department(entity.getDepartment())
                .industry(entity.getIndustry())
                .sourcePlatform(entity.getSourcePlatform())
                .skills(emptyIfNull(entity.getSkills()))
                .resume(entity.getResume())
                .resumeToJobScore(entity.getResumeToJobScore() != null ? entity.getResumeToJobScore() : 0)
                .notes(entity.getNotes() != null ? entity.getNotes() : "")
                .jobStatus(resolveStatus(entity))
                .customStatus(entity.getCustomStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public JobSummaryResponse toJobSummaryResponse(JobEntity entity) {
        if (entity == null) {
            return null;
        }

        return JobSummaryResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .company(entity.getCompany())
                .location(entity.getLocation())
                .employmentType(entity.getEmploymentType())
                .workMode(entity.getWorkMode())
                .experience(entity.getExperience())
                .salary(entity.getSalary())
                .sourcePlatform(entity.getSourcePlatform())
                .skills(emptyIfNull(entity.getSkills()))
                .resume(entity.getResume())
                .resumeToJobScore(entity.getResumeToJobScore() != null ? entity.getResumeToJobScore() : 0)
                .notes(entity.getNotes() != null ? entity.getNotes() : "")
                .jobStatus(resolveStatus(entity))
                .customStatus(entity.getCustomStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private static JobStatus resolveStatus(JobEntity entity) {
        return entity.getJobStatus() != null ? entity.getJobStatus() : JobStatus.APPLIED;
    }

    private static String emptyIfNull(String value) {
        return value != null ? value : "";
    }

}
