package com.developer.copilot.common.storage.service.impl;

import com.developer.copilot.common.storage.config.StorageProperties;
import com.developer.copilot.common.storage.service.FileStorageService;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.developer.copilot.common.storage.dto.StoredFile;
import com.developer.copilot.common.storage.exception.StorageException;
import com.developer.copilot.common.storage.util.ChecksumUtil;
import io.minio.PutObjectArgs;

import io.minio.RemoveObjectArgs;

import io.minio.GetObjectArgs;
import org.springframework.core.io.InputStreamResource;


import java.io.ByteArrayInputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private final MinioClient minioClient;
    private final StorageProperties storageProperties;

    @Override
    public StoredFile upload(MultipartFile file, String folderPath) {

        try {
            byte[] fileBytes = file.getBytes();
            String checksum = ChecksumUtil.generateSha256(new ByteArrayInputStream(fileBytes));
            String storageKey = folderPath + "/" + UUID.randomUUID() + ".pdf";
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(storageProperties.getBucketName())
                            .object(storageKey)
                            .stream(
                                    new ByteArrayInputStream(fileBytes),
                                    fileBytes.length,
                                    -1
                            )
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("File uploaded successfully : {}", storageKey);

            return StoredFile.builder()
                    .storageKey(storageKey)
                    .originalFilename(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .checksum(checksum)
                    .build();
        } catch (Exception ex) {
            throw new StorageException("Failed to upload file.", ex);
        }

    }

    @Override
    public Resource download(String folderPath) {
        try {
            return new InputStreamResource(
                    minioClient.getObject(
                            GetObjectArgs.builder()
                                    .bucket(storageProperties.getBucketName())
                                    .object(folderPath)
                                    .build()
                    )
            );

        } catch (Exception ex) {
            throw new StorageException("Failed to download file.", ex);
        }

    }

    @Override
    public void delete(String folderPath) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(storageProperties.getBucketName())
                            .object(folderPath)
                            .build()
            );
            log.info("File deleted successfully : {}", folderPath);
        } catch (Exception ex) {
            throw new StorageException("Failed to delete file.", ex);
        }
    }

    @Override
    public boolean exists(String folderPath) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(storageProperties.getBucketName())
                            .object(folderPath)
                            .build()
            );
            return true;
        } catch (Exception ex) {
            return false;
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

}