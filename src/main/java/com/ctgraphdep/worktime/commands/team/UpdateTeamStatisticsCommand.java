package com.ctgraphdep.worktime.commands.team;

import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.dto.TeamMemberDTO;
import com.ctgraphdep.model.dto.team.*;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.worktime.commands.WorktimeOperationCommand;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Command to update statistics for all team members.
 * Replaces TeamStatisticsService.updateTeamStatistics() method.
 * Updates work time stats, register stats, session details, and time off information.
 */
public class UpdateTeamStatisticsCommand extends WorktimeOperationCommand<Object> {
    private final String teamLeadUsername;
    private final int year;
    private final int month;

    public UpdateTeamStatisticsCommand(WorktimeOperationContext context,
                                       String teamLeadUsername,
                                       int year,
                                       int month) {
        super(context);
        this.teamLeadUsername = teamLeadUsername;
        this.year = year;
        this.month = month;
    }

    @Override
    protected void validate() {
        if (teamLeadUsername == null || teamLeadUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("Team lead username cannot be null or empty");
        }
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Invalid year: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month: " + month);
        }

        // Validate team lead permissions
        context.validateUserPermissions(teamLeadUsername, "update team statistics");

        // Additional validation: ensure current user is team lead
        String currentUsername = context.getCurrentUsername();
        if (!teamLeadUsername.equals(currentUsername)) {
            throw new SecurityException("Only the team lead can update team statistics");
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Validating team statistics update for team lead %s - %d/%d",
                teamLeadUsername, year, month));
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Updating team statistics for team lead %s - %d/%d",
                teamLeadUsername, year, month));

        try {
            // Read existing team members for the specified period
            List<TeamMemberDTO> teamMemberDTOS = context.readTeamMembers(teamLeadUsername, year, month);

            if (teamMemberDTOS.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "No team members found for team lead %s for period %d/%d",
                        teamLeadUsername, year, month));

                return OperationResult.failure(
                        "No team members found to update statistics for",
                        getOperationType());
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Found %d team members to update", teamMemberDTOS.size()));

            int successfulUpdates = 0;
            List<String> updateErrors = new ArrayList<>();

            for (TeamMemberDTO member : teamMemberDTOS) {
                try {
                    // Update work time statistics
                    updateWorkTimeStats(member, year, month);

                    // Update register statistics
                    updateRegisterStats(member, year, month);

                    // Update session details
                    updateSessionDetails(member);

                    // Update time off information
                    updateTimeOffList(member, year, month);

                    successfulUpdates++;

                    LoggerUtil.debug(this.getClass(), String.format(
                            "Successfully updated statistics for team member: %s", member.getUsername()));

                } catch (Exception e) {
                    String errorMsg = String.format("Failed to update statistics for %s: %s",
                            member.getUsername(), e.getMessage());
                    updateErrors.add(errorMsg);
                    LoggerUtil.error(this.getClass(), errorMsg, e);
                    // Continue with other members
                }
            }

            // Save updated team members
            context.writeTeamMembers(teamMemberDTOS, teamLeadUsername, year, month);

            // Create result message
            String message;
            if (updateErrors.isEmpty()) {
                message = String.format(
                        "Successfully updated statistics for all %d team members for %s - %d/%d",
                        successfulUpdates, teamLeadUsername, year, month);
            } else {
                message = String.format(
                        "Updated statistics for %d/%d team members for %s - %d/%d. %d failures: %s",
                        successfulUpdates, teamMemberDTOS.size(), teamLeadUsername, year, month,
                        updateErrors.size(), String.join("; ", updateErrors));
            }

            LoggerUtil.info(this.getClass(), message);

            // Create side effects tracking
            OperationResult.OperationSideEffects sideEffects = OperationResult.OperationSideEffects.builder()
                    .fileUpdated(String.format("team/%s/%d/%d", teamLeadUsername, year, month))
                    .build();

            return OperationResult.successWithSideEffects(
                    message,
                    getOperationType(),
                    teamMemberDTOS,
                    sideEffects);

        } catch (Exception e) {
            String errorMessage = String.format(
                    "Error updating team statistics for %s - %d/%d: %s",
                    teamLeadUsername, year, month, e.getMessage());

            LoggerUtil.error(this.getClass(), errorMessage, e);

            return OperationResult.failure(errorMessage, getOperationType());
        }
    }

    /**
     * Update work time statistics for a team member
     */
    private void updateWorkTimeStats(TeamMemberDTO member, int year, int month) {
        try {
            List<WorkTimeTable> worktime = context.loadViewOnlyWorktime(member.getUsername(), year, month);

            if (worktime == null || worktime.isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "No worktime data found for member %s", member.getUsername()));
                return;
            }

            // Calculate average start and end times
            OptionalDouble avgStartHour = worktime.stream()
                    .filter(wt -> wt.getDayStartTime() != null)
                    .mapToInt(wt -> wt.getDayStartTime().getHour())
                    .average();

            OptionalDouble avgStartMinute = worktime.stream()
                    .filter(wt -> wt.getDayStartTime() != null)
                    .mapToInt(wt -> wt.getDayStartTime().getMinute())
                    .average();

            OptionalDouble avgEndHour = worktime.stream()
                    .filter(wt -> wt.getDayEndTime() != null)
                    .mapToInt(wt -> wt.getDayEndTime().getHour())
                    .average();

            OptionalDouble avgEndMinute = worktime.stream()
                    .filter(wt -> wt.getDayEndTime() != null)
                    .mapToInt(wt -> wt.getDayEndTime().getMinute())
                    .average();

            if (avgStartHour.isPresent() && avgStartMinute.isPresent()) {
                member.getCurrentMonthWorkStatsDTO().setAverageStartTime(
                        LocalTime.of((int) avgStartHour.getAsDouble(),
                                (int) avgStartMinute.getAsDouble()));
            }

            if (avgEndHour.isPresent() && avgEndMinute.isPresent()) {
                member.getCurrentMonthWorkStatsDTO().setAverageEndTime(
                        LocalTime.of((int) avgEndHour.getAsDouble(),
                                (int) avgEndMinute.getAsDouble()));
            }

            LoggerUtil.debug(this.getClass(), String.format(
                    "Updated work time stats for %s", member.getUsername()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating work time stats for %s: %s", member.getUsername(), e.getMessage()));
            // Don't throw - continue with other updates
        }
    }

    /**
     * Update register statistics for a team member
     */
    private void updateRegisterStats(TeamMemberDTO member, int year, int month) {
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Loading register entries for team member %s - %d/%d",
                    member.getUsername(), year, month));

            // Use ServiceResult pattern to load entries
            ServiceResult<List<RegisterEntry>> entriesResult = context.loadUserRegisterEntries(
                    member.getUsername(), member.getUserId(), year, month);

            if (entriesResult.isSuccess()) {
                List<RegisterEntry> entries = entriesResult.getData();

                if (entries == null || entries.isEmpty()) {
                    LoggerUtil.debug(this.getClass(), String.format(
                            "No register entries found for team member %s - %d/%d",
                            member.getUsername(), year, month));
                    // Set empty stats for this member
                    member.getRegisterStats().setMonthSummaryDTO(createEmptyMonthSummary());
                    member.getRegisterStats().setClientSpecificStats(new HashMap<>());
                    return;
                }

                // Log warnings if any
                if (entriesResult.hasWarnings()) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Loaded register entries for %s with warnings: %s",
                            member.getUsername(), String.join(", ", entriesResult.getWarnings())));
                }

                // Update month summary
                MonthSummaryDTO monthSummaryDTO = calculateMonthSummary(entries);
                member.getRegisterStats().setMonthSummaryDTO(monthSummaryDTO);

                // Update client specific stats
                Map<String, ClientDetailedStatsDTO> clientStats = calculateClientStats(entries);
                member.getRegisterStats().setClientSpecificStats(clientStats);

                LoggerUtil.debug(this.getClass(), String.format(
                        "Successfully updated register stats for %s: %d entries, %d clients",
                        member.getUsername(), entries.size(), clientStats.size()));

            } else {
                // Handle service failure gracefully
                LoggerUtil.warn(this.getClass(), String.format(
                        "Failed to load register entries for team member %s - %d/%d: %s",
                        member.getUsername(), year, month, entriesResult.getErrorMessage()));

                // Set empty stats for this member instead of failing the entire operation
                member.getRegisterStats().setMonthSummaryDTO(createEmptyMonthSummary());
                member.getRegisterStats().setClientSpecificStats(new HashMap<>());
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Unexpected error updating register stats for team member %s - %d/%d: %s",
                    member.getUsername(), year, month, e.getMessage()), e);

            // Set empty stats for this member to prevent the entire team statistics update from failing
            member.getRegisterStats().setMonthSummaryDTO(createEmptyMonthSummary());
            member.getRegisterStats().setClientSpecificStats(new HashMap<>());
        }
    }

    /**
     * Update session details for a team member
     */
    private void updateSessionDetails(TeamMemberDTO member) {
        try {
            WorkUsersSessionsStates session = context.readNetworkSessionFile(
                    member.getUsername(), member.getUserId());

            if (session != null && session.getSessionStatus() != null) {
                // Normalize status based on exact statuses
                String normalizedStatus;
                String originalStatus = session.getSessionStatus();

                if (originalStatus.equals("Online")) {
                    normalizedStatus = "WORK_ONLINE";
                } else if (originalStatus.equals("Temporary Stop")) {
                    normalizedStatus = "WORK_TEMPORARY_STOP";
                } else {
                    normalizedStatus = "WORK_OFFLINE";
                }

                LoggerUtil.debug(this.getClass(), String.format(
                        "Updated session status for %s: %s -> %s",
                        member.getUsername(), originalStatus, normalizedStatus));

                member.setSessionDetailsDTO(SessionDetailsDTO.builder()
                        .status(normalizedStatus)
                        .dayStartTime(session.getDayStartTime())
                        .dayEndTime(session.getDayEndTime())
                        .build());
            } else {
                LoggerUtil.debug(this.getClass(), String.format(
                        "No session data found for member %s", member.getUsername()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating session details for %s: %s", member.getUsername(), e.getMessage()));
            // Don't throw - continue with other updates
        }
    }

    /**
     * Update time off list for a team member
     */
    private void updateTimeOffList(TeamMemberDTO member, int year, int month) {
        try {
            // Load work time entries to get time off information
            List<WorkTimeTable> worktime = context.loadViewOnlyWorktime(member.getUsername(), year, month);

            if (worktime == null || worktime.isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "No worktime data found for time off calculation for member %s", member.getUsername()));
                return;
            }

            // Group by time off type
            Map<String, List<LocalDate>> timeOffDays = worktime.stream()
                    .filter(wt -> wt.getTimeOffType() != null)
                    .collect(Collectors.groupingBy(
                            WorkTimeTable::getTimeOffType,
                            Collectors.mapping(WorkTimeTable::getWorkDate, Collectors.toList())
                    ));

            // Create time off entries
            List<TimeOffEntryDTO> coEntries = createTimeOffEntries("CO", timeOffDays.get("CO"));
            List<TimeOffEntryDTO> cmEntries = createTimeOffEntries("CM", timeOffDays.get("CM"));
            List<TimeOffEntryDTO> snEntries = createTimeOffEntries("SN", timeOffDays.get("SN"));

            member.setTimeOffListDTO(TimeOffListDTO.builder()
                    .timeOffCO(coEntries)
                    .timeOffCM(cmEntries)
                    .timeOffSN(snEntries)
                    .build());

            LoggerUtil.debug(this.getClass(), String.format(
                    "Updated time off list for %s: CO=%d, CM=%d, SN=%d",
                    member.getUsername(), coEntries.size(), cmEntries.size(), snEntries.size()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating time off list for %s: %s", member.getUsername(), e.getMessage()));
            // Don't throw - continue with other updates
        }
    }

    /**
     * Create empty month summary for cases where no data is available
     */
    private MonthSummaryDTO createEmptyMonthSummary() {
        return MonthSummaryDTO.builder()
                .totalWorkDays(0)
                .processedOrders(0)
                .uniqueClients(0)
                .averageComplexity(0.0)
                .averageArticleNumbers(0.0)
                .build();
    }

    /**
     * Calculate month summary from register entries
     */
    private MonthSummaryDTO calculateMonthSummary(List<RegisterEntry> entries) {
        Set<String> uniqueClients = entries.stream()
                .map(RegisterEntry::getClientName)
                .collect(Collectors.toSet());

        Set<LocalDate> uniqueDays = entries.stream()
                .map(RegisterEntry::getDate)
                .collect(Collectors.toSet());

        double avgComplexity = entries.stream()
                .mapToDouble(RegisterEntry::getGraphicComplexity)
                .average()
                .orElse(0.0);

        double avgArticles = entries.stream()
                .mapToDouble(RegisterEntry::getArticleNumbers)
                .average()
                .orElse(0.0);

        return MonthSummaryDTO.builder()
                .totalWorkDays(uniqueDays.size())
                .processedOrders(entries.size())
                .uniqueClients(uniqueClients.size())
                .averageComplexity(avgComplexity)
                .averageArticleNumbers(avgArticles)
                .build();
    }

    /**
     * Calculate client specific statistics
     */
    private Map<String, ClientDetailedStatsDTO> calculateClientStats(List<RegisterEntry> entries) {
        Map<String, List<RegisterEntry>> entriesByClient = entries.stream()
                .collect(Collectors.groupingBy(RegisterEntry::getClientName));

        return entriesByClient.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> calculateClientDetailedStats(e.getValue())
                ));
    }

    /**
     * Calculate detailed statistics for a specific client
     */
    private ClientDetailedStatsDTO calculateClientDetailedStats(List<RegisterEntry> clientEntries) {
        // Calculate overall averages
        double avgComplexity = clientEntries.stream()
                .mapToDouble(RegisterEntry::getGraphicComplexity)
                .average()
                .orElse(0.0);

        double avgArticles = clientEntries.stream()
                .mapToDouble(RegisterEntry::getArticleNumbers)
                .average()
                .orElse(0.0);

        // Calculate stats per action type
        Map<String, ActionTypeStatsDTO> actionTypeStats = calculateActionTypeStats(clientEntries);

        // Calculate overall print prep type distribution
        Map<String, Integer> printPrepDist = calculatePrintPrepDistribution(clientEntries);

        return ClientDetailedStatsDTO.builder()
                .totalOrders(clientEntries.size())
                .averageComplexity(avgComplexity)
                .averageArticleNumbers(avgArticles)
                .actionTypeStats(actionTypeStats)
                .printPrepTypeDistribution(printPrepDist)
                .build();
    }

    /**
     * Calculate action type specific statistics
     */
    private Map<String, ActionTypeStatsDTO> calculateActionTypeStats(List<RegisterEntry> entries) {
        Map<String, List<RegisterEntry>> entriesByActionType = entries.stream()
                .collect(Collectors.groupingBy(RegisterEntry::getActionType));

        return entriesByActionType.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            List<RegisterEntry> actionTypeEntries = e.getValue();
                            return ActionTypeStatsDTO.builder()
                                    .count(actionTypeEntries.size())
                                    .averageComplexity(actionTypeEntries.stream()
                                            .mapToDouble(RegisterEntry::getGraphicComplexity)
                                            .average()
                                            .orElse(0.0))
                                    .averageArticleNumbers(actionTypeEntries.stream()
                                            .mapToDouble(RegisterEntry::getArticleNumbers)
                                            .average()
                                            .orElse(0.0))
                                    .printPrepTypeDistribution(
                                            calculatePrintPrepDistribution(actionTypeEntries))
                                    .build();
                        }
                ));
    }

    /**
     * Calculate print preparation type distribution
     */
    private Map<String, Integer> calculatePrintPrepDistribution(List<RegisterEntry> entries) {
        return entries.stream()
                .flatMap(entry -> entry.getPrintPrepTypes().stream())
                .collect(Collectors.groupingBy(
                        type -> type,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));
    }

    /**
     * Create time off entries from days list
     */
    private List<TimeOffEntryDTO> createTimeOffEntries(String type, List<LocalDate> days) {
        if (days == null || days.isEmpty()) {
            return new ArrayList<>();
        }

        return List.of(TimeOffEntryDTO.builder()
                .timeOffType(type)
                .days(new ArrayList<>(days))
                .build());
    }

    @Override
    protected String getCommandName() {
        return String.format("UpdateTeamStatistics[lead=%s, period=%d/%d]",
                teamLeadUsername, year, month);
    }

    @Override
    protected String getOperationType() {
        return "UPDATE_TEAM_STATISTICS";
    }
}