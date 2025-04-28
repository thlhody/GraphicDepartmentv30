package com.ctgraphdep.controller;

import com.ctgraphdep.service.UserLogService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Controller for handling user log operations
 */
@Controller
@RequestMapping("/logs")
public class UserLogController {
    private final UserLogService userLogService;

    public UserLogController(UserLogService userLogService) {
        this.userLogService = userLogService;
    }

    /**
     * Show the log viewer page
     */
    @GetMapping
    public String viewLogs(Model model) {
        try {
            // List user logs from network
            List<String> usernames = userLogService.getUserLogsList();
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
        Optional<String> content = userLogService.getUserLogContent(username);

        return content.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Manually trigger sync for all logs with graceful network handling
     */
    @PostMapping("/sync")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncLogs() {
        Map<String, Object> response = new HashMap<>();

        // Call the updated service method that returns a SyncResult
        UserLogService.SyncResult result = userLogService.manualSync();

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
            List<String> usernames = userLogService.getUserLogsList();
            return ResponseEntity.ok(usernames);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error listing log files: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new ArrayList<>());
        }
    }
}