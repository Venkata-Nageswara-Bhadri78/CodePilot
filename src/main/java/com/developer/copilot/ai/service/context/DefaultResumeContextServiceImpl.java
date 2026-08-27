package com.developer.copilot.ai.service.context;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.user.dto.parsing.ResumeParsedDataResponse;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.UserProfileNotFoundException;
import com.developer.copilot.user.service.ResumeParsingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Production implementation of {@link ResumeContextService}.
 * <p>
 * Delegates to the user module's {@link ResumeParsingService} for parsed resume
 * context. Ownership validation and parsing remain entirely in user-service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultResumeContextServiceImpl implements ResumeContextService {

    private static final String COMPLETED_STATUS = "COMPLETED";
    private static final String FAILED_STATUS = "FAILED";

    private final ResumeParsingService resumeParsingService;

    @Override
    public String getResumeContext(Long resumeId) {
        log.debug("Fetching parsed resume context, resumeId={}", resumeId);

        try {
            ResumeParsedDataResponse parsed = resumeParsingService.getParsedResume(resumeId);
            return extractUsableContext(parsed);
        } catch (ResumeNotFoundException ex) {
            throw new AiServiceException(
                    "No active resume was found for your account. Please upload a resume to continue.",
                    ex);
        } catch (UserProfileNotFoundException ex) {
            throw new AiServiceException(
                    "Your profile is not set up yet. Please complete your profile before using AI features.",
                    ex);
        }
    }

    private String extractUsableContext(ResumeParsedDataResponse parsed) {
        if (parsed == null) {
            throw new AiServiceException("Unable to load resume context. Please try again.");
        }

        if (FAILED_STATUS.equals(parsed.getStatus())) {
            String detail = StringUtils.hasText(parsed.getLastError())
                    ? parsed.getLastError().trim()
                    : "Resume parsing failed.";
            throw new AiServiceException(
                    "Your resume could not be parsed: " + detail + " Please upload a different PDF and try again.");
        }

        if (!COMPLETED_STATUS.equals(parsed.getStatus())
                || !StringUtils.hasText(parsed.getContextText())) {
            throw new AiServiceException(
                    "Your resume is still being processed. Please try again in a few moments.");
        }

        return parsed.getContextText().trim();
    }
}
