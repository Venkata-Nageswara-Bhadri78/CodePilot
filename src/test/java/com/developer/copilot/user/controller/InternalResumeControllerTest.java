package com.developer.copilot.user.controller;

import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.user.controller.internal.InternalResumeController;
import com.developer.copilot.user.dto.parsing.ResumeParsedDataResponse;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.ResumeParsingException;
import com.developer.copilot.user.service.ResumeParsingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalResumeControllerTest {

    @Mock
    private ResumeParsingService resumeParsingService;

    @InjectMocks
    private InternalResumeController internalResumeController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(internalResumeController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getParsedResume_returnsParsedData() throws Exception {
        when(resumeParsingService.getParsedResume(5L)).thenReturn(parsed());

        mockMvc.perform(get("/api/v1/internal/resumes/5/parsed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resumeId").value(5))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.candidateName").value("Jane Doe"))
                .andExpect(jsonPath("$.data.sections.SUMMARY").value("Backend engineer."))
                .andExpect(jsonPath("$.data.contextText").value("CANDIDATE RESUME PROFILE"));
    }

    @Test
    void getParsedHighPriorityResume_resolvesWithoutResumeId() throws Exception {
        when(resumeParsingService.getParsedResume(isNull())).thenReturn(parsed());

        mockMvc.perform(get("/api/v1/internal/resumes/parsed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.highPriority").value(true));

        verify(resumeParsingService).getParsedResume(isNull());
    }

    @Test
    void getParsedResume_unknownOrForeignResume_returns404() throws Exception {
        when(resumeParsingService.getParsedResume(999L)).thenThrow(new ResumeNotFoundException());

        mockMvc.perform(get("/api/v1/internal/resumes/999/parsed"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getParsedResume_failedParse_returns422() throws Exception {
        when(resumeParsingService.getParsedResume(5L))
                .thenThrow(new ResumeParsingException("Resume contains no extractable text."));

        mockMvc.perform(get("/api/v1/internal/resumes/5/parsed"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Resume contains no extractable text."));
    }

    @Test
    void getParsedResume_pendingParse_returns422() throws Exception {
        when(resumeParsingService.getParsedResume(5L))
                .thenThrow(new ResumeParsingException(
                        "Resume parsing is still in progress. Please retry shortly."));

        mockMvc.perform(get("/api/v1/internal/resumes/5/parsed"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "Resume parsing is still in progress. Please retry shortly."));
    }

    private ResumeParsedDataResponse parsed() {
        return ResumeParsedDataResponse.builder()
                .resumeId(5L)
                .originalFilename("resume.pdf")
                .highPriority(true)
                .status("COMPLETED")
                .attemptCount(1)
                .candidateName("Jane Doe")
                .email("jane@example.com")
                .sections(Map.of("SUMMARY", "Backend engineer."))
                .contextText("CANDIDATE RESUME PROFILE")
                .build();
    }
}
