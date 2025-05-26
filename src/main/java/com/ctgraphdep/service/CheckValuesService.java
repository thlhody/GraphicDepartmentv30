package com.ctgraphdep.service;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.model.CheckValuesEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UsersCheckValueEntry;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing check values
 */
@Service
public class CheckValuesService {

    private final DataAccessService dataAccessService;
    private final UserService userService;

    public CheckValuesService(DataAccessService dataAccessService, UserService userService) {
        this.dataAccessService = dataAccessService;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Creates a default check values entry for a user
     * @param username The username
     * @param userId The user ID
     * @return A default check values entry
     */
    private UsersCheckValueEntry createDefaultCheckValuesEntry(String username, Integer userId) {
        try {
            // Try to get user info if possible
            String name = username;
            try {
                User user = userService.getUserById(userId)
                        .orElse(null);
                if (user != null && user.getName() != null) {
                    name = user.getName();
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), "Could not retrieve user name for " + username + ": " + e.getMessage());
            }

            // Create default check values
            CheckValuesEntry defaultValues = CheckValuesEntry.createDefault();

            return UsersCheckValueEntry.builder()
                    .userId(userId)
                    .username(username)
                    .name(name)
                    .checkValuesEntry(defaultValues)
                    .latestEntry(defaultValues.getCreatedAt().toString())
                    .build();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating default check values entry: " + e.getMessage());
            // Create a bare minimum entry that won't cause further errors
            CheckValuesEntry defaultValues = CheckValuesEntry.createDefault();
            UsersCheckValueEntry fallbackEntry = new UsersCheckValueEntry();
            fallbackEntry.setUserId(userId);
            fallbackEntry.setUsername(username);
            fallbackEntry.setName(username);
            fallbackEntry.setCheckValuesEntry(defaultValues);
            fallbackEntry.setLatestEntry(defaultValues.getCreatedAt().toString());
            return fallbackEntry;
        }
    }

    /**
     * Saves check values for a specific user
     * @param entry The check values entry to save
     * @param username The username
     * @param userId The user ID
     */
    @PreAuthorize("hasAnyRole('ROLE_TL_CHECKING', 'ROLE_ADMIN')")
    public void saveUserCheckValues(UsersCheckValueEntry entry, String username, Integer userId) {
        try {
            // Ensure the entry has the correct user ID and username
            entry.setUserId(userId);
            entry.setUsername(username);

            // Get the user's name if it's not set
            if (entry.getName() == null) {
                User user = userService.getUserById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
                entry.setName(user.getName());
            }

            // Set creation time if it's not set
            if (entry.getCheckValuesEntry() != null && entry.getCheckValuesEntry().getCreatedAt() == null) {
                entry.getCheckValuesEntry().setCreatedAt(LocalDateTime.now());
                entry.setLatestEntry(entry.getCheckValuesEntry().getCreatedAt().toString());
            }

            dataAccessService.writeUserCheckValues(entry, username, userId);
            LoggerUtil.info(this.getClass(), "Saved check values for user " + username);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error saving check values for user " + username + ": " + e.getMessage());
            throw new RuntimeException("Error saving check values", e);
        }
    }

    /**
     * Creates a new check values entry for a user
     * @param username The username
     * @param userId The user ID
     * @param checkValuesEntry The new check values entry
     */
    @PreAuthorize("hasAnyRole('ROLE_TL_CHECKING', 'ROLE_ADMIN')")
    public void createCheckValuesEntry(String username, Integer userId, CheckValuesEntry checkValuesEntry) {
        try {
            UsersCheckValueEntry userEntry = getUserCheckValues(username, userId);

            // Set creation time if not already set
            if (checkValuesEntry.getCreatedAt() == null) {
                checkValuesEntry.setCreatedAt(LocalDateTime.now());
            }

            // Update with the new entry
            userEntry.setCheckValuesEntry(checkValuesEntry);
            userEntry.setLatestEntry(checkValuesEntry.getCreatedAt().toString());

            saveUserCheckValues(userEntry, username, userId);

            LoggerUtil.info(this.getClass(), "Created new check values entry for user " + username);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating check values entry for user " + username + ": " + e.getMessage());
            throw new RuntimeException("Error creating check values entry", e);
        }
    }

