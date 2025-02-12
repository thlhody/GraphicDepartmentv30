package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SessionRecoveryService {
    private final UserSessionService userSessionService;
    private final CalculateSessionService calculateSessionService;
    private final UserService userService;
    private final SystemNotificationService notificationService;

    @Autowired
    public SessionRecoveryService(
            UserSessionService userSessionService,
            CalculateSessionService calculateSessionService,
            UserService userService, SystemNotificationService notificationService) {
        this.userSessionService = userSessionService;
        this.calculateSessionService = calculateSessionService;
        this.userService = userService;
        this.notificationService = notificationService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public void recoverSession(String username, Integer userId) {
        try {
            LoggerUtil.info(this.getClass(), "Starting session recovery for user: " + username);

            User user = userService.getUserById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            if (user.isAdmin()) {
                LoggerUtil.info(this.getClass(),
                        "Skipping session recovery for admin user: " + username);
                return;
            }

            WorkUsersSessionsStates session = userSessionService.getCurrentSession(username, userId);
            if (!calculateSessionService.isValidSession(session)) {
                return;
            }

            // Calculate actual worked time before any recovery
            calculateSessionService.calculateCurrentWork(session, user.getSchedule());

            // Process based on session state
            WorkUsersSessionsStates recoveredSession = switch (session.getSessionStatus()) {
                case WorkCode.WORK_TEMPORARY_STOP -> handleTemporaryStop(user, session);
                case WorkCode.WORK_ONLINE -> handleOnlineSession(user, session);
                default -> null;
            };

            if (recoveredSession != null) {
                LoggerUtil.info(this.getClass(),
                        String.format("Successfully recovered session for %s: Status=%s, WorkedMinutes=%d",
                                username, recoveredSession.getSessionStatus(),
                                recoveredSession.getTotalWorkedMinutes()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error recovering session for user %s: %s", username, e.getMessage()));
        }
    }

    private WorkUsersSessionsStates handleTemporaryStop(User user, WorkUsersSessionsStates session) {
        int tempStopDuration = calculateSessionService.calculateCurrentTempStopDuration(session);
        LocalDateTime tempStopStart = session.getLastTemporaryStopTime();

        // Check if temp stop is stuck
        if (tempStopDuration >= WorkCode.MAX_TEMP_STOP_HOURS * WorkCode.HOUR_DURATION) {
            endStuckSession(user, session);
            notificationService.showLongTempStopWarning(
                    session.getUsername(),
                    session.getUserId(),
                    tempStopStart
            );
            return null;
        }

        // For normal temp stops, update calculations and continue monitoring
        if (!session.getTemporaryStops().isEmpty()) {
            TemporaryStop lastStop = session.getTemporaryStops().get(
                    session.getTemporaryStops().size() - 1
            );

            // Update current temp stop duration
            if (lastStop.getEndTime() == null) {
                lastStop.setDuration(tempStopDuration);
                session.setTotalTemporaryStopMinutes(
                        (session.getTotalTemporaryStopMinutes() != null ?
                                session.getTotalTemporaryStopMinutes() : 0) + tempStopDuration
                );
            }
        }

        userSessionService.resumeSession(session);
        return session;
    }

    private WorkUsersSessionsStates handleOnlineSession(User user, WorkUsersSessionsStates session) {
        if (calculateSessionService.isSessionFromPreviousDay(session)) {
            int finalMinutes = calculateSessionService.calculateFinalMinutes(user, session);
            userSessionService.endDay(session.getUsername(), session.getUserId(), finalMinutes);
            return null;
        }

        // Check work duration
        if (calculateSessionService.shouldEndSession(session, LocalDateTime.now())) {
            int finalMinutes = calculateSessionService.calculateFinalMinutes(user, session);
            notificationService.showSessionWarning(
                    session.getUsername(),
                    session.getUserId(),
                    finalMinutes
            );
        }

        userSessionService.resumeSession(session);
        return session;
    }

    private void endStuckSession(User user, WorkUsersSessionsStates session) {
        // Calculate final minutes
        int finalMinutes = calculateSessionService.calculateFinalMinutes(user, session);

        // End the session
        userSessionService.endDay(session.getUsername(), session.getUserId(), finalMinutes);

        LoggerUtil.info(this.getClass(),
                String.format("Ended stuck session for user %s with %d minutes",
                        session.getUsername(), finalMinutes));
    }
}