package com.developer.copilot.user.service.impl;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
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
import com.developer.copilot.user.entity.AdditionalProfileInformation;
import com.developer.copilot.user.entity.Education;
import com.developer.copilot.user.entity.ProfileLink;
import com.developer.copilot.user.entity.Project;
import com.developer.copilot.user.entity.UserProfile;
import com.developer.copilot.user.entity.WorkExperience;
import com.developer.copilot.user.exception.AdditionalProfileInformationNotFoundException;
import com.developer.copilot.common.storage.service.FileStorageService;
import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.repository.ResumeRepository;
import com.developer.copilot.user.exception.DuplicateUserProfileException;
import com.developer.copilot.user.exception.EducationNotFoundException;
import com.developer.copilot.user.exception.ProfileLinkNotFoundException;
import com.developer.copilot.user.exception.ProjectNotFoundException;
import com.developer.copilot.user.exception.UserProfileNotFoundException;
import com.developer.copilot.user.exception.WorkExperienceNotFoundException;
import com.developer.copilot.user.mapper.UserProfileMapper;
import com.developer.copilot.user.repository.AdditionalProfileInformationRepository;
import com.developer.copilot.user.repository.EducationRepository;
import com.developer.copilot.user.repository.ProfileLinkRepository;
import com.developer.copilot.user.repository.ProjectRepository;
import com.developer.copilot.user.repository.UserProfileRepository;
import com.developer.copilot.user.repository.WorkExperienceRepository;
import com.developer.copilot.user.service.ResumeParsingService;
import com.developer.copilot.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final CurrentUserService currentUserService;
    private final UserProfileRepository userProfileRepository;
    private final WorkExperienceRepository workExperienceRepository;
    private final EducationRepository educationRepository;
    private final ProjectRepository projectRepository;
    private final AdditionalProfileInformationRepository additionalProfileInformationRepository;
    private final ProfileLinkRepository profileLinkRepository;
    private final ResumeRepository resumeRepository;
    private final FileStorageService fileStorageService;
    private final ResumeParsingService resumeParsingService;
    private final UserProfileMapper userProfileMapper;

    // ─── Profile ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserProfileResponse createProfile(UserProfileRequest request) {
        User user = currentUserService.getCurrentUser();

        if (userProfileRepository.existsByUser(user)) {
            throw new DuplicateUserProfileException();
        }

        UserProfile profile = UserProfile.builder()
                .user(user)
                .headline(request.getHeadline())
                .summary(request.getSummary())
                .technicalSkills(request.getTechnicalSkills())
                .build();

        userProfileRepository.save(profile);

        return buildFullProfileResponse(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile() {
        UserProfile profile = resolveProfile();
        return buildFullProfileResponse(profile);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UserProfileRequest request) {
        UserProfile profile = resolveProfile();

        if (request.getHeadline() != null) {
            profile.setHeadline(request.getHeadline());
        }
        if (request.getSummary() != null) {
            profile.setSummary(request.getSummary());
        }
        if (request.getTechnicalSkills() != null) {
            profile.setTechnicalSkills(request.getTechnicalSkills());
        }

        userProfileRepository.save(profile);

        return buildFullProfileResponse(profile);
    }

    @Override
    @Transactional
    public void deleteProfile() {
        UserProfile profile = resolveProfile();

        deleteResumesForProfile(profile);

        workExperienceRepository.deleteAll(workExperienceRepository.findByUserProfile(profile));
        educationRepository.deleteAll(educationRepository.findByUserProfile(profile));
        projectRepository.deleteAll(projectRepository.findByUserProfile(profile));
        additionalProfileInformationRepository.deleteAll(additionalProfileInformationRepository.findByUserProfile(profile));
        profileLinkRepository.deleteAll(profileLinkRepository.findByUserProfile(profile));

        userProfileRepository.delete(profile);
    }

    // ─── Work Experience ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public WorkExperienceResponse addWorkExperience(WorkExperienceRequest request) {
        UserProfile profile = resolveProfile();

        WorkExperience experience = WorkExperience.builder()
                .userProfile(profile)
                .companyName(request.getCompanyName())
                .jobTitle(request.getJobTitle())
                .startYear(request.getStartYear())
                .endYear(request.getEndYear())
                .description(request.getDescription())
                .build();

        workExperienceRepository.save(experience);

        return userProfileMapper.toWorkExperienceResponse(experience);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkExperienceResponse> getWorkExperiences() {
        UserProfile profile = resolveProfile();
        return workExperienceRepository.findByUserProfile(profile)
                .stream()
                .map(userProfileMapper::toWorkExperienceResponse)
                .toList();
    }

    @Override
    @Transactional
    public WorkExperienceResponse updateWorkExperience(Long id, WorkExperienceRequest request) {
        UserProfile profile = resolveProfile();

        WorkExperience experience = workExperienceRepository
                .findByIdAndUserProfile(id, profile)
                .orElseThrow(WorkExperienceNotFoundException::new);

        experience.setCompanyName(request.getCompanyName());
        experience.setJobTitle(request.getJobTitle());
        experience.setStartYear(request.getStartYear());
        experience.setEndYear(request.getEndYear());
        experience.setDescription(request.getDescription());

        workExperienceRepository.save(experience);

        return userProfileMapper.toWorkExperienceResponse(experience);
    }

    @Override
    @Transactional
    public void deleteWorkExperience(Long id) {
        UserProfile profile = resolveProfile();

        WorkExperience experience = workExperienceRepository
                .findByIdAndUserProfile(id, profile)
                .orElseThrow(WorkExperienceNotFoundException::new);

        workExperienceRepository.delete(experience);
    }

    // ─── Education ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public EducationResponse addEducation(EducationRequest request) {
        UserProfile profile = resolveProfile();

        Education education = Education.builder()
                .userProfile(profile)
                .institutionName(request.getInstitutionName())
                .field(request.getField())
                .startYear(request.getStartYear())
                .endYear(request.getEndYear())
                .scoreOrGrade(request.getScoreOrGrade())
                .build();

        educationRepository.save(education);

        return userProfileMapper.toEducationResponse(education);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EducationResponse> getEducations() {
        UserProfile profile = resolveProfile();
        return educationRepository.findByUserProfile(profile)
                .stream()
                .map(userProfileMapper::toEducationResponse)
                .toList();
    }

    @Override
    @Transactional
    public EducationResponse updateEducation(Long id, EducationRequest request) {
        UserProfile profile = resolveProfile();

        Education education = educationRepository
                .findByIdAndUserProfile(id, profile)
                .orElseThrow(EducationNotFoundException::new);

        education.setInstitutionName(request.getInstitutionName());
        education.setField(request.getField());
        education.setStartYear(request.getStartYear());
        education.setEndYear(request.getEndYear());
        education.setScoreOrGrade(request.getScoreOrGrade());

        educationRepository.save(education);

        return userProfileMapper.toEducationResponse(education);
    }

    @Override
    @Transactional
    public void deleteEducation(Long id) {
        UserProfile profile = resolveProfile();

        Education education = educationRepository
                .findByIdAndUserProfile(id, profile)
                .orElseThrow(EducationNotFoundException::new);

        educationRepository.delete(education);
    }

    // ─── Projects ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ProjectResponse addProject(ProjectRequest request) {
        UserProfile profile = resolveProfile();

        Project project = Project.builder()
                .userProfile(profile)
                .projectTitle(request.getProjectTitle())
                .projectDescription(request.getProjectDescription())
                .projectLink(request.getProjectLink())
                .build();

        projectRepository.save(project);

        return userProfileMapper.toProjectResponse(project);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> getProjects() {
        UserProfile profile = resolveProfile();
        return projectRepository.findByUserProfile(profile)
                .stream()
                .map(userProfileMapper::toProjectResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProjectResponse updateProject(Long id, ProjectRequest request) {
        UserProfile profile = resolveProfile();

        Project project = projectRepository
                .findByIdAndUserProfile(id, profile)
                .orElseThrow(ProjectNotFoundException::new);

        project.setProjectTitle(request.getProjectTitle());
        project.setProjectDescription(request.getProjectDescription());
        project.setProjectLink(request.getProjectLink());

        projectRepository.save(project);

        return userProfileMapper.toProjectResponse(project);
    }

    @Override
    @Transactional
    public void deleteProject(Long id) {
        UserProfile profile = resolveProfile();

        Project project = projectRepository
                .findByIdAndUserProfile(id, profile)
                .orElseThrow(ProjectNotFoundException::new);

        projectRepository.delete(project);
    }

    // ─── Additional Information ───────────────────────────────────────────────

    @Override
    @Transactional
    public AdditionalProfileInformationResponse addAdditionalInformation(AdditionalProfileInformationRequest request) {
        UserProfile profile = resolveProfile();

        AdditionalProfileInformation info = AdditionalProfileInformation.builder()
                .userProfile(profile)
                .type(request.getType())
                .description(request.getDescription())
                .link(request.getLink())
                .build();

        additionalProfileInformationRepository.save(info);

        return userProfileMapper.toAdditionalInfoResponse(info);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdditionalProfileInformationResponse> getAdditionalInformation() {
        UserProfile profile = resolveProfile();
        return additionalProfileInformationRepository.findByUserProfile(profile)
                .stream()
                .map(userProfileMapper::toAdditionalInfoResponse)
                .toList();
    }

    @Override
    @Transactional
    public AdditionalProfileInformationResponse updateAdditionalInformation(Long id, AdditionalProfileInformationRequest request) {
        UserProfile profile = resolveProfile();

        AdditionalProfileInformation info = additionalProfileInformationRepository
                .findByIdAndUserProfile(id, profile)
                .orElseThrow(AdditionalProfileInformationNotFoundException::new);

        info.setType(request.getType());
        info.setDescription(request.getDescription());
        info.setLink(request.getLink());

        additionalProfileInformationRepository.save(info);

        return userProfileMapper.toAdditionalInfoResponse(info);
    }

    @Override
    @Transactional
    public void deleteAdditionalInformation(Long id) {
        UserProfile profile = resolveProfile();

        AdditionalProfileInformation info = additionalProfileInformationRepository
                .findByIdAndUserProfile(id, profile)
                .orElseThrow(AdditionalProfileInformationNotFoundException::new);

        additionalProfileInformationRepository.delete(info);
    }

    // ─── Profile Links ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ProfileLinkResponse addProfileLink(ProfileLinkRequest request) {
        UserProfile profile = resolveProfile();

        ProfileLink link = ProfileLink.builder()
                .userProfile(profile)
                .url(request.getUrl())
                .build();

        profileLinkRepository.save(link);

        return userProfileMapper.toProfileLinkResponse(link);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfileLinkResponse> getProfileLinks() {
        UserProfile profile = resolveProfile();
        return profileLinkRepository.findByUserProfile(profile)
                .stream()
                .map(userProfileMapper::toProfileLinkResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProfileLinkResponse updateProfileLink(Long id, ProfileLinkRequest request) {
        UserProfile profile = resolveProfile();

        ProfileLink link = profileLinkRepository
                .findByIdAndUserProfile(id, profile)
                .orElseThrow(ProfileLinkNotFoundException::new);

        link.setUrl(request.getUrl());

        profileLinkRepository.save(link);

        return userProfileMapper.toProfileLinkResponse(link);
    }

    @Override
    @Transactional
    public void deleteProfileLink(Long id) {
        UserProfile profile = resolveProfile();

        ProfileLink link = profileLinkRepository
                .findByIdAndUserProfile(id, profile)
                .orElseThrow(ProfileLinkNotFoundException::new);

        profileLinkRepository.delete(link);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private UserProfile resolveProfile() {
        User user = currentUserService.getCurrentUser();
        return userProfileRepository.findByUser(user)
                .orElseThrow(UserProfileNotFoundException::new);
    }

    private void deleteResumesForProfile(UserProfile profile) {
        List<Resume> resumes = resumeRepository.findByUserProfile(profile);

        // Parsed data references the resume rows, so it has to go first.
        resumeParsingService.deleteParsedDataFor(resumes);

        for (Resume resume : resumes) {
            fileStorageService.delete(resume.getStorageKey());
            resumeRepository.delete(resume);
        }
    }

    private UserProfileResponse buildFullProfileResponse(UserProfile profile) {
        List<WorkExperience> experiences = workExperienceRepository.findByUserProfile(profile);
        List<Education> educations = educationRepository.findByUserProfile(profile);
        List<Project> projects = projectRepository.findByUserProfile(profile);
        List<AdditionalProfileInformation> additionalInfos = additionalProfileInformationRepository.findByUserProfile(profile);
        List<ProfileLink> profileLinks = profileLinkRepository.findByUserProfile(profile);

        return userProfileMapper.toProfileResponse(
                profile, experiences, educations, projects, additionalInfos, profileLinks);
    }

}
