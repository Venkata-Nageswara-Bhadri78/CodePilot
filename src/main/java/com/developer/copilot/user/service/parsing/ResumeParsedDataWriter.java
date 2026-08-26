package com.developer.copilot.user.service.parsing;

import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.ResumeParsedData;
import com.developer.copilot.user.repository.ResumeParsedDataRepository;
import com.developer.copilot.user.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Persists parsed resume records in their own transaction.
 * <p>
 * Deliberately isolated from the caller's transaction: on the read path the
 * response and its persistence are independent operations, so a write failure must
 * never fail the request that already has its answer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeParsedDataWriter {

    private final ResumeRepository resumeRepository;
    private final ResumeParsedDataRepository resumeParsedDataRepository;

    /**
     * Inserts or updates the parsed record for a resume.
     * <p>
     * Flushes before returning so a lost race on the unique {@code resume_id}
     * constraint surfaces here rather than at an outer commit, letting the caller
     * retry the call as a plain update.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(Long resumeId, ResumeParsedData snapshot) {

        Optional<Resume> resume = resumeRepository.findById(resumeId);
        if (resume.isEmpty()) {
            log.debug("Skipping parsed data persistence, resume {} no longer exists", resumeId);
            return;
        }

        ResumeParsedData target = resumeParsedDataRepository
                .findByResumeId(resumeId)
                .orElseGet(() -> ResumeParsedData.builder().resume(resume.get()).build());

        copyInto(snapshot, target);

        resumeParsedDataRepository.saveAndFlush(target);

        log.debug("Persisted parsed data for resume {} with status {}", resumeId, target.getStatus());
    }

    private void copyInto(ResumeParsedData source, ResumeParsedData target) {
        target.setStatus(source.getStatus());
        target.setAttemptCount(source.getAttemptCount());
        target.setLastError(source.getLastError());
        target.setParserVersion(source.getParserVersion());
        target.setParsedAt(source.getParsedAt());
        target.setPageCount(source.getPageCount());
        target.setCharacterCount(source.getCharacterCount());
        target.setRawText(source.getRawText());
        target.setSectionsJson(source.getSectionsJson());
        target.setCandidateName(source.getCandidateName());
        target.setEmail(source.getEmail());
        target.setPhone(source.getPhone());
        target.setLocation(source.getLocation());
        target.setLinkedinUrl(source.getLinkedinUrl());
        target.setGithubUrl(source.getGithubUrl());
    }
}
