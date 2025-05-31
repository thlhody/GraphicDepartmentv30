package com.ctgraphdep.controller.team;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.CheckValuesEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UsersCheckValueEntry;
import com.ctgraphdep.service.CheckValuesService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for check values operations
 */
@Controller
@RequestMapping("/user/check-values")
@PreAuthorize("hasAnyRole('ROLE_TL_CHECKING', 'ROLE_ADMIN')")
public class CheckValuesController extends BaseController {

    private final CheckValuesService checkValuesService;

    public CheckValuesController(UserService userService, TimeValidationService timeValidationService, CheckValuesService checkValuesService) {
        super(userService, null, timeValidationService);
        this.checkValuesService = checkValuesService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Display check values page
     */
    @GetMapping
    public String showCheckValues(@AuthenticationPrincipal UserDetails userDetails, Model model) {

        try {
            LoggerUtil.info(this.getClass(), "Accessing check values page at " + getStandardCurrentDateTime());

            // Get user and add common model attributes
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Get all check users
            List<User> checkUsers = checkValuesService.getAllCheckUsers();
            model.addAttribute("checkUsers", checkUsers);

            // Get all check values
            List<UsersCheckValueEntry> allCheckValues = checkValuesService.getAllCheckValues();
            model.addAttribute("allCheckValues", allCheckValues);

            return "user/check-values";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading check values page: " + e.getMessage(), e);
            model.addAttribute("error", "Error loading check values data: " + e.getMessage());
            return "user/check-values";
        }
    }

    /**
     * Get check values for a specific user
     */

    @ResponseBody
    @GetMapping("/{username}/{userId}")
    public ResponseEntity<UsersCheckValueEntry> getUserCheckValues(@AuthenticationPrincipal UserDetails userDetails, @PathVariable String username, @PathVariable String userId) {

        try {
            // Verify current user is authorized
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Parse userId safely - handle the literal string "null"
            Integer userIdInt = null;
            if (userId != null && !userId.equals("null") && !userId.isEmpty()) {
                try {
                    userIdInt = Integer.parseInt(userId);
                } catch (NumberFormatException e) {
                    LoggerUtil.warn(this.getClass(), "Invalid userId format: " + userId);
                    // Try to find user by username
                    Optional<User> user = getUserService().getUserByUsername(username);
                    if (user.isPresent()) {
                        userIdInt = user.get().getUserId();
                        LoggerUtil.info(this.getClass(), "Resolved userId " + userIdInt + " for username: " + username);
                    }
                }
            } else {
                // Try to find user by username
                Optional<User> user = getUserService().getUserByUsername(username);
                if (user.isPresent()) {
                    userIdInt = user.get().getUserId();
                    LoggerUtil.info(this.getClass(), "Resolved userId " + userIdInt + " for username: " + username);
                }
            }

            UsersCheckValueEntry entry = checkValuesService.getUserCheckValues(username, userIdInt);
            return ResponseEntity.ok(entry);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting check values for user " + username + ": " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Save check values for a specific user
     */
    @PostMapping("/{username}/{userId}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> saveUserCheckValues(@AuthenticationPrincipal UserDetails userDetails, @PathVariable String username,
                                                                   @PathVariable Integer userId, @RequestBody Map<String, Object> requestMap) {

        try {
            // Verify current user is authorized
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Get existing check values
            UsersCheckValueEntry existingEntry = checkValuesService.getUserCheckValues(username, userId);

            // Extract check values from request safely
            Object checkValuesObj = requestMap.get("checkValuesEntry");
            if (!(checkValuesObj instanceof Map)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "Invalid checkValuesEntry format in request"
                ));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> checkValuesMap = (Map<String, Object>) checkValuesObj;

            // Update check values with new values from request
            CheckValuesEntry checkValues = existingEntry.getCheckValuesEntry();
            if (checkValues == null) {
                checkValues = CheckValuesEntry.createDefault();
            }

            // Update specific fields
            if (checkValuesMap.containsKey("workUnitsPerHour")) {
                checkValues.setWorkUnitsPerHour(parseDoubleOrDefault(checkValuesMap.get("workUnitsPerHour"), 4.5));
            }
            if (checkValuesMap.containsKey("layoutValue")) {
                checkValues.setLayoutValue(parseDoubleOrDefault(checkValuesMap.get("layoutValue"), 1.0));
            }
            if (checkValuesMap.containsKey("kipstaLayoutValue")) {
                checkValues.setKipstaLayoutValue(parseDoubleOrDefault(checkValuesMap.get("kipstaLayoutValue"), 0.25));
            }
            if (checkValuesMap.containsKey("layoutChangesValue")) {
                checkValues.setLayoutChangesValue(parseDoubleOrDefault(checkValuesMap.get("layoutChangesValue"), 0.25));
            }
            if (checkValuesMap.containsKey("gptArticlesValue")) {
                checkValues.setGptArticlesValue(parseDoubleOrDefault(checkValuesMap.get("gptArticlesValue"), 0.1));
            }
            if (checkValuesMap.containsKey("gptFilesValue")) {
                checkValues.setGptFilesValue(parseDoubleOrDefault(checkValuesMap.get("gptFilesValue"), 0.1));
            }
            if (checkValuesMap.containsKey("productionValue")) {
                checkValues.setProductionValue(parseDoubleOrDefault(checkValuesMap.get("productionValue"), 0.1));
            }
            if (checkValuesMap.containsKey("reorderValue")) {
                checkValues.setReorderValue(parseDoubleOrDefault(checkValuesMap.get("reorderValue"), 0.1));
            }
            if (checkValuesMap.containsKey("sampleValue")) {
                checkValues.setSampleValue(parseDoubleOrDefault(checkValuesMap.get("sampleValue"), 0.3));
            }
            if (checkValuesMap.containsKey("omsProductionValue")) {
                checkValues.setOmsProductionValue(parseDoubleOrDefault(checkValuesMap.get("omsProductionValue"), 0.1));
            }
            if (checkValuesMap.containsKey("kipstaProductionValue")) {
                checkValues.setKipstaProductionValue(parseDoubleOrDefault(checkValuesMap.get("kipstaProductionValue"), 0.1));
            }

            // Set creation time
            checkValues.setCreatedAt(LocalDateTime.now());

            // Update entry
            existingEntry.setCheckValuesEntry(checkValues);
            existingEntry.setLatestEntry(LocalDateTime.now().toString());

            // Log values for debugging
            LoggerUtil.info(this.getClass(), "Saving check values for user " + username + " with workUnitsPerHour: " + checkValues.getWorkUnitsPerHour());

            // Save entry
            checkValuesService.saveUserCheckValues(existingEntry, username, userId);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Check values saved successfully for user " + username,
                    "timestamp", existingEntry.getLatestEntry()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error saving check values for user " + username + ": " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Parse a double value or return a default
     */
    private double parseDoubleOrDefault(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        try {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            } else if (value instanceof String) {
                return Double.parseDouble((String) value);
            }
        } catch (NumberFormatException e) {
            // Ignore and return default
        }

        return defaultValue;
    }

    /**
     * Save check values for multiple users
     */
    @ResponseBody
    @PostMapping("/batch")
    public ResponseEntity<Map<String, String>> saveBatchCheckValues(@AuthenticationPrincipal UserDetails userDetails, @RequestBody List<Map<String, Object>> requestList) {

        try {
            // Verify current user is authorized
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            int successCount = 0;

            for (Map<String, Object> requestMap : requestList) {
                try {
                    // Extract basic info
                    String username = (String) requestMap.get("username");
                    Integer userId = null;
                    Object userIdObj = requestMap.get("userId");
                    if (userIdObj instanceof Integer) {
                        userId = (Integer) userIdObj;
                    } else if (userIdObj instanceof Number) {
                        userId = ((Number) userIdObj).intValue();
                    } else if (userIdObj instanceof String) {
                        try {
                            userId = Integer.parseInt((String) userIdObj);
                        } catch (NumberFormatException e) {
                            LoggerUtil.warn(this.getClass(), "Invalid userId format: " + userIdObj);
                        }
                    }

                    if (username == null || userId == null) {
                        LoggerUtil.warn(this.getClass(), "Missing username or userId in batch entry");
                        continue;
                    }

                    // Get existing check values
                    UsersCheckValueEntry existingEntry = checkValuesService.getUserCheckValues(username, userId);

                    // Extract check values from request
                    Object checkValuesObj = requestMap.get("checkValuesEntry");
                    if (!(checkValuesObj instanceof Map)) {
                        LoggerUtil.warn(this.getClass(), "Invalid checkValuesEntry format for user " + username);
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> checkValuesMap = (Map<String, Object>) checkValuesObj;

                    // Update check values with new values from request
                    CheckValuesEntry checkValues = existingEntry.getCheckValuesEntry();
                    if (checkValues == null) {
                        checkValues = CheckValuesEntry.createDefault();
                    }

                    // Update specific fields
                    if (checkValuesMap.containsKey("workUnitsPerHour")) {
                        checkValues.setWorkUnitsPerHour(parseDoubleOrDefault(checkValuesMap.get("workUnitsPerHour"), 4.5));
                    }
                    if (checkValuesMap.containsKey("layoutValue")) {
                        checkValues.setLayoutValue(parseDoubleOrDefault(checkValuesMap.get("layoutValue"), 1.0));
                    }
                    if (checkValuesMap.containsKey("kipstaLayoutValue")) {
                        checkValues.setKipstaLayoutValue(parseDoubleOrDefault(checkValuesMap.get("kipstaLayoutValue"), 0.25));
                    }
                    if (checkValuesMap.containsKey("layoutChangesValue")) {
                        checkValues.setLayoutChangesValue(parseDoubleOrDefault(checkValuesMap.get("layoutChangesValue"), 0.25));
                    }
                    if (checkValuesMap.containsKey("gptArticlesValue")) {
                        checkValues.setGptArticlesValue(parseDoubleOrDefault(checkValuesMap.get("gptArticlesValue"), 0.1));
                    }
                    if (checkValuesMap.containsKey("gptFilesValue")) {
                        checkValues.setGptFilesValue(parseDoubleOrDefault(checkValuesMap.get("gptFilesValue"), 0.1));
                    }
                    if (checkValuesMap.containsKey("productionValue")) {
                        checkValues.setProductionValue(parseDoubleOrDefault(checkValuesMap.get("productionValue"), 0.1));
                    }
                    if (checkValuesMap.containsKey("reorderValue")) {
                        checkValues.setReorderValue(parseDoubleOrDefault(checkValuesMap.get("reorderValue"), 0.1));
                    }
                    if (checkValuesMap.containsKey("sampleValue")) {
                        checkValues.setSampleValue(parseDoubleOrDefault(checkValuesMap.get("sampleValue"), 0.3));
                    }
                    if (checkValuesMap.containsKey("omsProductionValue")) {
                        checkValues.setOmsProductionValue(parseDoubleOrDefault(checkValuesMap.get("omsProductionValue"), 0.1));
                    }
                    if (checkValuesMap.containsKey("kipstaProductionValue")) {
                        checkValues.setKipstaProductionValue(parseDoubleOrDefault(checkValuesMap.get("kipstaProductionValue"), 0.1));
                    }

                    // Set creation time
                    checkValues.setCreatedAt(LocalDateTime.now());

                    // Update entry
                    existingEntry.setCheckValuesEntry(checkValues);
                    existingEntry.setLatestEntry(LocalDateTime.now().toString());

                    // Log values for debugging
                    LoggerUtil.info(this.getClass(), "Saving batch check values for user " + username +
                            " with workUnitsPerHour: " + checkValues.getWorkUnitsPerHour());

                    // Save entry
                    checkValuesService.saveUserCheckValues(existingEntry, username, userId);

                    successCount++;
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), "Error processing batch entry: " + e.getMessage(), e);
                    // Continue with next entry
                }
            }

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Batch update successful for " + successCount + " users"));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error saving batch check values: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

}