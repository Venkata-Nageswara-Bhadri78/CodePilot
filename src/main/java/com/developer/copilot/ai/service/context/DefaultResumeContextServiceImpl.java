package com.developer.copilot.ai.service.context;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.developer.copilot.ai.exception.AiResumePendingException;
import com.developer.copilot.ai.metrics.AiMetrics;
import com.developer.copilot.user.dto.parsing.ResumeParsedDataResponse;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.ResumeParsingException;
import com.developer.copilot.user.service.ResumeParsingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultResumeContextServiceImpl implements ResumeContextService {

    private static final String COMPLETED_STATUS = "COMPLETED";
    private static final String FAILED_STATUS = "FAILED";

    private final ResumeParsingService resumeParsingService;
    private final AiMetrics aiMetrics;

    @Override
    public String getResumeContext(Long resumeId) {
        log.debug("Fetching parsed resume context, resumeId={}", resumeId);

        ResumeParsedDataResponse parsed;
        try {
            parsed = resumeParsingService.getParsedResume(resumeId);
        } catch (ResumeParsingException ex) {
            if (isInProgress(ex.getMessage())) {
                throw new AiResumePendingException(
                        "Your resume is still being processed. Please try again in a few moments.");
            }
            aiMetrics.recordParseFailure();
            throw ex;
        }
        return extractUsableContext(parsed);
    }

    private String extractUsableContext(ResumeParsedDataResponse parsed) {
        if (parsed == null) {
            throw new ResumeNotFoundException();
        }

        if (FAILED_STATUS.equals(parsed.getStatus())) {
            log.warn("Resume parsing failed for resumeId={}, lastErrorPresent={}",
                    parsed.getResumeId(), StringUtils.hasText(parsed.getLastError()));
            aiMetrics.recordParseFailure();
            throw new ResumeParsingException(
                    "Your resume could not be parsed. Please upload a different PDF and try again.");
        }

        if (!COMPLETED_STATUS.equals(parsed.getStatus())) {
            throw new AiResumePendingException(
                    "Your resume is still being processed. Please try again in a few moments.");
        }

        if (!StringUtils.hasText(parsed.getContextText())) {
            aiMetrics.recordParseFailure();
            throw new ResumeParsingException("Resume text was empty. Re-upload the PDF.");
        }

        return parsed.getContextText().trim();
    }

    private static boolean isInProgress(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("still in progress")
                || lower.contains("still being processed")
                || lower.contains("retry shortly");
    }
}
