package com.ctgraphdep.service;

import com.ctgraphdep.fileOperations.data.RegisterDataService;
import com.ctgraphdep.fileOperations.data.SessionDataService;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.dto.TeamMemberDTO;
import com.ctgraphdep.model.dto.team.*;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FIXED TeamStatisticsService to work with refactored UserRegisterService
 * Key Changes:
 * - Updated updateRegisterStats method to handle ServiceResult<List<RegisterEntry>>
 * - Added proper error handling for register service calls
 * - Enhanced logging for debugging register loading issues
 * - Graceful handling of service failures
 */
@Service
public class TeamStatisticsService {
    private final UserService userService;
    private final WorktimeManagementService worktimeManagementService;
    private final UserRegisterService registerService;
    private final RegisterDataService registerDataService;
    private final SessionDataService sessionDataService;

    public TeamStatisticsService(UserService userService, WorktimeManagementService worktimeManagementService,
                                 UserRegisterService registerService, RegisterDataService registerDataService, SessionDataService sessionDataService) {
        this.userService = userService;
        this.worktimeManagementService = worktimeManagementService;
        this.registerService = registerService;
        this.registerDataService = registerDataService;
        this.sessionDataService = sessionDataService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Initialize team member entries for selected users
     * @param selectedUserIds List of user IDs to add to team
     * @param teamLeadUsername Username of the team leader creating the team
     * @param year Year for the statistics
     * @param month Month for the statistics
     */
    public void initializeTeamMembers(List<Integer> selectedUserIds, String teamLeadUsername, int year, int month) {
        try {
            List<TeamMemberDTO> teamMemberDTOS = new ArrayList<>();

            for (Integer userId : selectedUserIds) {
                User user = userService.getUserById(userId).orElseThrow(() -> new RuntimeException("User not found: " + userId));

                TeamMemberDTO member = createInitialTeamMember(user);
                teamMemberDTOS.add(member);
            }

            // Save initial team members to JSON with year and month
            registerDataService.writeTeamMembers(teamMemberDTOS, teamLeadUsername, year, month);

            LoggerUtil.info(this.getClass(), String.format("Initialized %d team members for team lead %s for period %d/%d", teamMemberDTOS.size(), teamLeadUsername, year, month));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error initializing team members for team lead %s for period %d/%d: %s", teamLeadUsername, year, month, e.getMessage()));
            throw new RuntimeException("Failed to initialize team members", e);
        }
    }

    /**
     * Update statistics for all team members
     * @param teamLeadUsername Username of the team leader
     * @param year Year to update statistics for
     * @param month Month to update statistics for
     */
    public void updateTeamStatistics(String teamLeadUsername, int year, int month) {
        try {
            // Read existing team members for the specified period
            List<TeamMemberDTO> teamMemberDTOS = registerDataService.readTeamMembers(teamLeadUsername, year, month);

            if (teamMemberDTOS.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format("No team members found for team lead %s for period %d/%d", teamLeadUsername, year, month));
                return;
            }

            for (TeamMemberDTO member : teamMemberDTOS) {
                // Update work time statistics
                updateWorkTimeStats(member, year, month);

                // Update register statistics - FIXED to handle ServiceResult
                updateRegisterStats(member, year, month);

                // Update session details
                updateSessionDetails(member);

                // Update time off information
                updateTimeOffList(member, year, month);
            }

            // Save updated team members with year and month
            registerDataService.writeTeamMembers(teamMemberDTOS, teamLeadUsername, year, month);

            LoggerUtil.info(this.getClass(), String.format("Updated statistics for %d team members for team lead %s for period %d/%d", teamMemberDTOS.size(), teamLeadUsername, year, month));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating team statistics for team lead %s for period %d/%d: %s", teamLeadUsername, year, month, e.getMessage()));
            throw new RuntimeException("Failed to update team statistics", e);
        }
    }

    private TeamMemberDTO createInitialTeamMember(User user) {
        return TeamMemberDTO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .name(user.getName())
                .employeeId(user.getEmployeeId())
                .schedule(user.getSchedule())
                .role(user.getRole())
                .currentMonthWorkStatsDTO(new CurrentMonthWorkStatsDTO())
                .timeOffListDTO(new TimeOffListDTO())
                .sessionDetailsDTO(new SessionDetailsDTO())
                .registerStats(createInitialRegisterStats())
                .build();
    }

    private TeamMemberRegisterStatsDTO createInitialRegisterStats() {
        return TeamMemberRegisterStatsDTO.builder()
                .monthSummaryDTO(new MonthSummaryDTO())
                .clientSpecificStats(new HashMap<>())
                .build();
    }

