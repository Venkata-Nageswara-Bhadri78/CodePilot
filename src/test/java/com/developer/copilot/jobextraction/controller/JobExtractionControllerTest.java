package com.developer.copilot.jobextraction.controller;

import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.common.exception.InvalidJobUrlException;
import com.developer.copilot.jobextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.service.JobExtractionService;
import com.developer.copilot.jobs.exception.DuplicateJobException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class JobExtractionControllerTest {

    @Mock
    private JobExtractionService jobExtractionService;

    @InjectMocks
    private JobExtractionController jobExtractionController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(jobExtractionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String validRequestBody() {
        return """
                {
                  "sourceUrl": "https://example.com/jobs/123",
                  "rawJobText": "Full pasted job posting text describing the role."
                }
                """;
    }

    @Test
    void parseJobInfo_ValidRequest_Returns200WithMappedResponse() throws Exception {
        JobExtractionResultResponse result = JobExtractionResultResponse.builder()
                .sourceUrl("https://example.com/jobs/123")
                .title("Software Engineer")
                .company("Acme Corp")
                .skills(Collections.emptyList())
                .build();
        when(jobExtractionService.extractJobInfo(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sourceUrl").value("https://example.com/jobs/123"))
                .andExpect(jsonPath("$.data.title").value("Software Engineer"))
                .andExpect(jsonPath("$.data.company").value("Acme Corp"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void parseJobInfo_MissingSourceUrl_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rawJobText": "Full pasted job posting text."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void parseJobInfo_MissingRawJobText_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/123"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parseJobInfo_SourceUrlTooLong_Returns400() throws Exception {
        String oversizedUrl = "https://example.com/" + "a".repeat(2001);

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "%s",
                                  "rawJobText": "Full pasted job posting text."
                                }
                                """.formatted(oversizedUrl)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parseJobInfo_RawJobTextTooLong_Returns400() throws Exception {
        String oversizedText = "a".repeat(50001);

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/123",
                                  "rawJobText": "%s"
                                }
                                """.formatted(oversizedText)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parseJobInfo_MalformedJson_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not-json "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parseJobInfo_InvalidJobUrlException_Returns400() throws Exception {
        when(jobExtractionService.extractJobInfo(any()))
                .thenThrow(new InvalidJobUrlException("Job URL must be a valid absolute http/https URL."));

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void parseJobInfo_DuplicateJobException_Returns409() throws Exception {
        when(jobExtractionService.extractJobInfo(any()))
                .thenThrow(new DuplicateJobException("This post was already added to your records."));

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void parseJobInfo_InvalidCredentialsException_Returns401() throws Exception {
        when(jobExtractionService.extractJobInfo(any()))
                .thenThrow(new InvalidCredentialsException("User is not authenticated."));

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void parseJobInfo_AiServiceException_Returns502() throws Exception {
        when(jobExtractionService.extractJobInfo(any()))
                .thenThrow(new AiServiceException("AI provider unavailable."));

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }
}
