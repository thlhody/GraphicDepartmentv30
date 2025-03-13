package com.ctgraphdep.model;

import lombok.Data;

@Data
public class VersionInfo {
    private String currentVersion;
    private String newVersion;
    private String installerPath;
    private boolean updateAvailable;
}