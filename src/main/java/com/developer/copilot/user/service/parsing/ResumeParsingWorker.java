package com.developer.copilot.user.service.parsing;

import com.developer.copilot.user.config.ResumeParsingAsyncConfig;
import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.ResumeParsedData;
import com.developer.copilot.user.repository.ResumeParsedDataRepository;
import com.developer.copilot.user.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Runs resume parsing and parsed-data persistence off the request thread.
 * <p>
 * Kept as a separate bean from the parsing service so {@code @Async} and
 * {@code @Transactional} always go through the Spring proxy rather than a
 * self-invocation that would silently run inline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeParsingWorker {

    private final ResumeRepository resumeRepository;
    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final ResumeParser resumeParser;
    private final ResumeParsedDataWriter resumeParsedDataWriter;

    /**
     * Background parse triggered after upload.
     */
    @Async(ResumeParsingAsyncConfig.RESUME_PARSING_EXECUTOR)
    public void parseAndPersist(Long resumeId) {

        Optional<Resume> resume = resumeRepository.findById(resumeId);
        if (resume.isEmpty()) {
            log.debug("Skipping background parse, resume {} no longer exists", resumeId);
            return;
        }

        ResumeParsedData existing = resumeParsedDataRepository.findByResumeId(resumeId).orElse(null);

        // Anything already resolved was handled by an on-demand read; do not re-parse.
        if (existing != null && (existing.isCompleted() || existing.isFailed())) {
            log.debug("Skipping background parse, resume {} already has status {}",
                    resumeId, existing.getStatus());
            return;
        }

        try {
            ResumeParsedData parsed = resumeParser.parseWithRetry(resume.get(), existing);
            persist(resumeId, parsed);
        } catch (Exception ex) {
            log.error("Background parsing failed unexpectedly for resume {}", resumeId, ex);
        }
    }

    /**
     * Persists a parsed result produced elsewhere, such as the on-demand read path.
     */
    @Async(ResumeParsingAsyncConfig.RESUME_PARSING_EXECUTOR)
    public void persistAsync(Long resumeId, ResumeParsedData parsed) {
        try {
            persist(resumeId, parsed);
        } catch (Exception ex) {
            log.error("Unable to persist parsed data for resume {}", resumeId, ex);
        }
    }

    private void persist(Long resumeId, ResumeParsedData parsed) {
        try {
            resumeParsedDataWriter.persist(resumeId, parsed);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request inserted the row first. Repeating the call now finds
            // that row and applies this result as an update instead.
            log.debug("Parsed data for resume {} was inserted concurrently, retrying as update", resumeId);
            resumeParsedDataWriter.persist(resumeId, parsed);
        }
    }
}
