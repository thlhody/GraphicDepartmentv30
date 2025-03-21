package com.ctgraphdep.session.commands;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.ui.Model;

import java.time.LocalDateTime;

/**
 * Command to prepare view model for session page
 */
public class PrepareSessionViewModelCommand implements SessionCommand<Void> {
    private final Model model;
    private final WorkUsersSessionsStates session;
    private final User user;

    public PrepareSessionViewModelCommand(Model model, WorkUsersSessionsStates session, User user) {
        this.model = model;
        this.session = session;
        this.user = user;
    }

    @Override
    public Void execute(SessionContext context) {
        // Get standardized time values using the new validation system
        GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);

        // Add session information to model
        prepareSessionModel(model, session, context, timeValues.getCurrentTime());

        // Add user information
        model.addAttribute("username", user.getUsername());
        model.addAttribute("userFullName", user.getName());

        // Add current date/time (using the standardized time)
        model.addAttribute("currentDate", timeValues.getCurrentTime().toLocalDate());

        return null;
    }

    private void prepareSessionModel(Model model, WorkUsersSessionsStates session, SessionContext context, LocalDateTime currentTime) {
        if (session == null) {
            model.addAttribute("sessionStatus", "Offline");
            return;
        }

        String formattedStatus = getFormattedStatus(session.getSessionStatus());

        LoggerUtil.debug(this.getClass(), String.format("Session status: %s, Formatted status: %s", session.getSessionStatus(), formattedStatus));

        model.addAttribute("sessionStatus", formattedStatus);
        populateSessionModel(model, session, context, currentTime);
    }

    private void populateSessionModel(Model model, WorkUsersSessionsStates session, SessionContext context, LocalDateTime currentTime) {
        // Initial values
        model.addAttribute("sessionStatus", getFormattedStatus(session.getSessionStatus()));
        model.addAttribute("currentDateTime", CalculateWorkHoursUtil.formatDateTime(currentTime));
        model.addAttribute("dayStartTime", CalculateWorkHoursUtil.formatDateTime(session.getDayStartTime()));

        // Work values
        model.addAttribute("totalWorkRaw", formatWorkTime(session.getTotalWorkedMinutes()));
        model.addAttribute("actualWorkTime", formatWorkTime(session.getFinalWorkedMinutes()));
        model.addAttribute("lunchBreakStatus", session.getLunchBreakDeducted());
        model.addAttribute("overtime", formatWorkTime(session.getTotalOvertimeMinutes()));

        // Temporary stop values
        model.addAttribute("lastTemporaryStopTime", formatLastTempStopTime(session));
        model.addAttribute("temporaryStopCount", getTemporaryStopCount(session));

        // Calculate total temporary stop time using the calculation context
        int totalBreakMinutes = calculateTotalBreakMinutes(session, context, currentTime);
        model.addAttribute("totalTemporaryStopTime", formatWorkTime(totalBreakMinutes));

        LoggerUtil.debug(this.getClass(), "Model attributes populated");
    }

    // Helper methods
    private String formatWorkTime(Integer minutes) {
        return minutes != null ? CalculateWorkHoursUtil.minutesToHHmm(minutes) : "--:--";
    }

    private int getTemporaryStopCount(WorkUsersSessionsStates session) {
        return session != null && session.getTemporaryStopCount() != null ?
                session.getTemporaryStopCount() : 0;
    }

    private String formatLastTempStopTime(WorkUsersSessionsStates session) {
        return session != null && session.getLastTemporaryStopTime() != null ?
                CalculateWorkHoursUtil.formatDateTime(session.getLastTemporaryStopTime()) : "--:--";
    }

    private int calculateTotalBreakMinutes(WorkUsersSessionsStates session, SessionContext context, LocalDateTime currentTime) {
        // If not in temp stop status, just return the stored value
        if (!WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()) || session.getLastTemporaryStopTime() == null) {
            return session.getTotalTemporaryStopMinutes() != null ? session.getTotalTemporaryStopMinutes() : 0;
        }

        // Use the dedicated method in SessionContext to calculate total temporary stop minutes
        return context.calculateTotalTempStopMinutes(session, currentTime);
    }

    private String getFormattedStatus(String status) {
        if (status == null) return "Offline";

        return switch (status) {
            case WorkCode.WORK_ONLINE -> "Online";
            case WorkCode.WORK_TEMPORARY_STOP -> "Temporary Stop";
            default -> WorkCode.WORK_OFFLINE;
        };
    }
}