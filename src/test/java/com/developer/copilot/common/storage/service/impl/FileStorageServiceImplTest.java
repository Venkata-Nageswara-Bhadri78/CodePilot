package com.developer.copilot.common.storage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.developer.copilot.common.storage.config.StorageProperties;
import com.developer.copilot.common.storage.exception.StorageException;

import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceImplTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private StorageProperties storageProperties;

    @InjectMocks
    private FileStorageServiceImpl fileStorageService;

    @BeforeEach
    void setUp() {
        lenient().when(storageProperties.getBucketName()).thenReturn("copilot");
    }

    @Test
    void upload_rejectsPathTraversalBeforeMinio() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "%PDF-1.4 content".getBytes());

        assertThrows(StorageException.class,
                () -> fileStorageService.upload(file, "../other-user/resumes"));

        verify(minioClient, never()).putObject(any());
    }

    @Test
    void upload_rejectsNonPdfContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "not-a-pdf".getBytes());

        assertThrows(StorageException.class,
                () -> fileStorageService.upload(file, "users/1/resumes"));
    }

    @Test
    void download_rejectsUnsafeKey() {
        assertThrows(StorageException.class,
                () -> fileStorageService.download("users/1/../2/resumes/a.pdf"));
    }

    @Test
    void exists_returnsFalseForMissingObject() throws Exception {
        ErrorResponse errorResponse = mock(ErrorResponse.class);
        when(errorResponse.code()).thenReturn("NoSuchKey");
        ErrorResponseException missing = mock(ErrorResponseException.class);
        when(missing.errorResponse()).thenReturn(errorResponse);
        when(minioClient.statObject(any())).thenThrow(missing);

        assertFalse(fileStorageService.exists("users/1/resumes/file.pdf"));
    }

    @Test
    void exists_throwsForInfrastructureFailure() throws Exception {
        when(minioClient.statObject(any())).thenThrow(new RuntimeException("connection refused"));

        StorageException ex = assertThrows(StorageException.class,
                () -> fileStorageService.exists("users/1/resumes/file.pdf"));

        assertTrue(ex.getMessage().contains("Failed to check file existence"));
        assertEquals("connection refused", ex.getCause().getMessage());
    }
}
