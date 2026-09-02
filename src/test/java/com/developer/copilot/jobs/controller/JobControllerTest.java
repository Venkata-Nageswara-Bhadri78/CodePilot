package com.developer.copilot.jobs.controller;

import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.exception.DuplicateJobException;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.jobs.service.JobService;
import com.developer.copilot.jobs.util.JobLimits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock
    private JobService jobService;

    @InjectMocks
    private JobController jobController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(jobController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createJob_missingTitle_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/1",
                                  "originalDescription": "Job description text",
                                  "company": "Acme Corp"
                                }
                                """))
                .andExpect(status().isBadRequest());
        verify(jobService, never()).createJob(any());
    }

    @Test
    void createJob_missingCompany_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/1",
                                  "originalDescription": "Job description text",
                                  "title": "Software Engineer"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createJob_oversizedSkill_returns400() throws Exception {
        String oversizedSkill = "x".repeat(256);

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/1",
                                  "originalDescription": "Job description text",
                                  "title": "Software Engineer",
                                  "company": "Acme Corp",
                                  "skills": ["Java", "%s"]
                                }
                                """.formatted(oversizedSkill)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createJob_sourceUrlOver2000_returns400() throws Exception {
        String oversizedUrl = "https://example.com/" + "a".repeat(2000);

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "%s",
                                  "originalDescription": "Job description text",
                                  "title": "Software Engineer",
                                  "company": "Acme Corp"
                                }
                                """.formatted(oversizedUrl)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createJob_oversizedOriginalDescription_returns400() throws Exception {
        String oversized = "x".repeat(JobLimits.MAX_DESCRIPTION_LENGTH + 1);

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/1",
                                  "originalDescription": "%s",
                                  "title": "Software Engineer",
                                  "company": "Acme Corp"
                                }
                                """.formatted(oversized)))
                .andExpect(status().isBadRequest());
        verify(jobService, never()).createJob(any());
    }

    @Test
    void createJob_titleOver255_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/1",
                                  "originalDescription": "Job description text",
                                  "title": "%s",
                                  "company": "Acme Corp"
                                }
                                """.formatted("t".repeat(256))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createJob_duplicate_returns409() throws Exception {
        when(jobService.createJob(any())).thenThrow(
                new DuplicateJobException("This post was already added to your records."));

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/1",
                                  "originalDescription": "Job description text",
                                  "title": "Software Engineer",
                                  "company": "Acme Corp"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("This post was already added to your records."));
    }

    @Test
    void getJobById_notFound_returns404() throws Exception {
        when(jobService.getJobById(42L)).thenThrow(new JobNotFoundException("Job not found with id: 42"));

        mockMvc.perform(get("/api/v1/jobs/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Job not found with id: 42"));
    }

    @Test
    void updateSalary_oversizedValue_returns400() throws Exception {
        String oversizedSalary = "x".repeat(500);

        mockMvc.perform(patch("/api/v1/jobs/1/salary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "salary": "%s"
                                }
                                """.formatted(oversizedSalary)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTitle_blank_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/jobs/1/title")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "  "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateLocation_emptyString_isAccepted() throws Exception {
        when(jobService.updateLocation(eq(1L), any())).thenReturn(JobResponse.builder().id(1L).location("").build());

        mockMvc.perform(patch("/api/v1/jobs/1/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "location": ""
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void updateSkills_emptyList_isAccepted() throws Exception {
        when(jobService.updateSkills(eq(1L), any())).thenReturn(JobResponse.builder().id(1L).skills(java.util.List.of()).build());

        mockMvc.perform(patch("/api/v1/jobs/1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skills": []
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void getAllJobs_invalidSortBy_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .param("sortBy", "invalidColumn"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid sort field")));
    }

    @Test
    void getAllJobs_salarySort_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .param("sortBy", "salary"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid sort field")));
    }

    @Test
    void getAllJobs_sortByUserPassword_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .param("sortBy", "user.password"))
                .andExpect(status().isBadRequest());
        verify(jobService, never()).getAllJobs(any(), any());
    }

    @Test
    void getAllJobs_sortBySourceUrlHash_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .param("sortBy", "sourceUrlHash"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllJobs_maxIntSize_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .param("size", "2147483647"))
                .andExpect(status().isBadRequest());
        verify(jobService, never()).getAllJobs(any(), any());
    }

    @Test
    void getAllJobs_sizeTooLarge_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .param("size", "50000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("size must be between")));
        verify(jobService, never()).getAllJobs(any(), any());
    }

    @Test
    void getAllJobs_sizeZero_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllJobs_negativePage_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllJobs_searchTooLong_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .param("search", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("search cannot exceed")));
    }

    @Test
    void getAllJobs_validTitleSortAsc_reachesService() throws Exception {
        when(jobService.getAllJobs(any(), any())).thenReturn(
                new org.springframework.data.domain.PageImpl<>(
                        java.util.List.of(),
                        org.springframework.data.domain.PageRequest.of(0, 10),
                        0));

        mockMvc.perform(get("/api/v1/jobs")
                        .param("sortBy", "title")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk());
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed JSON."));
    }
}
