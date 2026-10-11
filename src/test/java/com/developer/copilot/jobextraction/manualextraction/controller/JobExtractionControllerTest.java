package com.developer.copilot.jobextraction.manualextraction.controller;

import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.common.exception.InvalidJobUrlException;
import com.developer.copilot.jobextraction.manualextraction.controller.JobExtractionController;
import com.developer.copilot.jobextraction.manualextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.manualextraction.exception.EmailNotVerifiedException;
import com.developer.copilot.jobextraction.manualextraction.exception.JobExtractionAiUnavailableException;
import com.developer.copilot.jobextraction.manualextraction.service.JobExtractionService;
import com.developer.copilot.jobextraction.persistence.JobAutoPersistResponse;
import com.developer.copilot.jobextraction.persistence.JobExtractionAutoPersistenceService;
import com.developer.copilot.jobs.dto.JobDraftResponse;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.entity.JobDraftFailureCategory;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class JobExtractionControllerTest {

    @Mock
    private JobExtractionService jobExtractionService;

    @Mock
    private JobExtractionAutoPersistenceService jobExtractionAutoPersistenceService;

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
                  "rawJobText": "Full pasted job posting text describing the role.",
                  "isAuto": false
                }
                """;
    }

    @Test
    void parseJobInfo_ValidRequest_Returns200WithMappedResponse() throws Exception {
        JobExtractionResultResponse result = JobExtractionResultResponse.builder()
                .sourceUrl("https://example.com/jobs/123")
                .title("Software Engineer")
                .company("Acme Corp")
                .skills("")
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
    void parseJobInfo_BlankSourceUrl_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "   ",
                                  "rawJobText": "Full pasted job posting text."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void parseJobInfo_BlankRawJobText_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/123",
                                  "rawJobText": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parseJobInfo_EmptyBody_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parseJobInfo_UnknownJsonFields_IgnoredAndReturns200() throws Exception {
        JobExtractionResultResponse result = JobExtractionResultResponse.builder()
                .sourceUrl("https://example.com/jobs/123")
                .title("Software Engineer")
                .company("Acme Corp")
                .skills("")
                .build();
        when(jobExtractionService.extractJobInfo(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/123",
                                  "rawJobText": "Full pasted job posting text describing the role.",
                                  "isAuto": false,
                                  "requiresManualReview": true,
                                  "hack": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void parseJobInfo_RawJobTextAtMaxLength_Returns200() throws Exception {
        JobExtractionResultResponse result = JobExtractionResultResponse.builder()
                .sourceUrl("https://example.com/jobs/123")
                .title("T")
                .company("C")
                .skills("")
                .build();
        when(jobExtractionService.extractJobInfo(any())).thenReturn(result);

        String maxText = "a".repeat(50000);
        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/123",
                                  "rawJobText": "%s",
                                  "isAuto": false
                                }
                                """.formatted(maxText)))
                .andExpect(status().isOk());
    }

    @Test
    void parseJobInfo_UnexpectedRuntimeException_Returns500() throws Exception {
        when(jobExtractionService.extractJobInfo(any()))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Something went wrong."));
    }

    @Test
    void parseJobInfo_InvalidJobUrlException_Returns400WithoutEchoingUrl() throws Exception {
        when(jobExtractionService.extractJobInfo(any()))
                .thenThrow(new InvalidJobUrlException("Job URL must be a valid absolute http or https link."));

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Job URL must be a valid absolute http or https link."));
    }

    @Test
    void parseJobInfo_InvalidJobUrlWithScript_DoesNotEchoScript() throws Exception {
        when(jobExtractionService.extractJobInfo(any()))
                .thenThrow(new InvalidJobUrlException("Job URL must be a valid absolute http or https link."));

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/<script>alert(1)</script>",
                                  "rawJobText": "Full pasted job posting text describing the role.",
                                  "isAuto": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Job URL must be a valid absolute http or https link."))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))));
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

    @Test
    void parseJobInfo_EmailNotVerifiedException_Returns403() throws Exception {
        when(jobExtractionService.extractJobInfo(any()))
                .thenThrow(new EmailNotVerifiedException());

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Please verify your email before using this feature."));
    }

    @Test
    void parseJobInfo_AiUnavailable_Returns503() throws Exception {
        when(jobExtractionService.extractJobInfo(any()))
                .thenThrow(new JobExtractionAiUnavailableException(
                        "The AI service is temporarily unavailable. Please try again shortly."));

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void parseJobInfo_isAutoFalse_doesNotPersist() throws Exception {
        when(jobExtractionService.extractJobInfo(any())).thenReturn(
                JobExtractionResultResponse.builder().title("Engineer").company("Acme").build());

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Engineer"))
                .andExpect(jsonPath("$.message").value(
                        "Job information extracted successfully. Review and edit before saving."));
        verify(jobExtractionAutoPersistenceService, never()).persistManual(any());
    }

    @Test
    void parseJobInfo_omittedIsAuto_savesJob() throws Exception {
        when(jobExtractionAutoPersistenceService.persistManual(any())).thenReturn(
                JobAutoPersistResponse.saved(JobResponse.builder().id(9L).title("Engineer").build()));

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/123",
                                  "rawJobText": "Full pasted job posting text describing the role."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.persistedAs").value("JOB"))
                .andExpect(jsonPath("$.data.job.id").value(9))
                .andExpect(jsonPath("$.message").value("Job created successfully."));
        verify(jobExtractionService, never()).extractJobInfo(any());
    }

    @Test
    void parseJobInfo_nullIsAuto_savesDraft() throws Exception {
        when(jobExtractionAutoPersistenceService.persistManual(any())).thenReturn(
                JobAutoPersistResponse.drafted(JobDraftResponse.builder()
                        .id(4L)
                        .failureCategory(JobDraftFailureCategory.VALIDATION)
                        .failureReason("title: Title cannot be blank.")
                        .build()));

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/123",
                                  "rawJobText": "Full pasted job posting text describing the role.",
                                  "isAuto": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.persistedAs").value("DRAFT"))
                .andExpect(jsonPath("$.data.draft.id").value(4))
                .andExpect(jsonPath("$.data.failureReason").value("title: Title cannot be blank."))
                .andExpect(jsonPath("$.message").value("Job could not be saved. Stored as a draft."));
    }

    @Test
    void parseJobInfo_isAutoTrue_duplicateStays409() throws Exception {
        when(jobExtractionAutoPersistenceService.persistManual(any()))
                .thenThrow(new DuplicateJobException("This post was already added to your records."));

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/123",
                                  "rawJobText": "Full pasted job posting text describing the role.",
                                  "isAuto": true
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This post was already added to your records."));
    }
}
