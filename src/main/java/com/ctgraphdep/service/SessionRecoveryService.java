package com.ctgraphdep.service;

import com.ctgraphdep.enums.SessionEndRule;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SessionRecoveryService {
    private final UserSessionService userSessionService;
    private final CalculateSessionService calculateSessionService;
    private final SessionPersistenceService persistenceService;
    private final UserService userService;
    private final SystemNotificationService notificationService;

    @Autowired
    public SessionRecoveryService(
            UserSessionService userSessionService,
            CalculateSessionService calculateSessionService,
            SessionPersistenceService persistenceService,
            UserService userService,
            SystemNotificationService notificationService) {
        this.userSessionService = userSessionService;
        this.calculateSessionService = calculateSessionService;
        this.persistenceService = persistenceService;
        this.userService = userService;
        this.notificationService = notificationService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Main recovery method for sessions
     */
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

            // Calculate current metrics before recovery
            session = calculateSessionService.calculateSessionMetrics(session, user.getSchedule());

            // Check for applicable rules
            Optional<SessionEndRule> rule = calculateSessionService.checkSessionEndRules(
                    session,
                    user.getSchedule()
            );

            // Process session based on rules
            if (rule.isPresent()) {
                handleSessionRecovery(session, user, rule.get());
            } else {
                // No rules apply, just resume normal session
                userSessionService.resumeSession(session);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error recovering session for user %s: %s",
                            username, e.getMessage()));
        }
    }

    /**
     * Handle session recovery based on rule
     */
    private void handleSessionRecovery(
            WorkUsersSessionsStates session,
            User user,
            SessionEndRule rule) {
        try {
            switch (rule) {
                case PREVIOUS_DAY_SESSION -> handlePreviousDaySession(session, user);
                case MAX_TEMP_STOP_REACHED -> handleMaxTempStopSession(session, user);
                case LONG_TEMP_STOP -> handleLongTempStopSession(session, user);
                case OVERTIME_REACHED, SCHEDULE_END_REACHED -> handleScheduleEndSession(session, user, rule);
                default -> {
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error handling session recovery with rule %s: %s",
                            rule, e.getMessage()));
        }
    }

    /**
     * Handle previous day session
     */
    private void handlePreviousDaySession(
            WorkUsersSessionsStates session,
            User user) {
        int finalMinutes = calculateSessionService.calculateFinalMinutes(user, session);
        userSessionService.endDay(session.getUsername(), session.getUserId(), finalMinutes);

        LoggerUtil.info(this.getClass(),
                String.format("Ended previous day session for user %s with %d minutes",
                        session.getUsername(), finalMinutes));

    }

    /**
     * Handle session that reached maximum temporary stop
     */
    private void handleMaxTempStopSession(
            WorkUsersSessionsStates session,
            User user) {
        endStuckSession(user, session);
    }

    /**
     * Handle long temporary stop session
     */
    private void handleLongTempStopSession(
            WorkUsersSessionsStates session,
            User user) {
        // Update current temporary stop duration
        updateTempStopDuration(session);

        // Show warning
        notificationService.showLongTempStopWarning(
                session.getUsername(),
                session.getUserId(),
                session.getLastTemporaryStopTime()
        );

        // Continue monitoring
        persistenceService.persistSession(session);
    }

    /**
     * Handle schedule end or overtime session
     */
    private void handleScheduleEndSession(
            WorkUsersSessionsStates session,
            User user,
            SessionEndRule rule) {
        // Calculate final minutes
        int finalMinutes = calculateSessionService.calculateFinalMinutes(user, session);

        // Show appropriate notification
        if (rule.requiresNotification()) {
            notificationService.showSessionWarning(
                    session.getUsername(),
                    session.getUserId(),
                    finalMinutes
            );
        }

        // Continue monitoring
        persistenceService.persistSession(session);
    }

    /**
     * Update temporary stop duration
     */
    private void updateTempStopDuration(WorkUsersSessionsStates session) {
        if (!session.getTemporaryStops().isEmpty()) {
            TemporaryStop lastStop = session.getTemporaryStops().get(
                    session.getTemporaryStops().size() - 1
            );

            // Calculate current duration
            int tempStopDuration = calculateSessionService.calculateCurrentTempStopDuration(session);

            // Update stop info
            if (lastStop.getEndTime() == null) {
                lastStop.setDuration(tempStopDuration);
                session.setTotalTemporaryStopMinutes(
                        (session.getTotalTemporaryStopMinutes() != null ?
                                session.getTotalTemporaryStopMinutes() : 0) + tempStopDuration
                );
            }
        }
    }

    /**
     * End a stuck session
     */
    private void endStuckSession(User user, WorkUsersSessionsStates session) {
        int finalMinutes = calculateSessionService.calculateFinalMinutes(user, session);
        userSessionService.endDay(session.getUsername(), session.getUserId(), finalMinutes);

        LoggerUtil.info(this.getClass(),
                String.format("Ended stuck session for user %s with %d minutes",
                        session.getUsername(), finalMinutes));
    }
}