package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.StatusAssignmentEngine;
import com.ctgraphdep.worktime.util.StatusAssignmentResult;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * RemoveCommand - Handles removal/reset of worktime entries
 * RULES FOR NON-ADMIN ROLES:
 * 1. If timeOffType=null:
 *    - User sets start=0 and end=0 → setAdminSync = DELETE
 *    - Any other combination → setAdminSync = EDITED (based on role)
 * 2. If timeOffType=CO/CM/SN:
 *    - User sets start=0 and end=0 → keep timeOffType, reset other values to 0, setAdminSync = EDITED (based on role)
 * RULES FOR ADMIN:
 * - Admin can delete anything → setAdminSync = DELETE
 * - Reset all values to 0/null and set DELETE status
 */
public class RemoveCommand extends WorktimeOperationCommand<WorkTimeTable> {

    private final String username;
    private final LocalDate date;
    private final boolean isAdminDelete; // True if admin is using delete button

    public RemoveCommand(WorktimeOperationContext context, WorktimeDataAccessor accessor,
                         String username, LocalDate date, boolean isAdminDelete) {
        super(context);
        this.accessor = accessor;
        this.username = username;
        this.date = date;
        this.isAdminDelete = isAdminDelete;
    }

    private final WorktimeDataAccessor accessor;

    @Override
    protected void validate() {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (context.getCurrentUser() == null) {
            throw new IllegalArgumentException("Current user context is required");
        }
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Executing RemoveCommand for %s on %s (adminDelete: %s)", username, date, isAdminDelete));

        try {
            // Load existing entries
            int year = date.getYear();
            int month = date.getMonthValue();
            List<WorkTimeTable> entries = accessor.readWorktime(username, year, month);

            // Find the entry to remove/reset
            Optional<WorkTimeTable> existingEntryOpt = findEntryByDate(entries,
                    context.getCurrentUser().getUserId(), date);

            if (existingEntryOpt.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "No entry found for %s on %s - nothing to remove", username, date));
                return OperationResult.failure("No entry found to remove", getOperationType());
            }

            WorkTimeTable entry = existingEntryOpt.get();
            String originalTimeOffType = entry.getTimeOffType();
            String userRole = context.getCurrentUser().getRole();

            LoggerUtil.debug(this.getClass(), String.format(
                    "Found entry to remove: timeOffType=%s, start=%s, end=%s, adminSync=%s",
                    originalTimeOffType,
                    entry.getDayStartTime() != null ? entry.getDayStartTime().toLocalTime() : "null",
                    entry.getDayEndTime() != null ? entry.getDayEndTime().toLocalTime() : "null",
                    entry.getAdminSync()));

            // Apply removal rules based on user role and entry type
            RemovalResult removalResult = applyRemovalRules(entry, userRole, isAdminDelete);

            // Assign status using StatusAssignmentEngine
            StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(
                    entry, userRole, removalResult.getOperationType());

