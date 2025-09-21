package com.ctgraphdep.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

@Service
public class StartupService {

    private static final Logger logger = LoggerFactory.getLogger(StartupService.class);

    @Value("${app.home:}")
    private String appHome;

    @Value("${server.port:8447}")
    private int serverPort;

    @PostConstruct
    public void configureStartup() {
        if (isFirstRun()) {
            CompletableFuture.runAsync(() -> {
                try {
                    configureWindowsStartup();
                    configurePortAndHosts();
                    createSystemTrayIcon();
                } catch (Exception e) {
                    logger.error("Failed to configure startup", e);
                }
            });
        }
    }

    private boolean isFirstRun() {
        if (appHome == null || appHome.isEmpty()) {
            return false;
        }

        Path firstRunMarker = Paths.get(appHome, ".cttt_configured");
        return !Files.exists(firstRunMarker);
    }

    private void configureWindowsStartup() throws Exception {
        logger.info("Configuring Windows startup...");

        // Create Windows startup shortcut
        String startupScript = createStartupScript();

        // Run PowerShell to create startup shortcut
        runPowerShellScript(startupScript);

        logger.info("Windows startup configured successfully");
    }

    private void configurePortAndHosts() throws Exception {
        logger.info("Configuring port and hosts...");

        // Configure port
        configureApplicationPort();

        // Configure hosts file
        configureHostsFile();

        logger.info("Port and hosts configured successfully");
    }

    private String createStartupScript() {
        return String.format("""
            $WshShell = New-Object -comObject WScript.Shell
            $Shortcut = $WshShell.CreateShortcut("$env:APPDATA\\Microsoft\\Windows\\Start Menu\\Programs\\Startup\\CTTT.lnk")
            $Shortcut.TargetPath = "java"
            $Shortcut.Arguments = "-jar `"%s\\ctgraphdep-web.jar`""
            $Shortcut.WorkingDirectory = "%s"
            $Shortcut.WindowStyle = 7
            $Shortcut.Description = "Creative Time And Task Tracking"
            $Shortcut.Save()

            # Create desktop shortcut too
            $DesktopShortcut = $WshShell.CreateShortcut("$env:USERPROFILE\\Desktop\\CTTT.lnk")
            $DesktopShortcut.TargetPath = "java"
            $DesktopShortcut.Arguments = "-jar `"%s\\ctgraphdep-web.jar`""
            $DesktopShortcut.WorkingDirectory = "%s"
            $DesktopShortcut.Description = "Creative Time And Task Tracking"
            $DesktopShortcut.Save()
            """, appHome, appHome, appHome, appHome);
    }

    private void configureApplicationPort() throws Exception {
        // Check if current port is available
        if (!isPortAvailable(serverPort)) {
            int newPort = findAvailablePort(serverPort);
            updateApplicationProperties(newPort);
            logger.info("Port changed from {} to {}", serverPort, newPort);
        }
    }

    private void configureHostsFile() throws Exception {
        String hostsScript = """
            $hostsFile = "$env:SystemRoot\\System32\\drivers\\etc\\hosts"
            $hostEntry = "127.0.0.1 CTTT"

            $content = Get-Content $hostsFile -Raw -ErrorAction SilentlyContinue
            if ($content -notlike "*CTTT*") {
                Add-Content -Path $hostsFile -Value "`n$hostEntry" -Encoding ASCII
                Write-Host "Added CTTT host entry"
            } else {
                Write-Host "CTTT host entry already exists"
            }
            """;

        runPowerShellScript(hostsScript);
    }

    private boolean isPortAvailable(int port) {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private int findAvailablePort(int startPort) {
        for (int port = startPort; port < startPort + 100; port++) {
            if (isPortAvailable(port)) {
                return port;
            }
        }
        throw new RuntimeException("No available ports found");
    }

    private void updateApplicationProperties(int newPort) throws IOException {
        Path configFile = Paths.get(appHome, "config", "application.properties");
        if (Files.exists(configFile)) {
            String content = Files.readString(configFile);
            content = content.replaceAll("server\\.port=\\d+", "server.port=" + newPort);
            Files.writeString(configFile, content);
        }
    }

    private void runPowerShellScript(String script) throws Exception {
        // Write script to temp file
        Path tempScript = Files.createTempFile("cttt_script", ".ps1");
        Files.writeString(tempScript, script);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy", "Bypass",
                "-File", tempScript.toString()
            );

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String error = reader.lines().reduce("", (a, b) -> a + "\n" + b);
                    logger.warn("PowerShell script warning: {}", error);
                }
            }
        } finally {
            Files.deleteIfExists(tempScript);
        }
    }

    private void createSystemTrayIcon() {
        // Mark as configured
        try {
            Path marker = Paths.get(appHome, ".cttt_configured");
            Files.createFile(marker);
            logger.info("CTTT configuration completed - startup configured");
        } catch (IOException e) {
            logger.warn("Could not create configuration marker", e);
        }
    }
}