    /**
     * Gets the value for a specific check type
     * @param username The username
     * @param userId The user ID
     * @param checkType The check type
     * @return The value for the check type
     */
    public double getCheckTypeValue(String username, Integer userId, String checkType) {
        UsersCheckValueEntry userEntry = getUserCheckValues(username, userId);
        CheckValuesEntry entry = userEntry.getCheckValuesEntry();

        if (entry == null) {
            return getDefaultCheckTypeValue(checkType);
        }

        return switch (checkType) {
            case "LAYOUT" -> entry.getLayoutValue();
            case "KIPSTA LAYOUT" -> entry.getKipstaLayoutValue();
            case "LAYOUT CHANGES" -> entry.getLayoutChangesValue();
            case "GPT" -> entry.getGptArticlesValue(); // For articles
            case "PRODUCTION" -> entry.getProductionValue();
            case "REORDER" -> entry.getReorderValue();
            case "SAMPLE" -> entry.getSampleValue();
            case "OMS PRODUCTION" -> entry.getOmsProductionValue();
            case "KIPSTA PRODUCTION" -> entry.getKipstaProductionValue();
            default -> getDefaultCheckTypeValue(checkType);
        };
    }

    /**
     * Gets the user's target work units per hour
     * @param username The username
     * @param userId The user ID
     * @return The target work units per hour
     */
    public double getTargetWorkUnitsPerHour(String username, Integer userId) {
        UsersCheckValueEntry userEntry = getUserCheckValues(username, userId);
        CheckValuesEntry entry = userEntry.getCheckValuesEntry();

        if (entry == null) {
            return 4.5; // Default value
        }

        return entry.getWorkUnitsPerHour();
    }

    /**
     * Gets check values for a specific user
     * @param username The username
     * @param userId The user ID
     * @return The check values entry or a default if none exists
     */
    public UsersCheckValueEntry getUserCheckValues(String username, Integer userId) {
        try {
            // Validate inputs
            if (username == null || userId == null) {
                LoggerUtil.warn(this.getClass(), "Null username or userId provided to getUserCheckValues");
                // Create a placeholder entry with default values
                return createDefaultCheckValuesEntry("unknown", -1);
            }

            Optional<UsersCheckValueEntry> existingEntry = dataAccessService.readUserCheckValues(username, userId);

            if (existingEntry.isPresent()) {
                return existingEntry.get();
            } else {
                // Create a default entry
                UsersCheckValueEntry newEntry = createDefaultCheckValuesEntry(username, userId);

                // Save the default entry to create the file
                dataAccessService.writeUserCheckValues(newEntry, username, userId);
                LoggerUtil.info(this.getClass(), "Created default check values file for user " + username);

                return newEntry;
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting check values for user " + username + ": " + e.getMessage());
            // Return a safe default rather than throwing exception
            return createDefaultCheckValuesEntry(username, userId != null ? userId : -1);
        }
    }

    /**
     * Gets all users with check-related roles
     * @return List of users with check-related roles
     */
    @PreAuthorize("hasAnyRole('ROLE_TL_CHECKING', 'ROLE_ADMIN')")
    public List<User> getAllCheckUsers() {
        try {
            List<User> allUsers = userService.getAllUsers();

            return allUsers.stream()
                    .filter(user -> user.getRole().contains(SecurityConstants.ROLE_CHECKING))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting check users: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Gets check values for all users with check-related roles
     * @return Maps of usernames to check values entries
     */
    @PreAuthorize("hasAnyRole('ROLE_TL_CHECKING', 'ROLE_ADMIN')")
    public List<UsersCheckValueEntry> getAllCheckValues() {
        try {
            List<User> checkUsers = getAllCheckUsers();
            List<UsersCheckValueEntry> allCheckValues = new ArrayList<>();

            for (User user : checkUsers) {
                UsersCheckValueEntry entry = getUserCheckValues(user.getUsername(), user.getUserId());
                allCheckValues.add(entry);
            }

            return allCheckValues;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting all check values: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Gets the default value for a specific check type
     * @param checkType The check type
     * @return The default value for the check type
     */
    private double getDefaultCheckTypeValue(String checkType) {
        return switch (checkType) {
            case "LAYOUT" -> 1.0;
            case "KIPSTA LAYOUT" -> 0.25;
            case "LAYOUT CHANGES" -> 0.25;
            case "GPT" -> 0.1;
            case "PRODUCTION" -> 0.1;
            case "REORDER" -> 0.1;
            case "SAMPLE" -> 0.3;
            case "OMS PRODUCTION" -> 0.1;
            case "KIPSTA PRODUCTION" -> 0.1;
            default -> 0.1;
        };
    }
}