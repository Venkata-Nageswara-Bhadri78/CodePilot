package com.developer.copilot.user.service.impl;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.storage.dto.StoredFile;
import com.developer.copilot.common.storage.service.FileStorageService;
import com.developer.copilot.user.config.ResumeProperties;
import com.developer.copilot.user.dto.ResumeDownload;
import com.developer.copilot.user.dto.ResumeResponse;
import com.developer.copilot.user.dto.ResumeUploadResponse;
import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.UserProfile;
import com.developer.copilot.user.exception.DuplicateResumeException;
import com.developer.copilot.user.exception.InvalidResumeException;
import com.developer.copilot.user.exception.ResumeLimitExceededException;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.UserProfileNotFoundException;
import com.developer.copilot.user.mapper.ResumeMapper;
import com.developer.copilot.user.metrics.UserMetrics;
import com.developer.copilot.user.repository.ResumeRepository;
import com.developer.copilot.user.repository.UserProfileRepository;
import com.developer.copilot.user.service.ResumeParsingService;
import com.developer.copilot.user.service.UserService;
import com.developer.copilot.user.util.AfterCommitActions;
import com.developer.copilot.user.util.PdfValidationUtil;
import com.developer.copilot.user.util.ResumeFilenameUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserProfileRepository userProfileRepository;
    private final ResumeRepository resumeRepository;
    private final FileStorageService fileStorageService;
    private final ResumeProperties resumeProperties;
    private final ResumeMapper resumeMapper;
    private final CurrentUserService currentUserService;
    private final ResumeParsingService resumeParsingService;
    private final UserMetrics userMetrics;

    @Override
    @Transactional
    public ResumeUploadResponse uploadResume(MultipartFile file) {

        User user = currentUserService.getCurrentUser();

        UserProfile profile = userProfileRepository
                .findByUserForUpdate(user)
                .orElseThrow(UserProfileNotFoundException::new);

        if (resumeRepository.countByUserProfileAndActiveTrue(profile)
                >= resumeProperties.getMaxResumeCount()) {

            userMetrics.recordUploadCap();
            throw new ResumeLimitExceededException(
                    resumeProperties.getMaxResumeCount());
        }

        if (file.isEmpty()) {
            userMetrics.recordUploadInvalid();
            throw new InvalidResumeException("Resume cannot be empty.");
        }

        if (!"application/pdf".equalsIgnoreCase(file.getContentType())
                || !PdfValidationUtil.hasPdfMagicBytes(file)) {
            userMetrics.recordUploadInvalid();
            throw new InvalidResumeException("Only PDF files are allowed.");
        }

        if (file.getSize() > resumeProperties.getMaxFileSizeMb() * 1024L * 1024L) {
            userMetrics.recordUploadInvalid();
            throw new InvalidResumeException(
                    "Maximum file size is "
                            + resumeProperties.getMaxFileSizeMb()
                            + " MB.");
        }

        if (ResumeFilenameUtil.isTooLong(file.getOriginalFilename())) {
            userMetrics.recordUploadInvalid();
            throw new InvalidResumeException(
                    "Original filename must not exceed "
                            + ResumeFilenameUtil.MAX_FILENAME_LENGTH
                            + " characters.");
        }

        StoredFile storedFile = fileStorageService.upload(
                file,
                "users/" + user.getId() + "/resumes"
        );

        if (resumeRepository.findByChecksumAndUserProfileAndActiveTrue(
                storedFile.getChecksum(),
                profile
        ).isPresent()) {

            fileStorageService.delete(storedFile.getStorageKey());
            userMetrics.recordUploadDuplicate();
            throw new DuplicateResumeException();
        }

        boolean firstResume = resumeRepository.countByUserProfileAndActiveTrue(profile) == 0;

        Resume resume = Resume.builder()
                .userProfile(profile)
                .originalFilename(ResumeFilenameUtil.sanitizeForDownload(storedFile.getOriginalFilename()))
                .storageKey(storedFile.getStorageKey())
                .checksum(storedFile.getChecksum())
                .fileSize(storedFile.getFileSize())
                .contentType(storedFile.getContentType())
                .highPriority(firstResume)
                .build();

        try {
            resumeRepository.saveAndFlush(resume);
            resumeParsingService.initializeAndScheduleParsing(resume);
        } catch (DataIntegrityViolationException ex) {
            fileStorageService.delete(storedFile.getStorageKey());
            userMetrics.recordUploadDuplicate();
            throw new DuplicateResumeException();
        } catch (RuntimeException ex) {
            fileStorageService.delete(storedFile.getStorageKey());
            throw ex;
        }

        log.info("User {} uploaded resume {}", user.getId(), resume.getId());
        userMetrics.recordUploadSuccess();

        return ResumeUploadResponse.builder()
                .resumeId(resume.getId())
                .message("Resume uploaded successfully.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResumeResponse> getAllResumes() {

        User user = currentUserService.getCurrentUser();

        UserProfile profile = userProfileRepository
                .findByUser(user)
                .orElseThrow(UserProfileNotFoundException::new);

        return resumeRepository
                .findByUserProfileAndActiveTrue(profile)
                .stream()
                .map(resumeMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ResumeDownload downloadResume(Long resumeId) {

        User user = currentUserService.getCurrentUser();

        UserProfile profile = userProfileRepository
                .findByUser(user)
                .orElseThrow(UserProfileNotFoundException::new);

        Resume resume = resumeRepository
                .findByIdAndUserProfileAndActiveTrue(resumeId, profile)
                .orElseThrow(ResumeNotFoundException::new);

        Resource resource = fileStorageService.download(resume.getStorageKey());

        return new ResumeDownload(resource, resume.getOriginalFilename());
    }

    @Override
    @Transactional
    public void deleteResume(Long resumeId) {

        User user = currentUserService.getCurrentUser();

        UserProfile profile = userProfileRepository
                .findByUserForUpdate(user)
                .orElseThrow(UserProfileNotFoundException::new);

        Resume resume = resumeRepository
                .findByIdAndUserProfileAndActiveTrue(resumeId, profile)
                .orElseThrow(ResumeNotFoundException::new);

        boolean wasPrimary = Boolean.TRUE.equals(resume.getHighPriority());
        String storageKey = resume.getStorageKey();

        resumeParsingService.deleteParsedDataFor(resume);
        resumeRepository.delete(resume);

        if (wasPrimary) {
            promoteMostRecentResume(profile);
        }

        AfterCommitActions.run(() -> {
            try {
                fileStorageService.delete(storageKey);
            } catch (RuntimeException ex) {
                log.error("Failed to delete stored resume {} after commit: {}", resumeId, ex.getMessage());
                userMetrics.recordMinioDeleteFailure();
            }
        });

        log.info("User {} deleted resume {}", user.getId(), resumeId);
    }

    @Override
    @Transactional
    public void setHighPriorityResume(Long resumeId) {

        User user = currentUserService.getCurrentUser();

        UserProfile profile = userProfileRepository
                .findByUserForUpdate(user)
                .orElseThrow(UserProfileNotFoundException::new);

        Resume selectedResume = resumeRepository
                .findByIdAndUserProfileAndActiveTrue(resumeId, profile)
                .orElseThrow(ResumeNotFoundException::new);

        resumeRepository.clearHighPriorityForProfile(profile);

        selectedResume.setHighPriority(true);
        resumeRepository.save(selectedResume);

        log.info("User {} set resume {} as high priority", user.getId(), resumeId);
    }

    private void promoteMostRecentResume(UserProfile profile) {
        resumeRepository
                .findByUserProfileAndActiveTrueOrderByCreatedAtDesc(profile)
                .stream()
                .findFirst()
                .ifPresent(remaining -> {
                    remaining.setHighPriority(true);
                    resumeRepository.save(remaining);
                });
    }
}
