package com.ctgraphdep.service;


import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SessionRecoveryService {
    private final UserSessionService userSessionService;
    private final SessionCalculator sessionCalculator;
    private final UserService userService;

    @Autowired
    public SessionRecoveryService(
            UserSessionService userSessionService,
            SessionCalculator sessionCalculator,
            UserService userService) {
        this.userSessionService = userSessionService;
        this.sessionCalculator = sessionCalculator;
        this.userService = userService;
    }

    public void recoverSession(String username, Integer userId) {
        try {
            User user = userService.getUserById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            WorkUsersSessionsStates session = userSessionService.getCurrentSession(username, userId);

            if (!sessionCalculator.isValidSession(session)) {
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
        return sessionCalculator.isSessionFromPreviousDay(session) ||
                sessionCalculator.isStuckTemporaryStop(session);
    }

    private void handleRecovery(User user, WorkUsersSessionsStates session) {
        if (sessionCalculator.isSessionFromPreviousDay(session)) {
            handlePreviousDaySession(user, session);
        } else if (sessionCalculator.isStuckTemporaryStop(session)) {
            handleStuckTemporaryStop(user, session);
        }
    }

    private void handlePreviousDaySession(User user, WorkUsersSessionsStates session) {
        int finalMinutes = sessionCalculator.calculateFinalMinutes(user, session);
        userSessionService.endDay(session.getUsername(), session.getUserId(), finalMinutes);
    }

    private void handleStuckTemporaryStop(User user, WorkUsersSessionsStates session) {
        int finalMinutes = session.getTotalWorkedMinutes() != null ?
                session.getTotalWorkedMinutes() -
                        (session.getTotalTemporaryStopMinutes() != null ?
                                session.getTotalTemporaryStopMinutes() : 0) : 0;

        userSessionService.endDay(session.getUsername(), session.getUserId(), finalMinutes);
    }
}