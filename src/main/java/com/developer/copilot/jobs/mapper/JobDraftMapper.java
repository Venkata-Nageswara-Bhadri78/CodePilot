package com.developer.copilot.jobs.mapper;

import org.springframework.stereotype.Component;

import com.developer.copilot.jobs.dto.JobDraftResponse;
import com.developer.copilot.jobs.dto.JobDraftUpdateRequest;
import com.developer.copilot.jobs.dto.JobDraftWrite;
import com.developer.copilot.jobs.dto.JobRequest;
import com.developer.copilot.jobs.entity.JobDraftEntity;
import com.developer.copilot.jobs.util.JobDraftTexts;
import com.developer.copilot.jobs.util.JobLimits;

@Component
public class JobDraftMapper {

    public JobDraftResponse toResponse(JobDraftEntity entity) {
        if (entity == null) {
            return null;
        }
        return JobDraftResponse.builder()
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
                .skills(entity.getSkills())
                .resume(entity.getResume())
                .resumeToJobScore(entity.getResumeToJobScore())
                .failureCategory(entity.getFailureCategory())
                .failureReason(entity.getFailureReason())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public JobRequest toJobRequest(JobDraftEntity entity) {
        return JobRequest.builder()
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
                .skills(entity.getSkills())
                .resume(entity.getResume())
                .resumeToJobScore(entity.getResumeToJobScore())
                .build();
    }

    public void applyWrite(JobDraftEntity entity, JobDraftWrite write, String normalizedUrl, String urlHash) {
        entity.setSourceUrl(normalizedUrl);
        entity.setSourceUrlHash(urlHash);
        applyContent(
                entity,
                write.getOriginalDescription(),
                write.getDescription(),
                write.getTitle(),
                write.getCompany(),
                write.getLocation(),
                write.getEmploymentType(),
                write.getWorkMode(),
                write.getExperience(),
                write.getSalary(),
                write.getEducation(),
                write.getDepartment(),
                write.getIndustry(),
                write.getSourcePlatform(),
                write.getSkills(),
                write.getResume(),
                write.getResumeToJobScore());
        entity.setFailureCategory(write.getFailureCategory());
        entity.setFailureReason(JobDraftTexts.clip(write.getFailureReason(), JobDraftTexts.MAX_FAILURE_REASON));
    }

    public void applyUpdate(JobDraftEntity entity, JobDraftUpdateRequest request) {
        applyContent(
                entity,
                request.getOriginalDescription(),
                request.getDescription(),
                request.getTitle(),
                request.getCompany(),
                request.getLocation(),
                request.getEmploymentType(),
                request.getWorkMode(),
                request.getExperience(),
                request.getSalary(),
                request.getEducation(),
                request.getDepartment(),
                request.getIndustry(),
                request.getSourcePlatform(),
                request.getSkills(),
                request.getResume(),
                request.getResumeToJobScore());
    }

    private void applyContent(
            JobDraftEntity entity,
            String originalDescription,
            String description,
            String title,
            String company,
            String location,
            String employmentType,
            String workMode,
            String experience,
            String salary,
            String education,
            String department,
            String industry,
            String sourcePlatform,
            String skills,
            Long resume,
            Integer resumeToJobScore) {
        entity.setOriginalDescription(blank(originalDescription, JobLimits.MAX_DESCRIPTION_LENGTH));
        entity.setDescription(blank(description, JobLimits.MAX_DESCRIPTION_LENGTH));
        entity.setTitle(blank(title, 255));
        entity.setCompany(blank(company, 255));
        entity.setLocation(blank(location, 255));
        entity.setEmploymentType(blank(employmentType, 100));
        entity.setWorkMode(blank(workMode, 50));
        entity.setExperience(blank(experience, 100));
        entity.setSalary(blank(salary, 100));
        entity.setEducation(blank(education, 255));
        entity.setDepartment(blank(department, 100));
        entity.setIndustry(blank(industry, 100));
        entity.setSourcePlatform(blank(sourcePlatform, 50));
        entity.setSkills(blank(skills, JobLimits.MAX_SKILLS_LENGTH));
        entity.setResume(resume);
        entity.setResumeToJobScore(JobDraftTexts.validScoreOrNull(resumeToJobScore));
    }

    private static String blank(String value, int maxLength) {
        return JobDraftTexts.blankToNull(JobDraftTexts.clip(value, maxLength));
    }
}
