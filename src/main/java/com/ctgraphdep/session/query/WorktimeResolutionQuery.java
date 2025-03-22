package com.ctgraphdep.session.query;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.util.List;

/**
 * Query that checks for unresolved worktime entries based solely on the worktime file.
 * This is completely decoupled from session state.
 */
public class WorktimeResolutionQuery implements SessionQuery<WorktimeResolutionQuery.ResolutionStatus> {
    private final String username;
    private final Integer userId;

    public WorktimeResolutionQuery(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public ResolutionStatus execute(SessionContext context) {
        try {
            // Find unresolved worktime entries using the existing query
            UnresolvedWorkTimeQuery unresolvedQuery = new UnresolvedWorkTimeQuery(username, userId);
            List<WorkTimeTable> unresolvedEntries = context.executeQuery(unresolvedQuery);

            boolean needsResolution = !unresolvedEntries.isEmpty();

            if (needsResolution) {
                LoggerUtil.info(this.getClass(),
                        String.format("User %s has %d unresolved worktime entries",
                                username, unresolvedEntries.size()));
            }

            return new ResolutionStatus(needsResolution, unresolvedEntries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error checking worktime resolution for user %s: %s",
                            username, e.getMessage()));
            return new ResolutionStatus(false, List.of());
        }
    }

    /**
     * Simple class representing the resolution status based solely on worktime entries
     */
    @Getter
    public static class ResolutionStatus {
        private final boolean needsResolution;
        private final List<WorkTimeTable> unresolvedEntries;

        public ResolutionStatus(boolean needsResolution, List<WorkTimeTable> unresolvedEntries) {
            this.needsResolution = needsResolution;
            this.unresolvedEntries = unresolvedEntries;
        }

        @Override
        public String toString() {
            return String.format("ResolutionStatus(needsResolution=%b, unresolvedEntries=%d)", needsResolution, unresolvedEntries.size());
        }
    }
}