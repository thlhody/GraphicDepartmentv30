package com.ctgraphdep.worktime.commands.team;

import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dto.TeamMemberDTO;
import com.ctgraphdep.model.dto.team.*;
import com.ctgraphdep.worktime.commands.WorktimeOperationCommand;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.utils.LoggerUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * Command to initialize team member entries for selected users.
 * Replaces TeamStatisticsService.initializeTeamMembers() method.
 * Creates initial team member DTOs and saves them to team data files.
 */
public class InitializeTeamMembersCommand extends WorktimeOperationCommand<Object> {
    private final List<Integer> selectedUserIds;
    private final String teamLeadUsername;
    private final int year;
    private final int month;

    public InitializeTeamMembersCommand(WorktimeOperationContext context,
                                        List<Integer> selectedUserIds,
                                        String teamLeadUsername,
                                        int year,
                                        int month) {
        super(context);
        this.selectedUserIds = selectedUserIds;
        this.teamLeadUsername = teamLeadUsername;
        this.year = year;
        this.month = month;
    }

    @Override
    protected void validate() {
        if (selectedUserIds == null || selectedUserIds.isEmpty()) {
            throw new IllegalArgumentException("Selected user IDs cannot be null or empty");
        }
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
        context.validateUserPermissions(teamLeadUsername, "initialize team members");

        // Additional validation: ensure current user is team lead
        String currentUsername = context.getCurrentUsername();
        if (!teamLeadUsername.equals(currentUsername)) {
            throw new SecurityException("Only the team lead can initialize team members");
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Validating team member initialization: %d users for team lead %s - %d/%d",
                selectedUserIds.size(), teamLeadUsername, year, month));
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Initializing team members: %d users for team lead %s - %d/%d",
                selectedUserIds.size(), teamLeadUsername, year, month));

        try {
            List<TeamMemberDTO> teamMemberDTOs = new ArrayList<>();

            // Create team member DTOs for each selected user
            for (Integer userId : selectedUserIds) {
                try {
                    Optional<User> userOpt = context.getUserById(userId);
                    if (userOpt.isEmpty()) {
                        LoggerUtil.warn(this.getClass(), String.format(
                                "User not found during team initialization: %d", userId));
                        continue;
                    }

                    User user = userOpt.get();
                    TeamMemberDTO member = createInitialTeamMember(user);
                    teamMemberDTOs.add(member);

                    LoggerUtil.debug(this.getClass(), String.format(
                            "Created initial team member: %s (%d)", user.getName(), userId));

                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), String.format(
                            "Error creating team member for user %d: %s", userId, e.getMessage()), e);
                    // Continue with other users
                }
            }

            if (teamMemberDTOs.isEmpty()) {
                return OperationResult.failure(
                        "No valid team members could be created from selected users",
                        getOperationType());
            }

            // Save team members using context
            context.writeTeamMembers(teamMemberDTOs, teamLeadUsername, year, month);

            String message = String.format("Successfully initialized %d team members for %s - %d/%d",
                    teamMemberDTOs.size(), teamLeadUsername, year, month);

            LoggerUtil.info(this.getClass(), message);

            return OperationResult.successWithSideEffects(message, getOperationType(), teamMemberDTOs, null);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error initializing team members for %s - %d/%d: %s",
                    teamLeadUsername, year, month, e.getMessage()), e);

            return OperationResult.failure(
                    "Failed to initialize team members: " + e.getMessage(),
                    getOperationType());
        }
    }

    /**
     * Create initial team member DTO with empty statistics
     */
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

    /**
     * Create initial register statistics with empty values
     */
    private TeamMemberRegisterStatsDTO createInitialRegisterStats() {
        return TeamMemberRegisterStatsDTO.builder()
                .monthSummaryDTO(new MonthSummaryDTO())
                .clientSpecificStats(new HashMap<>())
                .build();
    }

    @Override
    protected String getCommandName() {
        return String.format("InitializeTeamMembers[lead=%s, users=%d, period=%d/%d]",
                teamLeadUsername, selectedUserIds.size(), year, month);
    }

    @Override
    protected String getOperationType() {
        return "INITIALIZE_TEAM_MEMBERS";
    }
}