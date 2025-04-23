package com.ctgraphdep.fileOperations.util;

import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Utility methods for file operations.
 */
public class FileOperationsUtil {

    /**
     * Normalizes a network path to ensure proper UNC format.
     *
     * @param path The network path to normalize
     * @return The normalized path
     */
    public static String normalizeNetworkPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return path;
        }

        // Remove any quotes, brackets or parentheses
        path = path.replaceAll("[\"'()]", "");

        // Fix UNC path format - must start with \\
        if (path.startsWith("\\") && !path.startsWith("\\\\")) {
            path = "\\" + path;
        }

        // Normalize excessive backslashes
        if (path.matches("^\\\\\\\\+.*")) {
            path = "\\\\" + path.replaceAll("^\\\\+", "");
        }

        return path;
    }

    /**
     * Generates a unique filename based on the current timestamp and a random UUID.
     *
     * @param prefix An optional prefix for the filename
     * @param extension The file extension (without the dot)
     * @return A unique filename
     */
    public static String generateUniqueFilename(String prefix, String extension) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8); // Take first 8 chars of UUID

        if (prefix == null || prefix.isEmpty()) {
            prefix = "file";
        }

        if (extension == null || extension.isEmpty()) {
            extension = "tmp";
        }

        if (!extension.startsWith(".")) {
            extension = "." + extension;
        }

        return prefix + "_" + timestamp + "_" + uuid + extension;
    }

    /**
     * Calculates a hash for a file's content.
     *
     * @param filePath The path to the file
     * @return The hash as a hex string
     * @throws IOException If the file cannot be read
     * @throws NoSuchAlgorithmException If the hash algorithm is not available
     */
    public static String calculateFileHash(Path filePath) throws IOException, NoSuchAlgorithmException {
        byte[] content = Files.readAllBytes(filePath);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content);

        return HexFormat.of().formatHex(hash);
    }

    /**
     * Safely creates a temporary file with the given content.
     *
     * @param directory The directory to create the file in
     * @param prefix The filename prefix
     * @param extension The file extension
     * @param content The content to write to the file
     * @return The path to the created file
     * @throws IOException If the file cannot be created
     */
    public static Path createTempFile(Path directory, String prefix, String extension, byte[] content) throws IOException {
        Files.createDirectories(directory);
        String filename = generateUniqueFilename(prefix, extension);
        Path tempFile = directory.resolve(filename);
        Files.write(tempFile, content);
        return tempFile;
    }

    /**
     * Safely copies a file with proper error handling.
     *
     * @param source The source file
     * @param target The target file
     * @return The result of the operation
     */
    public static FileOperationResult safeCopyFile(FilePath source, FilePath target) {
        Path sourcePath = source.getPath();
        Path targetPath = target.getPath();

        try {
            if (!Files.exists(sourcePath)) {
                return FileOperationResult.failure(sourcePath, "Source file does not exist");
            }

            Files.createDirectories(targetPath.getParent());
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            LoggerUtil.debug(FileOperationsUtil.class,
                    String.format("Copied file from %s to %s", sourcePath, targetPath));

            return FileOperationResult.success(targetPath);
        } catch (IOException e) {
            LoggerUtil.error(FileOperationsUtil.class,
                    String.format("Failed to copy file from %s to %s: %s", sourcePath, targetPath, e.getMessage()), e);

            return FileOperationResult.failure(targetPath, "Failed to copy file: " + e.getMessage(), e);
        }
    }

    /**
     * Safely moves a file with proper error handling.
     *
     * @param source The source file
     * @param target The target file
     * @return The result of the operation
     */
    public static FileOperationResult safeMoveFile(FilePath source, FilePath target) {
        Path sourcePath = source.getPath();
        Path targetPath = target.getPath();

        try {
            if (!Files.exists(sourcePath)) {
                return FileOperationResult.failure(sourcePath, "Source file does not exist");
            }

            Files.createDirectories(targetPath.getParent());
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            LoggerUtil.debug(FileOperationsUtil.class,
                    String.format("Moved file from %s to %s", sourcePath, targetPath));

            return FileOperationResult.success(targetPath);
        } catch (IOException e) {
            LoggerUtil.error(FileOperationsUtil.class,
                    String.format("Failed to move file from %s to %s: %s", sourcePath, targetPath, e.getMessage()), e);

            return FileOperationResult.failure(targetPath, "Failed to move file: " + e.getMessage(), e);
        }
    }

    /**
     * Safely deletes a file with proper error handling.
     *
     * @param filePath The file to delete
     * @return The result of the operation
     */
    public static FileOperationResult safeDeleteFile(FilePath filePath) {
        Path path = filePath.getPath();

        try {
            if (Files.deleteIfExists(path)) {
                LoggerUtil.debug(FileOperationsUtil.class, "Deleted file: " + path);
                return FileOperationResult.success(path);
            } else {
                LoggerUtil.debug(FileOperationsUtil.class, "File did not exist, nothing to delete: " + path);
                return FileOperationResult.success(path);
            }
        } catch (IOException e) {
            LoggerUtil.error(FileOperationsUtil.class,
                    String.format("Failed to delete file %s: %s", path, e.getMessage()), e);

            return FileOperationResult.failure(path, "Failed to delete file: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if a file exists and has a minimum size.
     *
     * @param filePath The file to check
     * @param minSize The minimum size in bytes
     * @return True if the file exists and meets the size requirement
     */
    public static boolean isFileValidAndExists(Path filePath, long minSize) {
        try {
            return Files.exists(filePath) && Files.size(filePath) >= minSize;
        } catch (IOException e) {
            LoggerUtil.error(FileOperationsUtil.class,
                    String.format("Error checking file %s: %s", filePath, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Gets the file extension from a path.
     *
     * @param path The file path
     * @return The extension, or an empty string if none
     */
    public static String getFileExtension(Path path) {
        String filename = path.getFileName().toString();
        return StringUtils.getFilenameExtension(filename) != null ?
                StringUtils.getFilenameExtension(filename) : "";
    }

    /**
     * Gets the filename without extension.
     *
     * @param path The file path
     * @return The filename without extension
     */
    public static String getFilenameWithoutExtension(Path path) {
        String filename = path.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }
}



