package com.ctgraphdep.installer;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "cttt.installer.mode", havingValue = "install")
public class SelfInstaller implements CommandLineRunner {

    private static final String DEFAULT_INSTALL_DIR = "C:\\Program Files\\CreativeTimeAndTaskTracker";
    private static final String SCRIPTS_RESOURCE_PATH = "/installer/scripts/";

    private final Map<String, String> embeddedScripts = Map.of(
        "install.ps1", loadScript("install.ps1"),
        "configure-port.ps1", loadScript("configure-port.ps1"),
        "create-hosts.ps1", loadScript("create-hosts.ps1"),
        "create-ssl.ps1", loadScript("create-ssl.ps1"),
        "start-app.ps1", loadScript("start-app.ps1"),
        "test-network.ps1", loadScript("test-network.ps1"),
        "log-manager.ps1", loadScript("log-manager.ps1")
    );

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== CTTT Self-Installer ===");

        String installDir = getInstallDir(args);
        String networkPath = getNetworkPath(args);

        System.out.println("Install Directory: " + installDir);
        System.out.println("Network Path: " + (networkPath != null ? networkPath : "Not specified"));

        if (confirmInstallation()) {
            performInstallation(installDir, networkPath);
        } else {
            System.out.println("Installation cancelled by user.");
        }
    }

    private String getInstallDir(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--install-dir".equals(args[i])) {
                return args[i + 1];
            }
        }
        return DEFAULT_INSTALL_DIR;
    }

    private String getNetworkPath(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--network-path".equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    private boolean confirmInstallation() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Do you want to continue with the installation? (y/N): ");
        String response = scanner.nextLine().trim().toLowerCase();
        return "y".equals(response) || "yes".equals(response);
    }

    private void performInstallation(String installDir, String networkPath) throws Exception {
        System.out.println("Starting installation...");

        // 1. Create directory structure
        createDirectoryStructure(installDir);

        // 2. Copy JAR file to install directory
        copyApplicationFiles(installDir);

        // 3. Extract embedded scripts
        extractEmbeddedScripts(installDir);

        // 4. Run installation scripts
        runInstallationProcess(installDir, networkPath);

        System.out.println("Installation completed successfully!");
        System.out.println("You can access CTTT at: http://localhost:8447 or https://CTTT:8443");
    }

    private void createDirectoryStructure(String installDir) throws IOException {
        System.out.println("Creating directory structure...");

        Path installPath = Paths.get(installDir);
        Files.createDirectories(installPath);
        Files.createDirectories(installPath.resolve("config"));
        Files.createDirectories(installPath.resolve("graphics"));
        Files.createDirectories(installPath.resolve("logs"));
        Files.createDirectories(installPath.resolve("scripts"));
        Files.createDirectories(installPath.resolve("hosts"));

        System.out.println("Directory structure created.");
    }

    private void copyApplicationFiles(String installDir) throws IOException {
        System.out.println("Copying application files...");

        // Copy current JAR to install directory
        String currentJarPath = getCurrentJarPath();
        Path targetJar = Paths.get(installDir, "ctgraphdep-web.jar");
        Files.copy(Paths.get(currentJarPath), targetJar, StandardCopyOption.REPLACE_EXISTING);

        // Copy configuration files
        copyResourceToFile("/application.properties", Paths.get(installDir, "config", "application.properties"));

        // Copy graphics
        copyResourceToFile("/static/ct3logoicon.ico", Paths.get(installDir, "graphics", "ct3logoicon.ico"));

        System.out.println("Application files copied.");
    }

    private void extractEmbeddedScripts(String installDir) throws IOException {
        System.out.println("Extracting installation scripts...");

        Path scriptsDir = Paths.get(installDir, "scripts");

        for (Map.Entry<String, String> script : embeddedScripts.entrySet()) {
            Path scriptFile = scriptsDir.resolve(script.getKey());
            Files.write(scriptFile, script.getValue().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }

        System.out.println("Scripts extracted.");
    }

    private void runInstallationProcess(String installDir, String networkPath) throws Exception {
        System.out.println("Running installation process...");

        // Prepare PowerShell command
        List<String> command = new ArrayList<>();
        command.add("powershell.exe");
        command.add("-NoProfile");
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-File");
        command.add(Paths.get(installDir, "scripts", "install.ps1").toString());
        command.add("-InstallDir");
        command.add("\"" + installDir + "\"");

        if (networkPath != null) {
            command.add("-NetworkPath");
            command.add("\"" + networkPath + "\"");
        }

        command.add("-Version");
        command.add("\"7.2.0\"");

        // Run as administrator
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        System.out.println("Running: " + String.join(" ", command));

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Installation script failed with exit code: " + exitCode);
        }

        System.out.println("Installation scripts completed successfully.");
    }

    private String getCurrentJarPath() {
        try {
            return new File(SelfInstaller.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getPath();
        } catch (Exception e) {
            throw new RuntimeException("Could not determine current JAR path", e);
        }
    }

    private void copyResourceToFile(String resourcePath, Path targetFile) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is != null) {
                Files.createDirectories(targetFile.getParent());
                Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String loadScript(String scriptName) {
        try (InputStream is = SelfInstaller.class.getResourceAsStream(SCRIPTS_RESOURCE_PATH + scriptName)) {
            if (is == null) {
                System.err.println("Warning: Script not found: " + scriptName);
                return "# Script not found: " + scriptName;
            }
            return new String(is.readAllBytes());
        } catch (IOException e) {
            System.err.println("Error loading script " + scriptName + ": " + e.getMessage());
            return "# Error loading script: " + scriptName;
        }
    }
}