package com.ctgraphdep.service;

import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.team.*;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TeamStatisticsService {
    private final DataAccessService dataAccessService;
    private final UserService userService;
    private final UserWorkTimeService workTimeService;
    private final UserRegisterService registerService;

    public TeamStatisticsService(DataAccessService dataAccessService,
                                 UserService userService,
                                 UserWorkTimeService workTimeService,
                                 UserRegisterService registerService) {
        this.dataAccessService = dataAccessService;
        this.userService = userService;
        this.workTimeService = workTimeService;
        this.registerService = registerService;
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
            List<TeamMember> teamMembers = new ArrayList<>();

            for (Integer userId : selectedUserIds) {
                User user = userService.getUserById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found: " + userId));

                TeamMember member = createInitialTeamMember(user);
                teamMembers.add(member);
            }

            // Save initial team members to JSON with year and month
            dataAccessService.writeTeamMembers(teamMembers, teamLeadUsername, year, month);

            LoggerUtil.info(this.getClass(),
                    String.format("Initialized %d team members for team lead %s for period %d/%d",
                            teamMembers.size(), teamLeadUsername, year, month));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error initializing team members for team lead %s for period %d/%d: %s",
                            teamLeadUsername, year, month, e.getMessage()));
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
            List<TeamMember> teamMembers = dataAccessService.readTeamMembers(teamLeadUsername, year, month);

            if (teamMembers.isEmpty()) {
                LoggerUtil.warn(this.getClass(),
                        String.format("No team members found for team lead %s for period %d/%d",
                                teamLeadUsername, year, month));
                return;
            }

            for (TeamMember member : teamMembers) {
                // Update work time statistics
                updateWorkTimeStats(member, year, month);

                // Update register statistics
                updateRegisterStats(member, year, month);

                // Update session details
                updateSessionDetails(member);

                // Update time off information
                updateTimeOffList(member, year, month);
            }

            // Save updated team members with year and month
            dataAccessService.writeTeamMembers(teamMembers, teamLeadUsername, year, month);

            LoggerUtil.info(this.getClass(),
                    String.format("Updated statistics for %d team members for team lead %s for period %d/%d",
                            teamMembers.size(), teamLeadUsername, year, month));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error updating team statistics for team lead %s for period %d/%d: %s",
                            teamLeadUsername, year, month, e.getMessage()));
            throw new RuntimeException("Failed to update team statistics", e);
        }
    }

    private TeamMember createInitialTeamMember(User user) {
        return TeamMember.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .name(user.getName())
                .employeeId(user.getEmployeeId())
                .schedule(user.getSchedule())
                .role(user.getRole())
                .currentMonthWorkStats(new CurrentMonthWorkStats())
                .timeOffList(new TimeOffList())
                .sessionDetails(new SessionDetails())
                .registerStats(createInitialRegisterStats())
                .build();
    }

    private TeamMemberRegisterStats createInitialRegisterStats() {
        return TeamMemberRegisterStats.builder()
                .monthSummary(new MonthSummary())
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
    public List<TeamMember> getTeamMembers(String teamLeadUsername, int year, int month) {
        try {
            List<TeamMember> teamMembers = dataAccessService.readTeamMembers(teamLeadUsername, year, month);

            // If no members found, return empty list instead of null
            if (teamMembers == null) {
                return new ArrayList<>();
            }

            LoggerUtil.info(this.getClass(),
                    String.format("Retrieved %d team members for team lead %s for period %d/%d",
                            teamMembers.size(), teamLeadUsername, year, month));

            return teamMembers;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error retrieving team members for team lead %s for period %d/%d: %s",
                            teamLeadUsername, year, month, e.getMessage()));
            return new ArrayList<>(); // Return empty list on error
        }
    }

    private void updateWorkTimeStats(TeamMember member, int year, int month) {
        List<WorkTimeTable> worktime = workTimeService.loadMonthWorktime(
                member.getUsername(), year, month);

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
            member.getCurrentMonthWorkStats().setAverageStartTime(
                    LocalTime.of((int) avgStartHour.getAsDouble(),
                            (int) avgStartMinute.getAsDouble()));
        }

        if (avgEndHour.isPresent() && avgEndMinute.isPresent()) {
            member.getCurrentMonthWorkStats().setAverageEndTime(
                    LocalTime.of((int) avgEndHour.getAsDouble(),
                            (int) avgEndMinute.getAsDouble()));
        }
    }

    private void updateRegisterStats(TeamMember member, int year, int month) {
        List<RegisterEntry> entries = registerService.loadMonthEntries(
                member.getUsername(), member.getUserId(), year, month);

        if (entries == null || entries.isEmpty()) {
            return;
        }

        // Update month summary
        MonthSummary monthSummary = calculateMonthSummary(entries);
        member.getRegisterStats().setMonthSummary(monthSummary);

        // Update client specific stats
        Map<String, ClientDetailedStats> clientStats = calculateClientStats(entries);
        member.getRegisterStats().setClientSpecificStats(clientStats);
    }

    private MonthSummary calculateMonthSummary(List<RegisterEntry> entries) {
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

        return MonthSummary.builder()
                .totalWorkDays(uniqueDays.size())
                .processedOrders(entries.size())
                .uniqueClients(uniqueClients.size())
                .averageComplexity(avgComplexity)
                .averageArticleNumbers(avgArticles)
                .build();
    }

    private Map<String, ClientDetailedStats> calculateClientStats(List<RegisterEntry> entries) {
        Map<String, List<RegisterEntry>> entriesByClient = entries.stream()
                .collect(Collectors.groupingBy(RegisterEntry::getClientName));

        return entriesByClient.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> calculateClientDetailedStats(e.getValue())
                ));
    }

    private ClientDetailedStats calculateClientDetailedStats(List<RegisterEntry> clientEntries) {
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
        Map<String, ActionTypeStats> actionTypeStats = calculateActionTypeStats(clientEntries);

        // Calculate overall print prep type distribution
        Map<String, Integer> printPrepDist = calculatePrintPrepDistribution(clientEntries);

        return ClientDetailedStats.builder()
                .totalOrders(clientEntries.size())
                .averageComplexity(avgComplexity)
                .averageArticleNumbers(avgArticles)
                .actionTypeStats(actionTypeStats)
                .printPrepTypeDistribution(printPrepDist)
                .build();
    }

    private Map<String, ActionTypeStats> calculateActionTypeStats(List<RegisterEntry> entries) {
        Map<String, List<RegisterEntry>> entriesByActionType = entries.stream()
                .collect(Collectors.groupingBy(RegisterEntry::getActionType));

        return entriesByActionType.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            List<RegisterEntry> actionTypeEntries = e.getValue();
                            return ActionTypeStats.builder()
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

    private void updateSessionDetails(TeamMember member) {
        WorkUsersSessionsStates session = dataAccessService.readNetworkSessionFile(
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

            LoggerUtil.info(this.getClass(),
                    "Original session status for " + member.getUsername() + ": " + originalStatus +
                            ", Normalized status: " + normalizedStatus);

            member.setSessionDetails(SessionDetails.builder()
                    .status(normalizedStatus)
                    .dayStartTime(session.getDayStartTime())
                    .dayEndTime(session.getDayEndTime())
                    .build());
        }
    }

    private void updateTimeOffList(TeamMember member, int year, int month) {
        // Load work time entries to get time off information
        List<WorkTimeTable> worktime = workTimeService.loadMonthWorktime(
                member.getUsername(), year, month);

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
        List<TimeOffEntry> coEntries = createTimeOffEntries("CO", timeOffDays.get("CO"));
        List<TimeOffEntry> cmEntries = createTimeOffEntries("CM", timeOffDays.get("CM"));
        List<TimeOffEntry> snEntries = createTimeOffEntries("SN", timeOffDays.get("SN"));

        member.setTimeOffList(TimeOffList.builder()
                .timeOffCO(coEntries)
                .timeOffCM(cmEntries)
                .timeOffSN(snEntries)
                .build());
    }

    private List<TimeOffEntry> createTimeOffEntries(String type, List<LocalDate> days) {
        if (days == null || days.isEmpty()) {
            return new ArrayList<>();
        }

        return List.of(TimeOffEntry.builder()
                .timeOffType(type)
                .days(new ArrayList<>(days))
                .build());
    }
}