package com.developer.copilot.jobs.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.util.UrlNormalizationUtil;
import com.developer.copilot.jobs.dto.JobDraftResponse;
import com.developer.copilot.jobs.dto.JobDraftWrite;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.entity.JobDraftEntity;
import com.developer.copilot.jobs.entity.JobDraftFailureCategory;
import com.developer.copilot.jobs.exception.DuplicateJobException;
import com.developer.copilot.jobs.exception.JobDraftNotFoundException;
import com.developer.copilot.jobs.exception.JobDraftPersistenceException;
import com.developer.copilot.jobs.exception.JobValidationException;
import com.developer.copilot.jobs.mapper.JobDraftMapper;
import com.developer.copilot.jobs.repository.JobDraftRepository;
import com.developer.copilot.jobs.service.JobDraftStore;
import com.developer.copilot.jobs.service.JobService;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class JobDraftServiceImplTest {

    @Mock
    private JobDraftRepository jobDraftRepository;
    @Mock
    private JobDraftStore jobDraftStore;
    @Mock
    private JobService jobService;
    @Mock
    private CurrentUserService currentUserService;

    private JobDraftServiceImpl service;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new JobDraftServiceImpl(
                jobDraftRepository,
                jobDraftStore,
                new JobDraftMapper(),
                jobService,
                currentUserService,
                new UrlNormalizationUtil(),
                validator);
        User user = new User();
        user.setId(1L);
        lenient().when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void saveAsJob_deletesDraftOnlyAfterJobIsCreated() {
        JobDraftEntity draft = completeDraft();
        when(jobDraftRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(draft));
        when(jobService.createJob(any())).thenReturn(JobResponse.builder().id(9L).build());

        JobResponse saved = service.saveAsJob(5L);

        assertEquals(9L, saved.getId());
        InOrder order = inOrder(jobService, jobDraftRepository);
        order.verify(jobService).createJob(any());
        order.verify(jobDraftRepository).delete(draft);
    }

    @Test
    void saveAsJob_createFailure_leavesDraft() {
        when(jobDraftRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(completeDraft()));
        when(jobService.createJob(any()))
                .thenThrow(new DuplicateJobException("This post was already added to your records."));

        assertThrows(DuplicateJobException.class, () -> service.saveAsJob(5L));
        verify(jobDraftRepository, never()).delete(any());
    }

    @Test
    void saveAsJob_incompleteDraft_doesNotCreateOrDelete() {
        JobDraftEntity draft = completeDraft();
        draft.setTitle(null);
        when(jobDraftRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(draft));

        assertThrows(JobValidationException.class, () -> service.saveAsJob(5L));
        verify(jobService, never()).createJob(any());
        verify(jobDraftRepository, never()).delete(any());
    }

    @Test
    void get_otherUsersDraft_isNotFound() {
        when(jobDraftRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.empty());

        assertThrows(JobDraftNotFoundException.class, () -> service.get(5L));
        verify(jobDraftRepository).findByIdAndUserId(5L, 1L);
    }

    @Test
    void saveFallback_duplicateConstraint_retriesUpsert() {
        JobDraftEntity saved = completeDraft();
        saved.setId(8L);
        when(jobDraftStore.upsert(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry for key 'uk_job_draft_user_source_url_hash'"))
                .thenReturn(saved);

        JobDraftResponse response = service.saveFallback(JobDraftWrite.builder()
                .sourceUrl("https://example.com/jobs/1")
                .failureCategory(JobDraftFailureCategory.VALIDATION)
                .failureReason("title: Title cannot be blank.")
                .build());

        assertEquals(8L, response.getId());
        verify(jobDraftStore, times(2)).upsert(any());
    }

    @Test
    void saveFallback_otherPersistenceFailure_doesNotReturnDraft() {
        when(jobDraftStore.upsert(any())).thenThrow(new DataIntegrityViolationException("column too long"));

        assertThrows(JobDraftPersistenceException.class, () -> service.saveFallback(JobDraftWrite.builder()
                .sourceUrl("https://example.com/jobs/1")
                .build()));
        verify(jobDraftStore, times(1)).upsert(any());
    }

    @Test
    void deleteMatchingQuietly_swallowsCleanupFailure() {
        doThrow(new IllegalStateException("redis")).when(jobDraftStore).deleteByCanonicalUrl(any());

        service.deleteMatchingQuietly("https://example.com/jobs/1");
    }

    private static JobDraftEntity completeDraft() {
        JobDraftEntity draft = new JobDraftEntity();
        draft.setId(5L);
        draft.setSourceUrl("https://example.com/jobs/1");
        draft.setOriginalDescription("Pasted posting");
        draft.setTitle("Engineer");
        draft.setCompany("Acme");
        draft.setResume(12L);
        draft.setResumeToJobScore(80);
        return draft;
    }
}
