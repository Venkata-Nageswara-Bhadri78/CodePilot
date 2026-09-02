package com.developer.copilot.common.storage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.developer.copilot.common.storage.config.StorageProperties;
import com.developer.copilot.common.storage.dto.StoredFile;
import com.developer.copilot.common.storage.exception.InvalidFileException;
import com.developer.copilot.common.storage.exception.StorageException;

import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;

class FileStorageServiceImplTest {

    private MinioClient minioClient;
    private StorageProperties storageProperties;
    private FileStorageServiceImpl fileStorageService;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        storageProperties = mock(StorageProperties.class);
        when(storageProperties.getBucketName()).thenReturn("copilot");
        fileStorageService = new FileStorageServiceImpl(minioClient, storageProperties);
    }

    @Test
    void upload_rejectsPathTraversalBeforeMinio() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "%PDF-1.4 content".getBytes());

        assertThrows(InvalidFileException.class,
                () -> fileStorageService.upload(file, "../other-user/resumes"));

        verify(minioClient, never()).putObject(any());
    }

    @Test
    void upload_rejectsNonPdfContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "not-a-pdf".getBytes());

        assertThrows(InvalidFileException.class,
                () -> fileStorageService.upload(file, "users/1/resumes"));
    }

    @Test
    void upload_rejectsMagicByteMismatch_evenWithCorrectExtensionAndContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "NOT-PDF-MAGIC-BYTES".getBytes());

        InvalidFileException ex = assertThrows(InvalidFileException.class,
                () -> fileStorageService.upload(file, "users/1/resumes"));

        assertEquals("Only PDF uploads are supported.", ex.getMessage());
        verify(minioClient, never()).putObject(any());
    }

    @Test
    void upload_succeedsForGenuinelyValidPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "%PDF-1.7 real content".getBytes());

        StoredFile stored = fileStorageService.upload(file, "users/1/resumes");

        assertNotNull(stored);
        assertEquals("application/pdf", stored.getContentType());
        assertNotNull(stored.getChecksum());
        assertEquals((long) "%PDF-1.7 real content".getBytes().length, stored.getFileSize());
        assertTrue(stored.getStorageKey().startsWith("users/1/resumes/"));
        assertTrue(stored.getStorageKey().endsWith(".pdf"));
        verify(minioClient, times(1)).putObject(any());
    }

    @Test
    void upload_rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[0]);

        assertThrows(InvalidFileException.class,
                () -> fileStorageService.upload(file, "users/1/resumes"));
    }

    @Test
    void download_rejectsUnsafeKey() {
        assertThrows(InvalidFileException.class,
                () -> fileStorageService.download("users/1/../2/resumes/a.pdf"));
    }

    @Test
    void download_wrapsUnexpectedMinioFailureAsStorageException_preservingCause() throws Exception {
        RuntimeException minioFailure = new RuntimeException("connection reset");
        when(minioClient.getObject(any())).thenThrow(minioFailure);

        StorageException ex = assertThrows(StorageException.class,
                () -> fileStorageService.download("users/1/resumes/a.pdf"));

        assertEquals(minioFailure, ex.getCause());
    }

    @Test
    void delete_wrapsUnexpectedMinioFailureAsStorageException_preservingCause() throws Exception {
        RuntimeException minioFailure = new RuntimeException("bucket unreachable");
        org.mockito.Mockito.doThrow(minioFailure).when(minioClient).removeObject(any());

        StorageException ex = assertThrows(StorageException.class,
                () -> fileStorageService.delete("users/1/resumes/a.pdf"));

        assertEquals(minioFailure, ex.getCause());
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

    @Test
    void validateFolderPath_rejectsUnsafeCharactersAndSegments() {
        String[] unsafeFolderPaths = {
                "users//1/resumes",
                "users/./resumes",
                "users/../resumes",
                "users/1 resumes",
                "users/<script>/resumes",
                "users/1;drop/resumes"
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "%PDF-1.4 content".getBytes());

        for (String unsafeFolderPath : unsafeFolderPaths) {
            assertThrows(InvalidFileException.class,
                    () -> fileStorageService.upload(file, unsafeFolderPath),
                    unsafeFolderPath);
        }
    }

    @Test
    void validateStorageKey_rejectsUnsafeCharactersAndSegments() {
        String[] unsafeKeys = {
                "users/1//resumes/a.pdf",
                "users/./resumes/a.pdf",
                "users/../resumes/a.pdf",
                "users/1/re sumes/a.pdf",
                "users/1/<script>/a.pdf"
        };
        for (String unsafeKey : unsafeKeys) {
            assertThrows(InvalidFileException.class,
                    () -> fileStorageService.download(unsafeKey),
                    unsafeKey);
        }
    }

    @Test
    void initializeStorage_bucketAlreadyExists_doesNotCreateBucket() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);

        fileStorageService.initializeStorage();

        verify(minioClient, never()).makeBucket(any());
    }

    @Test
    void initializeStorage_bucketMissingAndAutoCreateEnabled_createsBucket() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(false);
        when(storageProperties.isAutoCreateBucket()).thenReturn(true);

        fileStorageService.initializeStorage();

        verify(minioClient, times(1)).makeBucket(any());
    }

    @Test
    void initializeStorage_bucketMissingAndAutoCreateDisabled_throwsWithoutCreating() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(false);
        when(storageProperties.isAutoCreateBucket()).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> fileStorageService.initializeStorage());

        verify(minioClient, never()).makeBucket(any());
    }

    @Test
    void initializeStorage_bucketMissingAndAutoCreateDisabled_messageContainsBucketName() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(false);
        when(storageProperties.isAutoCreateBucket()).thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> fileStorageService.initializeStorage());

        assertTrue(ex.getMessage().contains("copilot"));
    }

    @Test
    void exists_returnsFalseForNoSuchObject() throws Exception {
        assertExistsFalseForMissingCode("NoSuchObject");
    }

    @Test
    void exists_returnsFalseForNotFound() throws Exception {
        assertExistsFalseForMissingCode("NotFound");
    }

    private void assertExistsFalseForMissingCode(String code) throws Exception {
        ErrorResponse errorResponse = mock(ErrorResponse.class);
        when(errorResponse.code()).thenReturn(code);
        ErrorResponseException missing = mock(ErrorResponseException.class);
        when(missing.errorResponse()).thenReturn(errorResponse);
        when(minioClient.statObject(any())).thenThrow(missing);

        assertFalse(fileStorageService.exists("users/1/resumes/file.pdf"), code);
    }

    @Test
    void exists_unknownErrorCode_isStorageException() throws Exception {
        ErrorResponse errorResponse = mock(ErrorResponse.class);
        when(errorResponse.code()).thenReturn("AccessDenied");
        ErrorResponseException denied = mock(ErrorResponseException.class);
        when(denied.errorResponse()).thenReturn(errorResponse);
        when(minioClient.statObject(any())).thenThrow(denied);

        assertThrows(StorageException.class,
                () -> fileStorageService.exists("users/1/resumes/file.pdf"));
    }

    @Test
    void download_missingObject_isNotFoundNotStorageOutage() throws Exception {
        ErrorResponse errorResponse = mock(ErrorResponse.class);
        when(errorResponse.code()).thenReturn("NoSuchKey");
        ErrorResponseException missing = mock(ErrorResponseException.class);
        when(missing.errorResponse()).thenReturn(errorResponse);
        when(minioClient.getObject(any())).thenThrow(missing);

        assertThrows(com.developer.copilot.common.storage.exception.StorageObjectNotFoundException.class,
                () -> fileStorageService.download("users/1/resumes/a.pdf"));
    }

    @Test
    void upload_acceptsApplicationXPdfWhenMagicMatches() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/x-pdf", "%PDF-1.4 content".getBytes());

        StoredFile stored = fileStorageService.upload(file, "users/1/resumes");

        assertEquals("application/pdf", stored.getContentType());
        verify(minioClient, times(1)).putObject(any());
    }

    @Test
    void upload_rejectsPngContentTypeEvenWithPdfMagic() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "image/png", "%PDF-1.4 content".getBytes());

        assertThrows(InvalidFileException.class,
                () -> fileStorageService.upload(file, "users/1/resumes"));
    }

    @Test
    void upload_allowsNullContentTypeWhenNameAndMagicArePdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", null, "%PDF-1.4 content".getBytes());

        StoredFile stored = fileStorageService.upload(file, "users/1/resumes");

        assertNotNull(stored.getStorageKey());
    }

    @Test
    void upload_acceptsUppercasePdfExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.PDF", "application/pdf", "%PDF-1.4 content".getBytes());

        assertNotNull(fileStorageService.upload(file, "users/1/resumes"));
    }

    @Test
    void upload_normalizesBackslashFolder() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "%PDF-1.4 content".getBytes());

        StoredFile stored = fileStorageService.upload(file, "\\users\\1\\resumes");

        assertTrue(stored.getStorageKey().startsWith("users/1/resumes/"));
    }

    @Test
    void upload_stripsLeadingAndTrailingSlashesOnFolder() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "%PDF-1.4 content".getBytes());

        StoredFile stored = fileStorageService.upload(file, "/users/1/resumes/");

        assertTrue(stored.getStorageKey().startsWith("users/1/resumes/"));
    }
}
