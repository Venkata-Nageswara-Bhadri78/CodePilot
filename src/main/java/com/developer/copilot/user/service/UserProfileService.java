package com.developer.copilot.user.service;

import com.developer.copilot.user.dto.additionalinfo.AdditionalProfileInformationRequest;
import com.developer.copilot.user.dto.additionalinfo.AdditionalProfileInformationResponse;
import com.developer.copilot.user.dto.education.EducationRequest;
import com.developer.copilot.user.dto.education.EducationResponse;
import com.developer.copilot.user.dto.experience.WorkExperienceRequest;
import com.developer.copilot.user.dto.experience.WorkExperienceResponse;
import com.developer.copilot.user.dto.profile.UserProfileRequest;
import com.developer.copilot.user.dto.profile.UserProfileResponse;
import com.developer.copilot.user.dto.profilelink.ProfileLinkRequest;
import com.developer.copilot.user.dto.profilelink.ProfileLinkResponse;
import com.developer.copilot.user.dto.project.ProjectRequest;
import com.developer.copilot.user.dto.project.ProjectResponse;

import java.util.List;

public interface UserProfileService {

    UserProfileResponse createProfile(UserProfileRequest request);

    UserProfileResponse getProfile();

    UserProfileResponse updateProfile(UserProfileRequest request);

    void deleteProfile();

    WorkExperienceResponse addWorkExperience(WorkExperienceRequest request);

    List<WorkExperienceResponse> getWorkExperiences();

    WorkExperienceResponse updateWorkExperience(Long id, WorkExperienceRequest request);

    void deleteWorkExperience(Long id);

    EducationResponse addEducation(EducationRequest request);

    List<EducationResponse> getEducations();

    EducationResponse updateEducation(Long id, EducationRequest request);

    void deleteEducation(Long id);

    ProjectResponse addProject(ProjectRequest request);

    List<ProjectResponse> getProjects();

    ProjectResponse updateProject(Long id, ProjectRequest request);

    void deleteProject(Long id);

    AdditionalProfileInformationResponse addAdditionalInformation(AdditionalProfileInformationRequest request);

    List<AdditionalProfileInformationResponse> getAdditionalInformation();

    AdditionalProfileInformationResponse updateAdditionalInformation(Long id, AdditionalProfileInformationRequest request);

    void deleteAdditionalInformation(Long id);

    ProfileLinkResponse addProfileLink(ProfileLinkRequest request);

    List<ProfileLinkResponse> getProfileLinks();

    ProfileLinkResponse updateProfileLink(Long id, ProfileLinkRequest request);

    void deleteProfileLink(Long id);

}
