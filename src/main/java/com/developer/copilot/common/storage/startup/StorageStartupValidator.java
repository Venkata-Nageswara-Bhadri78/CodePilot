package com.developer.copilot.common.storage.startup;

import java.util.Arrays;
import java.util.Locale;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.developer.copilot.common.storage.config.StorageProperties;
import com.developer.copilot.common.storage.service.FileStorageService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorageStartupValidator {

    private final FileStorageService fileStorageService;
    private final StorageProperties storageProperties;
    private final Environment environment;

    @PostConstruct
    public void initialize() {
        validateNonLaptopCredentials();
        fileStorageService.initializeStorage();
    }

    /**
     * Loopback MinIO (laptop docker) may still use {@code minioadmin}. A remote endpoint
     * outside {@code local}/{@code dev} must not.
     */
    void validateNonLaptopCredentials() {
        if (isLaptopProfile()) {
            return;
        }
        if (isLoopbackEndpoint(storageProperties.getEndpoint())) {
            return;
        }
        if (isDefaultAdmin(storageProperties.getAccessKey())
                || isDefaultAdmin(storageProperties.getSecretKey())) {
            throw new IllegalStateException(
                    "storage.access-key and storage.secret-key must not be minioadmin outside local/dev.");
        }
        if (storageProperties.isAutoCreateBucket()) {
            throw new IllegalStateException(
                    "storage.auto-create-bucket must be false outside local/dev; pre-create a private bucket.");
        }
    }

    private boolean isLaptopProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("local") || profile.equals("dev"));
    }

    static boolean isLoopbackEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return false;
        }
        String lower = endpoint.toLowerCase(Locale.ROOT);
        return lower.contains("localhost")
                || lower.contains("127.0.0.1")
                || lower.contains("[::1]");
    }

    private static boolean isDefaultAdmin(String value) {
        return StringUtils.hasText(value) && "minioadmin".equalsIgnoreCase(value.trim());
    }
}
