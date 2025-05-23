package com.ctgraphdep.fileOperations.core;

import com.ctgraphdep.fileOperations.model.FileTransactionResult;
import com.ctgraphdep.fileOperations.service.BackupService;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enhanced transaction class that represents a transactional unit of file operations.
 * Multiple file operations can be grouped together to be committed or rolled back as a unit.
 * Now supports criticality levels for different types of files.
 */
@Getter
public class FileTransaction {
    private final String transactionId;
    private final Map<Path, byte[]> backups = new HashMap<>();
    private final List<FileOperation> operations = new ArrayList<>();
    private boolean active = true;

    public FileTransaction() {
        this.transactionId = UUID.randomUUID().toString();
        LoggerUtil.debug(this.getClass(), "Created new file transaction with ID: " + transactionId);
    }

    /**
     * Add a write operation to this transaction
     *
     * @param filePath The file path to write to
     * @param data The data to write
     */
    public void addWrite(FilePath filePath, byte[] data) {
        if (!active) {
            throw new IllegalStateException("Cannot add operations to a completed transaction");
        }

        Path path = filePath.getPath();
        try {
            // Create backup if file exists and we don't already have one
            if (Files.exists(path) && !backups.containsKey(path)) {
                byte[] backupData = Files.readAllBytes(path);
                backups.put(path, backupData);
                LoggerUtil.debug(this.getClass(), "Created in-memory backup for: " + path);
            }

            // Add operation to the list
            operations.add(new WriteOperation(filePath, data));
            LoggerUtil.debug(this.getClass(), "Added write operation to transaction " + transactionId + ": " + path);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error preparing write operation for path " + path + ": " + e.getMessage(), e);
            throw new FileOperationException("Failed to prepare write operation", path, "WRITE_PREPARE", e);
        }
    }

    /**
     * Add a sync operation to this transaction
     *
     * @param sourceFile The source file to sync from
     * @param targetFile The target file to sync to
     */
    public void addSync(FilePath sourceFile, FilePath targetFile) {
        if (!active) {
            throw new IllegalStateException("Cannot add operations to a completed transaction");
        }

        operations.add(new SyncOperation(sourceFile, targetFile));
        LoggerUtil.debug(this.getClass(), "Added sync operation to transaction " + transactionId + ": "
                + sourceFile.getPath() + " -> " + targetFile.getPath());
    }

    /**
     * Execute all operations in this transaction
     *
     * @return A result object indicating success or failure
     */
    public FileTransactionResult commit() {
        if (!active) {
            return FileTransactionResult.failure(transactionId, "Transaction is no longer active");
        }

        LoggerUtil.info(this.getClass(), "Committing transaction " + transactionId
                + " with " + operations.size() + " operations");

        List<FileOperationResult> results = new ArrayList<>();
        boolean allSucceeded = true;

        // Execute operations
        for (FileOperation operation : operations) {
            try {
                LoggerUtil.debug(this.getClass(), "Executing operation: " + operation.getClass().getSimpleName());
                FileOperationResult result = operation.execute();
                results.add(result);

                if (!result.isSuccess()) {
                    allSucceeded = false;
                    LoggerUtil.warn(this.getClass(), "Operation failed: " + result.getErrorMessage().orElse("Unknown error"));
                } else {
                    LoggerUtil.debug(this.getClass(), "Operation succeeded");
                }
            } catch (Exception e) {
                FileOperationResult result = FileOperationResult.failure(
                        operation instanceof WriteOperation ?
                                ((WriteOperation) operation).filePath.getPath() :
                                ((SyncOperation) operation).sourceFile.getPath(),
                        "Unexpected exception during operation: " + e.getMessage(),
                        e
                );
                results.add(result);
                allSucceeded = false;
                LoggerUtil.error(this.getClass(), "Exception during transaction operation: " + e.getMessage(), e);
            }
        }

        // If everything succeeded, clear backups
        if (allSucceeded) {
            backups.clear();
            LoggerUtil.info(this.getClass(), "Transaction " + transactionId + " committed successfully");
        } else {
            // Otherwise, roll back
            rollback();
            LoggerUtil.warn(this.getClass(), "Transaction " + transactionId + " failed, rolled back");
        }

        active = false;
        return allSucceeded ?
                FileTransactionResult.success(transactionId, results) :
                FileTransactionResult.failure(transactionId, "One or more operations failed", results);
    }

