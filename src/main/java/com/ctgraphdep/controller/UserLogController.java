package com.ctgraphdep.controller;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.service.UserLogService;
import com.ctgraphdep.service.UserLogService.SyncResult;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Controller for handling user log operations
 */
@Controller
@RequestMapping("/logs")
public class UserLogController {
    private final UserLogService userLogService;
    private final PathConfig pathConfig;

    public UserLogController(UserLogService userLogService, PathConfig pathConfig) {
        this.userLogService = userLogService;
        this.pathConfig = pathConfig;
    }

    /**
     * Show the log viewer page
     */
    @GetMapping
    public String viewLogs(Model model) {
        try {
            // Create network logs directory if it doesn't exist
            Path networkLogsDir = pathConfig.getNetworkLogDirectory();
            if (!Files.exists(networkLogsDir)) {
                Files.createDirectories(networkLogsDir);
                LoggerUtil.info(this.getClass(), "Created network logs directory: " + networkLogsDir);
            }

            // List user logs from network
            List<String> usernames = getUserLogsList();
            model.addAttribute("usernames", usernames);

            return "logs/viewer";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading logs view: " + e.getMessage());
            model.addAttribute("error", "Failed to load log files: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Get log content for a specific user
     */
    @GetMapping("/{username}")
    @ResponseBody
    public ResponseEntity<String> getUserLog(@PathVariable String username) {
        try {
            Path logPath = pathConfig.getNetworkLogDirectory()
                    .resolve("ctgraphdep-logger_" + username + ".log");

            if (!Files.exists(logPath)) {
                return ResponseEntity.notFound().build();
            }

            String content = Files.readString(logPath);
            return ResponseEntity.ok(content);
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error reading log for " + username + ": " + e.getMessage());
            return ResponseEntity.internalServerError().body("Error reading log file: " + e.getMessage());
        }
    }

    /**
     * Manually trigger sync for all logs with graceful network handling
     */
    @PostMapping("/sync")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncLogs() {
        Map<String, Object> response = new HashMap<>();

        // Call the updated service method that returns a SyncResult
        SyncResult result = userLogService.manualSync();

        if (result.isSuccess()) {
            response.put("success", true);
            response.put("message", result.getMessage());
            LoggerUtil.info(this.getClass(), "Manual log sync triggered successfully");
            return ResponseEntity.ok(response);
        } else {
            // If network unavailable, return a specific response
            if (result.getMessage().contains("Network is currently unavailable")) {
                response.put("success", false);
                response.put("networkUnavailable", true);
                response.put("message", result.getMessage());
                LoggerUtil.info(this.getClass(), "Manual log sync requested but network unavailable");
                return ResponseEntity.ok(response); // Still return 200 OK with detailed message
            } else {
                // Other error
                response.put("success", false);
                response.put("message", result.getMessage());
                LoggerUtil.error(this.getClass(), "Error syncing logs: " + result.getMessage());
                return ResponseEntity.ok(response); // Return 200 OK with error message for consistent handling
            }
        }
    }

    /**
     * Get list of usernames with available logs (AJAX endpoint)
     */
    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<List<String>> getLogsList() {
        try {
            List<String> usernames = getUserLogsList();
            return ResponseEntity.ok(usernames);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error listing log files: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new ArrayList<>());
        }
    }

    /**
     * Get list of usernames with available logs
     */
    private List<String> getUserLogsList() {
        try {
            Path logDir = pathConfig.getNetworkLogDirectory();
            if (!Files.exists(logDir)) {
                return new ArrayList<>();
            }
            try (Stream<Path> files = Files.list(logDir)) {
                return files
                        .filter(path -> path.getFileName().toString().startsWith("ctgraphdep-logger_") &&
                                path.getFileName().toString().endsWith(".log"))
                        .map(path -> {
                            String filename = path.getFileName().toString();
                            // Extract username from filename format: ctgraphdep-logger_username.log
                            return filename.substring(18, filename.length() - 4);
                        })
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error listing log files: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}