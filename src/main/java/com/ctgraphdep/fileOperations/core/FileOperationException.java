package com.ctgraphdep.fileOperations.core;

import lombok.Getter;

import java.nio.file.Path;

/**
 * Exception thrown when file operations fail.
 */
@Getter
public class FileOperationException extends RuntimeException {
    private final Path filePath;
    private final String operation;

    public FileOperationException(String message, Path filePath, String operation) {
        super(message);
        this.filePath = filePath;
        this.operation = operation;
    }

    public FileOperationException(String message, Path filePath, String operation, Throwable cause) {
        super(message, cause);
        this.filePath = filePath;
        this.operation = operation;
    }

}
