package com.developer.copilot.user.mapper;

import com.developer.copilot.user.dto.additionalinfo.AdditionalProfileInformationResponse;
import com.developer.copilot.user.dto.education.EducationResponse;
import com.developer.copilot.user.dto.experience.WorkExperienceResponse;
import com.developer.copilot.user.dto.profile.UserProfileResponse;
import com.developer.copilot.user.dto.profilelink.ProfileLinkResponse;
import com.developer.copilot.user.dto.project.ProjectResponse;
import com.developer.copilot.user.entity.AdditionalProfileInformation;
import com.developer.copilot.user.entity.Education;
import com.developer.copilot.user.entity.ProfileLink;
import com.developer.copilot.user.entity.Project;
import com.developer.copilot.user.entity.UserProfile;
import com.developer.copilot.user.entity.WorkExperience;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserProfileMapper {

    public WorkExperienceResponse toWorkExperienceResponse(WorkExperience e) {
        return WorkExperienceResponse.builder()
                .id(e.getId())
                .companyName(e.getCompanyName())
                .jobTitle(e.getJobTitle())
                .startYear(e.getStartYear())
                .endYear(e.getEndYear())
                .description(e.getDescription())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    public EducationResponse toEducationResponse(Education e) {
        return EducationResponse.builder()
                .id(e.getId())
                .institutionName(e.getInstitutionName())
                .field(e.getField())
                .startYear(e.getStartYear())
                .endYear(e.getEndYear())
                .scoreOrGrade(e.getScoreOrGrade())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    public ProjectResponse toProjectResponse(Project p) {
        return ProjectResponse.builder()
                .id(p.getId())
                .projectTitle(p.getProjectTitle())
                .projectDescription(p.getProjectDescription())
                .projectLink(p.getProjectLink())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    public AdditionalProfileInformationResponse toAdditionalInfoResponse(AdditionalProfileInformation a) {
        return AdditionalProfileInformationResponse.builder()
                .id(a.getId())
                .type(a.getType())
                .description(a.getDescription())
                .link(a.getLink())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    public ProfileLinkResponse toProfileLinkResponse(ProfileLink pl) {
        return ProfileLinkResponse.builder()
                .id(pl.getId())
                .url(pl.getUrl())
                .createdAt(pl.getCreatedAt())
                .updatedAt(pl.getUpdatedAt())
                .build();
    }

    public UserProfileResponse toProfileResponse(
            UserProfile profile,
            List<WorkExperience> experiences,
            List<Education> educations,
            List<Project> projects,
            List<AdditionalProfileInformation> additionalInfos,
            List<ProfileLink> profileLinks) {

        return UserProfileResponse.builder()
                .id(profile.getId())
                .fullName(profile.getUser().getFullName())
                .email(profile.getUser().getEmail())
                .headline(profile.getHeadline())
                .summary(profile.getSummary())
                .technicalSkills(profile.getTechnicalSkills())
                .workExperiences(experiences.stream().map(this::toWorkExperienceResponse).toList())
                .educations(educations.stream().map(this::toEducationResponse).toList())
                .projects(projects.stream().map(this::toProjectResponse).toList())
                .additionalInformation(additionalInfos.stream().map(this::toAdditionalInfoResponse).toList())
                .profileLinks(profileLinks.stream().map(this::toProfileLinkResponse).toList())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

}
