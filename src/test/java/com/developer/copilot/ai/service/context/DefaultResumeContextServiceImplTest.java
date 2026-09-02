package com.developer.copilot.ai.service.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.developer.copilot.ai.exception.AiResumePendingException;
import com.developer.copilot.ai.metrics.AiMetrics;
import com.developer.copilot.user.dto.parsing.ResumeParsedDataResponse;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.ResumeParsingException;
import com.developer.copilot.user.exception.UserProfileNotFoundException;
import com.developer.copilot.user.service.ResumeParsingService;

@ExtendWith(MockitoExtension.class)
class DefaultResumeContextServiceImplTest {

    @Mock
    private ResumeParsingService resumeParsingService;

    @Mock
    private AiMetrics aiMetrics;

    @InjectMocks
    private DefaultResumeContextServiceImpl resumeContextService;

    @Test
    void getResumeContext_withoutResumeId_usesHighPriorityResume() {
        when(resumeParsingService.getParsedResume(null)).thenReturn(completedResponse("parsed resume context"));

        String context = resumeContextService.getResumeContext(null);

        assertEquals("parsed resume context", context);
        verify(resumeParsingService).getParsedResume(null);
    }

    @Test
    void getResumeContext_withResumeId_usesSpecificResume() {
        when(resumeParsingService.getParsedResume(7L)).thenReturn(completedResponse("resume seven"));

        String context = resumeContextService.getResumeContext(7L);

        assertEquals("resume seven", context);
        verify(resumeParsingService).getParsedResume(7L);
    }

    @Test
    void getResumeContext_resumeNotFound_propagatesDomainException() {
        when(resumeParsingService.getParsedResume(99L)).thenThrow(new ResumeNotFoundException());

        assertThrows(ResumeNotFoundException.class, () -> resumeContextService.getResumeContext(99L));
    }

    @Test
    void getResumeContext_profileNotFound_propagatesDomainException() {
        when(resumeParsingService.getParsedResume(null)).thenThrow(new UserProfileNotFoundException());

        assertThrows(UserProfileNotFoundException.class, () -> resumeContextService.getResumeContext(null));
    }

    @Test
    void getResumeContext_parsingFailed_throwsResumeParsingExceptionWithoutInternals() {
        when(resumeParsingService.getParsedResume(null)).thenReturn(
                ResumeParsedDataResponse.builder()
                        .status("FAILED")
                        .lastError("stacktrace:/internal/parser")
                        .build());

        ResumeParsingException ex = assertThrows(ResumeParsingException.class,
                () -> resumeContextService.getResumeContext(null));

        assertTrue(ex.getMessage().contains("could not be parsed"));
        assertTrue(!ex.getMessage().contains("stacktrace"));
    }

    @Test
    void getResumeContext_stillPending_throwsAiResumePendingException() {
        when(resumeParsingService.getParsedResume(null)).thenReturn(
                ResumeParsedDataResponse.builder()
                        .status("PENDING")
                        .build());

        assertThrows(AiResumePendingException.class, () -> resumeContextService.getResumeContext(null));
    }

    @Test
    void getResumeContext_inProgressParseException_throwsAiResumePendingException() {
        when(resumeParsingService.getParsedResume(null)).thenThrow(
                new ResumeParsingException("Resume parsing is still in progress. Please retry shortly."));

        assertThrows(AiResumePendingException.class, () -> resumeContextService.getResumeContext(null));
    }

    @Test
    void getResumeContext_completedEmptyText_throwsResumeParsingException() {
        when(resumeParsingService.getParsedResume(null)).thenReturn(completedResponse(""));

        ResumeParsingException ex = assertThrows(ResumeParsingException.class,
                () -> resumeContextService.getResumeContext(null));

        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void getResumeContext_completedWhitespaceText_throwsResumeParsingException() {
        when(resumeParsingService.getParsedResume(null)).thenReturn(completedResponse("   "));

        assertThrows(ResumeParsingException.class, () -> resumeContextService.getResumeContext(null));
    }

    @Test
    void getResumeContext_nullResponse_throwsResumeNotFound() {
        when(resumeParsingService.getParsedResume(null)).thenReturn(null);

        assertThrows(ResumeNotFoundException.class, () -> resumeContextService.getResumeContext(null));
    }

    private ResumeParsedDataResponse completedResponse(String contextText) {
        return ResumeParsedDataResponse.builder()
                .resumeId(5L)
                .status("COMPLETED")
                .contextText(contextText)
                .build();
    }
}
