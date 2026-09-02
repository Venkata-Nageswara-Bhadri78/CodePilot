package com.developer.copilot.user.service.parsing;

import com.developer.copilot.common.storage.service.FileStorageService;
import com.developer.copilot.user.config.ResumeProperties;
import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.ResumeParsedData;
import com.developer.copilot.user.entity.ResumeParsingStatus;
import com.developer.copilot.user.metrics.UserMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;

/**
 * Turns a stored resume PDF into a {@link ResumeParsedData} record, retrying a
 * bounded number of times before marking the record permanently failed.
 * <p>
 * The returned record is never persisted here; persistence is a separate concern
 * handled by {@link ResumeParsedDataWriter}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeParser {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final FileStorageService fileStorageService;
    private final ResumeTextExtractor resumeTextExtractor;
    private final ResumeSectionParser resumeSectionParser;
    private final ResumeSectionsCodec resumeSectionsCodec;
    private final ResumeProperties resumeProperties;
    private final UserMetrics userMetrics;

    /**
     * @param existing record to update in place, or {@code null} to start a fresh one
     * @return a record with status {@link ResumeParsingStatus#COMPLETED} or
     * {@link ResumeParsingStatus#FAILED}; failure is terminal and is never retried again
     */
    public ResumeParsedData parseWithRetry(Resume resume, ResumeParsedData existing) {

        int maxAttempts = Math.max(1, resumeProperties.getParsing().getMaxAttempts());
        ResumeParsedData target = existing != null ? existing : newPendingRecord(resume);

        String lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            target.setAttemptCount(attempt);

            try {
                ExtractedResumeText extracted = resumeTextExtractor.extract(readBytes(resume));
                ParsedResumeContent content = resumeSectionParser.parse(extracted.text());

                applySuccess(target, extracted, content);

                log.info("Parsed resume {} on attempt {}/{} ({} chars, {} sections)",
                        resume.getId(), attempt, maxAttempts,
                        extracted.text().length(), content.getSections().size());
                userMetrics.recordParseCompleted();

                return target;

            } catch (Exception ex) {
                lastError = describe(ex);
                log.warn("Resume {} parse attempt {}/{} failed: {}",
                        resume.getId(), attempt, maxAttempts, lastError);
            }
        }

        target.setStatus(ResumeParsingStatus.FAILED);
        target.setLastError(truncate(lastError, MAX_ERROR_LENGTH));
        target.setParsedAt(LocalDateTime.now());

        log.error("Resume {} marked as FAILED after {} attempts: {}",
                resume.getId(), maxAttempts, lastError);
        userMetrics.recordParseFailed();

        return target;
    }

    public ResumeParsedData newPendingRecord(Resume resume) {
        return ResumeParsedData.builder()
                .resume(resume)
                .status(ResumeParsingStatus.PENDING)
                .attemptCount(0)
                .parserVersion(resumeProperties.getParsing().getParserVersion())
                .build();
    }

    private void applySuccess(ResumeParsedData target,
                              ExtractedResumeText extracted,
                              ParsedResumeContent content) {

        target.setStatus(ResumeParsingStatus.COMPLETED);
        target.setRawText(extracted.text());
        target.setPageCount(extracted.pageCount());
        target.setCharacterCount(extracted.text().length());
        target.setTruncated(extracted.truncated());
        target.setSectionsJson(resumeSectionsCodec.toJson(content.getSections()));
        target.setCandidateName(content.getCandidateName());
        target.setEmail(content.getEmail());
        target.setPhone(content.getPhone());
        target.setLocation(content.getLocation());
        target.setLinkedinUrl(content.getLinkedinUrl());
        target.setGithubUrl(content.getGithubUrl());
        target.setParserVersion(resumeProperties.getParsing().getParserVersion());
        target.setParsedAt(LocalDateTime.now());
        target.setLastError(null);
    }

    private byte[] readBytes(Resume resume) {
        Resource resource = fileStorageService.download(resume.getStorageKey());
        try (InputStream inputStream = resource.getInputStream()) {
            return inputStream.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Unable to read stored resume " + resume.getStorageKey(), ex);
        }
    }

    private String describe(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank()
                ? ex.getClass().getSimpleName()
                : ex.getClass().getSimpleName() + ": " + message;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
