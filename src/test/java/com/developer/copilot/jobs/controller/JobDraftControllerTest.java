package com.developer.copilot.jobs.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.jobs.dto.JobDraftResponse;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.exception.JobDraftNotFoundException;
import com.developer.copilot.jobs.exception.JobValidationException;
import com.developer.copilot.jobs.service.JobDraftService;

@ExtendWith(MockitoExtension.class)
class JobDraftControllerTest {

    @Mock
    private JobDraftService jobDraftService;

    @InjectMocks
    private JobDraftController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getDraft_missing_returns404() throws Exception {
        when(jobDraftService.get(4L)).thenThrow(new JobDraftNotFoundException("Job draft not found with id: 4"));

        mockMvc.perform(get("/api/v1/job-drafts/4"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Job draft not found with id: 4"));
    }

    @Test
    void listDrafts_returnsPage() throws Exception {
        when(jobDraftService.list(any())).thenReturn(new PageImpl<>(
                java.util.List.of(JobDraftResponse.builder().id(1L).title("Engineer").build()),
                org.springframework.data.domain.PageRequest.of(0, 10),
                1));

        mockMvc.perform(get("/api/v1/job-drafts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.message").value("Job drafts retrieved successfully."));
    }

    @Test
    void saveDraft_returnsCreatedJob() throws Exception {
        when(jobDraftService.saveAsJob(4L)).thenReturn(JobResponse.builder().id(9L).title("Engineer").build());

        mockMvc.perform(post("/api/v1/job-drafts/4/save"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.message").value("Job created successfully."));
    }

    @Test
    void saveDraft_invalid_returns400AndDoesNotClaimCreated() throws Exception {
        when(jobDraftService.saveAsJob(eq(4L)))
                .thenThrow(new JobValidationException("title: Title cannot be blank."));

        mockMvc.perform(post("/api/v1/job-drafts/4/save"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("title: Title cannot be blank."));
    }

    @Test
    void updateDraft_rejectsScoreOver100() throws Exception {
        mockMvc.perform(put("/api/v1/job-drafts/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeToJobScore\":140}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteDraft_missing_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new JobDraftNotFoundException("Job draft not found with id: 4"))
                .when(jobDraftService).delete(4L);

        mockMvc.perform(delete("/api/v1/job-drafts/4"))
                .andExpect(status().isNotFound());
    }
}
