package com.ctgraphdep.model.dto;

import lombok.Data;

@Data
public class VersionInfoDTO {
    private String currentVersion;
    private String newVersion;
    private String installerPath;
    private boolean updateAvailable;
}