package com.developer.copilot.user.service;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.storage.dto.StoredFile;
import com.developer.copilot.common.storage.service.FileStorageService;
import com.developer.copilot.user.config.ResumeProperties;
import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.UserProfile;
import com.developer.copilot.user.exception.DuplicateResumeException;
import com.developer.copilot.user.exception.InvalidResumeException;
import com.developer.copilot.user.exception.ResumeLimitExceededException;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.UserProfileNotFoundException;
import com.developer.copilot.user.mapper.ResumeMapper;
import com.developer.copilot.user.repository.ResumeRepository;
import com.developer.copilot.user.repository.UserProfileRepository;
import com.developer.copilot.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ResumeProperties resumeProperties;

    @Mock
    private ResumeMapper resumeMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ResumeParsingService resumeParsingService;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;
    private UserProfile profile;
    private MockMultipartFile pdfFile;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(10L);

        profile = UserProfile.builder().id(1L).user(user).build();

        pdfFile = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                "%PDF-1.4 test content".getBytes()
        );

        when(currentUserService.getCurrentUser()).thenReturn(user);
        lenient().when(resumeProperties.getMaxResumeCount()).thenReturn(10);
        lenient().when(resumeProperties.getMaxFileSizeMb()).thenReturn(5);
    }

    @Test
    void uploadResume_withoutProfile_throwsUserProfileNotFound() {
        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.empty());

        assertThrows(UserProfileNotFoundException.class, () -> userService.uploadResume(pdfFile));
    }

    @Test
    void uploadResume_nonPdfContent_throwsInvalidResume() {
        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));

        MockMultipartFile textFile = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                "plain text".getBytes()
        );

        assertThrows(InvalidResumeException.class, () -> userService.uploadResume(textFile));
    }

    @Test
    void uploadResume_success_savesResume() {
        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));
        when(resumeRepository.countByUserProfileAndActiveTrue(profile)).thenReturn(0L);
        when(fileStorageService.upload(eq(pdfFile), eq("users/10/resumes")))
                .thenReturn(StoredFile.builder()
                        .storageKey("users/10/resumes/uuid.pdf")
                        .originalFilename("resume.pdf")
                        .checksum("abc")
                        .fileSize(100L)
                        .contentType("application/pdf")
                        .build());
        when(resumeRepository.findByChecksumAndUserProfileAndActiveTrue("abc", profile))
                .thenReturn(Optional.empty());
        when(resumeRepository.saveAndFlush(any(Resume.class))).thenAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            resume.setId(5L);
            return resume;
        });

        var response = userService.uploadResume(pdfFile);

        assertEquals(5L, response.getResumeId());
        verify(resumeRepository).saveAndFlush(argThat(r -> r.getHighPriority()));
        verify(resumeParsingService).initializeAndScheduleParsing(argThat(r -> r.getId().equals(5L)));
    }

    @Test
    void uploadResume_duplicateChecksum_deletesStorageAndThrows() {
        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));
        when(resumeRepository.countByUserProfileAndActiveTrue(profile)).thenReturn(0L);
        when(fileStorageService.upload(any(MultipartFile.class), anyString()))
                .thenReturn(StoredFile.builder()
                        .storageKey("key")
                        .originalFilename("resume.pdf")
                        .checksum("dup")
                        .fileSize(10L)
                        .contentType("application/pdf")
                        .build());
        when(resumeRepository.findByChecksumAndUserProfileAndActiveTrue("dup", profile))
                .thenReturn(Optional.of(new Resume()));

        assertThrows(DuplicateResumeException.class, () -> userService.uploadResume(pdfFile));
        verify(fileStorageService).delete("key");
    }

    @Test
    void uploadResume_uniqueConstraintRace_mapsToDuplicate() {
        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));
        when(resumeRepository.countByUserProfileAndActiveTrue(profile)).thenReturn(0L);
        when(fileStorageService.upload(any(MultipartFile.class), anyString()))
                .thenReturn(StoredFile.builder()
                        .storageKey("key")
                        .originalFilename("resume.pdf")
                        .checksum("abc")
                        .fileSize(10L)
                        .contentType("application/pdf")
                        .build());
        when(resumeRepository.findByChecksumAndUserProfileAndActiveTrue("abc", profile))
                .thenReturn(Optional.empty());
        when(resumeRepository.saveAndFlush(any(Resume.class)))
                .thenThrow(new DataIntegrityViolationException("uk_resume_profile_checksum"));

        assertThrows(DuplicateResumeException.class, () -> userService.uploadResume(pdfFile));
        verify(fileStorageService).delete("key");
    }

    @Test
    void uploadResume_filenameTooLong_throwsInvalidResume() {
        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));
        MockMultipartFile longName = new MockMultipartFile(
                "file",
                "a".repeat(256) + ".pdf",
                "application/pdf",
                "%PDF-1.4 test".getBytes()
        );

        InvalidResumeException ex = assertThrows(
                InvalidResumeException.class, () -> userService.uploadResume(longName));
        assertTrue(ex.getMessage().contains("255"));
        verify(fileStorageService, never()).upload(any(), any());
    }

    @Test
    void uploadResume_emptyFile_throwsInvalidResume() {
        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));
        MockMultipartFile empty = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", new byte[0]);

        InvalidResumeException ex = assertThrows(
                InvalidResumeException.class, () -> userService.uploadResume(empty));
        assertEquals("Resume cannot be empty.", ex.getMessage());
        verify(fileStorageService, never()).upload(any(), any());
    }

    @Test
    void uploadResume_oversize_throwsInvalidResume() throws Exception {
        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));
        MultipartFile big = mock(MultipartFile.class);
        when(big.isEmpty()).thenReturn(false);
        when(big.getContentType()).thenReturn("application/pdf");
        when(big.getSize()).thenReturn(5L * 1024 * 1024 + 1);
        when(big.getInputStream()).thenReturn(new java.io.ByteArrayInputStream("%PDF-1.4".getBytes()));

        InvalidResumeException ex = assertThrows(
                InvalidResumeException.class, () -> userService.uploadResume(big));
        assertEquals("Maximum file size is 5 MB.", ex.getMessage());
        verify(fileStorageService, never()).upload(any(), any());
    }

    @Test
    void uploadResume_atMaxCount_throwsLimitExceeded() {
        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));
        when(resumeRepository.countByUserProfileAndActiveTrue(profile)).thenReturn(10L);

        assertThrows(ResumeLimitExceededException.class, () -> userService.uploadResume(pdfFile));
        verify(fileStorageService, never()).upload(any(), any());
    }

    @Test
    void uploadResume_pngContentType_throwsInvalidResume() {
        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));
        MockMultipartFile png = new MockMultipartFile(
                "file", "resume.pdf", "image/png", "%PDF-1.4 test".getBytes());

        InvalidResumeException ex = assertThrows(
                InvalidResumeException.class, () -> userService.uploadResume(png));
        assertEquals("Only PDF files are allowed.", ex.getMessage());
    }

    @Test
    void uploadResume_secondResume_isNotPrimary() {
        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));
        when(resumeRepository.countByUserProfileAndActiveTrue(profile)).thenReturn(1L);
        when(fileStorageService.upload(eq(pdfFile), eq("users/10/resumes")))
                .thenReturn(StoredFile.builder()
                        .storageKey("users/10/resumes/uuid.pdf")
                        .originalFilename("resume.pdf")
                        .checksum("abc")
                        .fileSize(100L)
                        .contentType("application/pdf")
                        .build());
        when(resumeRepository.findByChecksumAndUserProfileAndActiveTrue("abc", profile))
                .thenReturn(Optional.empty());
        when(resumeRepository.saveAndFlush(any(Resume.class))).thenAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            resume.setId(6L);
            return resume;
        });

        userService.uploadResume(pdfFile);

        verify(resumeRepository).saveAndFlush(argThat(r -> !r.getHighPriority()));
    }

    @Test
    void uploadResume_saveFails_deletesStoredObject() {
        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));
        when(resumeRepository.countByUserProfileAndActiveTrue(profile)).thenReturn(0L);
        when(fileStorageService.upload(any(MultipartFile.class), anyString()))
                .thenReturn(StoredFile.builder()
                        .storageKey("key")
                        .originalFilename("resume.pdf")
                        .checksum("abc")
                        .fileSize(10L)
                        .contentType("application/pdf")
                        .build());
        when(resumeRepository.findByChecksumAndUserProfileAndActiveTrue("abc", profile))
                .thenReturn(Optional.empty());
        when(resumeRepository.saveAndFlush(any(Resume.class)))
                .thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, () -> userService.uploadResume(pdfFile));
        verify(fileStorageService).delete("key");
    }

    @Test
    void getAllResumes_withoutProfile_throwsUserProfileNotFound() {
        when(userProfileRepository.findByUser(user)).thenReturn(Optional.empty());

        assertThrows(UserProfileNotFoundException.class, () -> userService.getAllResumes());
    }

    @Test
    void deleteResume_promotesRemainingPrimary() {
        Resume primary = Resume.builder()
                .id(2L)
                .userProfile(profile)
                .storageKey("key-2")
                .highPriority(true)
                .active(true)
                .build();
        Resume remaining = Resume.builder()
                .id(3L)
                .userProfile(profile)
                .storageKey("key-3")
                .highPriority(false)
                .active(true)
                .build();

        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));
        when(resumeRepository.findByIdAndUserProfileAndActiveTrue(2L, profile))
                .thenReturn(Optional.of(primary));
        when(resumeRepository.findByUserProfileAndActiveTrueOrderByCreatedAtDesc(profile))
                .thenReturn(List.of(remaining));

        userService.deleteResume(2L);

        verify(resumeParsingService).deleteParsedDataFor(primary);
        verify(resumeRepository).delete(primary);
        verify(resumeRepository).save(argThat(r -> r.getId().equals(3L) && r.getHighPriority()));
        verify(fileStorageService).delete("key-2");
    }

    @Test
    void deleteResume_lastResume_doesNotPromote() {
        Resume only = Resume.builder()
                .id(2L)
                .userProfile(profile)
                .storageKey("key-2")
                .highPriority(true)
                .active(true)
                .build();

        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));
        when(resumeRepository.findByIdAndUserProfileAndActiveTrue(2L, profile))
                .thenReturn(Optional.of(only));
        when(resumeRepository.findByUserProfileAndActiveTrueOrderByCreatedAtDesc(profile))
                .thenReturn(List.of());

        userService.deleteResume(2L);

        verify(resumeRepository).delete(only);
        verify(resumeRepository, never()).save(any(Resume.class));
        verify(fileStorageService).delete("key-2");
    }

    @Test
    void downloadResume_returnsOriginalFilename() {
        Resume resume = Resume.builder()
                .id(1L)
                .userProfile(profile)
                .storageKey("key")
                .originalFilename("John_Resume.pdf")
                .active(true)
                .build();

        when(userProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(resumeRepository.findByIdAndUserProfileAndActiveTrue(1L, profile))
                .thenReturn(Optional.of(resume));
        when(fileStorageService.download("key"))
                .thenReturn(new ByteArrayResource("%PDF".getBytes()));

        var download = userService.downloadResume(1L);

        assertEquals("John_Resume.pdf", download.filename());
    }

    @Test
    void setHighPriorityResume_clearsPreviousPrimary() {
        Resume selected = Resume.builder()
                .id(4L)
                .userProfile(profile)
                .active(true)
                .highPriority(false)
                .build();

        when(userProfileRepository.findByUserForUpdate(user)).thenReturn(Optional.of(profile));
        when(resumeRepository.findByIdAndUserProfileAndActiveTrue(4L, profile))
                .thenReturn(Optional.of(selected));

        userService.setHighPriorityResume(4L);

        verify(resumeRepository).clearHighPriorityForProfile(profile);
        verify(resumeRepository).save(argThat(r -> r.getHighPriority()));
    }

    @Test
    void downloadResume_notFound_throwsResumeNotFound() {
        when(userProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(resumeRepository.findByIdAndUserProfileAndActiveTrue(99L, profile))
                .thenReturn(Optional.empty());

        assertThrows(ResumeNotFoundException.class, () -> userService.downloadResume(99L));
    }
}
