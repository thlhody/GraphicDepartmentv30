package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Query to validate authentication and retrieve a user
 */
public class AuthenticatedUserQuery implements SessionQuery<User> {
    private final UserDetails userDetails;

    public AuthenticatedUserQuery(UserDetails userDetails) {
        this.userDetails = userDetails;
    }

    @Override
    public User execute(SessionContext context) {
        if (userDetails == null) {
            LoggerUtil.error(this.getClass(), "Null UserDetails during authentication");
        }

        return context.getUserService().getUserByUsername(userDetails.getUsername()).orElseThrow(() -> {
                    LoggerUtil.error(this.getClass(), String.format("No user found for username: %s", userDetails.getUsername()));
                    return new IllegalStateException("User not found");
                });
    }
}