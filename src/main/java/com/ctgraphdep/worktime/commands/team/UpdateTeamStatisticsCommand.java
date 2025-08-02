package com.ctgraphdep.worktime.commands.team;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.dto.TeamMemberDTO;
import com.ctgraphdep.model.dto.team.*;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.worktime.commands.WorktimeOperationCommand;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.accessor.NetworkOnlyAccessor;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class UpdateTeamStatisticsCommand extends WorktimeOperationCommand<Object> {
    private final String teamLeadUsername;
    private final int year;
    private final int month;

    public UpdateTeamStatisticsCommand(WorktimeOperationContext context, String teamLeadUsername, int year, int month) {
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

        LoggerUtil.info(this.getClass(), String.format("Validating team statistics update for team lead %s - %d/%d", teamLeadUsername, year, month));
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format("Updating team statistics for team lead %s - %d/%d",
                teamLeadUsername, year, month));

        try {
            // Read existing team members for the specified period
            List<TeamMemberDTO> teamMemberDTOS = context.readTeamMembers(teamLeadUsername, year, month);

            if (teamMemberDTOS.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format("No team members found for team lead %s for period %d/%d",
                        teamLeadUsername, year, month));

                return OperationResult.failure("No team members found to update statistics for", getOperationType());
            }

            LoggerUtil.info(this.getClass(), String.format("Found %d team members to update", teamMemberDTOS.size()));

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

                    LoggerUtil.debug(this.getClass(), String.format("Successfully updated statistics for team member: %s", member.getUsername()));

                } catch (Exception e) {
                    String errorMsg = String.format("Failed to update statistics for %s: %s", member.getUsername(), e.getMessage());
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
                message = String.format("Successfully updated statistics for all %d team members for %s - %d/%d", successfulUpdates, teamLeadUsername, year, month);
            } else {
                message = String.format("Updated statistics for %d/%d team members for %s - %d/%d. %d failures: %s",
                        successfulUpdates, teamMemberDTOS.size(), teamLeadUsername, year, month,
                        updateErrors.size(), String.join("; ", updateErrors));
            }

            LoggerUtil.info(this.getClass(), message);

            // Create side effects tracking
            OperationResult.OperationSideEffects sideEffects = OperationResult.OperationSideEffects.builder()
                    .fileUpdated(String.format("team/%s/%d/%d", teamLeadUsername, year, month)).build();

            return OperationResult.successWithSideEffects(message, getOperationType(), teamMemberDTOS, sideEffects);

        } catch (Exception e) {
            String errorMessage = String.format("Error updating team statistics for %s - %d/%d: %s", teamLeadUsername, year, month, e.getMessage());

            LoggerUtil.error(this.getClass(), errorMessage, e);

            return OperationResult.failure(errorMessage, getOperationType());
        }
    }

    // Update work time statistics for a team member - ORIGINAL LOGIC with NetworkOnlyAccessor
    private void updateWorkTimeStats(TeamMemberDTO member, int year, int month) {
        try {
            // Use NetworkOnlyAccessor for consistent cross-user data access
            WorktimeDataAccessor accessor = new NetworkOnlyAccessor(
                    context.getWorktimeDataService(),
                    context.getRegisterDataService(),
                    context.getCheckRegisterDataService(),
                    context.getTimeOffDataService()
            );

            List<WorkTimeTable> worktime = accessor.readWorktime(member.getUsername(), year, month);

            if (worktime == null || worktime.isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format("No worktime data found for member %s", member.getUsername()));
                return;
            }

            // Calculate average start and end times - ORIGINAL LOGIC
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

            LoggerUtil.debug(this.getClass(), String.format("Updated work time stats for %s", member.getUsername()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating work time stats for %s: %s", member.getUsername(), e.getMessage()));
            // Don't throw - continue with other updates
        }
    }

    // Update register statistics for a team member - ORIGINAL LOGIC
    private void updateRegisterStats(TeamMemberDTO member, int year, int month) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Loading register entries for team member %s - %d/%d",
                    member.getUsername(), year, month));

            // Use ServiceResult pattern to load entries - ORIGINAL LOGIC
            ServiceResult<List<RegisterEntry>> entriesResult = context.loadUserRegisterEntries(member.getUsername(), member.getUserId(), year, month);

            if (entriesResult.isSuccess()) {
                List<RegisterEntry> entries = entriesResult.getData();

                if (entries == null || entries.isEmpty()) {
                    LoggerUtil.debug(this.getClass(), String.format("No register entries found for team member %s - %d/%d", member.getUsername(), year, month));
                    // Set empty stats for this member
                    member.getRegisterStats().setMonthSummaryDTO(createEmptyMonthSummary());
                    member.getRegisterStats().setClientSpecificStats(new HashMap<>());
                    return;
                }

                // Log warnings if any
                if (entriesResult.hasWarnings()) {
                    LoggerUtil.warn(this.getClass(), String.format("Loaded register entries for %s with warnings: %s",
                            member.getUsername(), String.join(", ", entriesResult.getWarnings())));
                }

                // Update month summary
                MonthSummaryDTO monthSummaryDTO = calculateMonthSummary(entries);
                member.getRegisterStats().setMonthSummaryDTO(monthSummaryDTO);

                // Update client specific stats
                Map<String, ClientDetailedStatsDTO> clientStats = calculateClientStats(entries);
                member.getRegisterStats().setClientSpecificStats(clientStats);

                LoggerUtil.debug(this.getClass(), String.format("Successfully updated register stats for %s: %d entries, %d clients",
                        member.getUsername(), entries.size(), clientStats.size()));

            } else {
                // Handle service failure gracefully
                LoggerUtil.warn(this.getClass(), String.format("Failed to load register entries for team member %s - %d/%d: %s",
                        member.getUsername(), year, month, entriesResult.getErrorMessage()));

                // Set empty stats for this member instead of failing the entire operation
                member.getRegisterStats().setMonthSummaryDTO(createEmptyMonthSummary());
                member.getRegisterStats().setClientSpecificStats(new HashMap<>());
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Unexpected error updating register stats for team member %s - %d/%d: %s",
                    member.getUsername(), year, month, e.getMessage()), e);

            // Set empty stats for this member to prevent the entire team statistics update from failing
            member.getRegisterStats().setMonthSummaryDTO(createEmptyMonthSummary());
            member.getRegisterStats().setClientSpecificStats(new HashMap<>());
        }
    }

    // Update session details for a team member - ORIGINAL LOGIC
    private void updateSessionDetails(TeamMemberDTO member) {
        try {
            WorkUsersSessionsStates session = context.readNetworkSessionFile(member.getUsername(), member.getUserId());

            if (session != null && session.getSessionStatus() != null) {
                // Normalize status based on exact statuses - ORIGINAL LOGIC
                String normalizedStatus;
                String originalStatus = session.getSessionStatus();

                if (originalStatus.equals(WorkCode.WORK_ONLINE)) {
                    normalizedStatus = WorkCode.WORK_ONLINE_LONG;
                } else if (originalStatus.equals(WorkCode.WORK_TEMPORARY_STOP)) {
                    normalizedStatus = WorkCode.WORK_TEMPORARY_STOP_LONG;
                } else {
                    normalizedStatus = WorkCode.WORK_OFFLINE_LONG;
                }

                LoggerUtil.debug(this.getClass(), String.format("Updated session status for %s: %s -> %s",
                        member.getUsername(), originalStatus, normalizedStatus));

                member.setSessionDetailsDTO(SessionDetailsDTO.builder()
                        .status(normalizedStatus)
                        .dayStartTime(session.getDayStartTime())
                        .dayEndTime(session.getDayEndTime())
                        .build());
            } else {
                LoggerUtil.debug(this.getClass(), String.format("No session data found for member %s", member.getUsername()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating session details for %s: %s", member.getUsername(), e.getMessage()));
            // Don't throw - continue with other updates
        }
    }

    // Update time off list for a team member - ORIGINAL LOGIC with NetworkOnlyAccessor
    private void updateTimeOffList(TeamMemberDTO member, int year, int month) {
        try {
            // Use NetworkOnlyAccessor for consistent cross-user data access
            WorktimeDataAccessor accessor = new NetworkOnlyAccessor(
                    context.getWorktimeDataService(),
                    context.getRegisterDataService(),
                    context.getCheckRegisterDataService(),
                    context.getTimeOffDataService()
            );

            // Load work time entries to get time off information - ORIGINAL LOGIC
            List<WorkTimeTable> worktime = accessor.readWorktime(member.getUsername(), year, month);

            if (worktime == null || worktime.isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format("No worktime data found for time off calculation for member %s", member.getUsername()));
                return;
            }

            // Group by time off type - ORIGINAL LOGIC
            Map<String, List<LocalDate>> timeOffDays = worktime.stream().filter(wt -> wt.getTimeOffType() != null)
                    .collect(Collectors.groupingBy(WorkTimeTable::getTimeOffType,
                            Collectors.mapping(WorkTimeTable::getWorkDate, Collectors.toList())));

            // Create time off entries - ORIGINAL LOGIC
            List<TimeOffEntryDTO> coEntries = createTimeOffEntries(WorkCode.TIME_OFF_CODE, timeOffDays.get(WorkCode.TIME_OFF_CODE));
            List<TimeOffEntryDTO> cmEntries = createTimeOffEntries(WorkCode.MEDICAL_LEAVE_CODE, timeOffDays.get(WorkCode.MEDICAL_LEAVE_CODE));
            List<TimeOffEntryDTO> snEntries = createTimeOffEntries(WorkCode.NATIONAL_HOLIDAY_CODE, timeOffDays.get(WorkCode.NATIONAL_HOLIDAY_CODE));

            member.setTimeOffListDTO(TimeOffListDTO.builder().timeOffCO(coEntries).timeOffCM(cmEntries).timeOffSN(snEntries).build());

            LoggerUtil.debug(this.getClass(), String.format("Updated time off list for %s: CO=%d, CM=%d, SN=%d",
                    member.getUsername(), coEntries.size(), cmEntries.size(), snEntries.size()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating time off list for %s: %s", member.getUsername(), e.getMessage()));
            // Don't throw - continue with other updates
        }
    }

    // Create empty month summary for cases where no data is available - ORIGINAL LOGIC
    private MonthSummaryDTO createEmptyMonthSummary() {
        return MonthSummaryDTO.builder()
                .totalWorkDays(0)
                .processedOrders(0)
                .uniqueClients(0)
                .averageComplexity(0.0)
                .averageArticleNumbers(0.0)
                .build();
    }

    // Calculate month summary from register entries - ORIGINAL LOGIC with IMPOSTARE filter
    private MonthSummaryDTO calculateMonthSummary(List<RegisterEntry> entries) {
        // Filter out IMPOSTARE entries from calculations
        List<RegisterEntry> filteredEntries = entries.stream()
                .filter(entry -> !WorkCode.AT_IMPOSTARE.equals(entry.getActionType()))
                .toList();

        Set<String> uniqueClients = filteredEntries.stream()
                .map(RegisterEntry::getClientName)
                .collect(Collectors.toSet());

        Set<LocalDate> uniqueDays = filteredEntries.stream()
                .map(RegisterEntry::getDate)
                .collect(Collectors.toSet());

        double avgComplexity = filteredEntries.stream()
                .mapToDouble(RegisterEntry::getGraphicComplexity)
                .average()
                .orElse(0.0);

        double avgArticles = filteredEntries.stream()
                .mapToDouble(RegisterEntry::getArticleNumbers)
                .average()
                .orElse(0.0);

        return MonthSummaryDTO.builder()
                .totalWorkDays(uniqueDays.size())
                .processedOrders(filteredEntries.size()) // Use filtered count
                .uniqueClients(uniqueClients.size())
                .averageComplexity(avgComplexity)
                .averageArticleNumbers(avgArticles)
                .build();
    }

    // Calculate client specific statistics - ORIGINAL LOGIC with IMPOSTARE filter
    private Map<String, ClientDetailedStatsDTO> calculateClientStats(List<RegisterEntry> entries) {
        Map<String, List<RegisterEntry>> entriesByClient = entries.stream().collect(Collectors.groupingBy(RegisterEntry::getClientName));

        return entriesByClient.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey, e -> calculateClientDetailedStats(e.getValue())));
    }

    // Calculate detailed statistics for a specific client - ORIGINAL LOGIC with IMPOSTARE filter
    private ClientDetailedStatsDTO calculateClientDetailedStats(List<RegisterEntry> clientEntries) {
        // Filter out IMPOSTARE entries from calculations
        List<RegisterEntry> filteredEntries = clientEntries.stream()
                .filter(entry -> !WorkCode.AT_IMPOSTARE.equals(entry.getActionType()))
                .collect(Collectors.toList());

        // Calculate overall averages (excluding IMPOSTARE)
        double avgComplexity = filteredEntries.stream()
                .mapToDouble(RegisterEntry::getGraphicComplexity)
                .average()
                .orElse(0.0);

        double avgArticles = filteredEntries.stream()
                .mapToDouble(RegisterEntry::getArticleNumbers)
                .average()
                .orElse(0.0);

        // Calculate stats per action type (including IMPOSTARE for display, but filtered for counts)
        Map<String, ActionTypeStatsDTO> actionTypeStats = calculateActionTypeStats(clientEntries);

        // Calculate overall print prep type distribution (excluding IMPOSTARE)
        Map<String, Integer> printPrepDist = calculatePrintPrepDistribution(filteredEntries);

        return ClientDetailedStatsDTO.builder().totalOrders(filteredEntries.size()).averageComplexity(avgComplexity)
                .averageArticleNumbers(avgArticles).actionTypeStats(actionTypeStats).printPrepTypeDistribution(printPrepDist)
                .build();
    }

    // Calculate action type specific statistics - ORIGINAL LOGIC with IMPOSTARE filter
    private Map<String, ActionTypeStatsDTO> calculateActionTypeStats(List<RegisterEntry> entries) {
        Map<String, List<RegisterEntry>> entriesByActionType = entries.stream().collect(Collectors.groupingBy(RegisterEntry::getActionType));

        return entriesByActionType.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            List<RegisterEntry> actionTypeEntries = e.getValue();

                            // For IMPOSTARE: show in display but use 0 for counts
                            if (WorkCode.AT_IMPOSTARE.equals(e.getKey())) {
                                return ActionTypeStatsDTO.builder()
                                        .count(0) // Set count to 0 for IMPOSTARE
                                        .averageComplexity(0.0)
                                        .averageArticleNumbers(0.0)
                                        .printPrepTypeDistribution(new HashMap<>())
                                        .build();
                            }

                            // For all other action types: normal calculations
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

    // Calculate print preparation type distribution - ORIGINAL LOGIC
    private Map<String, Integer> calculatePrintPrepDistribution(List<RegisterEntry> entries) {
        return entries.stream().flatMap(entry -> entry.getPrintPrepTypes().stream())
                .collect(Collectors.groupingBy(type -> type,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    // Create time off entries from days list - ORIGINAL LOGIC
    private List<TimeOffEntryDTO> createTimeOffEntries(String type, List<LocalDate> days) {
        if (days == null || days.isEmpty()) {
            return new ArrayList<>();
        }

        return List.of(TimeOffEntryDTO.builder().timeOffType(type).days(new ArrayList<>(days)).build());
    }

    @Override
    protected String getCommandName() {
        return String.format("UpdateTeamStatistics[lead=%s, period=%d/%d]", teamLeadUsername, year, month);
    }

    @Override
    protected String getOperationType() {
        return "UPDATE_TEAM_STATISTICS";
    }
}