package com.developer.copilot.jobextraction.service;

import com.developer.copilot.ai.dto.request.JobExtractionAiRequest;
import com.developer.copilot.ai.dto.response.JobExtractionAiResponse;
import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.ai.service.AiService;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.enums.Role;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.common.exception.InvalidJobUrlException;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.util.UrlNormalizationUtil;
import com.developer.copilot.jobextraction.dto.request.JobExtractionRequest;
import com.developer.copilot.jobextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.mapper.JobExtractionMapper;
import com.developer.copilot.jobextraction.service.impl.JobExtractionServiceImpl;
import com.developer.copilot.jobs.exception.DuplicateJobException;
import com.developer.copilot.jobs.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobExtractionServiceImplTest {

    @Spy
    private UrlNormalizationUtil urlNormalizationUtil = new UrlNormalizationUtil();

    @Mock
    private JobRepository jobRepository;

    @Mock
    private AiService aiService;

    @Spy
    private JobExtractionMapper jobExtractionMapper = new JobExtractionMapper();

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private JobExtractionServiceImpl jobExtractionService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setEmailVerified(true);
        testUser.setEnabled(true);
        testUser.setRole(Role.USER);
    }

    private void mockAuthenticatedUser() {
        when(currentUserService.getCurrentUser()).thenReturn(testUser);
    }

    @Test
    void extractJobInfo_HappyPath_ReturnsMappedResult() {
        mockAuthenticatedUser();
        when(jobRepository.existsByUserIdAndSourceUrlHash(eq(1L), any())).thenReturn(false);

        JobExtractionAiResponse aiResponse = JobExtractionAiResponse.builder()
                .title("Senior Full Stack Engineer")
                .company("Stripe")
                .skills(List.of("React", "Node.js"))
                .build();
        when(aiService.extractJobInfo(any(JobExtractionAiRequest.class))).thenReturn(aiResponse);

        JobExtractionRequest request = JobExtractionRequest.builder()
                .sourceUrl("https://stripe.com/jobs/senior-engineer?utm_source=linkedin")
                .rawJobText("Full pasted job posting text.")
                .build();

        JobExtractionResultResponse result = jobExtractionService.extractJobInfo(request);

        assertNotNull(result);
        assertEquals("Senior Full Stack Engineer", result.getTitle());
        assertEquals("Stripe", result.getCompany());
        assertEquals("https://stripe.com/jobs/senior-engineer", result.getSourceUrl());
        assertEquals("Full pasted job posting text.", result.getOriginalDescription());
        assertEquals(List.of("React", "Node.js"), result.getSkills());
        assertFalse(result.isRequiresManualReview());

        verify(aiService, times(1)).extractJobInfo(any(JobExtractionAiRequest.class));
    }

    @Test
    void extractJobInfo_TrackingParamsStripped_CorrectHashPassedToRepository() {
        mockAuthenticatedUser();
        when(jobRepository.existsByUserIdAndSourceUrlHash(eq(1L), any())).thenReturn(false);
        when(aiService.extractJobInfo(any(JobExtractionAiRequest.class)))
                .thenReturn(JobExtractionAiResponse.builder().title("T").company("C").build());

        String expectedNormalizedUrl = "https://stripe.com/jobs/senior-engineer";
        String expectedHash = urlNormalizationUtil.sha256Hex(expectedNormalizedUrl);

        JobExtractionRequest request = JobExtractionRequest.builder()
                .sourceUrl("https://www.stripe.com/jobs/senior-engineer?utm_source=linkedin&utm_medium=social")
                .rawJobText("Full pasted job posting text.")
                .build();

        jobExtractionService.extractJobInfo(request);

        verify(jobRepository).existsByUserIdAndSourceUrlHash(1L, expectedHash);
    }

    @Test
    void extractJobInfo_AiReturnsNullSkills_MapsToEmptyList() {
        mockAuthenticatedUser();
        when(jobRepository.existsByUserIdAndSourceUrlHash(eq(1L), any())).thenReturn(false);
        when(aiService.extractJobInfo(any(JobExtractionAiRequest.class)))
                .thenReturn(JobExtractionAiResponse.builder().title("T").company("C").skills(null).build());

        JobExtractionRequest request = JobExtractionRequest.builder()
                .sourceUrl("https://stripe.com/jobs/senior-engineer")
                .rawJobText("Full pasted job posting text.")
                .build();

        JobExtractionResultResponse result = jobExtractionService.extractJobInfo(request);

        assertNotNull(result.getSkills());
        assertTrue(result.getSkills().isEmpty());
    }

    @Test
    void extractJobInfo_AiReturnsBlankTitleAndCompany_RequiresManualReview() {
        mockAuthenticatedUser();
        when(jobRepository.existsByUserIdAndSourceUrlHash(eq(1L), any())).thenReturn(false);
        when(aiService.extractJobInfo(any(JobExtractionAiRequest.class)))
                .thenReturn(JobExtractionAiResponse.builder().title("").company("").build());

        JobExtractionRequest request = JobExtractionRequest.builder()
                .sourceUrl("https://stripe.com/jobs/senior-engineer")
                .rawJobText("Full pasted job posting text.")
                .build();

        JobExtractionResultResponse result = jobExtractionService.extractJobInfo(request);

        assertTrue(result.isRequiresManualReview());
    }

    @Test
    void extractJobInfo_InvalidUrl_RejectedBeforeCallingAi() {
        mockAuthenticatedUser();

        JobExtractionRequest request = JobExtractionRequest.builder()
                .sourceUrl("not-a-valid-url")
                .rawJobText("Full pasted job posting text.")
                .build();

        assertThrows(InvalidJobUrlException.class, () -> jobExtractionService.extractJobInfo(request));
        verify(aiService, never()).extractJobInfo(any());
    }

    @Test
    void extractJobInfo_Duplicate_RejectedBeforeCallingAi() {
        mockAuthenticatedUser();
        when(jobRepository.existsByUserIdAndSourceUrlHash(eq(1L), any())).thenReturn(true);

        JobExtractionRequest request = JobExtractionRequest.builder()
                .sourceUrl("https://stripe.com/jobs/senior-engineer")
                .rawJobText("Full pasted job posting text.")
                .build();

        assertThrows(DuplicateJobException.class, () -> jobExtractionService.extractJobInfo(request));
        verify(aiService, never()).extractJobInfo(any());
    }

    @Test
    void extractJobInfo_AiFailure_PropagatesException() {
        mockAuthenticatedUser();
        when(jobRepository.existsByUserIdAndSourceUrlHash(eq(1L), any())).thenReturn(false);
        when(aiService.extractJobInfo(any(JobExtractionAiRequest.class)))
                .thenThrow(new AiServiceException("AI provider unavailable."));

        JobExtractionRequest request = JobExtractionRequest.builder()
                .sourceUrl("https://stripe.com/jobs/senior-engineer")
                .rawJobText("Full pasted job posting text.")
                .build();

        assertThrows(AiServiceException.class, () -> jobExtractionService.extractJobInfo(request));
    }

    @Test
    void extractJobInfo_UnauthenticatedUser_Rejected() {
        when(currentUserService.getCurrentUser())
                .thenThrow(new InvalidCredentialsException("User is not authenticated."));

        JobExtractionRequest request = JobExtractionRequest.builder()
                .sourceUrl("https://stripe.com/jobs/senior-engineer")
                .rawJobText("Full pasted job posting text.")
                .build();

        assertThrows(InvalidCredentialsException.class, () -> jobExtractionService.extractJobInfo(request));
        verify(aiService, never()).extractJobInfo(any());
    }

    @Test
    void extractJobInfo_UnverifiedEmail_Rejected() {
        testUser.setEmailVerified(false);
        mockAuthenticatedUser();

        JobExtractionRequest request = JobExtractionRequest.builder()
                .sourceUrl("https://stripe.com/jobs/senior-engineer")
                .rawJobText("Full pasted job posting text.")
                .build();

        assertThrows(InvalidCredentialsException.class, () -> jobExtractionService.extractJobInfo(request));
        verify(aiService, never()).extractJobInfo(any());
        verify(jobRepository, never()).existsByUserIdAndSourceUrlHash(any(), any());
    }
}
