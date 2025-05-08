package com.ctgraphdep.controller;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.notification.service.DefaultNotificationService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class AboutController {

    @Autowired
    private DefaultNotificationService notificationService;

    public AboutController() {
    }

    @GetMapping("/about")
    public String about() {

        LoggerUtil.info(this.getClass(), "Accessing about page");
        return "about";
    }

    @PostMapping("/about/trigger-mockup")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> triggerMockupNotification(@RequestParam String type, @RequestParam(required = false) String username) {
        Map<String, Object> response = new HashMap<>();
        boolean success;

        // Default to first user if none provided
        if (username == null || username.isEmpty()) {
            username = "default";
        }

        LoggerUtil.info(this.getClass(), "Triggering mockup notification of type: " + type + " for user: " + username);

        try {
            String title;
            String message;
            String trayMessage;
            String mockupType;

            switch (type) {
                case "test":
                    title = WorkCode.TEST_NOTICE_TITLE;
                    message = WorkCode.TEST_MESSAGE;
                    trayMessage = WorkCode.TEST_MESSAGE_TRAY;
                    mockupType = WorkCode.TEST_TYPE;
                    break;
                case "start-day":
                    title = WorkCode.START_DAY_TITLE;
                    message = WorkCode.START_DAY_MESSAGE;
                    trayMessage = WorkCode.START_DAY_MESSAGE_TRAY;
                    mockupType = WorkCode.START_DAY_TYPE;
                    break;
                case "schedule-end":
                    title = WorkCode.END_SCHEDULE_TITLE;
                    message = WorkCode.SESSION_WARNING_MESSAGE;
                    trayMessage = WorkCode.SESSION_WARNING_TRAY;
                    mockupType = WorkCode.SCHEDULE_END_TYPE;
                    break;
                case "hourly":
                    title = WorkCode.OVERTIME_TITLE;
                    message = WorkCode.HOURLY_WARNING_MESSAGE;
                    trayMessage = WorkCode.HOURLY_WARNING_TRAY;
                    mockupType = WorkCode.HOURLY_TYPE;
                    break;
                case "temp-stop":
                    title = WorkCode.TEMPORARY_STOP_TITLE;
                    message = String.format(WorkCode.LONG_TEMP_STOP_WARNING, 1, 30); // 1 hour, 30 minutes
                    trayMessage = String.format(WorkCode.LONG_TEMP_STOP_WARNING_TRAY, 1, 30);
                    mockupType = WorkCode.TEMP_STOP_TYPE;
                    break;
                case "resolution":
                    title = WorkCode.RESOLUTION_TITLE;
                    message = WorkCode.RESOLUTION_MESSAGE;
                    trayMessage = WorkCode.RESOLUTION_MESSAGE_TRAY;
                    mockupType = WorkCode.RESOLUTION_REMINDER_TYPE;
                    break;
                default:
                    response.put("error", "Unknown notification type: " + type);
                    return ResponseEntity.badRequest().body(response);
            }

            success = notificationService.showMockupNotification(username, title, message, trayMessage, mockupType);

            response.put("success", success);
            response.put("message", success ? "Mockup notification triggered successfully" : "Failed to trigger mockup notification");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error triggering mockup notification: " + e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}