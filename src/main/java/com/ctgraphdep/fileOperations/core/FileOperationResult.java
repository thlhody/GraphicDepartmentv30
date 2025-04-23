package com.ctgraphdep.fileOperations.core;

import lombok.Getter;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Represents the result of a file operation.
 * Contains information about success/failure, error details if applicable,
 * and the path of the file that was operated on.
 */
@Getter
public class FileOperationResult {

    private final boolean success;
    private final Path filePath;
    private final String errorMessage;
    private final Exception exception;
    private final LocalDateTime timestamp;

    private FileOperationResult(boolean success, Path filePath, String errorMessage, Exception exception) {
        this.success = success;
        this.filePath = filePath;
        this.errorMessage = errorMessage;
        this.exception = exception;
        this.timestamp = LocalDateTime.now();
    }

    public static FileOperationResult success(Path filePath) {
        return new FileOperationResult(true, filePath, null, null);
    }

    public static FileOperationResult failure(Path filePath, String errorMessage) {
        return new FileOperationResult(false, filePath, errorMessage, null);
    }

    public static FileOperationResult failure(Path filePath, String errorMessage, Exception exception) {
        return new FileOperationResult(false, filePath, errorMessage, exception);
    }

    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    public Optional<Exception> getException() {
        return Optional.ofNullable(exception);
    }

    @Override
    public String toString() {
        if (success) {
            return "Operation succeeded for file: " + filePath;
        } else {
            return "Operation failed for file: " + filePath + " - " + errorMessage;
        }
    }
}