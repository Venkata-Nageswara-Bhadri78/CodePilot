package com.developer.copilot.common.storage.startup;

import com.developer.copilot.common.storage.service.FileStorageService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorageStartupValidator {

    private final FileStorageService fileStorageService;

    @PostConstruct
    public void initialize() {
        fileStorageService.initializeStorage();
    }
}