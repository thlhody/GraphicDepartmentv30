package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.dto.session.EndTimeCalculationDTO;
import com.ctgraphdep.model.dto.session.ResolutionCalculationDTO;
import com.ctgraphdep.model.dto.session.WorkSessionDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.service.cache.MainDefaultUserContextService;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.service.cache.SessionCacheService;
import com.ctgraphdep.session.commands.UpdateSessionCalculationsCommand;
import com.ctgraphdep.session.query.UnresolvedWorkTimeQuery;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.validation.commands.IsActiveSessionCommand;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Centralized service for work session management and calculations.
 * This service coordinates commands/queries and provides consistent DTOs
 * with calculated values for the frontend.
 */
@Service
public class SessionService {

    @Autowired
    private SessionCacheService sessionCacheService;
    @Autowired
    private CalculationService calculationService;
    private final SessionCommandService sessionCommandService;
    private final SessionCommandFactory sessionCommandFactory;
    private final TimeValidationService timeValidationService;
    private final SessionMonitorService sessionMonitorService;
    private final WorktimeOperationContext worktimeOperationContext;
    private final MainDefaultUserContextService mainDefaultUserContextService;
    // Date formatters
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy :: HH:mm");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy");

    @Autowired
    public SessionService(SessionCommandService sessionCommandService, SessionCommandFactory sessionCommandFactory, TimeValidationService timeValidationService, SessionMonitorService sessionMonitorService,
                          WorktimeOperationContext worktimeOperationContext, MainDefaultUserContextService mainDefaultUserContextService) {
        this.sessionCommandService = sessionCommandService;
        this.sessionCommandFactory = sessionCommandFactory;
        this.timeValidationService = timeValidationService;
        this.sessionMonitorService = sessionMonitorService;
        this.worktimeOperationContext = worktimeOperationContext;
        this.mainDefaultUserContextService = mainDefaultUserContextService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // Gets the current work session with fully calculated values
    public WorkSessionDTO getCurrentSession(String username, Integer userId) {
        try {
            LoggerUtil.info(this.getClass(), "Getting current session for user: " + username);

            // Get standardized time values
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = getStandardTimeValues();
            LocalDateTime currentTime = timeValues.getCurrentTime();

            // Let SessionCacheService handle all fallback logic (including network)
            WorkUsersSessionsStates session = sessionCacheService.readSessionWithFallback(username, userId);

            // Continue with normal flow - no need for manual network checks
            if (session == null) {
                LoggerUtil.info(this.getClass(), "No session found for user: " + username);
                return createOfflineSessionDTO(username, currentTime);
            }

            // Get user schedule
            int userSchedule = getUserSchedule();

            // Update calculations using cache service if session is active
            if (isActiveSession(session)) {

                UpdateSessionCalculationsCommand updateCommand = sessionCommandFactory.createUpdateSessionCalculationsCacheOnlyCommand(session, currentTime);
                session = sessionCommandService.executeCommand(updateCommand);

                LoggerUtil.debug(this.getClass(), "Updating calculations for active session (cache only)");
            }

            // Create and return the DTO
            return createSessionDTO(session, userSchedule, currentTime);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting current session: " + e.getMessage(), e);
            // Return basic offline session in case of error
            return createOfflineSessionDTO(username, LocalDateTime.now());
        }
    }

    // Gets unresolved work time entries with recommended end times
    public List<ResolutionCalculationDTO> getUnresolvedWorkTimeEntries(String username) {
        try {
            LoggerUtil.info(this.getClass(), "Getting unresolved work time entries for user: " + username);

            // Get user schedule
            int userSchedule = getUserSchedule();

            // Get unresolved entries
            List<WorkTimeTable> unresolvedEntries = getUnresolvedEntries(username);

            // Map to resolution DTOs with recommended end times
            return unresolvedEntries.stream().map(entry -> createResolutionDTO(entry, userSchedule)).collect(Collectors.toList());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting unresolved entries: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    // Calculates work time for given end time (used for resolution and end time scheduler)
    public EndTimeCalculationDTO calculateEndTimeWork(String username, Integer userId, int endHour, int endMinute) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Calculating end time work for user %s at %02d:%02d", username, endHour, endMinute));

            // Validate inputs
            if (endHour < 0 || endHour > 23 || endMinute < 0 || endMinute > 59) {
                return createErrorEndTimeDTO("Invalid time values");
            }

            // Get current time and user schedule
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = getStandardTimeValues();
            LocalDate currentDate = timeValues.getCurrentDate();
            int userSchedule = getUserSchedule();

            // Get current session FROM CACHE
            WorkUsersSessionsStates session = sessionCacheService.readSessionWithFallback(username, userId);
            if (session == null || session.getDayStartTime() == null) {
                return createErrorEndTimeDTO("No active session");
            }

            // Create the end time
            LocalDateTime proposedEndTime = LocalDateTime.of(currentDate, LocalTime.of(endHour, endMinute));

            // Validate end time is after start time
            if (proposedEndTime.isBefore(session.getDayStartTime())) {
                return createErrorEndTimeDTO("End time must be after start time");
            }

            // Clone session to avoid modifying actual session
            WorkUsersSessionsStates tempSession = cloneSession(session);

            // Calculate raw minutes using calculation service (DIRECT CALL)
            int rawMinutes = calculationService.calculateRawWorkMinutes(tempSession, proposedEndTime);

            // Calculate processed work time (DIRECT CALL)
            WorkTimeCalculationResultDTO result = calculationService.calculateWorkTime(rawMinutes, userSchedule);

            // Calculate total elapsed minutes (DIRECT CALL - using utility method)
            int totalElapsedMinutes = calculationService.calculateMinutesBetween(session.getDayStartTime(), proposedEndTime);

            // Get break minutes from session
            int breakMinutes = session.getTotalTemporaryStopMinutes() != null ? session.getTotalTemporaryStopMinutes() : 0;

            // Create and return the result DTO
            return createEndTimeCalculationDTO(
                    totalElapsedMinutes,
                    breakMinutes,
                    result.isLunchDeducted(),
                    result.isLunchDeducted() ? 30 : 0,
                    result.getProcessedMinutes(),
                    result.getOvertimeMinutes(),
                    result.getRawMinutes(),
                    result.getFinalTotalMinutes()
            );
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating end time work: " + e.getMessage(), e);
            return createErrorEndTimeDTO("Error calculating work time: " + e.getMessage());
        }
    }

    // Calculates resolution values for a specific work date and end time
    public ResolutionCalculationDTO calculateResolutionValues(String username, LocalDate workDate, int endHour, int endMinute) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Calculating resolution values for user %s, date %s at %02d:%02d", username, workDate, endHour, endMinute));

            // Validate inputs
            if (endHour < 0 || endHour > 23 || endMinute < 0 || endMinute > 59) {
                return createErrorResolutionDTO("Invalid time values");
            }

            // Get user schedule
            int userSchedule = getUserSchedule();

            // Find the work time entry for this date
            WorkTimeTable entry = findEntryForDate(username, workDate);
            if (entry == null) {
                return createErrorResolutionDTO("No work time entry found for date: " + formatDate(workDate));
            }

            // Create end time
            LocalDateTime endTime = LocalDateTime.of(workDate, LocalTime.of(endHour, endMinute));

            // Validate end time is after start time
            if (entry.getDayStartTime() != null && endTime.isBefore(entry.getDayStartTime())) {
                return createErrorResolutionDTO("End time must be after start time");
            }

            // Calculate raw work minutes (DIRECT CALL)
            int rawMinutes = calculationService.calculateRawWorkMinutesForEntry(entry, endTime);

            // Calculate processed work time (DIRECT CALL)
            WorkTimeCalculationResultDTO result = calculationService.calculateWorkTime(rawMinutes, userSchedule);

            // Calculate total elapsed minutes (DIRECT CALL)
            int totalElapsedMinutes = calculationService.calculateMinutesBetween(entry.getDayStartTime(), endTime);

            // Get break minutes from entry
            int breakMinutes = entry.getTotalTemporaryStopMinutes() != null ? entry.getTotalTemporaryStopMinutes() : 0;

            // Calculate recommended end time (DIRECT CALL)
            LocalDateTime recommendedEndTime = calculationService.calculateRecommendedEndTime(entry, userSchedule);

            // Create and return the DTO
            return createDetailedResolutionDTO(
                    entry,
                    endTime,
                    totalElapsedMinutes,
                    breakMinutes,
                    result,
                    rawMinutes,
                    recommendedEndTime
            );
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating resolution values: " + e.getMessage(), e);
            return createErrorResolutionDTO("Error calculating resolution: " + e.getMessage());
        }
    }

    // Helper methods for retrieving data

    // Gets unresolved worktime entries using the query
    private List<WorkTimeTable> getUnresolvedEntries(String username) {
        UnresolvedWorkTimeQuery unresolvedQuery = new UnresolvedWorkTimeQuery(username);
        return sessionCommandService.executeQuery(unresolvedQuery);
    }

    // Finds a specific worktime entry for a date
    private WorkTimeTable findEntryForDate(String username, LocalDate date) {
        try {
            List<WorkTimeTable> entries = worktimeOperationContext.getDataAccessor(username).readWorktime(username,date.getYear(), date.getMonthValue());
            return entries.stream()
                    .filter(e -> e.getWorkDate() != null && e.getWorkDate().equals(date))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error finding entry for date: " + e.getMessage(), e);
            return null;
        }
    }

    // Gets the user's schedule (hours)
    private int getUserSchedule() {
        User currentUser = mainDefaultUserContextService.getOriginalUser();
        if (currentUser != null && currentUser.getSchedule() != null) {
            return currentUser.getSchedule();
        }
        LoggerUtil.warn(this.getClass(), "No current user or schedule found, defaulting to 8 hours");
        return 8; // Default fallback
    }

    // Checks if session is active (online or temporary stop)
    private boolean isActiveSession(WorkUsersSessionsStates session) {
        IsActiveSessionCommand command = timeValidationService.getValidationFactory().createIsActiveSessionCommand(session);
        return timeValidationService.execute(command);
    }

    // Gets standardized time values
    private GetStandardTimeValuesCommand.StandardTimeValues getStandardTimeValues() {
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
        return timeValidationService.execute(timeCommand);
    }

    // DTO creation methods

    // Creates a WorkSessionDTO for an offline session
    private WorkSessionDTO createOfflineSessionDTO(String username, LocalDateTime currentTime) {
        WorkSessionDTO dto = new WorkSessionDTO();

        // Basic information
        dto.setUsername(username);
        dto.setSessionStatus(WorkCode.WORK_OFFLINE);
        dto.setFormattedStatus("Offline");

        // Time information
        dto.setCurrentTime(currentTime);
        dto.setFormattedCurrentTime(formatDateTime(currentTime));

        // Default values
        dto.setRawWorkMinutes(0);
        dto.setActualWorkMinutes(0);
        dto.setFormattedRawWorkTime("00:00");
        dto.setFormattedActualWorkTime("00:00");
        dto.setTemporaryStopCount(0);
        dto.setTotalTemporaryStopMinutes(0);
        dto.setFormattedTotalTemporaryStopTime("00:00");
        dto.setOvertimeMinutes(0);
        dto.setFormattedOvertimeMinutes("00:00");
        dto.setLunchBreakDeducted(false);
        dto.setLunchBreakMinutes(0);
        dto.setDiscardedMinutes(0);
        dto.setWorkdayCompleted(false);

        // Add scheduled end time if any
        LocalDateTime scheduledEndTime = sessionMonitorService.getScheduledEndTime(username);
        if (scheduledEndTime != null) {
            dto.setScheduledEndTime(scheduledEndTime);
            dto.setFormattedScheduledEndTime(formatDateTime(scheduledEndTime));
        }

        return dto;
    }

    // Creates a complete WorkSessionDTO from a session
    private WorkSessionDTO createSessionDTO(WorkUsersSessionsStates session, int userSchedule, LocalDateTime currentTime) {
        WorkSessionDTO dto = new WorkSessionDTO();

        // Basic information
        dto.setUsername(session.getUsername());
        dto.setSessionStatus(session.getSessionStatus());
        dto.setFormattedStatus(getFormattedStatus(session.getSessionStatus()));

        // Time information
        dto.setDayStartTime(session.getDayStartTime());
        dto.setDayEndTime(session.getDayEndTime());
        dto.setCurrentTime(currentTime);
        dto.setFormattedDayStartTime(formatDateTime(session.getDayStartTime()));
        dto.setFormattedCurrentTime(formatDateTime(currentTime));

        // Calculate estimated end time if session is active
        if (isActiveSession(session) && session.getDayStartTime() != null) {
            WorkTimeTable tempEntry = new WorkTimeTable();
            tempEntry.setWorkDate(session.getDayStartTime().toLocalDate());
            tempEntry.setDayStartTime(session.getDayStartTime());
            tempEntry.setTotalWorkedMinutes(session.getTotalWorkedMinutes());
            tempEntry.setTemporaryStopCount(session.getTemporaryStopCount());
            tempEntry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());

            LocalDateTime estimatedEndTime = calculationService.calculateRecommendedEndTime(tempEntry, userSchedule);

            dto.setEstimatedEndTime(estimatedEndTime);
            dto.setFormattedEstimatedEndTime(formatDateTime(estimatedEndTime));
        }

        // Work time calculations
        dto.setRawWorkMinutes(session.getTotalWorkedMinutes());
        dto.setFormattedRawWorkTime(formatMinutes(session.getTotalWorkedMinutes()));

        // For actual work time, use calculation result
        if (session.getTotalWorkedMinutes() != null && session.getTotalWorkedMinutes() > 0) {
            WorkTimeCalculationResultDTO result = CalculateWorkHoursUtil.calculateWorkTime(session.getTotalWorkedMinutes(), userSchedule);

            dto.setActualWorkMinutes(result.getProcessedMinutes());
            dto.setFormattedActualWorkTime(formatMinutes(result.getProcessedMinutes()));
            dto.setOvertimeMinutes(result.getOvertimeMinutes());
            dto.setFormattedOvertimeMinutes(formatMinutes(result.getOvertimeMinutes()));
            dto.setLunchBreakDeducted(result.isLunchDeducted());
            dto.setLunchBreakMinutes(result.isLunchDeducted() ? 30 : 0);

            // Calculate discarded minutes
            int discardedMinutes = session.getTotalWorkedMinutes() - result.getFinalTotalMinutes();
            dto.setDiscardedMinutes(discardedMinutes);
        } else {
            dto.setActualWorkMinutes(0);
            dto.setFormattedActualWorkTime("00:00");
            dto.setOvertimeMinutes(0);
            dto.setFormattedOvertimeMinutes("00:00");
            dto.setLunchBreakDeducted(false);
            dto.setLunchBreakMinutes(0);
            dto.setDiscardedMinutes(0);
        }

        // Temporary stop information
        dto.setTemporaryStopCount(session.getTemporaryStopCount() != null ? session.getTemporaryStopCount() : 0);
        
        // Use live calculation for temporary stop time to prevent doubling on refresh
        int liveTemporaryStopMinutes = calculationService.calculateTotalTempStopMinutes(session, currentTime);
        dto.setTotalTemporaryStopMinutes(liveTemporaryStopMinutes);
        dto.setFormattedTotalTemporaryStopTime(formatMinutes(liveTemporaryStopMinutes));
        dto.setLastTemporaryStopTime(session.getLastTemporaryStopTime());
        dto.setFormattedLastTemporaryStopTime(formatDateTime(session.getLastTemporaryStopTime()));

        // Schedule information
        dto.setUserSchedule(userSchedule);
        dto.setWorkdayCompleted(session.getWorkdayCompleted() != null ? session.getWorkdayCompleted() : false);

        // End time scheduling
        LocalDateTime scheduledEndTime = sessionMonitorService.getScheduledEndTime(session.getUsername());
        if (scheduledEndTime != null) {
            dto.setScheduledEndTime(scheduledEndTime);
            dto.setFormattedScheduledEndTime(formatDateTime(scheduledEndTime));
        }

        return dto;
    }

    // Creates a basic ResolutionDTO with calculated values
    private ResolutionCalculationDTO createResolutionDTO(WorkTimeTable entry, int userSchedule) {
        ResolutionCalculationDTO dto = new ResolutionCalculationDTO();

        // Basic information
        dto.setWorkDate(entry.getWorkDate());
        dto.setFormattedWorkDate(formatDate(entry.getWorkDate()));
        dto.setStartTime(entry.getDayStartTime());
        dto.setFormattedStartTime(formatTime(entry.getDayStartTime()));

        // Break information
        int breakMinutes = entry.getTotalTemporaryStopMinutes() != null ? entry.getTotalTemporaryStopMinutes() : 0;
        dto.setBreakMinutes(breakMinutes);
        dto.setFormattedBreakTime(formatMinutes(breakMinutes));

        // Calculate recommended end time
        LocalDateTime recommendedEndTime = calculationService.calculateRecommendedEndTime(entry, userSchedule);

        dto.setRecommendedEndTime(recommendedEndTime);
        dto.setFormattedRecommendedEndTime(formatTime(recommendedEndTime));

        return dto;
    }

    // Creates a detailed ResolutionDTO with all calculation results
    private ResolutionCalculationDTO createDetailedResolutionDTO(WorkTimeTable entry, LocalDateTime endTime, int totalElapsedMinutes,
            int breakMinutes, WorkTimeCalculationResultDTO result, int rawMinutes, LocalDateTime recommendedEndTime) {

        ResolutionCalculationDTO dto = new ResolutionCalculationDTO();

        // Basic information
        dto.setWorkDate(entry.getWorkDate());
        dto.setFormattedWorkDate(formatDate(entry.getWorkDate()));
        dto.setStartTime(entry.getDayStartTime());
        dto.setEndTime(endTime);
        dto.setFormattedStartTime(formatTime(entry.getDayStartTime()));
        dto.setFormattedEndTime(formatTime(endTime));

        // Calculation results
        dto.setTotalElapsedMinutes(totalElapsedMinutes);
        dto.setFormattedTotalElapsed(formatMinutes(totalElapsedMinutes));
        dto.setBreakMinutes(breakMinutes);
        dto.setFormattedBreakTime(formatMinutes(breakMinutes));
        dto.setLunchDeducted(result.isLunchDeducted());
        dto.setLunchBreakMinutes(result.isLunchDeducted() ? 30 : 0);
        dto.setNetWorkMinutes(result.getProcessedMinutes());
        dto.setFormattedNetWorkTime(formatMinutes(result.getProcessedMinutes()));
        dto.setOvertimeMinutes(result.getOvertimeMinutes());
        dto.setFormattedOvertimeMinutes(formatMinutes(result.getOvertimeMinutes()));
        dto.setRawMinutes(rawMinutes);

        // Calculate discarded minutes
        int discardedMinutes = rawMinutes - result.getFinalTotalMinutes();
        dto.setDiscardedMinutes(discardedMinutes);

        // Recommended end time
        dto.setRecommendedEndTime(recommendedEndTime);
        dto.setFormattedRecommendedEndTime(formatTime(recommendedEndTime));

        // Status
        dto.setSuccess(true);

        return dto;
    }

    // Creates a EndTimeCalculationDTO for the end time scheduler
    private EndTimeCalculationDTO createEndTimeCalculationDTO(int totalElapsedMinutes, int breakMinutes, boolean lunchDeducted, int lunchBreakMinutes,
            int netWorkMinutes, int overtimeMinutes, int rawMinutes, int finalMinutes) {

        EndTimeCalculationDTO dto = new EndTimeCalculationDTO();

        dto.setSuccess(true);
        dto.setTotalElapsedMinutes(totalElapsedMinutes);
        dto.setBreakMinutes(breakMinutes);
        dto.setLunchDeducted(lunchDeducted);
        dto.setLunchBreakMinutes(lunchBreakMinutes);
        dto.setNetWorkMinutes(netWorkMinutes);
        dto.setOvertimeMinutes(overtimeMinutes);
        dto.setRawMinutes(rawMinutes);
        dto.setFinalMinutes(finalMinutes);

        // Formatted values for display
        dto.setFormattedTotalElapsed(formatMinutes(totalElapsedMinutes));
        dto.setFormattedBreakTime(formatMinutes(breakMinutes));
        dto.setFormattedNetWorkTime(formatMinutes(netWorkMinutes));
        dto.setFormattedOvertimeMinutes(formatMinutes(overtimeMinutes));

        return dto;
    }

    // Creates an error EndTimeCalculationDTO
    private EndTimeCalculationDTO createErrorEndTimeDTO(String errorMessage) {
        EndTimeCalculationDTO dto = new EndTimeCalculationDTO();
        dto.setSuccess(false);
        dto.setMessage(errorMessage);
        return dto;
    }

    // Creates an error ResolutionCalculationDTO
    private ResolutionCalculationDTO createErrorResolutionDTO(String errorMessage) {
        ResolutionCalculationDTO dto = new ResolutionCalculationDTO();
        dto.setSuccess(false);
        dto.setErrorMessage(errorMessage);
        return dto;
    }

    // Utility methods

    // Creates a deep copy of a session
    private WorkUsersSessionsStates cloneSession(WorkUsersSessionsStates original) {
        WorkUsersSessionsStates clone = new WorkUsersSessionsStates();

        // Copy all relevant fields
        clone.setUserId(original.getUserId());
        clone.setUsername(original.getUsername());
        clone.setSessionStatus(original.getSessionStatus());
        clone.setDayStartTime(original.getDayStartTime());
        clone.setDayEndTime(original.getDayEndTime());
        clone.setCurrentStartTime(original.getCurrentStartTime());
        clone.setTotalWorkedMinutes(original.getTotalWorkedMinutes());
        clone.setFinalWorkedMinutes(original.getFinalWorkedMinutes());
        clone.setTotalOvertimeMinutes(original.getTotalOvertimeMinutes());
        clone.setLunchBreakDeducted(original.getLunchBreakDeducted());
        clone.setWorkdayCompleted(original.getWorkdayCompleted());
        clone.setTemporaryStopCount(original.getTemporaryStopCount());
        clone.setTotalTemporaryStopMinutes(original.getTotalTemporaryStopMinutes());

        // Deep copy temporary stops if they exist
        if (original.getTemporaryStops() != null) {
            List<TemporaryStop> clonedStops = new ArrayList<>();
            for (TemporaryStop stop : original.getTemporaryStops()) {
                TemporaryStop clonedStop = new TemporaryStop();
                clonedStop.setStartTime(stop.getStartTime());
                clonedStop.setEndTime(stop.getEndTime());
                clonedStop.setDuration(stop.getDuration());
                clonedStops.add(clonedStop);
            }
            clone.setTemporaryStops(clonedStops);
        }

        clone.setLastTemporaryStopTime(original.getLastTemporaryStopTime());
        clone.setLastActivity(original.getLastActivity());

        return clone;
    }

    // Formats minutes as HH:MM
    private String formatMinutes(Integer minutes) {
        return minutes != null ? CalculateWorkHoursUtil.minutesToHHmm(minutes) : "00:00";
    }

    // Formats datetime as dd/MM/yyyy :: HH:mm
    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FORMATTER) : "--/--/---- :: --:--";
    }

    // Formats time as HH:mm
    private String formatTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(TIME_FORMATTER) : "--:--";
    }

    // Formats date as EEEE, dd MMM yyyy
    private String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : "------";
    }

    // Gets a user-friendly formatted status string
    private String getFormattedStatus(String status) {
        if (status == null) return "Offline";

        return switch (status) {
            case WorkCode.WORK_ONLINE -> "Online";
            case WorkCode.WORK_TEMPORARY_STOP -> "Temporary Stop";
            default -> "Offline";
        };
    }
}