package com.developer.copilot.common.storage.exception;

/**
 * Thrown for client-caused, expected validation failures when uploading or referencing a
 * file (wrong content type, empty file, malformed/unsafe folder path or storage key).
 * <p>
 * Kept distinct from {@link StorageException}, which is reserved for genuine infrastructure
 * failures (MinIO unreachable, unexpected I/O errors). Separating the two lets callers log
 * and alert on them at different severities: a user uploading a `.docx` by mistake is a
 * normal, expected event, whereas a storage backend failure is an operational incident.
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }

    public InvalidFileException(String message, Throwable cause) {
        super(message, cause);
    }

}
