package com.developer.copilot.user.service;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.storage.service.FileStorageService;
import com.developer.copilot.user.dto.parsing.ResumeParsedDataResponse;
import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.ResumeParsedData;
import com.developer.copilot.user.entity.ResumeParsingStatus;
import com.developer.copilot.user.entity.UserProfile;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.UserProfileNotFoundException;
import com.developer.copilot.user.mapper.ResumeParsedDataMapper;
import com.developer.copilot.user.repository.ResumeParsedDataRepository;
import com.developer.copilot.user.repository.ResumeRepository;
import com.developer.copilot.user.repository.UserProfileRepository;
import com.developer.copilot.user.service.impl.ResumeParsingServiceImpl;
import com.developer.copilot.user.service.parsing.ResumeParser;
import com.developer.copilot.user.service.parsing.ResumeParsingWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeParsingServiceImplTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @Mock
    private ResumeParser resumeParser;

    @Mock
    private ResumeParsingWorker resumeParsingWorker;

    @Mock
    private ResumeParsedDataMapper resumeParsedDataMapper;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private ResumeParsingServiceImpl resumeParsingService;

    private User user;
    private UserProfile profile;
    private Resume resume;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(10L);

        profile = UserProfile.builder().id(1L).user(user).build();

        resume = Resume.builder()
                .id(5L)
                .userProfile(profile)
                .originalFilename("resume.pdf")
                .storageKey("users/10/resumes/a.pdf")
                .highPriority(true)
                .active(true)
                .build();

        lenient().when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void getParsedResume_completedRecord_returnsPersistedDataWithoutParsing() {
        ResumeParsedData completed = record(ResumeParsingStatus.COMPLETED);

        withProfile();
        when(resumeRepository.findByIdAndUserProfileAndActiveTrue(5L, profile))
                .thenReturn(Optional.of(resume));
        when(resumeParsedDataRepository.findByResume(resume)).thenReturn(Optional.of(completed));
        when(resumeParsedDataMapper.toResponse(resume, completed)).thenReturn(response("COMPLETED"));

        ResumeParsedDataResponse response = resumeParsingService.getParsedResume(5L);

        assertEquals("COMPLETED", response.getStatus());
        verifyNoInteractions(resumeParser, resumeParsingWorker, fileStorageService);
    }

    @Test
    void getParsedResume_failedRecord_isNotRetried() {
        ResumeParsedData failed = record(ResumeParsingStatus.FAILED);
        failed.setAttemptCount(3);

        withProfile();
        when(resumeRepository.findByIdAndUserProfileAndActiveTrue(5L, profile))
                .thenReturn(Optional.of(resume));
        when(resumeParsedDataRepository.findByResume(resume)).thenReturn(Optional.of(failed));
        when(resumeParsedDataMapper.toResponse(resume, failed)).thenReturn(response("FAILED"));

        ResumeParsedDataResponse response = resumeParsingService.getParsedResume(5L);

        assertEquals("FAILED", response.getStatus());
        verify(resumeParser, never()).parseWithRetry(any(), any());
        verify(resumeParsingWorker, never()).persistAsync(any(), any());
    }

    @Test
    void getParsedResume_noRecord_parsesInlineAndPersistsSeparately() {
        ResumeParsedData parsed = record(ResumeParsingStatus.COMPLETED);

        withProfile();
        when(resumeRepository.findByIdAndUserProfileAndActiveTrue(5L, profile))
                .thenReturn(Optional.of(resume));
        when(resumeParsedDataRepository.findByResume(resume)).thenReturn(Optional.empty());
        when(fileStorageService.exists("users/10/resumes/a.pdf")).thenReturn(true);
        when(resumeParser.parseWithRetry(resume, null)).thenReturn(parsed);
        when(resumeParsedDataMapper.toResponse(resume, parsed)).thenReturn(response("COMPLETED"));

        ResumeParsedDataResponse response = resumeParsingService.getParsedResume(5L);

        assertEquals("COMPLETED", response.getStatus());
        verify(resumeParsingWorker).persistAsync(5L, parsed);
    }

    @Test
    void getParsedResume_pendingRecord_isParsedOnDemand() {
        ResumeParsedData pending = record(ResumeParsingStatus.PENDING);
        ResumeParsedData parsed = record(ResumeParsingStatus.COMPLETED);

        withProfile();
        when(resumeRepository.findByIdAndUserProfileAndActiveTrue(5L, profile))
                .thenReturn(Optional.of(resume));
        when(resumeParsedDataRepository.findByResume(resume)).thenReturn(Optional.of(pending));
        when(fileStorageService.exists("users/10/resumes/a.pdf")).thenReturn(true);
        when(resumeParser.parseWithRetry(resume, pending)).thenReturn(parsed);
        when(resumeParsedDataMapper.toResponse(resume, parsed)).thenReturn(response("COMPLETED"));

        resumeParsingService.getParsedResume(5L);

        verify(resumeParser).parseWithRetry(resume, pending);
        verify(resumeParsingWorker).persistAsync(5L, parsed);
    }

    @Test
    void getParsedResume_missingStoredFile_throwsResumeNotFound() {
        withProfile();
        when(resumeRepository.findByIdAndUserProfileAndActiveTrue(5L, profile))
                .thenReturn(Optional.of(resume));
        when(resumeParsedDataRepository.findByResume(resume)).thenReturn(Optional.empty());
        when(fileStorageService.exists("users/10/resumes/a.pdf")).thenReturn(false);

        assertThrows(ResumeNotFoundException.class, () -> resumeParsingService.getParsedResume(5L));
        verify(resumeParser, never()).parseWithRetry(any(), any());
    }

    @Test
    void getParsedResume_nullResumeId_resolvesHighPriorityResume() {
        ResumeParsedData completed = record(ResumeParsingStatus.COMPLETED);

        withProfile();
        when(resumeRepository.findByHighPriorityTrueAndUserProfileAndActiveTrue(profile))
                .thenReturn(Optional.of(resume));
        when(resumeParsedDataRepository.findByResume(resume)).thenReturn(Optional.of(completed));
        when(resumeParsedDataMapper.toResponse(resume, completed)).thenReturn(response("COMPLETED"));

        resumeParsingService.getParsedResume(null);

        verify(resumeRepository).findByHighPriorityTrueAndUserProfileAndActiveTrue(profile);
        verify(resumeRepository, never()).findByIdAndUserProfileAndActiveTrue(any(), any());
    }

    @Test
    void getParsedResume_nullResumeIdWithoutHighPriorityResume_throwsResumeNotFound() {
        withProfile();
        when(resumeRepository.findByHighPriorityTrueAndUserProfileAndActiveTrue(profile))
                .thenReturn(Optional.empty());

        assertThrows(ResumeNotFoundException.class, () -> resumeParsingService.getParsedResume(null));
    }

    @Test
    void getParsedResume_resumeOwnedByAnotherProfile_throwsResumeNotFound() {
        withProfile();
        when(resumeRepository.findByIdAndUserProfileAndActiveTrue(999L, profile))
                .thenReturn(Optional.empty());

        assertThrows(ResumeNotFoundException.class, () -> resumeParsingService.getParsedResume(999L));
        verifyNoInteractions(resumeParser, fileStorageService);
    }

    @Test
    void getParsedResume_withoutProfile_throwsUserProfileNotFound() {
        when(userProfileRepository.findByUser(user)).thenReturn(Optional.empty());

        assertThrows(UserProfileNotFoundException.class, () -> resumeParsingService.getParsedResume(5L));
    }

    @Test
    void initializeAndScheduleParsing_createsPendingRecordAndSchedulesWork() {
        ResumeParsedData pending = record(ResumeParsingStatus.PENDING);

        when(resumeParsedDataRepository.existsByResume(resume)).thenReturn(false);
        when(resumeParser.newPendingRecord(resume)).thenReturn(pending);

        resumeParsingService.initializeAndScheduleParsing(resume);

        verify(resumeParsedDataRepository).save(pending);
        verify(resumeParsingWorker).parseAndPersist(5L);
    }

    @Test
    void initializeAndScheduleParsing_existingRecord_isNotDuplicated() {
        when(resumeParsedDataRepository.existsByResume(resume)).thenReturn(true);

        resumeParsingService.initializeAndScheduleParsing(resume);

        verify(resumeParsedDataRepository, never()).save(any());
        verify(resumeParsingWorker).parseAndPersist(5L);
    }

    @Test
    void deleteParsedDataFor_singleResume_delegatesToRepository() {
        resumeParsingService.deleteParsedDataFor(resume);

        verify(resumeParsedDataRepository).deleteByResume(resume);
    }

    @Test
    void deleteParsedDataFor_emptyList_doesNothing() {
        resumeParsingService.deleteParsedDataFor(List.of());

        verify(resumeParsedDataRepository, never()).deleteByResumeIn(any());
    }

    @Test
    void deleteParsedDataFor_resumeList_delegatesToRepository() {
        List<Resume> resumes = List.of(resume);

        resumeParsingService.deleteParsedDataFor(resumes);

        verify(resumeParsedDataRepository).deleteByResumeIn(eq(resumes));
    }

    private void withProfile() {
        when(userProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
    }

    private ResumeParsedData record(ResumeParsingStatus status) {
        return ResumeParsedData.builder()
                .id(77L)
                .resume(resume)
                .status(status)
                .build();
    }

    private ResumeParsedDataResponse response(String status) {
        return ResumeParsedDataResponse.builder()
                .resumeId(5L)
                .status(status)
                .build();
    }
}
