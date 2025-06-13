package com.ctgraphdep.worktime.service;

import com.ctgraphdep.model.dto.TeamMemberDTO;
import com.ctgraphdep.worktime.commands.team.InitializeTeamMembersCommand;
import com.ctgraphdep.worktime.commands.team.LoadTeamMembersCommand;
import com.ctgraphdep.worktime.commands.team.UpdateTeamStatisticsCommand;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for team operations using the command pattern.
 * Replaces TeamStatisticsService with command-based approach.
 * Coordinates team leader operations for managing team statistics.
 */
@Service
public class TeamOperationService {

    private final WorktimeOperationContext context;

    public TeamOperationService(WorktimeOperationContext context) {
        this.context = context;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Initialize team members for selected users
     * @param selectedUserIds List of user IDs to add to team
     * @param teamLeadUsername Username of the team leader creating the team
     * @param year Year for the statistics
     * @param month Month for the statistics
     * @return Operation result with success/failure details
     */
    public OperationResult initializeTeamMembers(List<Integer> selectedUserIds, String teamLeadUsername, int year, int month) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Initializing team members: %d users for team lead %s - %d/%d",
                    selectedUserIds.size(), teamLeadUsername, year, month));

            return new InitializeTeamMembersCommand(context, selectedUserIds, teamLeadUsername, year, month).execute();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error during team member initialization for %s: %s", teamLeadUsername, e.getMessage()), e);
            return OperationResult.failure("Failed to initialize team members: " + e.getMessage(), "INITIALIZE_TEAM_MEMBERS");
        }
    }

    /**
     * Update statistics for all team members
     * @param teamLeadUsername Username of the team leader
     * @param year Year to update statistics for
     * @param month Month to update statistics for
     * @return Operation result with success/failure details
     */
    public OperationResult updateTeamStatistics(String teamLeadUsername, int year, int month) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Updating team statistics for team lead %s - %d/%d",
                    teamLeadUsername, year, month));

            return new UpdateTeamStatisticsCommand(context, teamLeadUsername, year, month).execute();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error during team statistics update for %s: %s", teamLeadUsername, e.getMessage()), e);
            return OperationResult.failure("Failed to update team statistics: " + e.getMessage(), "UPDATE_TEAM_STATISTICS");
        }
    }

    /**
     * Load team members for a specific period
     * @param teamLeadUsername Username of the team leader
     * @param year Year to get members for
     * @param month Month to get members for
     * @return Operation result with team members data
     */
    public OperationResult loadTeamMembers(String teamLeadUsername, int year, int month) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Loading team members for team lead %s - %d/%d",
                    teamLeadUsername, year, month));

            return new LoadTeamMembersCommand(context, teamLeadUsername, year, month).execute();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error during team members load for %s: %s", teamLeadUsername, e.getMessage()), e);
            return OperationResult.failure("Failed to load team members: " + e.getMessage(), "LOAD_TEAM_MEMBERS");
        }
    }

    /**
     * Check if team has been initialized for the given period
     * @param teamLeadUsername Username of the team leader
     * @param year Year to check
     * @param month Month to check
     * @return true if team members exist for the period
     */
    public boolean hasTeamMembers(String teamLeadUsername, int year, int month) {
        try {
            OperationResult result = loadTeamMembers(teamLeadUsername, year, month);
            if (result.isSuccess()) {
                List<TeamMemberDTO> members = result.getTeamMembersData();
                return members != null && !members.isEmpty();
            }
            return false;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error checking team members existence for %s - %d/%d: %s",
                    teamLeadUsername, year, month, e.getMessage()));
            return false;
        }
    }

    /**
     * Get team member count for the given period
     * @param teamLeadUsername Username of the team leader
     * @param year Year to check
     * @param month Month to check
     * @return Number of team members, or 0 if none or error
     */
    public int getTeamMemberCount(String teamLeadUsername, int year, int month) {
        try {
            OperationResult result = loadTeamMembers(teamLeadUsername, year, month);
            if (result.isSuccess()) {
                List<TeamMemberDTO> members = result.getTeamMembersData();
                return members != null ? members.size() : 0;
            }
            return 0;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting team member count for %s - %d/%d: %s",
                    teamLeadUsername, year, month, e.getMessage()));
            return 0;
        }
    }
}