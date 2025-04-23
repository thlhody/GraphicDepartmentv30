package com.ctgraphdep.session.commands;

import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import org.springframework.ui.Model;

import java.time.LocalDateTime;

/**
 * Command to prepare view model for session page
 */
public class PrepareSessionViewModelCommand extends BaseSessionCommand<Void> {
    private final Model model;
    private final WorkUsersSessionsStates session;
    private final User user;

    /**
     * Creates a command to prepare the session view model
     *
     * @param model The Spring UI model
     * @param session The user's session
     * @param user The user
     */
    public PrepareSessionViewModelCommand(Model model, WorkUsersSessionsStates session, User user) {
        validateCondition(model != null, "Model cannot be null");
        validateCondition(user != null, "User cannot be null");

        this.model = model;
        this.session = session;
        this.user = user;
    }

    @Override
    public Void execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            debug("Preparing session view model for user: " + user.getUsername());

            // Get standardized time values
            GetStandardTimeValuesCommand timeCommand = ctx.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = ctx.getValidationService().execute(timeCommand);

            // Add session information to model
            prepareSessionModel(model, session, ctx, timeValues.getCurrentTime());

            // Add user information
            model.addAttribute("username", user.getUsername());
            model.addAttribute("userFullName", user.getName());

            // Add current date/time (using the standardized time)
            model.addAttribute("currentDate", timeValues.getCurrentTime().toLocalDate());

            info("Session view model prepared successfully for user: " + user.getUsername());
            return null;
        });
    }

    /**
     * Prepares session model with appropriate status
     */
    private void prepareSessionModel(Model model, WorkUsersSessionsStates session, SessionContext context, LocalDateTime currentTime) {
        if (session == null) {
            model.addAttribute("sessionStatus", "Offline");
            debug("No active session found, setting status to Offline");
            return;
        }

        String formattedStatus = getFormattedStatus(session.getSessionStatus());
        debug(String.format("Session status: %s, Formatted status: %s", session.getSessionStatus(), formattedStatus));

        model.addAttribute("sessionStatus", formattedStatus);
        populateSessionModel(model, session, context, currentTime);
    }

    /**
     * Populates all session attributes in the model
     */
    private void populateSessionModel(Model model, WorkUsersSessionsStates session, SessionContext context, LocalDateTime currentTime) {
        // Initial values
        model.addAttribute("sessionStatus", getFormattedStatus(session.getSessionStatus()));
        model.addAttribute("currentDateTime", CalculateWorkHoursUtil.formatDateTime(currentTime));
        model.addAttribute("dayStartTime", CalculateWorkHoursUtil.formatDateTime(session.getDayStartTime()));

        // Get user schedule from user object
        int userSchedule = context.getUserService()
                .getUserById(session.getUserId())
                .map(User::getSchedule)
                .orElse(8); // Default to 8 hours if not found

        // Raw work time (unchanged)
        model.addAttribute("totalWorkRaw", formatWorkTime(session.getTotalWorkedMinutes()));

        // For actual work time, recalculate based on proper logic
        if (session.getTotalWorkedMinutes() != null) {
            // Use CalculateWorkHoursUtil to properly calculate work time according to business rules
            WorkTimeCalculationResultDTO result = CalculateWorkHoursUtil.calculateWorkTime(
                    session.getTotalWorkedMinutes(), userSchedule);

            // Use the processed minutes from the calculation
            model.addAttribute("actualWorkTime", formatWorkTime(result.getProcessedMinutes()));

            // Ensure overtime is shown correctly (using the calculated value)
            model.addAttribute("overtime", formatWorkTime(result.getOvertimeMinutes()));
            
            // Add discarded minutes calculation (calculate it correctly)
            int discardedMinutes = 0;
            if (session.getTotalWorkedMinutes() != null) {
                discardedMinutes = session.getTotalWorkedMinutes() - result.getFinalTotalMinutes();
            }
            model.addAttribute("discardedMinutes", discardedMinutes);
            model.addAttribute("discardedMinutes", discardedMinutes);
        } else {
            model.addAttribute("actualWorkTime", "--:--");
            model.addAttribute("overtime", "--:--");
            model.addAttribute("discardedMinutes", 0);
        }

        // Lunch break status (unchanged)
        model.addAttribute("lunchBreakStatus", session.getLunchBreakDeducted());

        // Temporary stop values (unchanged)
        model.addAttribute("lastTemporaryStopTime", formatLastTempStopTime(session));
        model.addAttribute("temporaryStopCount", getTemporaryStopCount(session));

        // Calculate total temporary stop time using the calculation context (unchanged)
        int totalBreakMinutes = calculateTotalBreakMinutes(session, context, currentTime);
        model.addAttribute("totalTemporaryStopTime", formatWorkTime(totalBreakMinutes));

        debug("Model attributes populated for session with recalculated work times");
    }

    /**
     * Formats work time minutes as HH:mm
     */
    private String formatWorkTime(Integer minutes) {
        return minutes != null ? CalculateWorkHoursUtil.minutesToHHmm(minutes) : "--:--";
    }

    /**
     * Gets temporary stop count with null safety
     */
    private int getTemporaryStopCount(WorkUsersSessionsStates session) {
        return session != null && session.getTemporaryStopCount() != null ?
                session.getTemporaryStopCount() : 0;
    }

    /**
     * Formats last temporary stop time with null safety
     */
    private String formatLastTempStopTime(WorkUsersSessionsStates session) {
        return session != null && session.getLastTemporaryStopTime() != null ?
                CalculateWorkHoursUtil.formatDateTime(session.getLastTemporaryStopTime()) : "--:--";
    }

    /**
     * Calculates total break minutes with temporary stop handling
     */
    private int calculateTotalBreakMinutes(WorkUsersSessionsStates session, SessionContext context, LocalDateTime currentTime) {
        // If not in temp stop status, just return the stored value
        if (!WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()) || session.getLastTemporaryStopTime() == null) {
            return session.getTotalTemporaryStopMinutes() != null ? session.getTotalTemporaryStopMinutes() : 0;
        }

        // Use the dedicated method in SessionContext to calculate total temporary stop minutes
        return context.calculateTotalTempStopMinutes(session, currentTime);
    }

    /**
     * Gets a user-friendly formatted status string
     */
    private String getFormattedStatus(String status) {
        if (status == null) return "Offline";

        return switch (status) {
            case WorkCode.WORK_ONLINE -> "Online";
            case WorkCode.WORK_TEMPORARY_STOP -> "Temporary Stop";
            default -> "Offline";
        };
    }
}