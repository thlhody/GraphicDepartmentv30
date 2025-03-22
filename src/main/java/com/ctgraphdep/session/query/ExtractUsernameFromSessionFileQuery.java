package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.ValidationUtil;

import java.util.Optional;

public class ExtractUsernameFromSessionFileQuery implements SessionQuery<Optional<String>> {
    private final String filename;

    public ExtractUsernameFromSessionFileQuery(String filename) {
        ValidationUtil.validateNotEmpty(filename, "Filename");
        this.filename = filename;
    }

    @Override
    public Optional<String> execute(SessionContext context) {
        try {
            String cleanFilename = filename.replace("session_", "").replace(".json", "");
            String[] parts = cleanFilename.split("_");

            return parts.length >= 2
                    ? Optional.of(parts[0])
                    : Optional.empty();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error extracting username from filename: " + filename, e);
            return Optional.empty();
        }
    }
}