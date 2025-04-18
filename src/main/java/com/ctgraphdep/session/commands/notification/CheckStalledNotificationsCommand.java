package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

public class CheckStalledNotificationsCommand implements SessionCommand<Void> {

    // Constants for stalled notification thresholds (in minutes)
    private static final int NOTIFICATION_THRESHOLD = 15;

    @Override
    public Void execute(SessionContext context) {
        // Get standardized time values using the validation system
        GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);
        LocalDateTime currentTime = timeValues.getCurrentTime();

        // Check for stalled notifications using tracking files
        checkStalledTrackingFiles(context, currentTime);

        return null;
    }

    /**
     * Checks for stalled notifications by examining tracking files
     *
     * @param context The session context
     * @param currentTime The current time
     */
    private void checkStalledTrackingFiles(SessionContext context, LocalDateTime currentTime) {
        try {
            // Get the notification tracking directory
            Path trackingDir = context.getPathConfig().getLocalPath().resolve("notification");

            if (!Files.exists(trackingDir)) {
                return;
            }

            // List all tracking files with proper resource management
            try (Stream<Path> paths = Files.list(trackingDir)) {
                paths.filter(path -> path.getFileName().toString().endsWith(".lock"))
                        .forEach(trackingFile -> {
                            try {
                                // Extract username and notification type from filename
                                String filename = trackingFile.getFileName().toString();
                                String[] parts = filename.replace(".lock", "").split("_");

                                if (parts.length >= 2) {
                                    String username = parts[0];
                                    String notificationType = parts[1];

                                    // Read the timestamp from the file
                                    String content = new String(Files.readAllBytes(trackingFile));
                                    LocalDateTime notificationTime = LocalDateTime.parse(content);

                                    // Check if notification is stalled
                                    long minutesSince = ChronoUnit.MINUTES.between(notificationTime, currentTime);

                                    if (minutesSince >= NOTIFICATION_THRESHOLD) {
                                        // Get user
                                        User user = context.getUserService().getUserByUsername(username).orElse(null);
                                        if (user != null) {
                                            // Get session
                                            WorkUsersSessionsStates session = context.getCurrentSession(username, user.getUserId());

                                            // Check if session is in appropriate state
                                            if (isSessionActive(session, notificationType)) {
                                                LoggerUtil.warn(this.getClass(),
                                                        String.format("Detected stalled %s notification for user %s",
                                                                notificationType, username));

                                                // Clean up stalled tracking file
                                                Files.deleteIfExists(trackingFile);

                                                LoggerUtil.info(this.getClass(),
                                                        String.format("Cleaned up stalled notification tracking for %s (%s)",
                                                                username, notificationType));
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                LoggerUtil.error(this.getClass(),
                                        String.format("Error processing tracking file %s: %s",
                                                trackingFile.getFileName(), e.getMessage()));
                            }
                        });
            } // Stream is automatically closed here
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking stalled notifications: " + e.getMessage());
        }
    }

    /**
     * Checks if a session is in active status appropriate for the notification type
     *
     * @param session The session to check
     * @param notificationType The type of notification
     * @return true if the session is in the appropriate active state, false otherwise
     */
    private boolean isSessionActive(WorkUsersSessionsStates session, String notificationType) {
        if (session == null) {
            return false;
        }

        return switch (notificationType) {
            case "SCHEDULE_END", "HOURLY_WARNING" -> WorkCode.WORK_ONLINE.equals(session.getSessionStatus());
            case "TEMP_STOP" -> WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus());
            default -> false;
        };
    }
}