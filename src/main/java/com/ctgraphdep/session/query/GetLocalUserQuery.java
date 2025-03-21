package com.ctgraphdep.session.query;

import com.ctgraphdep.model.User;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.utils.LoggerUtil;

import java.util.List;

public class GetLocalUserQuery implements SessionQuery<User> {
    @Override
    public User execute(SessionContext context) {
        try {
            // Use DataAccessService to read local users
            List<User> localUsers = context.getDataAccessService().readLocalUsers();

            // Return the first user (since it's a single-user application)
            if (!localUsers.isEmpty()) {
                return localUsers.get(0);
            }

            LoggerUtil.warn(this.getClass(), "No local user found");
            return null;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading local user: " + e.getMessage());
            return null;
        }
    }
}
