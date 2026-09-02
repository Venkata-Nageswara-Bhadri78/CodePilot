package com.developer.copilot.jobs.service;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.util.UrlNormalizationUtil;
import com.developer.copilot.jobs.dto.JobPatchRequest;
import com.developer.copilot.jobs.dto.JobRequest;
import com.developer.copilot.jobs.dto.request.UpdateCompanyRequest;
import com.developer.copilot.jobs.dto.request.UpdateDepartmentRequest;
import com.developer.copilot.jobs.dto.request.UpdateDescriptionRequest;
import com.developer.copilot.jobs.dto.request.UpdateEducationRequest;
import com.developer.copilot.jobs.dto.request.UpdateEmploymentTypeRequest;
import com.developer.copilot.jobs.dto.request.UpdateExperienceRequest;
import com.developer.copilot.jobs.dto.request.UpdateIndustryRequest;
import com.developer.copilot.jobs.dto.request.UpdateLocationRequest;
import com.developer.copilot.jobs.dto.request.UpdateOriginalDescriptionRequest;
import com.developer.copilot.jobs.dto.request.UpdateSalaryRequest;
import com.developer.copilot.jobs.dto.request.UpdateSkillsRequest;
import com.developer.copilot.jobs.dto.request.UpdateSourcePlatformRequest;
import com.developer.copilot.jobs.dto.request.UpdateSourceUrlRequest;
import com.developer.copilot.jobs.dto.request.UpdateTitleRequest;
import com.developer.copilot.jobs.dto.request.UpdateWorkModeRequest;
import com.developer.copilot.jobs.exception.JobNotFoundException;
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
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Foreign job ids must look like missing rows (404), never 403 with a payload.
 */
@ExtendWith(MockitoExtension.class)
class JobOwnershipIsolationTest {

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

    @BeforeEach
    void attackerIsUserTwo() {
        User attacker = new User();
        attacker.setId(2L);
        attacker.setEmail("attacker@example.com");
        when(currentUserService.getCurrentUser()).thenReturn(attacker);
        lenient().when(jobRepository.findByIdAndUserId(100L, 2L)).thenReturn(Optional.empty());
    }

    @Test
    void foreignId_isNotFound_onEveryOwnedOperation() {
        JobRequest replace = JobRequest.builder()
                .title("x")
                .company("y")
                .sourceUrl("https://example.com/jobs/1")
                .originalDescription("text")
                .build();

        assertThrows(JobNotFoundException.class, () -> jobService.getJobById(100L));
        assertThrows(JobNotFoundException.class, () -> jobService.deleteJob(100L));
        assertThrows(JobNotFoundException.class, () -> jobService.updateJob(100L, replace));
        assertThrows(JobNotFoundException.class, () -> jobService.patchJob(100L, JobPatchRequest.builder().salary("1").build()));
        assertThrows(JobNotFoundException.class, () -> jobService.updateLocation(100L, UpdateLocationRequest.builder().location("x").build()));
        assertThrows(JobNotFoundException.class, () -> jobService.updateTitle(100L, UpdateTitleRequest.builder().title("x").build()));
        assertThrows(JobNotFoundException.class, () -> jobService.updateCompany(100L, UpdateCompanyRequest.builder().company("x").build()));
        assertThrows(JobNotFoundException.class, () -> jobService.updateEmploymentType(100L, UpdateEmploymentTypeRequest.builder().employmentType("x").build()));
        assertThrows(JobNotFoundException.class, () -> jobService.updateWorkMode(100L, UpdateWorkModeRequest.builder().workMode("x").build()));
        assertThrows(JobNotFoundException.class, () -> jobService.updateExperience(100L, UpdateExperienceRequest.builder().experience("x").build()));
        assertThrows(JobNotFoundException.class, () -> jobService.updateSalary(100L, UpdateSalaryRequest.builder().salary("x").build()));
        assertThrows(JobNotFoundException.class, () -> jobService.updateEducation(100L, UpdateEducationRequest.builder().education("x").build()));
        assertThrows(JobNotFoundException.class, () -> jobService.updateDepartment(100L, UpdateDepartmentRequest.builder().department("x").build()));
        assertThrows(JobNotFoundException.class, () -> jobService.updateIndustry(100L, UpdateIndustryRequest.builder().industry("x").build()));
        assertThrows(JobNotFoundException.class, () -> jobService.updateSourcePlatform(100L, UpdateSourcePlatformRequest.builder().sourcePlatform("x").build()));
        assertThrows(JobNotFoundException.class, () -> jobService.updateSourceUrl(100L, UpdateSourceUrlRequest.builder().sourceUrl("https://example.com/jobs/2").build()));
        assertThrows(JobNotFoundException.class, () -> jobService.updateSkills(100L, UpdateSkillsRequest.builder().skills(List.of("Java")).build()));
        assertThrows(JobNotFoundException.class, () -> jobService.updateDescription(100L, UpdateDescriptionRequest.builder().description("x").build()));
        assertThrows(JobNotFoundException.class, () -> jobService.updateOriginalDescription(100L, UpdateOriginalDescriptionRequest.builder().originalDescription("x").build()));

        verify(jobRepository, never()).save(any());
        verify(jobRepository, never()).delete(any());
        verify(jobRepository, never()).findById(anyLong());
    }

    @Test
    void list_onlyQueriesTheCallerUserId() {
        when(jobRepository.findAllByUserId(2L, PageRequest.of(0, 10)))
                .thenReturn(org.springframework.data.domain.Page.empty(PageRequest.of(0, 10)));

        jobService.getAllJobs(null, PageRequest.of(0, 10));

        verify(jobRepository).findAllByUserId(2L, PageRequest.of(0, 10));
        verify(jobRepository, never()).findAllByUserId(org.mockito.ArgumentMatchers.eq(1L), any());
    }
}
