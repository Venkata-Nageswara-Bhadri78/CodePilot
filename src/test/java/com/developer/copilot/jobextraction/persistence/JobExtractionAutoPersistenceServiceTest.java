package com.developer.copilot.jobextraction.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.util.UrlNormalizationUtil;
import com.developer.copilot.jobextraction.automatedjobextraction.dto.request.AutomatedJobExtractionRequest;
import com.developer.copilot.jobextraction.automatedjobextraction.exception.RecoverableAutomatedExtractionException;
import com.developer.copilot.jobextraction.automatedjobextraction.service.AutomatedJobExtractionService;
import com.developer.copilot.jobextraction.manualextraction.dto.request.JobExtractionRequest;
import com.developer.copilot.jobextraction.manualextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.manualextraction.exception.JobExtractionAiUnavailableException;
import com.developer.copilot.jobextraction.manualextraction.service.JobExtractionService;
import com.developer.copilot.jobs.dto.JobDraftResponse;
import com.developer.copilot.jobs.dto.JobDraftWrite;
import com.developer.copilot.jobs.dto.JobRequest;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.entity.JobDraftFailureCategory;
import com.developer.copilot.jobs.exception.DuplicateJobException;
import com.developer.copilot.jobs.exception.JobDraftPersistenceException;
import com.developer.copilot.jobs.service.JobDraftService;
import com.developer.copilot.jobs.service.JobService;
import com.developer.copilot.user.exception.ResumeNotFoundException;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class JobExtractionAutoPersistenceServiceTest {

    @Mock
    private JobExtractionService jobExtractionService;
    @Mock
    private AutomatedJobExtractionService automatedJobExtractionService;
    @Mock
    private JobService jobService;
    @Mock
    private JobDraftService jobDraftService;
    @Mock
    private CurrentUserService currentUserService;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final UrlNormalizationUtil urlNormalizationUtil = new UrlNormalizationUtil();
    private JobExtractionAutoPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new JobExtractionAutoPersistenceService(
                jobExtractionService,
                automatedJobExtractionService,
                jobService,
                jobDraftService,
                validator,
                urlNormalizationUtil,
                currentUserService);
        User user = new User();
        user.setId(1L);
        lenient().when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void validExtraction_savesJobAndClearsMatchingDraft() {
        when(jobExtractionService.extractJobInfo(any())).thenReturn(completeResult());
        when(jobService.createJob(any())).thenReturn(JobResponse.builder().id(9L).title("Engineer").build());

        JobAutoPersistResponse outcome = service.persistManual(manualRequest());

        assertEquals(PersistedAs.JOB, outcome.getPersistedAs());
        assertEquals(9L, outcome.getJob().getId());
        ArgumentCaptor<JobRequest> captor = ArgumentCaptor.forClass(JobRequest.class);
        verify(jobService).createJob(captor.capture());
        assertEquals("Engineer", captor.getValue().getTitle());
        assertEquals(12L, captor.getValue().getResume());
        assertEquals(80, captor.getValue().getResumeToJobScore());
        verify(jobDraftService).deleteMatchingQuietly("https://example.com/jobs/1");
        verify(jobDraftService, never()).saveFallback(any());
    }

    @Test
    void blankTitle_storesValidationDraftAndDoesNotCreateJob() {
        JobExtractionResultResponse result = completeResult();
        result.setTitle(" ");
        when(jobExtractionService.extractJobInfo(any())).thenReturn(result);
        when(jobDraftService.saveFallback(any())).thenReturn(JobDraftResponse.builder().id(4L).build());

        JobAutoPersistResponse outcome = service.persistManual(manualRequest());

        assertEquals(PersistedAs.DRAFT, outcome.getPersistedAs());
        verify(jobService, never()).createJob(any());
        ArgumentCaptor<JobDraftWrite> captor = ArgumentCaptor.forClass(JobDraftWrite.class);
        verify(jobDraftService).saveFallback(captor.capture());
        assertEquals(JobDraftFailureCategory.VALIDATION, captor.getValue().getFailureCategory());
        assertEquals("https://example.com/jobs/1", captor.getValue().getSourceUrl());
    }

    @Test
    void duplicateJob_doesNotCreateDraft() {
        when(jobExtractionService.extractJobInfo(any()))
                .thenThrow(new DuplicateJobException("This post was already added to your records."));

        DuplicateJobException ex = assertThrows(DuplicateJobException.class, () -> service.persistManual(manualRequest()));

        assertEquals("This post was already added to your records.", ex.getMessage());
        verify(jobDraftService, never()).saveFallback(any());
        verify(jobService, never()).createJob(any());
    }

    @Test
    void persistenceConflict_storesDraft() {
        when(jobExtractionService.extractJobInfo(any())).thenReturn(completeResult());
        when(jobService.createJob(any())).thenThrow(new DataIntegrityViolationException("constraint"));
        when(jobDraftService.saveFallback(any())).thenReturn(JobDraftResponse.builder()
                .id(5L)
                .failureReason(JobExtractionAutoPersistenceService.PERSISTENCE_REASON)
                .build());

        JobAutoPersistResponse outcome = service.persistManual(manualRequest());

        assertEquals(PersistedAs.DRAFT, outcome.getPersistedAs());
        ArgumentCaptor<JobDraftWrite> captor = ArgumentCaptor.forClass(JobDraftWrite.class);
        verify(jobDraftService).saveFallback(captor.capture());
        assertEquals(JobDraftFailureCategory.PERSISTENCE, captor.getValue().getFailureCategory());
        assertEquals(JobExtractionAutoPersistenceService.PERSISTENCE_REASON, captor.getValue().getFailureReason());
    }

    @Test
    void draftSaveFailure_doesNotClaimSuccess() {
        when(jobExtractionService.extractJobInfo(any())).thenReturn(completeResult());
        when(jobService.createJob(any())).thenThrow(new DataIntegrityViolationException("constraint"));
        when(jobDraftService.saveFallback(any()))
                .thenThrow(new JobDraftPersistenceException(new IllegalStateException("disk")));

        JobDraftPersistenceException ex = assertThrows(
                JobDraftPersistenceException.class, () -> service.persistManual(manualRequest()));

        assertEquals(JobDraftPersistenceException.MESSAGE, ex.getMessage());
    }

    @Test
    void aiFailure_storesExternalDraftWithRawText() {
        AiServiceException aiFailure = new AiServiceException("provider exploded");
        when(jobExtractionService.extractJobInfo(any())).thenThrow(aiFailure);
        when(jobDraftService.saveFallback(any())).thenReturn(JobDraftResponse.builder()
                .id(6L)
                .failureReason(JobExtractionAutoPersistenceService.EXTERNAL_REASON)
                .build());

        JobAutoPersistResponse outcome = service.persistManual(JobExtractionRequest.builder()
                .sourceUrl("https://example.com/jobs/1?utm_source=x")
                .rawJobText("Pasted posting")
                .resume(12L)
                .build());

        assertEquals(PersistedAs.DRAFT, outcome.getPersistedAs());
        ArgumentCaptor<JobDraftWrite> captor = ArgumentCaptor.forClass(JobDraftWrite.class);
        verify(jobDraftService).saveFallback(captor.capture());
        assertEquals(JobDraftFailureCategory.EXTERNAL, captor.getValue().getFailureCategory());
        assertEquals("https://example.com/jobs/1", captor.getValue().getSourceUrl());
        assertEquals("Pasted posting", captor.getValue().getOriginalDescription());
        assertEquals(12L, captor.getValue().getResume());
        verify(jobService, never()).createJob(any());
    }

    @Test
    void aiFailureAndDraftFailure_rethrowsOriginalAiError() {
        AiServiceException aiFailure = new AiServiceException("provider exploded");
        when(jobExtractionService.extractJobInfo(any())).thenThrow(aiFailure);
        when(jobDraftService.saveFallback(any())).thenThrow(new JobDraftPersistenceException(new IllegalStateException("disk")));

        AiServiceException thrown = assertThrows(AiServiceException.class, () -> service.persistManual(manualRequest()));

        assertSame(aiFailure, thrown);
    }

    @Test
    void aiUnavailableAndDraftFailure_rethrowsUnavailable() {
        JobExtractionAiUnavailableException unavailable =
                new JobExtractionAiUnavailableException("The AI service is temporarily unavailable. Please try again shortly.");
        when(jobExtractionService.extractJobInfo(any())).thenThrow(unavailable);
        when(jobDraftService.saveFallback(any())).thenThrow(new IllegalStateException("disk"));

        assertSame(unavailable, assertThrows(
                JobExtractionAiUnavailableException.class, () -> service.persistManual(manualRequest())));
    }

    @Test
    void foreignResume_doesNotCreateDraft() {
        when(jobExtractionService.extractJobInfo(any())).thenThrow(new ResumeNotFoundException());

        assertThrows(ResumeNotFoundException.class, () -> service.persistManual(manualRequest()));
        verify(jobDraftService, never()).saveFallback(any());
    }

    @Test
    void automatedAiFailure_storesExtractedText() {
        when(automatedJobExtractionService.extractForAutoSave(any())).thenThrow(
                new RecoverableAutomatedExtractionException(
                        "https://example.com/jobs/9",
                        "Job Title: Engineer\nBuild APIs",
                        4L,
                        new AiServiceException("bad json")));
        when(jobDraftService.saveFallback(any())).thenReturn(JobDraftResponse.builder().id(2L).build());

        JobAutoPersistResponse outcome = service.persistAutomated(AutomatedJobExtractionRequest.builder()
                .sourceUrl("https://example.com/jobs/9")
                .resume(4L)
                .build());

        assertEquals(PersistedAs.DRAFT, outcome.getPersistedAs());
        ArgumentCaptor<JobDraftWrite> captor = ArgumentCaptor.forClass(JobDraftWrite.class);
        verify(jobDraftService).saveFallback(captor.capture());
        assertEquals("Job Title: Engineer\nBuild APIs", captor.getValue().getOriginalDescription());
        assertEquals(4L, captor.getValue().getResume());
        assertEquals(JobDraftFailureCategory.EXTERNAL, captor.getValue().getFailureCategory());
    }

    private static JobExtractionRequest manualRequest() {
        return JobExtractionRequest.builder()
                .sourceUrl("https://example.com/jobs/1")
                .rawJobText("Pasted posting")
                .resume(12L)
                .isAuto(true)
                .build();
    }

    private static JobExtractionResultResponse completeResult() {
        return JobExtractionResultResponse.builder()
                .sourceUrl("https://example.com/jobs/1")
                .originalDescription("Pasted posting")
                .description("Build APIs")
                .title("Engineer")
                .company("Acme")
                .resume(12L)
                .resumeToJobScore(80)
                .skills("Java")
                .build();
    }
}
