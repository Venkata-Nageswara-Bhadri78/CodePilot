package com.developer.copilot.user.service;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.storage.service.FileStorageService;
import com.developer.copilot.user.dto.experience.WorkExperienceRequest;
import com.developer.copilot.user.dto.profile.UserProfileRequest;
import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.UserProfile;
import com.developer.copilot.user.entity.WorkExperience;
import com.developer.copilot.user.exception.DuplicateUserProfileException;
import com.developer.copilot.user.exception.ProfileItemLimitExceededException;
import com.developer.copilot.user.exception.UserProfileNotFoundException;
import com.developer.copilot.user.config.UserProfileProperties;
import com.developer.copilot.user.mapper.UserProfileMapper;
import com.developer.copilot.user.repository.AdditionalProfileInformationRepository;
import com.developer.copilot.user.repository.EducationRepository;
import com.developer.copilot.user.repository.ProfileLinkRepository;
import com.developer.copilot.user.repository.ProjectRepository;
import com.developer.copilot.user.repository.ResumeRepository;
import com.developer.copilot.user.repository.UserProfileRepository;
import com.developer.copilot.user.repository.WorkExperienceRepository;
import com.developer.copilot.user.service.impl.UserProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private WorkExperienceRepository workExperienceRepository;

    @Mock
    private EducationRepository educationRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AdditionalProfileInformationRepository additionalProfileInformationRepository;

    @Mock
    private ProfileLinkRepository profileLinkRepository;

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ResumeParsingService resumeParsingService;

    @Mock
    private UserProfileProperties userProfileProperties;

    @Spy
    private UserProfileMapper userProfileMapper = new UserProfileMapper();

    @InjectMocks
    private UserProfileServiceImpl userProfileService;

    private User user;
    private UserProfile profile;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setFullName("Test User");
        user.setEmail("test@example.com");

        profile = UserProfile.builder()
                .id(10L)
                .user(user)
                .headline("Dev")
                .build();

        lenient().when(currentUserService.getCurrentUser()).thenReturn(user);
        lenient().when(userProfileProperties.getMaxChildItems()).thenReturn(20);
    }

    @Test
    void createProfile_success() {
        UserProfileRequest request = new UserProfileRequest();
        request.setHeadline("Engineer");

        when(userProfileRepository.existsByUser(user)).thenReturn(false);
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> {
            UserProfile saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(workExperienceRepository.findByUserProfile(any())).thenReturn(List.of());
        when(educationRepository.findByUserProfile(any())).thenReturn(List.of());
        when(projectRepository.findByUserProfile(any())).thenReturn(List.of());
        when(additionalProfileInformationRepository.findByUserProfile(any())).thenReturn(List.of());
        when(profileLinkRepository.findByUserProfile(any())).thenReturn(List.of());

        var response = userProfileService.createProfile(request);

        assertEquals("Engineer", response.getHeadline());
        assertEquals("test@example.com", response.getEmail());
    }

    @Test
    void createProfile_duplicate_throws() {
        when(userProfileRepository.existsByUser(user)).thenReturn(true);

        assertThrows(DuplicateUserProfileException.class,
                () -> userProfileService.createProfile(new UserProfileRequest()));
    }

    @Test
    void getProfile_notFound_throws() {
        when(userProfileRepository.findByUser(user)).thenReturn(Optional.empty());

        assertThrows(UserProfileNotFoundException.class, () -> userProfileService.getProfile());
    }

    @Test
    void deleteProfile_deletesResumesFromStorageAndDb() {
        Resume resume = Resume.builder()
                .id(1L)
                .userProfile(profile)
                .storageKey("users/1/resumes/a.pdf")
                .build();

        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));
        when(resumeRepository.findByUserProfile(profile)).thenReturn(List.of(resume));
        when(workExperienceRepository.findByUserProfile(profile)).thenReturn(List.of());
        when(educationRepository.findByUserProfile(profile)).thenReturn(List.of());
        when(projectRepository.findByUserProfile(profile)).thenReturn(List.of());
        when(additionalProfileInformationRepository.findByUserProfile(profile)).thenReturn(List.of());
        when(profileLinkRepository.findByUserProfile(profile)).thenReturn(List.of());

        userProfileService.deleteProfile();

        verify(resumeParsingService).deleteParsedDataFor(List.of(resume));
        verify(fileStorageService).delete("users/1/resumes/a.pdf");
        verify(resumeRepository).deleteAll(List.of(resume));
        verify(userProfileRepository).delete(profile);
    }

    @Test
    void addWorkExperience_invalidYearRange_failsValidation() {
        WorkExperienceRequest request = new WorkExperienceRequest();
        request.setCompanyName("Acme");
        request.setJobTitle("Dev");
        request.setStartYear(2023);
        request.setEndYear(2020);

        assertFalse(request.isEndYearValid());
    }

    @Test
    void deleteWorkExperience_removesRow() {
        WorkExperience experience = WorkExperience.builder().id(5L).userProfile(profile).build();

        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));
        when(workExperienceRepository.findByIdAndUserProfile(5L, profile))
                .thenReturn(Optional.of(experience));

        userProfileService.deleteWorkExperience(5L);

        verify(workExperienceRepository).delete(experience);
    }

    @Test
    void updateProfile_nullHeadline_clearsField() {
        profile.setHeadline("A");
        profile.setSummary("old");

        UserProfileRequest request = new UserProfileRequest();
        request.setHeadline(null);
        request.setSummary("x");

        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));
        when(workExperienceRepository.findByUserProfile(profile)).thenReturn(List.of());
        when(educationRepository.findByUserProfile(profile)).thenReturn(List.of());
        when(projectRepository.findByUserProfile(profile)).thenReturn(List.of());
        when(additionalProfileInformationRepository.findByUserProfile(profile)).thenReturn(List.of());
        when(profileLinkRepository.findByUserProfile(profile)).thenReturn(List.of());

        var response = userProfileService.updateProfile(request);

        assertNull(response.getHeadline());
        assertEquals("x", response.getSummary());
    }

    @Test
    void addWorkExperience_atCap_throws() {
        WorkExperienceRequest request = new WorkExperienceRequest();
        request.setCompanyName("Acme");
        request.setJobTitle("Dev");
        request.setStartYear(2020);

        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));
        when(workExperienceRepository.countByUserProfile(profile)).thenReturn(20L);

        assertThrows(ProfileItemLimitExceededException.class,
                () -> userProfileService.addWorkExperience(request));
        verify(workExperienceRepository, never()).save(any());
    }
}