    /**
     * Get team members for a specific period
     * @param teamLeadUsername Username of the team leader
     * @param year Year to get members for
     * @param month Month to get members for
     * @return List of team members, empty list if none found
     */
    public List<TeamMemberDTO> getTeamMembers(String teamLeadUsername, int year, int month) {
        try {
            List<TeamMemberDTO> teamMemberDTOS = registerDataService.readTeamMembers(teamLeadUsername, year, month);

            // If no members found, return empty list instead of null
            if (teamMemberDTOS == null) {
                return new ArrayList<>();
            }

            LoggerUtil.info(this.getClass(),
                    String.format("Retrieved %d team members for team lead %s for period %d/%d",
                            teamMemberDTOS.size(), teamLeadUsername, year, month));

            return teamMemberDTOS;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error retrieving team members for team lead %s for period %d/%d: %s",
                            teamLeadUsername, year, month, e.getMessage()));
            return new ArrayList<>(); // Return empty list on error
        }
    }

    private void updateWorkTimeStats(TeamMemberDTO member, int year, int month) {
        List<WorkTimeTable> worktime = worktimeManagementService.loadViewOnlyWorktime(member.getUsername(), year, month);

        if (worktime == null || worktime.isEmpty()) {
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
    }

    /**
     * FIXED: Updated to handle ServiceResult from UserRegisterService
     */
    private void updateRegisterStats(TeamMemberDTO member, int year, int month) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Loading register entries for team member %s - %d/%d", member.getUsername(), year, month));

            // Use ServiceResult pattern to load entries
            ServiceResult<List<RegisterEntry>> entriesResult = registerService.loadMonthEntries(member.getUsername(), member.getUserId(), year, month);

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

    private MonthSummaryDTO calculateMonthSummary(List<RegisterEntry> entries) {
        Set<String> uniqueClients = entries.stream().map(RegisterEntry::getClientName).collect(Collectors.toSet());

        Set<LocalDate> uniqueDays = entries.stream().map(RegisterEntry::getDate).collect(Collectors.toSet());

        double avgComplexity = entries.stream().mapToDouble(RegisterEntry::getGraphicComplexity).average().orElse(0.0);

        double avgArticles = entries.stream().mapToDouble(RegisterEntry::getArticleNumbers).average().orElse(0.0);

        return MonthSummaryDTO.builder()
                .totalWorkDays(uniqueDays.size())
                .processedOrders(entries.size())
                .uniqueClients(uniqueClients.size())
                .averageComplexity(avgComplexity)
                .averageArticleNumbers(avgArticles)
                .build();
    }

    private Map<String, ClientDetailedStatsDTO> calculateClientStats(List<RegisterEntry> entries) {
        Map<String, List<RegisterEntry>> entriesByClient = entries.stream().collect(Collectors.groupingBy(RegisterEntry::getClientName));

        return entriesByClient.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> calculateClientDetailedStats(e.getValue())));
    }

    private ClientDetailedStatsDTO calculateClientDetailedStats(List<RegisterEntry> clientEntries) {
        // Calculate overall averages
        double avgComplexity = clientEntries.stream().mapToDouble(RegisterEntry::getGraphicComplexity).average().orElse(0.0);

        double avgArticles = clientEntries.stream().mapToDouble(RegisterEntry::getArticleNumbers).average().orElse(0.0);

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

    private Map<String, ActionTypeStatsDTO> calculateActionTypeStats(List<RegisterEntry> entries) {
        Map<String, List<RegisterEntry>> entriesByActionType = entries.stream().collect(Collectors.groupingBy(RegisterEntry::getActionType));

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

    private Map<String, Integer> calculatePrintPrepDistribution(List<RegisterEntry> entries) {
        return entries.stream()
                .flatMap(entry -> entry.getPrintPrepTypes().stream())
                .collect(Collectors.groupingBy(
                        type -> type,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));
    }

    private void updateSessionDetails(TeamMemberDTO member) {

        WorkUsersSessionsStates session = sessionDataService.readNetworkSessionFileReadOnly(member.getUsername(), member.getUserId());

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

            LoggerUtil.info(this.getClass(), "Original session status for " + member.getUsername() + ": " + originalStatus + ", Normalized status: " + normalizedStatus);

            member.setSessionDetailsDTO(SessionDetailsDTO.builder()
                    .status(normalizedStatus)
                    .dayStartTime(session.getDayStartTime())
                    .dayEndTime(session.getDayEndTime())
                    .build());
        }
    }

    private void updateTimeOffList(TeamMemberDTO member, int year, int month) {
        // Load work time entries to get time off information
        List<WorkTimeTable> worktime = worktimeManagementService.loadViewOnlyWorktime(member.getUsername(), year, month);

        if (worktime == null || worktime.isEmpty()) {
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
    }

    private List<TimeOffEntryDTO> createTimeOffEntries(String type, List<LocalDate> days) {
        if (days == null || days.isEmpty()) {
            return new ArrayList<>();
        }

        return List.of(TimeOffEntryDTO.builder().timeOffType(type).days(new ArrayList<>(days)).build());
    }
}