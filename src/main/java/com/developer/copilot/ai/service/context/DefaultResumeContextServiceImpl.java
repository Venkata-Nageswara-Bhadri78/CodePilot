package com.developer.copilot.ai.service.context;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.developer.copilot.ai.exception.AiResumePendingException;
import com.developer.copilot.user.dto.parsing.ResumeParsedDataResponse;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.ResumeParsingException;
import com.developer.copilot.user.service.ResumeParsingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Production implementation of {@link ResumeContextService}.
 * <p>
 * Delegates to the user module's {@link ResumeParsingService} for parsed resume
 * context. Ownership validation and parsing remain in user-service. Domain
 * exceptions are preserved so HTTP statuses stay meaningful (404 / 409 / 422).
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

        ResumeParsedDataResponse parsed = resumeParsingService.getParsedResume(resumeId);
        return extractUsableContext(parsed);
    }

    private String extractUsableContext(ResumeParsedDataResponse parsed) {
        if (parsed == null) {
            throw new ResumeNotFoundException();
        }

        if (FAILED_STATUS.equals(parsed.getStatus())) {
            log.warn("Resume parsing failed for resumeId={}, lastErrorPresent={}",
                    parsed.getResumeId(), StringUtils.hasText(parsed.getLastError()));
            throw new ResumeParsingException(
                    "Your resume could not be parsed. Please upload a different PDF and try again.");
        }

        if (!COMPLETED_STATUS.equals(parsed.getStatus())
                || !StringUtils.hasText(parsed.getContextText())) {
            throw new AiResumePendingException(
                    "Your resume is still being processed. Please try again in a few moments.");
        }

        return parsed.getContextText().trim();
    }
}
