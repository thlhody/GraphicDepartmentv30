package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.model.WorkUsersSessionsStates;

//Query to get the current session for a user
public class GetCurrentSessionQuery implements SessionQuery<WorkUsersSessionsStates> {

    private final String username;
    private final Integer userId;

    public GetCurrentSessionQuery(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        return context.getCurrentSession(username, userId);
    }
}