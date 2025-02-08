package com.ctgraphdep.service;

import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SessionRecoveryService {
    private final UserSessionService userSessionService;
    private final CalculateSessionService calculateSessionService;
    private final UserService userService;

    @Autowired
    public SessionRecoveryService(
            UserSessionService userSessionService,
            CalculateSessionService calculateSessionService,
            UserService userService) {
        this.userSessionService = userSessionService;
        this.calculateSessionService = calculateSessionService;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public void recoverSession(String username, Integer userId) {
        try {
            User user = userService.getUserById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            // Add check for admin user
            if (user.isAdmin()) {
                LoggerUtil.info(this.getClass(),
                        String.format("Skipping session recovery for admin user %s", username));
                return;
            }

            WorkUsersSessionsStates session = userSessionService.getCurrentSession(username, userId);

            if (!calculateSessionService.isValidSession(session)) {
                return;
            }

            if (needsRecovery(session)) {
                handleRecovery(user, session);
            } else {
                userSessionService.resumeSession(session);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error recovering session for user %s: %s", username, e.getMessage()));
        }
    }

    private boolean needsRecovery(WorkUsersSessionsStates session) {
        return calculateSessionService.isSessionFromPreviousDay(session) ||
                calculateSessionService.isStuckTemporaryStop(session);
    }

    private void handleRecovery(User user, WorkUsersSessionsStates session) {
        if (calculateSessionService.isSessionFromPreviousDay(session)) {
            handlePreviousDaySession(user, session);
        } else if (calculateSessionService.isStuckTemporaryStop(session)) {
            handleStuckTemporaryStop(user, session);
        }
    }

    private void handlePreviousDaySession(User user, WorkUsersSessionsStates session) {
        Integer finalMinutes = calculateSessionService.calculateFinalMinutes(user, session);
        userSessionService.endDay(session.getUsername(), session.getUserId(), finalMinutes);
    }

    private void handleStuckTemporaryStop(User user, WorkUsersSessionsStates session) {
        Integer finalMinutes = session.getTotalWorkedMinutes() != null ?
                session.getTotalWorkedMinutes() -
                        (session.getTotalTemporaryStopMinutes() != null ?
                                session.getTotalTemporaryStopMinutes() : 0) : 0;

        userSessionService.endDay(session.getUsername(), session.getUserId(), finalMinutes);
    }
}