            if (!statusResult.isSuccess()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Status assignment failed: %s", statusResult.getMessage()));
                return OperationResult.failure("Cannot remove entry: " + statusResult.getMessage(), getOperationType());
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Status assigned: %s → %s", statusResult.getOriginalStatus(), statusResult.getNewStatus()));

            // Replace entry in list
            replaceEntry(entries, entry);

            // Save using accessor
            accessor.writeWorktimeWithStatus(username, entries, year, month, userRole);

            // Create success message
            String message = createSuccessMessage(removalResult, originalTimeOffType, statusResult);

            // Create side effects tracking
            OperationResult.OperationSideEffects sideEffects = OperationResult.OperationSideEffects.builder()
                    .fileUpdated(createFilePathId(username, year, month))
                    .build();

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully executed removal for %s on %s: %s", username, date, message));

            return OperationResult.successWithSideEffects(message, getOperationType(), entry, sideEffects);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error executing removal for %s on %s: %s", username, date, e.getMessage()), e);
            return OperationResult.failure("Failed to remove entry: " + e.getMessage(), getOperationType());
        }
    }

    /**
     * Apply removal rules based on user role and entry characteristics
     */
    private RemovalResult applyRemovalRules(WorkTimeTable entry, String userRole, boolean isAdminDelete) {

        // ADMIN DELETE BUTTON - Reset everything and mark for deletion
        if (isAdminDelete && SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(userRole)) {
            LoggerUtil.debug(this.getClass(), "Applying admin delete button rules");

            resetAllFieldsExceptDate(entry);
            return RemovalResult.deleteEntry("Admin delete button - complete removal");
        }

        // NON-ADMIN RULES
        String timeOffType = entry.getTimeOffType();

        if (timeOffType == null || timeOffType.trim().isEmpty()) {
            // Rule 1: timeOffType=null - Reset all fields and mark for deletion
            LoggerUtil.debug(this.getClass(), "Applying rules for regular work day (no timeOffType)");

            resetAllFieldsExceptDate(entry);

            return RemovalResult.deleteEntry("Regular work day removed - all fields reset");

        } else {
            // Rule 2: timeOffType=CO/CM/SN - Reset all fields except timeOffType
            LoggerUtil.debug(this.getClass(), String.format(
                    "Applying rules for special day removal (timeOffType=%s)", timeOffType));

            // Keep timeOffType and date, reset everything else
            String preservedTimeOffType = entry.getTimeOffType();
            resetAllFieldsExceptDate(entry);
            entry.setTimeOffType(preservedTimeOffType); // Restore timeOffType

            return RemovalResult.resetSpecialDay("Special day work times reset - timeOffType preserved");
        }
    }

    /**
     * Reset all fields except date (for both admin and user operations)
     */
    private void resetAllFieldsExceptDate(WorkTimeTable entry) {
        // Reset all fields except userId, workDate, and adminSync (handled by StatusAssignmentEngine)
        entry.setDayStartTime(null);
        entry.setDayEndTime(null);
        entry.setTimeOffType(null);  // Will be restored for special days
        entry.setTemporaryStopCount(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTotalWorkedMinutes(0);
        entry.setTotalOvertimeMinutes(0);
        entry.setLunchBreakDeducted(false);

        LoggerUtil.debug(this.getClass(), "All fields reset except date (userId: "+entry.getUserId()+" workDate: "+ entry.getWorkDate()+" preserved)");
    }

    /**
     * Create success message based on removal result
     */
    private String createSuccessMessage(RemovalResult removalResult, String originalTimeOffType, StatusAssignmentResult statusResult) {

        StringBuilder message = new StringBuilder();

        if (removalResult.isAdminDelete()) {
            message.append("Admin deleted entire entry");
        } else if (originalTimeOffType != null) {
            message.append(String.format("Reset all fields for %s day - timeOffType preserved", originalTimeOffType));
        } else {
            message.append("Removed regular work day entry - all fields reset");
        }

        if (statusResult.statusChanged()) {
            message.append(String.format(" [Status: %s]", statusResult.getNewStatus()));
        }

        return message.toString();
    }

    @Override
    protected String getCommandName() {
        return String.format("Remove[%s, %s, adminDelete=%s]", username, date, isAdminDelete);
    }

    @Override
    protected String getOperationType() {
        if (isAdminDelete) {
            return "ADMIN_DELETE"; // Custom operation type for admin delete button
        }
        return "REMOVE_ENTRY"; // Custom operation type for user remove
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    private Optional<WorkTimeTable> findEntryByDate(List<WorkTimeTable> entries, Integer userId, LocalDate date) {
        return entries.stream()
                .filter(entry -> userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate()))
                .findFirst();
    }

    private void replaceEntry(List<WorkTimeTable> entries, WorkTimeTable updatedEntry) {
        entries.removeIf(entry ->
                updatedEntry.getUserId().equals(entry.getUserId()) &&
                        updatedEntry.getWorkDate().equals(entry.getWorkDate())
        );
        entries.add(updatedEntry);
        entries.sort(Comparator.comparing(WorkTimeTable::getWorkDate)
                .thenComparingInt(WorkTimeTable::getUserId));
    }

    private String createFilePathId(String username, int year, int month) {
        return String.format("%s/%d/%d", username, year, month);
    }

    // ========================================================================
    // RESULT CLASS
    // ========================================================================

    /**
     * Result of removal operation analysis
     */
    @Getter
    private static class RemovalResult {
        private final String operationType;
        private final String description;
        private final boolean isAdminDelete;

        private RemovalResult(String operationType, String description, boolean isAdminDelete) {
            this.operationType = operationType;
            this.description = description;
            this.isAdminDelete = isAdminDelete;
        }

        public static RemovalResult deleteEntry(String description) {
            return new RemovalResult("DELETE_ENTRY", description, false);
        }

        public static RemovalResult resetSpecialDay(String description) {
            return new RemovalResult("RESET_SPECIAL_DAY", description, false);
        }

    }
}