package com.developer.copilot.common.storage.service.impl;

import com.developer.copilot.common.storage.config.StorageProperties;
import com.developer.copilot.common.storage.service.FileStorageService;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.developer.copilot.common.storage.dto.StoredFile;
import com.developer.copilot.common.storage.exception.StorageException;
import com.developer.copilot.common.storage.util.ChecksumUtil;
import io.minio.PutObjectArgs;

import io.minio.RemoveObjectArgs;

import io.minio.GetObjectArgs;
import org.springframework.core.io.InputStreamResource;


import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final byte[] PDF_MAGIC = new byte[]{'%', 'P', 'D', 'F'};

    private final MinioClient minioClient;
    private final StorageProperties storageProperties;

    @Override
    public StoredFile upload(MultipartFile file, String folderPath) {
        String safeFolder = validateFolderPath(folderPath);
        validatePdfUpload(file);

        try {
            byte[] fileBytes = file.getBytes();
            String checksum = ChecksumUtil.generateSha256(new ByteArrayInputStream(fileBytes));
            String storageKey = safeFolder + "/" + UUID.randomUUID() + ".pdf";
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(storageProperties.getBucketName())
                            .object(storageKey)
                            .stream(
                                    new ByteArrayInputStream(fileBytes),
                                    fileBytes.length,
                                    -1
                            )
                            .contentType(PDF_CONTENT_TYPE)
                            .build()
            );

            log.info("File uploaded successfully : {}", storageKey);

            return StoredFile.builder()
                    .storageKey(storageKey)
                    .originalFilename(file.getOriginalFilename())
                    .contentType(PDF_CONTENT_TYPE)
                    .fileSize(file.getSize())
                    .checksum(checksum)
                    .build();
        } catch (StorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new StorageException("Failed to upload file.", ex);
        }

    }

    @Override
    public Resource download(String storageKey) {
        String safeKey = validateStorageKey(storageKey);
        try {
            return new InputStreamResource(
                    minioClient.getObject(
                            GetObjectArgs.builder()
                                    .bucket(storageProperties.getBucketName())
                                    .object(safeKey)
                                    .build()
                    )
            );

        } catch (Exception ex) {
            throw new StorageException("Failed to download file.", ex);
        }

    }

    @Override
    public void delete(String storageKey) {
        String safeKey = validateStorageKey(storageKey);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(storageProperties.getBucketName())
                            .object(safeKey)
                            .build()
            );
            log.info("File deleted successfully : {}", safeKey);
        } catch (Exception ex) {
            throw new StorageException("Failed to delete file.", ex);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        String safeKey = validateStorageKey(storageKey);
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(storageProperties.getBucketName())
                            .object(safeKey)
                            .build()
            );
            return true;
        } catch (ErrorResponseException ex) {
            String code = ex.errorResponse() != null ? ex.errorResponse().code() : null;
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NotFound".equals(code)) {
                return false;
            }
            throw new StorageException("Failed to check file existence.", ex);
        } catch (StorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new StorageException("Failed to check file existence.", ex);
        }
    }

    @Override
    public void initializeStorage() {
        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(storageProperties.getBucketName())
                            .build()
            );
            if (!bucketExists) {
                if (storageProperties.isAutoCreateBucket()) {
                    log.info("Bucket '{}' not found. Creating bucket...", storageProperties.getBucketName());
                    minioClient.makeBucket(
                            MakeBucketArgs.builder()
                                    .bucket(storageProperties.getBucketName())
                                    .build()
                    );
                    log.info("Bucket '{}' created successfully.", storageProperties.getBucketName());
                } else {
                    throw new IllegalStateException(
                            "Storage bucket does not exist: "
                                    + storageProperties.getBucketName()
                    );
                }
            } else {
                log.info("Storage bucket '{}' is available.", storageProperties.getBucketName());
            }

        } catch (Exception ex) {
            log.error("Failed to initialize object storage.", ex);
            throw new IllegalStateException(
                    "Unable to initialize object storage.",
                    ex
            );

        }

    }

    private void validatePdfUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new StorageException("Uploaded file is empty.");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.toLowerCase(Locale.ROOT).contains("pdf")) {
            throw new StorageException("Only PDF uploads are supported.");
        }
        String originalName = file.getOriginalFilename();
        if (StringUtils.hasText(originalName)
                && !originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new StorageException("Only PDF uploads are supported.");
        }
        try {
            byte[] header = file.getInputStream().readNBytes(4);
            if (header.length < 4
                    || header[0] != PDF_MAGIC[0]
                    || header[1] != PDF_MAGIC[1]
                    || header[2] != PDF_MAGIC[2]
                    || header[3] != PDF_MAGIC[3]) {
                throw new StorageException("Only PDF uploads are supported.");
            }
        } catch (StorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new StorageException("Failed to validate uploaded file.", ex);
        }
    }

    private String validateFolderPath(String folderPath) {
        if (!StringUtils.hasText(folderPath)) {
            throw new StorageException("Storage folder path is required.");
        }
        String normalized = folderPath.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        rejectUnsafePath(normalized);
        return normalized;
    }

    private String validateStorageKey(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            throw new StorageException("Storage key is required.");
        }
        String normalized = storageKey.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        rejectUnsafePath(normalized);
        return normalized;
    }

    private void rejectUnsafePath(String path) {
        if (path.contains("..") || path.contains("//")) {
            throw new StorageException("Invalid storage path.");
        }
        for (String segment : path.split("/")) {
            if (!StringUtils.hasText(segment) || ".".equals(segment) || "..".equals(segment)) {
                throw new StorageException("Invalid storage path.");
            }
            for (int i = 0; i < segment.length(); i++) {
                char c = segment.charAt(i);
                if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.')) {
                    throw new StorageException("Invalid storage path.");
                }
            }
        }
    }

}
