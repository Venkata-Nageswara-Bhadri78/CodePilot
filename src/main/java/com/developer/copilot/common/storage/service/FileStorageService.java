package com.developer.copilot.common.storage.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import com.developer.copilot.common.storage.dto.StoredFile;

public interface FileStorageService {
    void initializeStorage();
    StoredFile upload(MultipartFile file, String folderPath);
    Resource download(String storageKey);
    void delete(String storageKey);
    boolean exists(String storageKey);
}