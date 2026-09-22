package com.developer.copilot.jobs.service;

import com.developer.copilot.ai.dto.request.ResumeToJobScoreAiRequest;
import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.ai.service.AiService;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.user.dto.parsing.ResumeParsedDataResponse;
import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.UserProfile;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.ResumeParsingException;
import com.developer.copilot.user.repository.ResumeRepository;
import com.developer.copilot.user.repository.UserProfileRepository;
import com.developer.copilot.user.service.ResumeParsingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobResumeBindingServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private ResumeParsingService resumeParsingService;

    @Mock
    private AiService aiService;

    @InjectMocks
    private JobResumeBindingService service;

    private User user;
    private UserProfile profile;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        profile = UserProfile.builder().id(3L).user(user).build();
    }

    @Test
    void resolveResumeId_explicitMustBelongToUser() {
        when(userProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        Resume resume = Resume.builder().id(12L).userProfile(profile).active(true).build();
        when(resumeRepository.findByIdAndUserProfileAndActiveTrue(12L, profile)).thenReturn(Optional.of(resume));

        assertEquals(12L, service.resolveResumeId(user, 12L));
    }

    @Test
    void resolveResumeId_foreignId_throwsNotFound() {
        when(userProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(resumeRepository.findByIdAndUserProfileAndActiveTrue(99L, profile)).thenReturn(Optional.empty());

        assertThrows(ResumeNotFoundException.class, () -> service.resolveResumeId(user, 99L));
    }

    @Test
    void resolveResumeId_omitted_usesHighPriority() {
        when(userProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        Resume primary = Resume.builder().id(4L).highPriority(true).build();
        when(resumeRepository.findByHighPriorityTrueAndUserProfileAndActiveTrue(profile))
                .thenReturn(Optional.of(primary));

        assertEquals(4L, service.resolveResumeId(user, null));
    }

    @Test
    void resolveResumeId_noProfileAndNoRequest_returnsNull() {
        when(userProfileRepository.findByUser(user)).thenReturn(Optional.empty());

        assertEquals(null, service.resolveResumeId(user, null));
    }

    @Test
    void loadResumeContextQuietly_pending_returnsEmpty() {
        when(resumeParsingService.getParsedResume(5L))
                .thenThrow(new ResumeParsingException("Resume parsing is still in progress. Please retry shortly."));

        assertEquals("", service.loadResumeContextQuietly(5L));
    }

    @Test
    void scoreOrDefault_noResume_isZeroWithoutAi() {
        assertEquals(0, service.scoreOrDefault(null, JobEntity.builder().title("SE").build()));
        verify(aiService, never()).scoreResumeToJob(any());
    }

    @Test
    void scoreOrDefault_aiFailure_returnsZero() {
        when(resumeParsingService.getParsedResume(5L)).thenReturn(ResumeParsedDataResponse.builder()
                .status("COMPLETED")
                .contextText("Java, Spring")
                .build());
        when(aiService.scoreResumeToJob(any(ResumeToJobScoreAiRequest.class)))
                .thenThrow(new AiServiceException("model down"));

        assertEquals(0, service.scoreOrDefault(5L, JobEntity.builder().title("SE").skills("Java").build()));
    }

    @Test
    void scoreOrThrow_aiFailure_propagates() {
        when(resumeParsingService.getParsedResume(5L)).thenReturn(ResumeParsedDataResponse.builder()
                .status("COMPLETED")
                .contextText("Java, Spring")
                .build());
        when(aiService.scoreResumeToJob(any(ResumeToJobScoreAiRequest.class)))
                .thenThrow(new AiServiceException("model down"));

        assertThrows(AiServiceException.class,
                () -> service.scoreOrThrow(5L, JobEntity.builder().title("SE").build()));
    }

    @Test
    void buildJobSnapshot_omitsNotesAndStatus() {
        JobEntity job = JobEntity.builder()
                .title("SE")
                .company("Acme")
                .notes("secret note")
                .description("Build APIs")
                .skills("Java")
                .build();

        String snapshot = service.buildJobSnapshot(job);

        assertTrue(snapshot.contains("title: SE"));
        assertTrue(snapshot.contains("company: Acme"));
        assertTrue(snapshot.contains("Java"));
        assertTrue(snapshot.contains("Build APIs"));
        org.junit.jupiter.api.Assertions.assertFalse(snapshot.contains("secret note"));
        org.junit.jupiter.api.Assertions.assertFalse(snapshot.toLowerCase().contains("applied"));
    }
}
