package com.ctgraphdep.worktime.commands.team;

import com.ctgraphdep.model.dto.TeamMemberDTO;
import com.ctgraphdep.worktime.commands.WorktimeOperationCommand;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.utils.LoggerUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * REFACTORED: Command to load team members for a specific period.
 * Replaces TeamStatisticsService.getTeamMembers() method.
 * Returns team members for the specified team lead and period.
 * Team operations use context methods directly (no accessor needed).
 */
public class LoadTeamMembersCommand extends WorktimeOperationCommand<Object> {
    private final String teamLeadUsername;
    private final int year;
    private final int month;

    public LoadTeamMembersCommand(WorktimeOperationContext context,
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
        context.validateUserPermissions(teamLeadUsername, "load team members");

        // Additional validation: ensure current user is team lead
        String currentUsername = context.getCurrentUsername();
        if (!teamLeadUsername.equals(currentUsername)) {
            throw new SecurityException("Only the team lead can load team members");
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Validating team members load for team lead %s - %d/%d",
                teamLeadUsername, year, month));
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Loading team members for team lead %s - %d/%d",
                teamLeadUsername, year, month));

        try {
            // Load team members using context (no accessor needed for team operations)
            List<TeamMemberDTO> teamMembers = context.readTeamMembers(teamLeadUsername, year, month);

            // If no members found, return empty list instead of null
            if (teamMembers == null || teamMembers.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "No team members found for team lead %s for period %d/%d",
                        teamLeadUsername, year, month));

                return OperationResult.success(
                        String.format("No team members found for %s - %d/%d", teamLeadUsername, year, month),
                        getOperationType(),
                        teamMembers != null ? teamMembers : new ArrayList<>());
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully loaded %d team members for %s - %d/%d",
                    teamMembers.size(), teamLeadUsername, year, month));

            return OperationResult.success(
                    String.format("Loaded %d team members for %s - %d/%d",
                            teamMembers.size(), teamLeadUsername, year, month),
                    getOperationType(),
                    teamMembers);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading team members for %s - %d/%d: %s",
                    teamLeadUsername, year, month, e.getMessage()), e);
            return OperationResult.failure(
                    "Failed to load team members: " + e.getMessage(),
                    getOperationType());
        }
    }

    @Override
    protected String getCommandName() {
        return String.format("LoadTeamMembers[lead=%s, period=%d/%d]", teamLeadUsername, year, month);
    }

    @Override
    protected String getOperationType() {
        return "LOAD_TEAM_MEMBERS";
    }
}