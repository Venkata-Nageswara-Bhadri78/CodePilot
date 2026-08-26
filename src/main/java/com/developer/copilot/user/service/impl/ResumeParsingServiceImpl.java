package com.developer.copilot.user.service.impl;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.storage.service.FileStorageService;
import com.developer.copilot.user.dto.parsing.ResumeParsedDataResponse;
import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.ResumeParsedData;
import com.developer.copilot.user.entity.UserProfile;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.UserProfileNotFoundException;
import com.developer.copilot.user.mapper.ResumeParsedDataMapper;
import com.developer.copilot.user.repository.ResumeParsedDataRepository;
import com.developer.copilot.user.repository.ResumeRepository;
import com.developer.copilot.user.repository.UserProfileRepository;
import com.developer.copilot.user.service.ResumeParsingService;
import com.developer.copilot.user.service.parsing.ResumeParser;
import com.developer.copilot.user.service.parsing.ResumeParsingWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeParsingServiceImpl implements ResumeParsingService {

    private final UserProfileRepository userProfileRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final ResumeParser resumeParser;
    private final ResumeParsingWorker resumeParsingWorker;
    private final ResumeParsedDataMapper resumeParsedDataMapper;
    private final FileStorageService fileStorageService;
    private final CurrentUserService currentUserService;

    /**
     * Intentionally not transactional. Parsing on a cache miss can take seconds and
     * its result is persisted as an independent operation, so holding a database
     * transaction open across it would buy nothing.
     */
    @Override
    public ResumeParsedDataResponse getParsedResume(Long resumeId) {

        User user = currentUserService.getCurrentUser();

        UserProfile profile = userProfileRepository
                .findByUser(user)
                .orElseThrow(UserProfileNotFoundException::new);

        Resume resume = resolveResume(resumeId, profile);

        ResumeParsedData existing = resumeParsedDataRepository
                .findByResume(resume)
                .orElse(null);

        if (existing != null && (existing.isCompleted() || existing.isFailed())) {
            return resumeParsedDataMapper.toResponse(resume, existing);
        }

        if (!fileStorageService.exists(resume.getStorageKey())) {
            log.warn("Resume {} has no stored file at {}", resume.getId(), resume.getStorageKey());
            throw new ResumeNotFoundException();
        }

        ResumeParsedData parsed = resumeParser.parseWithRetry(resume, existing);

        ResumeParsedDataResponse response = resumeParsedDataMapper.toResponse(resume, parsed);

        resumeParsingWorker.persistAsync(resume.getId(), parsed);

        return response;
    }

    @Override
    @Transactional
    public void initializeAndScheduleParsing(Resume resume) {

        if (!resumeParsedDataRepository.existsByResume(resume)) {
            resumeParsedDataRepository.save(resumeParser.newPendingRecord(resume));
        }

        scheduleAfterCommit(resume.getId());
    }

    @Override
    @Transactional
    public void deleteParsedDataFor(Resume resume) {
        resumeParsedDataRepository.deleteByResume(resume);
    }

    @Override
    @Transactional
    public void deleteParsedDataFor(List<Resume> resumes) {
        if (resumes != null && !resumes.isEmpty()) {
            resumeParsedDataRepository.deleteByResumeIn(resumes);
        }
    }

    private Resume resolveResume(Long resumeId, UserProfile profile) {

        if (resumeId != null) {
            return resumeRepository
                    .findByIdAndUserProfileAndActiveTrue(resumeId, profile)
                    .orElseThrow(ResumeNotFoundException::new);
        }

        return resumeRepository
                .findByHighPriorityTrueAndUserProfileAndActiveTrue(profile)
                .orElseThrow(ResumeNotFoundException::new);
    }

    /**
     * Parsing must not start before the resume row is visible to other connections,
     * otherwise the worker thread would look up a row that has not committed yet.
     */
    private void scheduleAfterCommit(Long resumeId) {

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            resumeParsingWorker.parseAndPersist(resumeId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                resumeParsingWorker.parseAndPersist(resumeId);
            }
        });
    }
}
