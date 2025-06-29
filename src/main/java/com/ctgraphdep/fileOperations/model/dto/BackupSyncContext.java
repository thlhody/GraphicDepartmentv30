package com.ctgraphdep.fileOperations.model.dto;

import com.ctgraphdep.config.FileTypeConstants.CriticalityLevel;
import lombok.Getter;
import lombok.AllArgsConstructor;

import java.nio.file.Path;

/**
 * Context object for backup sync operations.
 */
@Getter
@AllArgsConstructor
public class BackupSyncContext {
    private final String username;
    private final CriticalityLevel level;
    private final Path localBackupDir;
    private final Path networkBackupDir;
}