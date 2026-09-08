package com.developer.copilot.jobextraction.automatedjobextraction.controller;

import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.common.exception.InvalidJobUrlException;
import com.developer.copilot.jobextraction.automatedjobextraction.exception.AutomatedJobExtractionUnavailableException;
import com.developer.copilot.jobextraction.automatedjobextraction.exception.AutomatedJobPageFetchException;
import com.developer.copilot.jobextraction.automatedjobextraction.exception.InvalidAutomatedJobUrlException;
import com.developer.copilot.jobextraction.automatedjobextraction.service.AutomatedJobExtractionService;
import com.developer.copilot.jobextraction.manualextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.manualextraction.exception.EmailNotVerifiedException;
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
class AutomatedJobExtractionControllerTest {

    @Mock
    private AutomatedJobExtractionService automatedJobExtractionService;

    @InjectMocks
    private AutomatedJobExtractionController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String validRequestBody() {
        return """
                {
                  "sourceUrl": "https://example.com/jobs/123"
                }
                """;
    }

    @Test
    void parseJobUrl_ValidRequest_Returns200() throws Exception {
        JobExtractionResultResponse result = JobExtractionResultResponse.builder()
                .sourceUrl("https://example.com/jobs/123")
                .title("Software Engineer")
                .company("Acme Corp")
                .skills(Collections.emptyList())
                .build();
        when(automatedJobExtractionService.extractFromUrl(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Software Engineer"))
                .andExpect(jsonPath("$.data.company").value("Acme Corp"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void parseJobUrl_MissingSourceUrl_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void parseJobUrl_BlankSourceUrl_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUrl\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parseJobUrl_SourceUrlTooLong_Returns400() throws Exception {
        String oversizedUrl = "https://example.com/" + "a".repeat(2001);
        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUrl\":\"" + oversizedUrl + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parseJobUrl_MalformedJson_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not-json "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parseJobUrl_EmptyBody_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parseJobUrl_UnknownJsonFields_Ignored() throws Exception {
        JobExtractionResultResponse result = JobExtractionResultResponse.builder()
                .sourceUrl("https://example.com/jobs/123")
                .title("T")
                .company("C")
                .skills(Collections.emptyList())
                .build();
        when(automatedJobExtractionService.extractFromUrl(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/123",
                                  "hack": 1
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void parseJobUrl_InvalidAutomatedJobUrl_Returns400WithFixedMessage() throws Exception {
        when(automatedJobExtractionService.extractFromUrl(any()))
                .thenThrow(new InvalidAutomatedJobUrlException());

        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID JOB URL"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("http"))));
    }

    @Test
    void parseJobUrl_InvalidFormatUrl_Returns400WithoutEchoingUrl() throws Exception {
        when(automatedJobExtractionService.extractFromUrl(any()))
                .thenThrow(new InvalidJobUrlException("Job URL must be a valid absolute http or https link."));

        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "javascript:alert(1)"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Job URL must be a valid absolute http or https link."))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("javascript"))));
    }

    @Test
    void parseJobUrl_Duplicate_Returns409() throws Exception {
        when(automatedJobExtractionService.extractFromUrl(any()))
                .thenThrow(new DuplicateJobException("This post was already added to your records."));

        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isConflict());
    }

    @Test
    void parseJobUrl_Unauthenticated_Returns401() throws Exception {
        when(automatedJobExtractionService.extractFromUrl(any()))
                .thenThrow(new InvalidCredentialsException("User is not authenticated."));

        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void parseJobUrl_EmailNotVerified_Returns403() throws Exception {
        when(automatedJobExtractionService.extractFromUrl(any()))
                .thenThrow(new EmailNotVerifiedException());

        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void parseJobUrl_FetchFailure_Returns502() throws Exception {
        when(automatedJobExtractionService.extractFromUrl(any()))
                .thenThrow(new AutomatedJobPageFetchException());

        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(AutomatedJobPageFetchException.MESSAGE));
    }

    @Test
    void parseJobUrl_Unavailable_Returns503() throws Exception {
        when(automatedJobExtractionService.extractFromUrl(any()))
                .thenThrow(new AutomatedJobExtractionUnavailableException(
                        "The job page could not be retrieved. Please try again shortly."));

        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void parseJobUrl_AiFailure_Returns502() throws Exception {
        when(automatedJobExtractionService.extractFromUrl(any()))
                .thenThrow(new AiServiceException("AI provider unavailable."));

        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isBadGateway());
    }

    @Test
    void parseJobUrl_Unexpected_Returns500WithoutDetails() throws Exception {
        when(automatedJobExtractionService.extractFromUrl(any()))
                .thenThrow(new RuntimeException("secret-host:6379"));

        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Something went wrong."))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("6379"))));
    }
}
