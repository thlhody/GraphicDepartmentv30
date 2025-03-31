package com.ctgraphdep.service;

import com.ctgraphdep.model.dto.VersionInfoDTO;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class UpdateService {

    @Value("${app.paths.network.installer:}")
    private String networkInstallerPath;

    @Value("${cttt.version:}")
    private String currentVersion;

    @Value("${installer.name:CTTT}")
    private String installerPrefix;

    private static final Pattern VERSION_PATTERN = Pattern.compile("_(\\d+\\.\\d+\\.\\d+)\\.exe$");

    public VersionInfoDTO checkForUpdates() {
        VersionInfoDTO versionInfoDTO = new VersionInfoDTO();
        versionInfoDTO.setCurrentVersion(currentVersion);
        versionInfoDTO.setUpdateAvailable(false);

        if (networkInstallerPath == null || networkInstallerPath.trim().isEmpty()) {
            LoggerUtil.warn(this.getClass(), "Network installer path not configured");
            return versionInfoDTO;
        }

        try {
            Path installerDir = Paths.get(networkInstallerPath);
            if (!Files.exists(installerDir) || !Files.isDirectory(installerDir)) {
                LoggerUtil.warn(this.getClass(), "Installer directory not found: " + networkInstallerPath);
                return versionInfoDTO;
            }

            // Find the latest installer file
            Optional<Path> latestInstaller;
            try (Stream<Path> files = Files.list(installerDir)) {
                latestInstaller = files
                        .filter(path -> {
                            String filename = path.getFileName().toString();
                            return Files.isRegularFile(path) &&
                                    filename.startsWith(installerPrefix) &&
                                    filename.toLowerCase().endsWith(".exe") &&
                                    VERSION_PATTERN.matcher(filename).find();
                        })
                        .max(Comparator.comparing(this::extractVersion));
            }

            if (latestInstaller.isEmpty()) {
                LoggerUtil.info(this.getClass(), "No installer files found in " + networkInstallerPath);
                return versionInfoDTO;
            }

            Path installerPath = latestInstaller.get();
            String installerName = installerPath.getFileName().toString();
            String installerVersion = extractVersion(installerPath);

            LoggerUtil.info(this.getClass(), "Found installer: " + installerName + ", version: " + installerVersion);

            // Compare versions
            if (compareVersions(installerVersion, currentVersion) > 0) {
                versionInfoDTO.setNewVersion(installerVersion);
                versionInfoDTO.setInstallerPath(installerPath.toString());
                versionInfoDTO.setUpdateAvailable(true);
                LoggerUtil.info(this.getClass(), "Update available: " + installerVersion);
            } else {
                LoggerUtil.info(this.getClass(), "No update available. Current: " + currentVersion + ", Latest: " + installerVersion);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking for updates: " + e.getMessage(), e);
        }

        return versionInfoDTO;
    }

    private String extractVersion(Path installerPath) {
        String filename = installerPath.getFileName().toString();
        Matcher matcher = VERSION_PATTERN.matcher(filename);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "0.0.0"; // Default version if pattern doesn't match
    }

    private int compareVersions(String v1, String v2) {
        if (v1 == null) return -1;
        if (v2 == null) return 1;

        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

            if (p1 != p2) {
                return p1 - p2;
            }
        }

        return 0;
    }
}