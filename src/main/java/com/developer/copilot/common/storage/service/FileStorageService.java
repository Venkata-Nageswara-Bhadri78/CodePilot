package com.developer.copilot.common.storage.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import com.developer.copilot.common.storage.dto.StoredFile;

/**
 * Abstraction over the underlying object storage backend (MinIO today) so the rest of the
 * codebase never has to know where or how files are physically stored.
 * <p>
 * <b>Important - {@code folderPath} / {@code storageKey} trust contract:</b> implementations
 * validate these arguments against path-traversal characters and an allow-listed character
 * set before every call, but that check only catches unsafe <i>characters</i>, not unsafe
 * <i>logic</i>. Callers must always build {@code folderPath} and {@code storageKey} from the
 * authenticated caller's own id (e.g. {@code "users/" + currentUser.getId() + "/resumes"}) or
 * another value already verified to belong to that user - never directly from a raw,
 * client-supplied request parameter. A "technically valid" but logically wrong path (e.g.
 * another user's id due to a bug elsewhere) will still pass validation.
 */
public interface FileStorageService {
    void initializeStorage();
    StoredFile upload(MultipartFile file, String folderPath);
    Resource download(String storageKey);
    void delete(String storageKey);
    boolean exists(String storageKey);
}