package com.ctgraphdep.session.query;

import com.ctgraphdep.model.User;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.utils.LoggerUtil;

// This query is used throughout the session system to get the current/local user for a single-user application environment.
public class GetLocalUserQuery implements SessionQuery<User> {

    @Override
    public User execute(SessionContext context) {
        try {
            User user = context.getMainDefaultUserContextService().getCurrentUser();

            if (user != null && !"system".equals(user.getUsername())) {
                LoggerUtil.debug(this.getClass(), String.format("Found local user from context: %s (ID: %d, Role: %s)", user.getUsername(), user.getUserId(), user.getRole()));
                return user;
            }

            LoggerUtil.warn(this.getClass(), "No local user found in user context");
            return null;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading local user from context: " + e.getMessage(), e);
            return null;
        }
    }
}