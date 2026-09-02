package com.developer.copilot.common.storage.startup;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.developer.copilot.common.storage.config.StorageProperties;
import com.developer.copilot.common.storage.service.FileStorageService;

class StorageStartupValidatorTest {

    @Test
    void initialize_invokesFileStorageOnce() {
        FileStorageService fileStorageService = mock(FileStorageService.class);
        StorageProperties properties = loopbackMinioAdmin();
        MockEnvironment environment = new MockEnvironment();

        new StorageStartupValidator(fileStorageService, properties, environment).initialize();

        verify(fileStorageService, times(1)).initializeStorage();
    }

    @Test
    void remoteMinioAdminOutsideLaptop_fails() {
        StorageProperties properties = new StorageProperties();
        properties.setEndpoint("https://minio.example.com");
        properties.setAccessKey("minioadmin");
        properties.setSecretKey("minioadmin");
        properties.setAutoCreateBucket(false);

        StorageStartupValidator validator = new StorageStartupValidator(
                mock(FileStorageService.class), properties, new MockEnvironment());

        assertThrows(IllegalStateException.class, validator::validateNonLaptopCredentials);
    }

    @Test
    void remoteAutoCreateOutsideLaptop_fails() {
        StorageProperties properties = new StorageProperties();
        properties.setEndpoint("https://minio.example.com");
        properties.setAccessKey("real-access");
        properties.setSecretKey("real-secret-that-is-not-default");
        properties.setAutoCreateBucket(true);

        StorageStartupValidator validator = new StorageStartupValidator(
                mock(FileStorageService.class), properties, new MockEnvironment());

        assertThrows(IllegalStateException.class, validator::validateNonLaptopCredentials);
    }

    @Test
    void loopbackMinioAdmin_isAllowedWithoutLaptopProfile() {
        StorageStartupValidator validator = new StorageStartupValidator(
                mock(FileStorageService.class), loopbackMinioAdmin(), new MockEnvironment());

        assertDoesNotThrow(validator::validateNonLaptopCredentials);
    }

    @Test
    void laptopProfile_allowsRemoteMinioAdmin() {
        StorageProperties properties = new StorageProperties();
        properties.setEndpoint("https://minio.example.com");
        properties.setAccessKey("minioadmin");
        properties.setSecretKey("minioadmin");
        properties.setAutoCreateBucket(true);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        StorageStartupValidator validator = new StorageStartupValidator(
                mock(FileStorageService.class), properties, environment);

        assertDoesNotThrow(validator::validateNonLaptopCredentials);
    }

    @Test
    void isLoopbackEndpoint_detectsLocalhost() {
        assertTrue(StorageStartupValidator.isLoopbackEndpoint("http://localhost:9000"));
        assertTrue(StorageStartupValidator.isLoopbackEndpoint("http://127.0.0.1:9000"));
        assertFalse(StorageStartupValidator.isLoopbackEndpoint("https://minio.example.com"));
        assertFalse(StorageStartupValidator.isLoopbackEndpoint(null));
    }

    private static StorageProperties loopbackMinioAdmin() {
        StorageProperties properties = new StorageProperties();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("minioadmin");
        properties.setSecretKey("minioadmin");
        properties.setAutoCreateBucket(true);
        properties.setBucketName("copilot-resumes");
        return properties;
    }
}
