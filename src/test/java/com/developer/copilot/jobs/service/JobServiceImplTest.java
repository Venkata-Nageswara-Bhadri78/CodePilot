package com.developer.copilot.jobs.service;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.common.exception.InvalidJobUrlException;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.util.UrlNormalizationUtil;
import com.developer.copilot.jobs.dto.JobPatchRequest;
import com.developer.copilot.jobs.dto.JobRequest;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.dto.request.UpdateLocationRequest;
import com.developer.copilot.jobs.dto.request.UpdateSkillsRequest;
import com.developer.copilot.jobs.dto.request.UpdateSourceUrlRequest;
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.jobs.exception.DuplicateJobException;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.jobs.exception.JobValidationException;
import com.developer.copilot.jobs.mapper.JobMapper;
import com.developer.copilot.jobs.repository.JobRepository;
import com.developer.copilot.jobs.service.impl.JobServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceImplTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Spy
    private JobMapper jobMapper = new JobMapper();

    @Spy
    private UrlNormalizationUtil urlNormalizationUtil = new UrlNormalizationUtil();

    @InjectMocks
    private JobServiceImpl jobService;

    private User testUser;
    private JobEntity testJob;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");

        testJob = JobEntity.builder()
                .id(100L)
                .user(testUser)
                .title("Software Development Engineer I")
                .company("Amazon")
                .sourceUrl("https://amazon.jobs/en/jobs/12345")
                .sourceUrlHash(urlNormalizationUtil.sha256Hex("https://amazon.jobs/en/jobs/12345"))
                .originalDescription("Full pasted job posting text.")
                .location("Bengaluru, India")
                .employmentType("Full Time")
                .workMode("Hybrid")
                .industry("Information Technology")
                .sourcePlatform("LinkedIn")
                .skills(new ArrayList<>(List.of("Java", "Spring Boot")))
                .build();
    }

    private void stubCurrentUser() {
        when(currentUserService.getCurrentUser()).thenReturn(testUser);
    }

    private JobRequest baseCreateRequest() {
        return JobRequest.builder()
                .title("Software Development Engineer I")
                .company("Amazon")
                .sourceUrl("https://www.amazon.jobs/en/jobs/12345?utm_source=linkedin")
                .originalDescription("Full pasted job posting text.")
                .location("Bengaluru, India")
                .industry("Information Technology")
                .sourcePlatform("LinkedIn")
                .skills(List.of("Java", "Spring Boot"))
                .build();
    }

    @Test
    void createJob_stripsTrackingAndSetsHash() {
        stubCurrentUser();
        when(jobRepository.existsByUserIdAndSourceUrlHash(eq(1L), any())).thenReturn(false);
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(invocation -> {
            JobEntity job = invocation.getArgument(0);
            job.setId(100L);
            return job;
        });

        JobResponse response = jobService.createJob(baseCreateRequest());

        assertEquals(100L, response.getId());
        assertEquals("Amazon", response.getCompany());
        assertEquals("https://amazon.jobs/en/jobs/12345", response.getSourceUrl());
        verify(jobRepository).save(argThat(job -> job.getSourceUrlHash() != null
                && job.getSourceUrlHash().length() == 64));
        verify(currentUserService).getCurrentUser();
    }

    @Test
    void createJob_duplicateSourceUrl_rejected() {
        stubCurrentUser();
        when(jobRepository.existsByUserIdAndSourceUrlHash(eq(1L), any())).thenReturn(true);

        assertThrows(DuplicateJobException.class, () -> jobService.createJob(baseCreateRequest()));
        verify(jobRepository, never()).save(any(JobEntity.class));
    }

    @Test
    void createJob_javascriptUrl_rejected() {
        stubCurrentUser();
        JobRequest request = baseCreateRequest();
        request.setSourceUrl("javascript:alert(1)");

        assertThrows(InvalidJobUrlException.class, () -> jobService.createJob(request));
        verify(jobRepository, never()).save(any(JobEntity.class));
    }

    @Test
    void createJob_dataUrl_rejected() {
        stubCurrentUser();
        JobRequest request = baseCreateRequest();
        request.setSourceUrl("data:text/html,hello");

        assertThrows(InvalidJobUrlException.class, () -> jobService.createJob(request));
    }

    @Test
    void createJob_sameNormalizedUrlAsExistingHash_isDuplicate() {
        stubCurrentUser();
        when(jobRepository.existsByUserIdAndSourceUrlHash(eq(1L), any())).thenReturn(true);

        JobRequest request = baseCreateRequest();
        request.setSourceUrl("https://AMAZON.jobs/en/jobs/12345/");

        assertThrows(DuplicateJobException.class, () -> jobService.createJob(request));
    }

    @Test
    void createJob_integrityViolationOnUniqueConstraint_mapsToDuplicate() {
        stubCurrentUser();
        when(jobRepository.existsByUserIdAndSourceUrlHash(eq(1L), any())).thenReturn(false);
        when(jobRepository.save(any(JobEntity.class))).thenThrow(
                new DataIntegrityViolationException(
                        "Duplicate entry for key 'uk_job_user_source_url_hash'"));

        DuplicateJobException ex = assertThrows(DuplicateJobException.class,
                () -> jobService.createJob(baseCreateRequest()));
        assertEquals("This post was already added to your records.", ex.getMessage());
    }

    @Test
    void createJob_unrelatedIntegrityViolation_isRethrown() {
        stubCurrentUser();
        when(jobRepository.existsByUserIdAndSourceUrlHash(eq(1L), any())).thenReturn(false);
        when(jobRepository.save(any(JobEntity.class))).thenThrow(
                new DataIntegrityViolationException("Duplicate entry for key 'some_other_constraint'"));

        assertThrows(DataIntegrityViolationException.class, () -> jobService.createJob(baseCreateRequest()));
    }

    @Test
    void createJob_nullSkills_persistsEmptyList() {
        stubCurrentUser();
        when(jobRepository.existsByUserIdAndSourceUrlHash(eq(1L), any())).thenReturn(false);
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobRequest request = baseCreateRequest();
        request.setSkills(null);

        JobResponse response = jobService.createJob(request);
        assertNotNull(response.getSkills());
        assertTrue(response.getSkills().isEmpty());
    }

    @Test
    void getCurrentUser_unauthenticated_propagates() {
        when(currentUserService.getCurrentUser())
                .thenThrow(new InvalidCredentialsException("User is not authenticated."));

        assertThrows(InvalidCredentialsException.class, () -> jobService.getJobById(100L));
        verify(jobRepository, never()).findByIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void patchJob_blankMandatoryField_rejected() {
        stubCurrentUser();
        when(jobRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testJob));

        JobPatchRequest request = JobPatchRequest.builder()
                .title("")
                .build();

        assertThrows(JobValidationException.class, () -> jobService.patchJob(100L, request));
        verify(jobRepository, never()).save(any(JobEntity.class));
    }

    @Test
    void patchJob_onlySalaryChanges() {
        stubCurrentUser();
        when(jobRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobPatchRequest request = JobPatchRequest.builder()
                .salary("15-20 LPA")
                .build();

        JobResponse response = jobService.patchJob(100L, request);

        assertEquals("15-20 LPA", response.getSalary());
        assertEquals("Amazon", response.getCompany());
    }

    @Test
    void patchJob_omittingSkills_leavesList() {
        stubCurrentUser();
        when(jobRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobPatchRequest request = JobPatchRequest.builder()
                .salary("20 LPA")
                .build();

        JobResponse response = jobService.patchJob(100L, request);
        assertEquals(List.of("Java", "Spring Boot"), response.getSkills());
    }

    @Test
    void patchJob_explicitSkillsReplaceList() {
        stubCurrentUser();
        when(jobRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobPatchRequest request = JobPatchRequest.builder()
                .skills(List.of("Go"))
                .build();

        JobResponse response = jobService.patchJob(100L, request);
        assertEquals(List.of("Go"), response.getSkills());
    }

    @Test
    void getJobById_success() {
        stubCurrentUser();
        when(jobRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testJob));

        JobResponse response = jobService.getJobById(100L);

        assertEquals("Amazon", response.getCompany());
        assertEquals("Software Development Engineer I", response.getTitle());
    }

    @Test
    void getJobById_foreignId_throwsNotFound() {
        stubCurrentUser();
        when(jobRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        assertThrows(JobNotFoundException.class, () -> jobService.getJobById(999L));
    }

    @Test
    void updateJob_omittingSkills_clearsList() {
        stubCurrentUser();
        when(jobRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testJob));
        when(jobRepository.existsByUserIdAndSourceUrlHashAndIdNot(eq(1L), any(), eq(100L))).thenReturn(false);
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobRequest request = JobRequest.builder()
                .title("Software Development Engineer I")
                .company("Amazon")
                .sourceUrl("https://amazon.jobs/en/jobs/12345")
                .originalDescription("Full pasted job posting text.")
                .skills(null)
                .build();

        JobResponse response = jobService.updateJob(100L, request);
        assertTrue(response.getSkills().isEmpty());
    }

    @Test
    void updateJob_explicitEmptySkills_clearsList() {
        stubCurrentUser();
        when(jobRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testJob));
        when(jobRepository.existsByUserIdAndSourceUrlHashAndIdNot(eq(1L), any(), eq(100L))).thenReturn(false);
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobRequest request = JobRequest.builder()
                .title("Software Development Engineer I")
                .company("Amazon")
                .sourceUrl("https://amazon.jobs/en/jobs/12345")
                .originalDescription("Full pasted job posting text.")
                .skills(List.of())
                .build();

        JobResponse response = jobService.updateJob(100L, request);
        assertTrue(response.getSkills().isEmpty());
    }

    @Test
    void updateJob_duplicateUrlOfOtherJob_rejected() {
        stubCurrentUser();
        when(jobRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testJob));
        when(jobRepository.existsByUserIdAndSourceUrlHashAndIdNot(eq(1L), any(), eq(100L))).thenReturn(true);

        JobRequest request = JobRequest.builder()
                .title("Other")
                .company("Other")
                .sourceUrl("https://example.com/jobs/other")
                .originalDescription("text")
                .build();

        assertThrows(DuplicateJobException.class, () -> jobService.updateJob(100L, request));
        verify(jobRepository, never()).save(any(JobEntity.class));
    }

    @Test
    void updateLocation_success() {
        stubCurrentUser();
        when(jobRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateLocationRequest request = UpdateLocationRequest.builder()
                .location("Hyderabad, Telangana, India")
                .build();

        JobResponse response = jobService.updateLocation(100L, request);
        assertEquals("Hyderabad, Telangana, India", response.getLocation());
    }

    @Test
    void updateLocation_emptyString_clearsField() {
        stubCurrentUser();
        when(jobRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateLocationRequest request = UpdateLocationRequest.builder()
                .location("")
                .build();

        JobResponse response = jobService.updateLocation(100L, request);
        assertEquals("", response.getLocation());
    }

    @Test
    void getAllJobs_withSearch_escapesWildcardsAndUsesSearchRepository() {
        stubCurrentUser();
        Pageable pageable = PageRequest.of(0, 10);
        Page<JobEntity> page = new PageImpl<>(List.of(testJob));
        when(jobRepository.searchJobsByUserId(1L, "100\\%", pageable)).thenReturn(page);

        jobService.getAllJobs(" 100% ", pageable);

        verify(jobRepository).searchJobsByUserId(1L, "100\\%", pageable);
        verify(jobRepository, never()).findAllByUserId(anyLong(), any(Pageable.class));
    }

    @Test
    void getAllJobs_withoutSearch_usesFindAllRepository() {
        stubCurrentUser();
        Pageable pageable = PageRequest.of(0, 10);
        Page<JobEntity> page = new PageImpl<>(List.of(testJob));
        when(jobRepository.findAllByUserId(1L, pageable)).thenReturn(page);

        Page<?> result = jobService.getAllJobs(null, pageable);

        assertEquals(1, result.getContent().size());
        verify(jobRepository).findAllByUserId(1L, pageable);
        verify(jobRepository, never()).searchJobsByUserId(anyLong(), anyString(), any(Pageable.class));
    }

    @Test
    void getAllJobs_blankSearch_usesFindAllRepository() {
        stubCurrentUser();
        Pageable pageable = PageRequest.of(0, 10);
        when(jobRepository.findAllByUserId(1L, pageable)).thenReturn(Page.empty());

        jobService.getAllJobs("   ", pageable);

        verify(jobRepository).findAllByUserId(1L, pageable);
    }

    @Test
    void getAllJobs_emptyPage_whenUserHasNoJobs() {
        stubCurrentUser();
        Pageable pageable = PageRequest.of(0, 10);
        when(jobRepository.findAllByUserId(1L, pageable)).thenReturn(Page.empty());

        Page<?> result = jobService.getAllJobs(null, pageable);
        assertTrue(result.isEmpty());
    }

    @Test
    void getAllJobs_searchTooLong_rejected() {
        stubCurrentUser();
        Pageable pageable = PageRequest.of(0, 10);

        assertThrows(JobValidationException.class,
                () -> jobService.getAllJobs("x".repeat(101), pageable));
        verify(jobRepository, never()).searchJobsByUserId(anyLong(), anyString(), any(Pageable.class));
    }

    @Test
    void updateSourceUrl_success_recalculatesHash() {
        stubCurrentUser();
        when(jobRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testJob));
        when(jobRepository.existsByUserIdAndSourceUrlHashAndIdNot(eq(1L), any(), eq(100L))).thenReturn(false);
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSourceUrlRequest request = UpdateSourceUrlRequest.builder()
                .sourceUrl("https://www.amazon.jobs/en/jobs/99999?utm_source=linkedin")
                .build();

        JobResponse response = jobService.updateSourceUrl(100L, request);

        assertEquals("https://amazon.jobs/en/jobs/99999", response.getSourceUrl());
        verify(jobRepository).save(argThat(job ->
                "https://amazon.jobs/en/jobs/99999".equals(job.getSourceUrl())
                        && job.getSourceUrlHash() != null));
    }

    @Test
    void updateSkills_emptyList_clearsSkills() {
        stubCurrentUser();
        when(jobRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSkillsRequest request = UpdateSkillsRequest.builder()
                .skills(List.of())
                .build();

        JobResponse response = jobService.updateSkills(100L, request);
        assertTrue(response.getSkills().isEmpty());
    }

    @Test
    void deleteJob_withSkills_deletesEntityNotBulkJpql() {
        stubCurrentUser();
        when(jobRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testJob));

        assertDoesNotThrow(() -> jobService.deleteJob(100L));
        verify(jobRepository).delete(testJob);
        verify(jobRepository, never()).save(any(JobEntity.class));
    }

    @Test
    void deleteJob_notOwnedByUser_throwsNotFound() {
        stubCurrentUser();
        when(jobRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        assertThrows(JobNotFoundException.class, () -> jobService.deleteJob(999L));
        verify(jobRepository, never()).delete(any(JobEntity.class));
    }
}
