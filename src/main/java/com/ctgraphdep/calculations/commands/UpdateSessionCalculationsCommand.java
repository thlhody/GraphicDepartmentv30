package com.ctgraphdep.calculations.commands;

import com.ctgraphdep.calculations.CalculationCommand;
import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeCalculationResult;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.util.SessionEntityBuilder;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;

/**
 * Command to update session calculations
 */
public class UpdateSessionCalculationsCommand implements CalculationCommand<WorkUsersSessionsStates> {
    private final WorkUsersSessionsStates session;
    private final LocalDateTime currentTime;
    private final int userSchedule;

    public UpdateSessionCalculationsCommand(
            WorkUsersSessionsStates session,
            LocalDateTime currentTime,
            int userSchedule) {
        this.session = session;
        this.currentTime = currentTime;
        this.userSchedule = userSchedule;
    }

    @Override
    public WorkUsersSessionsStates execute(CalculationContext context) {
        if (session == null) {
            return null;
        }

        try {
            if (WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
                updateOnlineCalculations(session, currentTime, userSchedule, context);
            } else if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                // Use a separate command for this specific calculation
                UpdateTempStopCalculationsCommand command =
                        context.getCommandFactory().createUpdateTempStopCalculationsCommand(session, currentTime);
                context.executeCommand(command);
            }

            // Always update last activity
            session.setLastActivity(currentTime);

            return session;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating session calculations: " + e.getMessage(), e);
            return session;
        }
    }

    private void updateOnlineCalculations(WorkUsersSessionsStates session,
                                          LocalDateTime currentTime,
                                          int userSchedule,
                                          CalculationContext context) {
        // Calculate raw work minutes
        int rawWorkedMinutes = CalculateWorkHoursUtil.calculateRawWorkMinutes(session, currentTime);

        // Calculate work time using the proven utility
        WorkTimeCalculationResult result = CalculateWorkHoursUtil.calculateWorkTime(rawWorkedMinutes, userSchedule);

        // Determine if workday is completed based on schedule (simplified for example)
        boolean workdayCompleted = result.getRawMinutes() >= (userSchedule * 60);

        // Update session with calculated values
        SessionEntityBuilder.updateSession(session, builder -> {
            builder.totalWorkedMinutes(rawWorkedMinutes)
                    .finalWorkedMinutes(result.getProcessedMinutes())
                    .totalOvertimeMinutes(result.getOvertimeMinutes())
                    .lunchBreakDeducted(result.isLunchDeducted())
                    .workdayCompleted(workdayCompleted);
        });
    }
}
