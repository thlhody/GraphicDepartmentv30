package com.ctgraphdep.worktime.util;

import lombok.Getter;

/**
 * Result of status assignment operation from StatusAssignmentEngine
 * Contains:
 * - Success/failure status
 * - Original and new status values
 * - Descriptive message
 * - Protection information
 */
@Getter
public class StatusAssignmentResult {
    private final boolean success;
    private final String originalStatus;
    private final String newStatus;
    private final String message;
    private final boolean wasProtected;

    private StatusAssignmentResult(boolean success, String originalStatus, String newStatus,
                                   String message, boolean wasProtected) {
        this.success = success;
        this.originalStatus = originalStatus;
        this.newStatus = newStatus;
        this.message = message;
        this.wasProtected = wasProtected;
    }

    // Create successful status assignment result
    public static StatusAssignmentResult success(String originalStatus, String newStatus, String message) {
        return new StatusAssignmentResult(true, originalStatus, newStatus, message, false);
    }

    // Create protected status result (no change allowed)
    public static StatusAssignmentResult protectedStatus(String currentStatus, String reason) {
        return new StatusAssignmentResult(false, currentStatus, currentStatus, reason, true);
    }

    // Create error result
    public static StatusAssignmentResult error(String message) {
        return new StatusAssignmentResult(false, null, null, message, false);
    }

    // ========================================================================
    // GETTERS
    // ========================================================================

    public boolean wasProtected() {
        return wasProtected;
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    // Check if status was actually changed
    public boolean statusChanged() {
        if (!success) return false;
        if (originalStatus == null && newStatus == null) return false;
        if (originalStatus == null) return true;
        return !originalStatus.equals(newStatus);
    }

    // Get status change description
    public String getChangeDescription() {
        if (!success) {
            return wasProtected ? "Protected: " + message : "Error: " + message;
        }

        if (statusChanged()) {
            return String.format("Changed: %s → %s", originalStatus != null ? originalStatus : "null", newStatus != null ? newStatus : "null");
        }

        return "No change needed";
    }

    @Override
    public String toString() {
        return String.format("StatusAssignmentResult{success=%s, original='%s', new='%s', message='%s', protected=%s}",
                success, originalStatus, newStatus, message, wasProtected);
    }
}