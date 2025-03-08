package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UserStatusDTO;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.db.SessionStatusEntity;
import com.ctgraphdep.repository.SessionStatusRepository;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing user session status in the database.
 * This provides a reliable way to track and display user status
 * without the concurrency issues of file-based status tracking.
 */
@Service
public class SessionStatusService {

    private final SessionStatusRepository sessionStatusRepository;
    private final UserService userService;

    // Simple in-memory cache with expiration for status DTOs
    private volatile List<UserStatusDTO> cachedStatuses = null;
    private volatile LocalDateTime cacheTimestamp = null;
    private static final long CACHE_TTL_SECONDS = 5; // Cache valid for 5 seconds

    // Cache for online/active counts
    private volatile int cachedOnlineCount = 0;
    private volatile int cachedActiveCount = 0;
    private volatile LocalDateTime countCacheTimestamp = null;

    @Autowired
    public SessionStatusService(SessionStatusRepository sessionStatusRepository, UserService userService) {
        this.sessionStatusRepository = sessionStatusRepository;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Scheduled method to invalidate cache periodically
     */
    @Scheduled(fixedRate = 10000) // Every 10 seconds
    public void invalidateCache() {
        cachedStatuses = null;
        cacheTimestamp = null;
        cachedOnlineCount = 0;
        cachedActiveCount = 0;
        countCacheTimestamp = null;
        LoggerUtil.debug(this.getClass(), "Session status cache invalidated");
    }

    /**
     * Updates a user's session status in the database
     */
    @Transactional
    public void updateSessionStatus(String username, Integer userId, String status, LocalDateTime lastActive) {
        try {
            User user = userService.getUserByUsername(username).orElse(null);
            if (user == null) {
                LoggerUtil.warn(this.getClass(), "Attempted to update status for unknown user: " + username);
                return;
            }

            // Find existing record or create new one
            SessionStatusEntity entity = sessionStatusRepository.findByUsername(username)
                    .orElse(new SessionStatusEntity());

            // Update entity fields
            entity.setUsername(username);
            entity.setUserId(userId);
            entity.setName(user.getName());
            entity.setStatus(status);
            entity.setLastActive(lastActive);
            entity.setLastUpdated(LocalDateTime.now());

            // Save to database
            sessionStatusRepository.save(entity);

            // Invalidate cache when status changes
            cachedStatuses = null;
            cacheTimestamp = null;
            cachedOnlineCount = 0;
            cachedActiveCount = 0;
            countCacheTimestamp = null;

            LoggerUtil.debug(this.getClass(),
                    String.format("Updated session status for %s to %s", username, status));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error updating session status for %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Updates a user's session status from a WorkUsersSessionsStates object
     */
    @Transactional
    public void updateSessionStatus(WorkUsersSessionsStates session) {
        if (session == null) return;

        try {
            String status = determineStatus(session.getSessionStatus());
            updateSessionStatus(
                    session.getUsername(),
                    session.getUserId(),
                    status,
                    session.getLastActivity()
            );
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error updating session status from session object: %s", e.getMessage()), e);
        }
    }

    /**
     * Gets all user statuses for display on the status page.
     * Uses in-memory caching to reduce database queries.
     */
    public List<UserStatusDTO> getAllUserStatuses() {
        try {
            // Check if we have a valid cache
            if (cachedStatuses != null && cacheTimestamp != null) {
                long secondsSinceLastCache =
                        java.time.Duration.between(cacheTimestamp, LocalDateTime.now()).getSeconds();

                if (secondsSinceLastCache < CACHE_TTL_SECONDS) {
                    LoggerUtil.debug(this.getClass(), "Returning cached user statuses");
                    return new ArrayList<>(cachedStatuses); // Return a copy of cached list
                }
            }

            // Get all users from UserService
            List<User> allUsers = userService.getAllUsers().stream()
                    .filter(user -> !user.isAdmin() &&
                            !user.getRole().equals("ADMIN") &&
                            !user.getRole().equals("ADMINISTRATOR") &&
                            !user.getUsername().equalsIgnoreCase("admin"))
                    .toList();

            // Create a map to store results
            List<UserStatusDTO> result = new ArrayList<>();

            // Get all status entities
            List<SessionStatusEntity> statusEntities = sessionStatusRepository.findAll();

            // Convert status entities to DTOs
            for (User user : allUsers) {
                // Find corresponding status entity
                SessionStatusEntity entity = statusEntities.stream()
                        .filter(e -> e.getUsername().equals(user.getUsername()))
                        .findFirst()
                        .orElse(null);

                // Convert to DTO
                if (entity != null) {
                    result.add(UserStatusDTO.builder()
                            .username(entity.getUsername())
                            .userId(entity.getUserId())
                            .name(entity.getName())
                            .status(entity.getStatus())
                            .lastActive(formatDateTime(entity.getLastActive()))
                            .build());
                } else {
                    // No status record exists - user is offline
                    result.add(UserStatusDTO.builder()
                            .username(user.getUsername())
                            .userId(user.getUserId())
                            .name(user.getName())
                            .status(WorkCode.WORK_OFFLINE)
                            .lastActive(WorkCode.LAST_ACTIVE_NEVER)
                            .build());
                }
            }

            // Sort results by status and then by name
            result.sort(Comparator
                    .comparing((UserStatusDTO dto) -> {
                        // First level sorting - by status with custom order
                        if (WorkCode.WORK_ONLINE.equals(dto.getStatus())) return 1;
                        if (WorkCode.WORK_TEMPORARY_STOP.equals(dto.getStatus())) return 2;
                        return 3; // All other statuses
                    })
                    .thenComparing(UserStatusDTO::getName, String.CASE_INSENSITIVE_ORDER));

            // Update cache
            cachedStatuses = new ArrayList<>(result);
            cacheTimestamp = LocalDateTime.now();

            // Update count cache as well
            updateCountCache(result);

            return result;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting all user statuses: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Updates the count cache based on the status results
     */
    private void updateCountCache(List<UserStatusDTO> statuses) {
        cachedOnlineCount = (int) statuses.stream()
                .filter(s -> WorkCode.WORK_ONLINE.equals(s.getStatus()))
                .count();

        cachedActiveCount = (int) statuses.stream()
                .filter(s -> WorkCode.WORK_ONLINE.equals(s.getStatus()) ||
                        WorkCode.WORK_TEMPORARY_STOP.equals(s.getStatus()))
                .count();

        countCacheTimestamp = LocalDateTime.now();
    }

    /**
     * Get the number of online users with caching
     */
    public int getOnlineUserCount() {
        try {
            // Check if we have a valid count cache
            if (countCacheTimestamp != null) {
                long secondsSinceLastCache =
                        java.time.Duration.between(countCacheTimestamp, LocalDateTime.now()).getSeconds();

                if (secondsSinceLastCache < CACHE_TTL_SECONDS) {
                    return cachedOnlineCount;
                }
            }

            // If no cached statuses or cache is expired, force a refresh
            if (cachedStatuses == null || cacheTimestamp == null ||
                    java.time.Duration.between(cacheTimestamp, LocalDateTime.now()).getSeconds() >= CACHE_TTL_SECONDS) {
                getAllUserStatuses(); // This will update the count cache
                return cachedOnlineCount;
            }

            // If we have cached statuses but no count cache, calculate and cache it
            cachedOnlineCount = (int) cachedStatuses.stream()
                    .filter(s -> WorkCode.WORK_ONLINE.equals(s.getStatus()))
                    .count();
            countCacheTimestamp = LocalDateTime.now();
            return cachedOnlineCount;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting online user count: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Get the number of active users (online or temporary stop) with caching
     */
    public int getActiveUserCount() {
        try {
            // Check if we have a valid count cache
            if (countCacheTimestamp != null) {
                long secondsSinceLastCache =
                        java.time.Duration.between(countCacheTimestamp, LocalDateTime.now()).getSeconds();

                if (secondsSinceLastCache < CACHE_TTL_SECONDS) {
                    return cachedActiveCount;
                }
            }

            // If no cached statuses or cache is expired, force a refresh
            if (cachedStatuses == null || cacheTimestamp == null ||
                    java.time.Duration.between(cacheTimestamp, LocalDateTime.now()).getSeconds() >= CACHE_TTL_SECONDS) {
                getAllUserStatuses(); // This will update the count cache
                return cachedActiveCount;
            }

            // If we have cached statuses but no count cache, calculate and cache it
            cachedActiveCount = (int) cachedStatuses.stream()
                    .filter(s -> WorkCode.WORK_ONLINE.equals(s.getStatus()) ||
                            WorkCode.WORK_TEMPORARY_STOP.equals(s.getStatus()))
                    .count();
            countCacheTimestamp = LocalDateTime.now();
            return cachedActiveCount;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting active user count: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Helper method to determine status string from work code
     */
    private String determineStatus(String workCode) {
        if (workCode == null) {
            return WorkCode.WORK_OFFLINE;
        }

        return switch (workCode) {
            case WorkCode.WORK_ONLINE -> WorkCode.WORK_ONLINE;
            case WorkCode.WORK_TEMPORARY_STOP -> WorkCode.WORK_TEMPORARY_STOP;
            case WorkCode.WORK_OFFLINE -> WorkCode.WORK_OFFLINE;
            default -> WorkCode.STATUS_UNKNOWN;
        };
    }

    /**
     * Format date/time for display
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return WorkCode.LAST_ACTIVE_NEVER;
        }
        return dateTime.format(WorkCode.INPUT_FORMATTER);
    }
}