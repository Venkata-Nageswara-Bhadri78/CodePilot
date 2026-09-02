package com.developer.copilot.common.storage.exception;

/**
 * The object key is valid but MinIO has no object at that key (orphan metadata, wrong
 * bucket, or a delete that already succeeded on storage). Distinct from
 * {@link StorageException} so callers can return 404 instead of paging as an outage.
 */
public class StorageObjectNotFoundException extends RuntimeException {

    public StorageObjectNotFoundException(String message) {
        super(message);
    }

    public StorageObjectNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
