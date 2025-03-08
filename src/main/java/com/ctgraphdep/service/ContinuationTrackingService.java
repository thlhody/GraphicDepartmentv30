package com.ctgraphdep.service;

import com.ctgraphdep.model.ContinuationPoint;
import com.ctgraphdep.model.db.ContinuationPointEntity;
import com.ctgraphdep.repository.ContinuationPointRepository;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service to track session continuation points.
 * This is used when users choose to continue working after notifications
 * or when the system detects a session without user response.
 */
@Service
public class ContinuationTrackingService {

    private final ContinuationPointRepository continuationPointRepository;

    public ContinuationTrackingService(ContinuationPointRepository continuationPointRepository) {
        this.continuationPointRepository = continuationPointRepository;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Records a continuation point when user chooses to continue working
     * or when the system detects no response to a notification.
     *
     * @param username The username
     * @param userId The user ID
     * @param timestamp When the continuation was recorded
     * @param isHourly Whether this is from an hourly warning (true) or schedule end (false)
     */
    @Transactional
    public void recordContinuationPoint(String username, Integer userId, LocalDateTime timestamp, boolean isHourly) {
        try {
            ContinuationPointEntity entity = new ContinuationPointEntity();
            entity.setUsername(username);
            entity.setUserId(userId);
            entity.setTimestamp(timestamp);
            entity.setSessionDate(timestamp.toLocalDate());
            entity.setType(isHourly ? "HOURLY" : "SCHEDULE_END");
            entity.setActive(true);
            entity.setResolved(false);
            entity.setResolvedBy(null);
            entity.setResolvedAt(null);

            continuationPointRepository.save(entity);

            LoggerUtil.info(this.getClass(),
                    String.format("Recorded %s continuation point for user %s at %s",
                            isHourly ? "hourly" : "schedule end", username, timestamp));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error recording continuation point for %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Records a temporary stop continuation
     *
     * @param username The username
     * @param userId The user ID
     * @param timestamp When the temp stop continuation was recorded
     */
    @Transactional
    public void recordTempStopContinuation(String username, Integer userId, LocalDateTime timestamp) {
        try {
            ContinuationPointEntity entity = new ContinuationPointEntity();
            entity.setUsername(username);
            entity.setUserId(userId);
            entity.setTimestamp(timestamp);
            entity.setSessionDate(timestamp.toLocalDate());
            entity.setType("TEMP_STOP");
            entity.setActive(true);
            entity.setResolved(false);
            entity.setResolvedBy(null);
            entity.setResolvedAt(null);

            continuationPointRepository.save(entity);

            LoggerUtil.info(this.getClass(),
                    String.format("Recorded temporary stop continuation for user %s at %s", username, timestamp));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error recording temp stop continuation for %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Record an automatic midnight session end
     * This happens when a session is still active at midnight
     *
     * @param username The username
     * @param userId The user ID
     */
    @Transactional
    public void recordMidnightSessionEnd(String username, Integer userId) {
        try {
            ContinuationPointEntity entity = new ContinuationPointEntity();
            entity.setUsername(username);
            entity.setUserId(userId);
            entity.setTimestamp(LocalDateTime.now());
            entity.setSessionDate(LocalDate.now().minusDays(1)); // Previous day
            entity.setType("MIDNIGHT_END");
            entity.setActive(true);
            entity.setResolved(false);
            entity.setResolvedBy(null);
            entity.setResolvedAt(null);

            continuationPointRepository.save(entity);

            LoggerUtil.info(this.getClass(),
                    String.format("Recorded midnight session end for user %s", username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error recording midnight session end for %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Gets all active continuation points for a user on a specific date
     *
     * @param username The username
     * @param date The date to check for continuation points
     * @return List of continuation points
     */
    public List<ContinuationPoint> getActiveContinuationPoints(String username, LocalDate date) {
        try {
            List<ContinuationPointEntity> entities =
                    continuationPointRepository.findByUsernameAndSessionDateAndActiveTrue(username, date);

            return entities.stream()
                    .map(this::mapEntityToModel)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error retrieving continuation points for %s: %s", username, e.getMessage()), e);
            return List.of();
        }
    }

    /**
     * Checks if a user has an active midnight session end that needs resolution
     *
     * @param username The username
     * @return true if there's an unresolved midnight session end
     */
    public boolean hasUnresolvedMidnightEnd(String username) {
        try {
            return continuationPointRepository.existsByUsernameAndTypeAndActiveTrueAndResolvedFalse(
                    username, "MIDNIGHT_END");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error checking for unresolved midnight end for %s: %s", username, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Resolves continuation points for a user
     *
     * @param username The username whose points are being resolved
     * @param date The date of the continuation points
     * @param resolvedByUsername The username of the person resolving the points
     * @param overtime The overtime minutes that were applied
     */
    @Transactional
    public void resolveContinuationPoints(String username, LocalDate date, String resolvedByUsername, int overtime) {
        try {
            List<ContinuationPointEntity> entities =
                    continuationPointRepository.findByUsernameAndSessionDateAndActiveTrue(username, date);

            LocalDateTime now = LocalDateTime.now();

            for (ContinuationPointEntity entity : entities) {
                entity.setActive(false);
                entity.setResolved(true);
                entity.setResolvedBy(resolvedByUsername);
                entity.setResolvedAt(now);
                entity.setOvertimeApplied(overtime);

                continuationPointRepository.save(entity);
            }

            LoggerUtil.info(this.getClass(),
                    String.format("Resolved %d continuation points for user %s on %s with %d overtime minutes",
                            entities.size(), username, date, overtime));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error resolving continuation points for %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Gets recommended overtime based on continuation points
     * Analyzes the continuation points to determine the appropriate overtime
     *
     * @param username The username
     * @param date The date to analyze
     * @return Recommended overtime in minutes
     */
    public int getRecommendedOvertime(String username, LocalDate date) {
        try {
            List<ContinuationPointEntity> entities =
                    continuationPointRepository.findByUsernameAndSessionDateAndActiveTrue(username, date);

            if (entities.isEmpty()) {
                return 0;
            }

            // Count hourly continuations (each is worth 60 minutes)
            long hourlyCount = entities.stream()
                    .filter(e -> "HOURLY".equals(e.getType()))
                    .count();

            // If there are only SCHEDULE_END or MIDNIGHT_END points, recommend 30 minutes
            if (hourlyCount == 0) {
                return 30;
            }

            // Limit to a maximum of 3 hours overtime
            return Math.min((int)hourlyCount * 60, 180);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error calculating recommended overtime for %s: %s", username, e.getMessage()), e);
            return 0;
        }
    }

    /**
     * Maps an entity to a model object
     */
    private ContinuationPoint mapEntityToModel(ContinuationPointEntity entity) {
        return ContinuationPoint.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .userId(entity.getUserId())
                .timestamp(entity.getTimestamp())
                .sessionDate(entity.getSessionDate())
                .type(entity.getType())
                .active(entity.isActive())
                .resolved(entity.isResolved())
                .resolvedBy(entity.getResolvedBy())
                .resolvedAt(entity.getResolvedAt())
                .overtimeApplied(entity.getOvertimeApplied())
                .build();
    }
}