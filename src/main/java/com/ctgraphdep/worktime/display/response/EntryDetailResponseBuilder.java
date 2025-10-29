package com.ctgraphdep.worktime.display.response;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.worktime.display.mappers.TimeOffLabelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Builder for entry detail API responses.
 *
 * This class provides THE SINGLE SOURCE OF TRUTH for building detailed worktime
 * entry information for API responses (used by admin worktime endpoint).
 *
 * Previously scattered across WorktimeDisplayService as 10+ private methods.
 *
 * Responsibilities:
 * - Build detailed entry responses with all fields
 * - Build "no entry" responses for empty dates
 * - Format time information (start/end times, elapsed time)
 * - Calculate work time totals (regular, overtime, discarded)
 * - Add break/temporary stop information
 * - Add time-off type information with labels
 * - Add status information with labels and CSS classes
 * - Add metadata flags for frontend convenience
 */
@Component
@RequiredArgsConstructor
public class EntryDetailResponseBuilder {

    private final TimeOffLabelMapper labelMapper;

    /**
     * Build comprehensive response for an existing entry.
     *
     * @param user User who owns the entry
     * @param date Date of the entry
     * @param entry Worktime entry data (can be null)
     * @return Map containing all entry details for API response
     */
    public Map<String, Object> buildDetailedResponse(User user, LocalDate date, WorkTimeTable entry) {
        Map<String, Object> response = new HashMap<>();

        if (entry == null) {
            return buildNoEntryResponse(user, date);
        }

        // Basic information
        response.put("hasEntry", true);
        response.put("date", date.toString());
        response.put("userId", user.getUserId());
        response.put("userName", user.getName());
        response.put("employeeId", user.getEmployeeId());

        // Add all entry details
        addTimeInformation(response, entry);
        addWorkCalculations(response, entry);
        addBreakInformation(response, entry);
        addTimeOffInformation(response, entry);
        addStatusInformation(response, entry);
        addMetadata(response, entry);

        return response;
    }

    /**
     * Build response for cases where no entry exists.
     *
     * @param user User for whom we're checking
     * @param date Date to check
     * @return Map containing minimal information indicating no entry
     */
    public Map<String, Object> buildNoEntryResponse(User user, LocalDate date) {
        Map<String, Object> response = new HashMap<>();
        response.put("hasEntry", false);
        response.put("date", date.toString());
        response.put("userId", user.getUserId());
        response.put("userName", user.getName());
        response.put("employeeId", user.getEmployeeId());
        response.put("displayFormat", "-");
        response.put("isTimeOff", false);
        response.put("isHolidayWithWork", false);
        return response;
    }

    /**
     * Add time-related information to response (start/end times, elapsed time).
     */
    private void addTimeInformation(Map<String, Object> response, WorkTimeTable entry) {
        // Start time
        if (entry.getDayStartTime() != null) {
            response.put("dayStartTime", entry.getDayStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            response.put("startDateTime", entry.getDayStartTime().toString());
        }

        // End time
        if (entry.getDayEndTime() != null) {
            response.put("dayEndTime", entry.getDayEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            response.put("endDateTime", entry.getDayEndTime().toString());
        }

        // Calculate total elapsed time if both start and end exist
        if (entry.getDayStartTime() != null && entry.getDayEndTime() != null) {
            long elapsedMinutes = Duration.between(entry.getDayStartTime(), entry.getDayEndTime()).toMinutes();
            response.put("totalElapsedMinutes", elapsedMinutes);
            response.put("formattedElapsedTime", CalculateWorkHoursUtil.minutesToHH((int) elapsedMinutes));
        }
    }

    /**
     * Add work time calculations and formatting to response.
     */
    private void addWorkCalculations(Map<String, Object> response, WorkTimeTable entry) {
        // Regular work minutes
        response.put("totalWorkedMinutes", entry.getTotalWorkedMinutes());
        if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
            response.put("formattedWorkTime", CalculateWorkHoursUtil.minutesToHH(entry.getTotalWorkedMinutes()));
        }

        // Overtime minutes
        response.put("totalOvertimeMinutes", entry.getTotalOvertimeMinutes());
        if (entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {
            response.put("formattedOvertimeTime", CalculateWorkHoursUtil.minutesToHH(entry.getTotalOvertimeMinutes()));
        }
    }

    /**
     * Add break and temporary stop information to response.
     */
    private void addBreakInformation(Map<String, Object> response, WorkTimeTable entry) {
        // Temporary stops
        response.put("temporaryStopCount", entry.getTemporaryStopCount());
        response.put("totalTemporaryStopMinutes", entry.getTotalTemporaryStopMinutes());

        if (entry.getTotalTemporaryStopMinutes() != null && entry.getTotalTemporaryStopMinutes() > 0) {
            response.put("formattedTempStopTime", CalculateWorkHoursUtil.minutesToHH(entry.getTotalTemporaryStopMinutes()));
        }

        // Add temporary stops list for detailed breakdown
        if (entry.getTemporaryStops() != null && !entry.getTemporaryStops().isEmpty()) {
            response.put("temporaryStops", entry.getTemporaryStops());
        }

        // Lunch break
        response.put("lunchBreakDeducted", entry.isLunchBreakDeducted());
    }

    /**
     * Add time-off type information and display formatting to response.
     */
    private void addTimeOffInformation(Map<String, Object> response, WorkTimeTable entry) {
        response.put("timeOffType", entry.getTimeOffType());

        if (entry.getTimeOffType() != null) {
            response.put("timeOffLabel", labelMapper.getTimeOffLabel(entry.getTimeOffType()));
            response.put("isTimeOff", true);

            // Special handling for special day types with work hours (SN:5, CO:5, CE:5, etc.)
            if (labelMapper.isSpecialDayType(entry.getTimeOffType()) &&
                entry.getTotalOvertimeMinutes() != null &&
                entry.getTotalOvertimeMinutes() > 0) {

                response.put("isHolidayWithWork", true);
                response.put("displayFormat", entry.getTimeOffType() + "/" +
                    CalculateWorkHoursUtil.minutesToHH(entry.getTotalOvertimeMinutes()));
            } else {
                response.put("isHolidayWithWork", false);
                response.put("displayFormat", entry.getTimeOffType());
            }
        } else {
            response.put("isTimeOff", false);
            response.put("isHolidayWithWork", false);

            // Regular work display
            if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
                response.put("displayFormat", CalculateWorkHoursUtil.minutesToHH(entry.getTotalWorkedMinutes()));
            } else {
                response.put("displayFormat", "-");
            }
        }
    }

    /**
     * Add administrative status information to response.
     */
    private void addStatusInformation(Map<String, Object> response, WorkTimeTable entry) {
        response.put("adminSync", entry.getAdminSync() != null ? entry.getAdminSync() : null);

        if (entry.getAdminSync() != null) {
            response.put("statusLabel", labelMapper.getStatusLabel(entry.getAdminSync()));
            response.put("statusClass", labelMapper.getStatusClass(entry.getAdminSync()));
        }
    }

    /**
     * Add metadata flags to response for frontend convenience.
     */
    private void addMetadata(Map<String, Object> response, WorkTimeTable entry) {
        response.put("hasWorkTime", entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0);
        response.put("hasOvertime", entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0);
        response.put("hasTempStops", entry.getTotalTemporaryStopMinutes() != null && entry.getTotalTemporaryStopMinutes() > 0);
        response.put("isComplete", entry.getDayStartTime() != null && entry.getDayEndTime() != null);
        response.put("hasLunchBreak", entry.isLunchBreakDeducted());
    }
}
