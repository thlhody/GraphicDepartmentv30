package com.ctgraphdep.controller.utility;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.monitoring.MonitoringStateService;
import com.ctgraphdep.monitoring.SchedulerHealthMonitor;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for system health monitoring utilities.
 * Handles overall health status, task health, and monitoring state information.
 */
@RestController
@RequestMapping("/utility/health")
public class HealthUtilityController extends BaseController {

    private final SchedulerHealthMonitor schedulerHealthMonitor;
    private final MonitoringStateService monitoringStateService;

    public HealthUtilityController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService timeValidationService,
            SchedulerHealthMonitor schedulerHealthMonitor,
            MonitoringStateService monitoringStateService) {

        super(userService, folderStatus, timeValidationService);
        this.schedulerHealthMonitor = schedulerHealthMonitor;
        this.monitoringStateService = monitoringStateService;
    }

    // ========================================================================
    // SYSTEM HEALTH MONITORING ENDPOINTS
    // ========================================================================

    /**
     * Get overall system health
     */
    @GetMapping("/overall")
    public ResponseEntity<Map<String, Object>> getOverallHealth() {
        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, Boolean> healthStatus = schedulerHealthMonitor.getHealthStatus();

            response.put("success", true);
            response.put("healthStatus", healthStatus);
            response.put("overallHealthy", healthStatus.values().stream().allMatch(Boolean::booleanValue));
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting overall health: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting overall health: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get detailed task health information
     */
    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> getTaskHealth() {
        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, Boolean> healthStatus = schedulerHealthMonitor.getHealthStatus();
            Map<String, Object> taskDetails = new HashMap<>();

            // Get detailed status for each task
            for (String taskId : healthStatus.keySet()) {
                boolean isHealthy = schedulerHealthMonitor.isTaskHealthy(taskId);
                SchedulerHealthMonitor.TaskStatus taskStatus = schedulerHealthMonitor.getTaskStatus(taskId);

                Map<String, Object> taskInfo = new HashMap<>();
                taskInfo.put("healthy", isHealthy);
                if (taskStatus != null) {
                    taskInfo.put("lastExecution", taskStatus.getLastExecutionTime());
                    taskInfo.put("consecutiveFailures", taskStatus.getConsecutiveFailures());
                    taskInfo.put("minutesSinceLastExecution", taskStatus.getMinutesSinceLastExecution());
                    taskInfo.put("lastError", taskStatus.getLastError());
                }

                taskDetails.put(taskId, taskInfo);
            }

            response.put("success", true);
            response.put("healthStatus", healthStatus);
            response.put("taskDetails", taskDetails);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting task health: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting task health: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get monitoring state for current user
     */
    @GetMapping("/monitoring-state")
    public ResponseEntity<Map<String, Object>> getMonitoringState(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            String username = currentUser.getUsername();
            String monitoringMode = monitoringStateService.getMonitoringMode(username);
            boolean scheduleMonitoring = monitoringStateService.isInScheduleMonitoring(username);
            boolean hourlyMonitoring = monitoringStateService.isInHourlyMonitoring(username);
            boolean tempStopMonitoring = monitoringStateService.isInTempStopMonitoring(username);
            boolean continuedAfterSchedule = monitoringStateService.hasContinuedAfterSchedule(username);

            response.put("success", true);
            response.put("username", username);
            response.put("monitoringMode", monitoringMode);
            response.put("scheduleMonitoring", scheduleMonitoring);
            response.put("hourlyMonitoring", hourlyMonitoring);
            response.put("tempStopMonitoring", tempStopMonitoring);
            response.put("continuedAfterSchedule", continuedAfterSchedule);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting monitoring state: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting monitoring state: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }
}
