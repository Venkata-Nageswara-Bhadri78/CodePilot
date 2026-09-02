package com.developer.copilot.user.service.impl;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.storage.service.FileStorageService;
import com.developer.copilot.user.config.ResumeParsingAsyncConfig;
import com.developer.copilot.user.config.ResumeProperties;
import com.developer.copilot.user.dto.parsing.ResumeParsedDataResponse;
import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.ResumeParsedData;
import com.developer.copilot.user.entity.UserProfile;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.ResumeParsingException;
import com.developer.copilot.user.exception.UserProfileNotFoundException;
import com.developer.copilot.user.mapper.ResumeParsedDataMapper;
import com.developer.copilot.user.metrics.UserMetrics;
import com.developer.copilot.user.repository.ResumeParsedDataRepository;
import com.developer.copilot.user.repository.ResumeRepository;
import com.developer.copilot.user.repository.UserProfileRepository;
import com.developer.copilot.user.service.ResumeParsingService;
import com.developer.copilot.user.service.parsing.ResumeParser;
import com.developer.copilot.user.service.parsing.ResumeParsingWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class ResumeParsingServiceImpl implements ResumeParsingService {

    private final UserProfileRepository userProfileRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final ResumeParser resumeParser;
    private final ResumeParsingWorker resumeParsingWorker;
    private final ResumeParsedDataMapper resumeParsedDataMapper;
    private final FileStorageService fileStorageService;
    private final CurrentUserService currentUserService;
    private final ResumeProperties resumeProperties;
    private final Executor resumeParsingExecutor;
    private final UserMetrics userMetrics;

    public ResumeParsingServiceImpl(
            UserProfileRepository userProfileRepository,
            ResumeRepository resumeRepository,
            ResumeParsedDataRepository resumeParsedDataRepository,
            ResumeParser resumeParser,
            ResumeParsingWorker resumeParsingWorker,
            ResumeParsedDataMapper resumeParsedDataMapper,
            FileStorageService fileStorageService,
            CurrentUserService currentUserService,
            ResumeProperties resumeProperties,
            @Qualifier(ResumeParsingAsyncConfig.RESUME_PARSING_EXECUTOR) Executor resumeParsingExecutor,
            UserMetrics userMetrics) {
        this.userProfileRepository = userProfileRepository;
        this.resumeRepository = resumeRepository;
        this.resumeParsedDataRepository = resumeParsedDataRepository;
        this.resumeParser = resumeParser;
        this.resumeParsingWorker = resumeParsingWorker;
        this.resumeParsedDataMapper = resumeParsedDataMapper;
        this.fileStorageService = fileStorageService;
        this.currentUserService = currentUserService;
        this.resumeProperties = resumeProperties;
        this.resumeParsingExecutor = resumeParsingExecutor;
        this.userMetrics = userMetrics;
    }

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

        String currentVersion = resumeProperties.getParsing().getParserVersion();
        boolean versionMatches = existing != null
                && Objects.equals(currentVersion, existing.getParserVersion());

        if (existing != null && existing.isCompleted() && versionMatches) {
            return resumeParsedDataMapper.toResponse(resume, existing);
        }

        if (existing != null && existing.isFailed() && versionMatches) {
            throw parsingFailed(existing);
        }

        if (!fileStorageService.exists(resume.getStorageKey())) {
            log.warn("Resume {} has no stored file", resume.getId());
            throw new ResumeNotFoundException();
        }

        ResumeParsedData parsed;
        long started = System.nanoTime();
        try {
            parsed = parseOnDemand(resume, existing);
        } finally {
            userMetrics.recordOnDemandParse(Duration.ofNanos(System.nanoTime() - started));
        }

        if (parsed.isFailed()) {
            persistQuietly(resume.getId(), parsed);
            throw parsingFailed(parsed);
        }

        ResumeParsedDataResponse response = resumeParsedDataMapper.toResponse(resume, parsed);
        persistQuietly(resume.getId(), parsed);
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

    private ResumeParsedData parseOnDemand(Resume resume, ResumeParsedData existing) {
        int timeoutSeconds = Math.max(1, resumeProperties.getParsing().getTimeoutSeconds());
        try {
            return CompletableFuture
                    .supplyAsync(() -> resumeParser.parseWithRetry(resume, existing), resumeParsingExecutor)
                    .get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            throw new ResumeParsingException("Resume parsing timed out.");
        } catch (TaskRejectedException ex) {
            throw new ResumeParsingException("Resume parsing is busy. Please retry shortly.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResumeParsingException("Resume parsing was interrupted.");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ResumeParsingException("Resume could not be parsed.", cause);
        }
    }

    private ResumeParsingException parsingFailed(ResumeParsedData parsed) {
        String message = parsed.getLastError();
        if (message == null || message.isBlank()) {
            message = "Resume could not be parsed.";
        }
        return new ResumeParsingException(message);
    }

    private void scheduleAfterCommit(Long resumeId) {

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            submitParse(resumeId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submitParse(resumeId);
            }
        });
    }

    private void persistQuietly(Long resumeId, ResumeParsedData parsed) {
        try {
            resumeParsingWorker.persistAsync(resumeId, parsed);
        } catch (TaskRejectedException ex) {
            log.warn("Parse persist queue full for resume {}", resumeId);
        }
    }

    private void submitParse(Long resumeId) {
        try {
            resumeParsingWorker.parseAndPersist(resumeId);
        } catch (TaskRejectedException ex) {
            log.warn("Parse queue full, resume {} stays PENDING", resumeId);
        }
    }
}
