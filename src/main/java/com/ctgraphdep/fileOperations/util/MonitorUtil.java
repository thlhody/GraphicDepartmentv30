package com.ctgraphdep.fileOperations.util;


import java.nio.file.Path;

/**
 * Monitor-related utilities.
 */
public class MonitorUtil {

    /**
     * Converts a file path to a string key for mapping.
     *
     * @param path The file path
     * @return A string key
     */
    public static String pathToKey(Path path) {
        return path.toString().replace('\\', '/');
    }

    /**
     * Parses a user ID from a filename based on a pattern.
     *
     * @param filename The filename to parse
     * @param pattern The pattern (e.g., "user_{userId}_")
     * @return The parsed user ID, or null if not found
     */
    public static Integer parseUserIdFromFilename(String filename, String pattern) {
        if (filename == null || pattern == null) {
            return null;
        }

        int startIndex = pattern.indexOf("{userId}");
        if (startIndex < 0) {
            return null;
        }

        String prefix = pattern.substring(0, startIndex);
        String suffix = pattern.substring(startIndex + "{userId}".length());

        if (filename.startsWith(prefix) && filename.contains(suffix)) {
            int idStartIndex = prefix.length();
            int idEndIndex = filename.indexOf(suffix, idStartIndex);

            if (idEndIndex > idStartIndex) {
                try {
                    String idStr = filename.substring(idStartIndex, idEndIndex);
                    return Integer.parseInt(idStr);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

        return null;
    }
}
