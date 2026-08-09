package com.developer.copilot.jobs.service;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.common.util.UrlNormalizationUtil;
import com.developer.copilot.jobs.dto.JobPatchRequest;
import com.developer.copilot.jobs.dto.JobRequest;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.dto.request.UpdateLocationRequest;
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.jobs.exception.DuplicateJobException;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.jobs.exception.JobValidationException;
import com.developer.copilot.jobs.mapper.JobMapper;
import com.developer.copilot.jobs.repository.JobRepository;
import com.developer.copilot.jobs.service.impl.JobServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceImplTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private UserRepository userRepository;

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

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("test@example.com", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        testJob = JobEntity.builder()
                .id(100L)
                .user(testUser)
                .title("Software Development Engineer I")
                .company("Amazon")
                .sourceUrl("https://www.amazon.jobs/en/jobs/12345")
                .sourceUrlHash(urlNormalizationUtil.sha256Hex("https://amazon.jobs/en/jobs/12345"))
                .originalDescription("Full pasted job posting text.")
                .location("Bengaluru, India")
                .employmentType("Full Time")
                .workMode("Hybrid")
                .industry("Information Technology")
                .sourcePlatform("LinkedIn")
                .skills(List.of("Java", "Spring Boot"))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testCreateJob() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jobRepository.existsByUserIdAndSourceUrlHash(eq(1L), any())).thenReturn(false);
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(invocation -> {
            JobEntity job = invocation.getArgument(0);
            job.setId(100L);
            return job;
        });

        JobRequest request = JobRequest.builder()
                .title("Software Development Engineer I")
                .company("Amazon")
                .sourceUrl("https://www.amazon.jobs/en/jobs/12345?utm_source=linkedin")
                .originalDescription("Full pasted job posting text.")
                .location("Bengaluru, India")
                .industry("Information Technology")
                .sourcePlatform("LinkedIn")
                .skills(List.of("Java", "Spring Boot"))
                .build();

        JobResponse response = jobService.createJob(request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("Amazon", response.getCompany());
        assertEquals("Information Technology", response.getIndustry());
        assertEquals("LinkedIn", response.getSourcePlatform());
        // Tracking parameter must be stripped by normalization.
        assertEquals("https://amazon.jobs/en/jobs/12345", response.getSourceUrl());
        verify(jobRepository, times(1)).save(any(JobEntity.class));
    }

    @Test
    void testCreateJob_DuplicateSourceUrl_Rejected() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jobRepository.existsByUserIdAndSourceUrlHash(eq(1L), any())).thenReturn(true);

        JobRequest request = JobRequest.builder()
                .title("Software Development Engineer I")
                .company("Amazon")
                .sourceUrl("https://amazon.jobs/en/jobs/12345")
                .originalDescription("Full pasted job posting text.")
                .build();

        assertThrows(DuplicateJobException.class, () -> jobService.createJob(request));
        verify(jobRepository, never()).save(any(JobEntity.class));
    }

    @Test
    void testPatchJob_BlankMandatoryField_Rejected() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jobRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testJob));

        JobPatchRequest request = JobPatchRequest.builder()
                .title("")
                .build();

        assertThrows(JobValidationException.class, () -> jobService.patchJob(100L, request));
        verify(jobRepository, never()).save(any(JobEntity.class));
    }

    @Test
    void testGetJobById_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jobRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testJob));

        JobResponse response = jobService.getJobById(100L);

        assertNotNull(response);
        assertEquals("Amazon", response.getCompany());
        assertEquals("Software Development Engineer I", response.getTitle());
    }

    @Test
    void testGetJobById_NotFound() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jobRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        assertThrows(JobNotFoundException.class, () -> jobService.getJobById(999L));
    }

    @Test
    void testUpdateLocation() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jobRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateLocationRequest request = UpdateLocationRequest.builder()
                .location("Hyderabad, Telangana, India")
                .build();

        JobResponse response = jobService.updateLocation(100L, request);

        assertNotNull(response);
        assertEquals("Hyderabad, Telangana, India", response.getLocation());
    }

    @Test
    void testPatchJob() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jobRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobPatchRequest request = JobPatchRequest.builder()
                .salary("15-20 LPA")
                .build();

        JobResponse response = jobService.patchJob(100L, request);

        assertNotNull(response);
        assertEquals("15-20 LPA", response.getSalary());
        assertEquals("Amazon", response.getCompany()); // Unchanged field preserved
    }

    @Test
    void testDeleteJob() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jobRepository.existsByIdAndUserId(100L, 1L)).thenReturn(true);
        doNothing().when(jobRepository).deleteByIdAndUserId(100L, 1L);

        assertDoesNotThrow(() -> jobService.deleteJob(100L));
        verify(jobRepository, times(1)).deleteByIdAndUserId(100L, 1L);
    }
}
