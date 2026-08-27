package com.developer.copilot.ai.service.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.user.dto.parsing.ResumeParsedDataResponse;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.UserProfileNotFoundException;
import com.developer.copilot.user.service.ResumeParsingService;

@ExtendWith(MockitoExtension.class)
class DefaultResumeContextServiceImplTest {

    @Mock
    private ResumeParsingService resumeParsingService;

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
    void getResumeContext_resumeNotFound_throwsAiServiceException() {
        when(resumeParsingService.getParsedResume(99L)).thenThrow(new ResumeNotFoundException());

        assertThrows(AiServiceException.class, () -> resumeContextService.getResumeContext(99L));
    }

    @Test
    void getResumeContext_profileNotFound_throwsAiServiceException() {
        when(resumeParsingService.getParsedResume(null)).thenThrow(new UserProfileNotFoundException());

        assertThrows(AiServiceException.class, () -> resumeContextService.getResumeContext(null));
    }

    @Test
    void getResumeContext_parsingFailed_throwsAiServiceException() {
        when(resumeParsingService.getParsedResume(null)).thenReturn(
                ResumeParsedDataResponse.builder()
                        .status("FAILED")
                        .lastError("No extractable text.")
                        .build());

        assertThrows(AiServiceException.class, () -> resumeContextService.getResumeContext(null));
    }

    @Test
    void getResumeContext_stillPending_throwsAiServiceException() {
        when(resumeParsingService.getParsedResume(null)).thenReturn(
                ResumeParsedDataResponse.builder()
                        .status("PENDING")
                        .build());

        assertThrows(AiServiceException.class, () -> resumeContextService.getResumeContext(null));
    }

    private ResumeParsedDataResponse completedResponse(String contextText) {
        return ResumeParsedDataResponse.builder()
                .resumeId(5L)
                .status("COMPLETED")
                .contextText(contextText)
                .build();
    }
}