    /**
     * Roll back all changes made by this transaction
     *
     * @return A result indicating success or failure of the rollback
     */
    public FileTransactionResult rollback() {
        if (!active) {
            return FileTransactionResult.failure(transactionId, "Transaction is no longer active");
        }

        LoggerUtil.info(this.getClass(), "Rolling back transaction " + transactionId
                + " with " + backups.size() + " backups");

        List<FileOperationResult> results = new ArrayList<>();
        boolean allSucceeded = true;

        // Restore all backups
        for (Map.Entry<Path, byte[]> entry : backups.entrySet()) {
            try {
                Files.write(entry.getKey(), entry.getValue());
                results.add(FileOperationResult.success(entry.getKey()));
                LoggerUtil.debug(this.getClass(), "Restored file from in-memory backup: " + entry.getKey());
            } catch (Exception e) {
                results.add(FileOperationResult.failure(entry.getKey(), "Failed to restore backup: " + e.getMessage(), e));
                allSucceeded = false;
                LoggerUtil.error(this.getClass(), "Error restoring file from backup: " + e.getMessage(), e);
            }
        }

        active = false;
        return allSucceeded ?
                FileTransactionResult.success(transactionId, results) :
                FileTransactionResult.failure(transactionId, "Rollback partially failed", results);
    }

    /**
     * Base class for operations in a transaction
     */
    private abstract static class FileOperation {
        public abstract FileOperationResult execute();
    }

    /**
     * Represents a write operation in a transaction
     */
    private static class WriteOperation extends FileOperation {
        private final FilePath filePath;
        private final byte[] data;

        public WriteOperation(FilePath filePath, byte[] data) {
            this.filePath = filePath;
            this.data = data;
        }

        @Override
        public FileOperationResult execute() {
            try {
                Path path = filePath.getPath();
                Files.createDirectories(path.getParent());
                Files.write(path, data);
                LoggerUtil.debug(this.getClass(), "Executed write operation for: " + path);
                return FileOperationResult.success(path);
            } catch (Exception e) {
                Path path = filePath.getPath();
                LoggerUtil.error(this.getClass(), "Error executing write operation for " + path + ": " + e.getMessage(), e);
                return FileOperationResult.failure(path, "Failed to write file: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Represents a sync operation in a transaction
     */
    private static class SyncOperation extends FileOperation {
        private final FilePath sourceFile;
        private final FilePath targetFile;

        public SyncOperation(FilePath sourceFile, FilePath targetFile) {
            this.sourceFile = sourceFile;
            this.targetFile = targetFile;
        }

        @Override
        public FileOperationResult execute() {
            try {
                Path sourcePath = sourceFile.getPath();
                Path targetPath = targetFile.getPath();

                if (!Files.exists(sourcePath)) {
                    return FileOperationResult.failure(sourcePath, "Source file does not exist");
                }

                // Create target directory if it doesn't exist
                Files.createDirectories(targetPath.getParent());

                // Determine criticality level based on file type
                BackupService.CriticalityLevel criticalityLevel = determineCriticalityLevel();

                // Create backup of target if it exists
                if (Files.exists(targetPath)) {
                    Path backupPath = targetPath.resolveSibling(targetPath.getFileName() + ".bak");
                    Files.copy(targetPath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    LoggerUtil.debug(this.getClass(), "Created backup: " + backupPath);
                }

                // Copy source to target
                Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                LoggerUtil.debug(this.getClass(), "Copied file from " + sourcePath + " to " + targetPath);

                // Delete backup on success only for low criticality files
                if (criticalityLevel == BackupService.CriticalityLevel.LEVEL1_LOW) {
                    Path backupPath = targetPath.resolveSibling(targetPath.getFileName() + ".bak");
                    if (Files.exists(backupPath)) {
                        Files.delete(backupPath);
                    }
                }

                return FileOperationResult.success(targetPath);
            } catch (Exception e) {
                Path targetPath = targetFile.getPath();
                LoggerUtil.error(this.getClass(), "Error syncing file to " + targetPath + ": " + e.getMessage(), e);
                return FileOperationResult.failure(targetPath, "Failed to sync file: " + e.getMessage(), e);
            }
        }

        /**
         * Determines the criticality level of a file based on its path
         * @return The appropriate criticality level
         */
        private BackupService.CriticalityLevel determineCriticalityLevel() {
            Path path = targetFile.getPath();
            String pathStr = path.toString().toLowerCase();

            // Status files - Low criticality
            if (pathStr.contains("status") || path.getFileName().toString().startsWith("status_")) {
                return BackupService.CriticalityLevel.LEVEL1_LOW;
            }

            // Session files - Medium criticality
            if (pathStr.contains("session") || path.getFileName().toString().contains("session")) {
                return BackupService.CriticalityLevel.LEVEL2_MEDIUM;
            }

            // Worktime or Register files - High criticality
            if (pathStr.contains("worktime") || pathStr.contains("register")) {
                return BackupService.CriticalityLevel.LEVEL3_HIGH;
            }

            // Default to medium criticality for unknown files
            return BackupService.CriticalityLevel.LEVEL2_MEDIUM;
        }
    }
}