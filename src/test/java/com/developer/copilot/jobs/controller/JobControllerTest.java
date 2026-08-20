package com.developer.copilot.jobs.controller;

import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.jobs.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
    void getAllJobs_invalidSortBy_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .param("sortBy", "invalidColumn"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid sort field")));
    }
}
