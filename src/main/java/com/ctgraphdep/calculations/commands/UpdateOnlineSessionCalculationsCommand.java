package com.ctgraphdep.calculations.commands;

import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.util.SessionEntityBuilder;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;

import java.time.LocalDateTime;

/**
 * Command to update calculations for online sessions
 */
public class UpdateOnlineSessionCalculationsCommand extends BaseSessionCalculationsCommand<WorkUsersSessionsStates> {

    /**
     * Creates a command to update online session calculations
     *
     * @param session      The session to update
     * @param currentTime  The current time
     * @param userSchedule The user's scheduled working hours
     */
    public UpdateOnlineSessionCalculationsCommand(WorkUsersSessionsStates session, LocalDateTime currentTime, int userSchedule) {
        super(session, currentTime, userSchedule);
    }

    @Override
    protected WorkUsersSessionsStates executeCommand(CalculationContext context) {
        // Calculate raw work minutes
        int rawWorkedMinutes = CalculateWorkHoursUtil.calculateRawWorkMinutes(session, currentTime);

        // Calculate work time using the proven utility
        WorkTimeCalculationResultDTO result = CalculateWorkHoursUtil.calculateWorkTime(rawWorkedMinutes, userSchedule);

        // Determine if workday is completed based on schedule
        boolean workdayCompleted = result.getRawMinutes() >= (userSchedule * 60);

        // Update session with calculated values
        SessionEntityBuilder.updateSession(session, builder -> builder.totalWorkedMinutes(rawWorkedMinutes)
                .finalWorkedMinutes(result.getProcessedMinutes())
                .totalOvertimeMinutes(result.getOvertimeMinutes())
                .lunchBreakDeducted(result.isLunchDeducted())
                .workdayCompleted(workdayCompleted));

        return session;
    }

    @Override
    protected WorkUsersSessionsStates handleError(Exception e) {
        return session;
    }
}