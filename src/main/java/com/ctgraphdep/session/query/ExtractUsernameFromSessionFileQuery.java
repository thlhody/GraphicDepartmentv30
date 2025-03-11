package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.utils.LoggerUtil;

/**
 * Query to extract a username from a session filename.
 */
public class ExtractUsernameFromSessionFileQuery implements SessionQuery<String> {
    private final String filename;

    /**
     * Creates a new query to extract username from session filename
     *
     * @param filename The session filename
     */
    public ExtractUsernameFromSessionFileQuery(String filename) {
        this.filename = filename;
    }

    @Override
    public String execute(SessionContext context) {
        try {
            String[] parts = filename.replace("session_", "").replace(".json", "").split("_");
            if (parts.length >= 2) {
                return parts[0];
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error extracting username from filename: " + filename);
        }
        return null;
    }
}