package com.ctgraphdep.fileOperations.core;

import lombok.Getter;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Represents a file path with metadata about its location.
 * Encapsulates information about whether a path is local or network,
 * and provides context for file operations.
 */
@Getter
public class FilePath {
    private final Path path;
    private final PathType pathType;
    private final String username;
    private final Integer userId;

    public enum PathType {
        LOCAL,
        NETWORK,
        BACKUP
    }

    private FilePath(Path path, PathType pathType, String username, Integer userId) {
        this.path = path;
        this.pathType = pathType;
        this.username = username;
        this.userId = userId;
    }

    public static FilePath local(Path path) {
        return new FilePath(path, PathType.LOCAL, null, null);
    }

    public static FilePath local(Path path, String username, Integer userId) {
        return new FilePath(path, PathType.LOCAL, username, userId);
    }

    public static FilePath network(Path path) {
        return new FilePath(path, PathType.NETWORK, null, null);
    }

    public static FilePath network(Path path, String username, Integer userId) {
        return new FilePath(path, PathType.NETWORK, username, userId);
    }

    public static FilePath backup(Path path) {
        return new FilePath(path, PathType.BACKUP, null, null);
    }

    public boolean isLocal() {
        return pathType == PathType.LOCAL;
    }

    public boolean isNetwork() {
        return pathType == PathType.NETWORK;
    }

    public boolean isBackup() {
        return pathType == PathType.BACKUP;
    }

    public Optional<String> getUsername() {
        return Optional.ofNullable(username);
    }

    public Optional<Integer> getUserId() {
        return Optional.ofNullable(userId);
    }

    @Override
    public String toString() {
        return String.format("%s path: %s [user: %s, id: %s]",
                pathType, path, username != null ? username : "none",
                userId != null ? userId : "none");
    }
}
