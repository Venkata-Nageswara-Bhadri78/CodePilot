package com.developer.copilot.common.storage.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.minio.MinioClient;

class StorageConfigTest {

    @Test
    void validateProvider_blankOrMinioOrS3_isAccepted() {
        assertDoesNotThrow(() -> StorageConfig.validateProvider(null));
        assertDoesNotThrow(() -> StorageConfig.validateProvider(" "));
        assertDoesNotThrow(() -> StorageConfig.validateProvider("minio"));
        assertDoesNotThrow(() -> StorageConfig.validateProvider("MINIO"));
        assertDoesNotThrow(() -> StorageConfig.validateProvider("s3"));
    }

    @Test
    void validateProvider_unknown_fails() {
        assertThrows(IllegalStateException.class, () -> StorageConfig.validateProvider("ftp"));
    }

    @Test
    void minioClient_usesEndpointAndKeysWithoutConnecting() {
        StorageProperties properties = new StorageProperties();
        properties.setProvider("minio");
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("key");
        properties.setSecretKey("secret");
        properties.setBucketName("copilot");

        MinioClient client = new StorageConfig().minioClient(properties);

        assertNotNull(client);
    }
}
