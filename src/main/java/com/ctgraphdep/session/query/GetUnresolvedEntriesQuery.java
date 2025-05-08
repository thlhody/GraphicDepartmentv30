package com.ctgraphdep.session.query;

import com.ctgraphdep.model.dto.session.ResolutionCalculationDTO;
import com.ctgraphdep.service.SessionService;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;

import java.util.List;

public class GetUnresolvedEntriesQuery implements SessionQuery<List<ResolutionCalculationDTO>> {
    private final String username;
    private final Integer userId;

    public GetUnresolvedEntriesQuery(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public List<ResolutionCalculationDTO> execute(SessionContext context) {
        // Get SessionService from context
        SessionService sessionService = context.getSessionService();

        // Use SessionService to get unresolved entries
        return sessionService.getUnresolvedWorkTimeEntries(username, userId);
    }
}
