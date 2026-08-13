package com.developer.copilot.user.dto.profile;

import com.developer.copilot.user.dto.additionalinfo.AdditionalProfileInformationResponse;
import com.developer.copilot.user.dto.education.EducationResponse;
import com.developer.copilot.user.dto.experience.WorkExperienceResponse;
import com.developer.copilot.user.dto.profilelink.ProfileLinkResponse;
import com.developer.copilot.user.dto.project.ProjectResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class UserProfileResponse {

    private Long id;
    private String fullName;
    private String email;
    private String headline;
    private String summary;
    private String technicalSkills;
    private List<WorkExperienceResponse> workExperiences;
    private List<EducationResponse> educations;
    private List<ProjectResponse> projects;
    private List<AdditionalProfileInformationResponse> additionalInformation;
    private List<ProfileLinkResponse> profileLinks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
