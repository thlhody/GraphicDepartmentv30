package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;

/**
 * Query to generate a unique notification key for rate limiting.
 */
public class GetNotificationKeyQuery implements SessionQuery<String> {
    private final String username;
    private final String notificationType;

    /**
     * Creates a new query to generate a notification key
     *
     * @param username The username
     * @param notificationType The notification type
     */
    public GetNotificationKeyQuery(String username, String notificationType) {
        this.username = username;
        this.notificationType = notificationType;
    }

    @Override
    public String execute(SessionContext context) {
        return username + "_" + notificationType;
    }
}