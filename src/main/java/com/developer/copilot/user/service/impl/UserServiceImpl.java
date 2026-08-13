package com.developer.copilot.user.service.impl;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.storage.dto.StoredFile;
import com.developer.copilot.common.storage.service.FileStorageService;
import com.developer.copilot.user.config.ResumeProperties;
import com.developer.copilot.user.dto.ResumeResponse;
import com.developer.copilot.user.dto.ResumeUploadResponse;
import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.UserProfile;
import com.developer.copilot.user.exception.DuplicateResumeException;
import com.developer.copilot.user.exception.InvalidResumeException;
import com.developer.copilot.user.exception.ResumeLimitExceededException;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.mapper.ResumeMapper;
import com.developer.copilot.user.repository.ResumeRepository;
import com.developer.copilot.user.repository.UserProfileRepository;
import com.developer.copilot.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional
    public ResumeUploadResponse uploadResume(MultipartFile file) {

        User user = currentUserService.getCurrentUser();

        UserProfile profile = userProfileRepository
                .findByUser(user)
                .orElseGet(() -> {
                    UserProfile userProfile = UserProfile.builder()
                            .user(user)
                            .build();
                    return userProfileRepository.save(userProfile);
                });

        if (resumeRepository.countByUserProfileAndActiveTrue(profile)
                >= resumeProperties.getMaxResumeCount()) {

            throw new ResumeLimitExceededException(
                    resumeProperties.getMaxResumeCount());

        }

        if (file.isEmpty()) {
            throw new InvalidResumeException("Resume cannot be empty.");
        }

        if (!"application/pdf".equalsIgnoreCase(file.getContentType())) {
            throw new InvalidResumeException("Only PDF files are allowed.");
        }

        if (file.getSize() >
                resumeProperties.getMaxFileSizeMb() * 1024L * 1024L) {

            throw new InvalidResumeException(
                    "Maximum file size is "
                            + resumeProperties.getMaxFileSizeMb()
                            + " MB."
            );
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

            throw new DuplicateResumeException();
        }

        boolean firstResume = resumeRepository.countByUserProfileAndActiveTrue(profile) == 0;

        Resume resume = Resume.builder()
                .userProfile(profile)
                .originalFilename(storedFile.getOriginalFilename())
                .storageKey(storedFile.getStorageKey())
                .checksum(storedFile.getChecksum())
                .fileSize(storedFile.getFileSize())
                .contentType(storedFile.getContentType())
                .highPriority(firstResume)
                .build();

                try {
                        resumeRepository.save(resume);
                    
                } catch (Exception ex) {
                    
                        fileStorageService.delete(storedFile.getStorageKey());
                    
                    throw ex;
                    
                }

        log.info(
                "User {} uploaded resume '{}'",
                user.getId(),
                storedFile.getOriginalFilename()
        );

        return ResumeUploadResponse.builder()
                .resumeId(resume.getId())
                .message("Resume uploaded successfully.")
                .build();
    }

    @Override
    public List<ResumeResponse> getAllResumes() {

        User user = currentUserService.getCurrentUser();

        UserProfile profile = userProfileRepository
                .findByUser(user)
                .orElseThrow(ResumeNotFoundException::new);

        return resumeRepository
                .findByUserProfileAndActiveTrue(profile)
                .stream()
                .map(resumeMapper::toResponse)
                .toList();
    }
    
    @Override
    @Transactional
    public Resource downloadResume(Long resumeId) {

        User user = currentUserService.getCurrentUser();

        UserProfile profile = userProfileRepository
                .findByUser(user)
                .orElseThrow(ResumeNotFoundException::new);

        Resume resume = resumeRepository
                .findByIdAndUserProfileAndActiveTrue(
                        resumeId,
                        profile
                )
                .orElseThrow(ResumeNotFoundException::new);

        return fileStorageService.download(
                resume.getStorageKey()
        );

    }
    
    @Override
    public void deleteResume(Long resumeId) {

        User user = currentUserService.getCurrentUser();

        UserProfile profile = userProfileRepository
                .findByUser(user)
                .orElseThrow(ResumeNotFoundException::new);

        Resume resume = resumeRepository
                .findByIdAndUserProfileAndActiveTrue(
                        resumeId,
                        profile
                )
                .orElseThrow(ResumeNotFoundException::new);

        fileStorageService.delete(
                resume.getStorageKey()
        );

        resumeRepository.delete(resume);

        log.info(
                "User {} deleted resume {}",
                user.getId(),
                resumeId
        );

    }
    
    @Override
    @Transactional
    public void setHighPriorityResume(Long resumeId) {

        User user = currentUserService.getCurrentUser();

        UserProfile profile = userProfileRepository
                .findByUser(user)
                .orElseThrow(ResumeNotFoundException::new);

        Resume selectedResume = resumeRepository
                .findByIdAndUserProfileAndActiveTrue(
                        resumeId,
                        profile
                )
                .orElseThrow(ResumeNotFoundException::new);

        resumeRepository
                .findByHighPriorityTrueAndUserProfileAndActiveTrue(profile)
                .ifPresent(existing -> {

                    existing.setHighPriority(false);

                    resumeRepository.save(existing);

                });

        selectedResume.setHighPriority(true);

        resumeRepository.save(selectedResume);

        log.info(
                "User {} set resume {} as high priority",
                user.getId(),
                resumeId
        );

    }
}