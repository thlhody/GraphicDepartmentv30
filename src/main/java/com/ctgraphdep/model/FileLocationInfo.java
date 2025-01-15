package com.ctgraphdep.model;

import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
import java.time.LocalDateTime;

@Getter
@Setter
public class FileLocationInfo {
    // Getters and setters
    private Path currentLocation;
    private LocalDateTime lastAccessTime;
    private boolean syncPending;
    private boolean networkAvailable;

    public FileLocationInfo(Path location) {
        this.currentLocation = location;
        this.lastAccessTime = LocalDateTime.now();
        this.syncPending = false;
        this.networkAvailable = false;
    }

}