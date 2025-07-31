// ============================================================================
// UNIVERSAL STATUS ENUM - Handles ALL Entity Types
// ============================================================================

package com.ctgraphdep.merge.status;

/**
 * Universal Status Enum for ALL entity types in the merge system.
 * Supports hybrid approach:
 * - Base statuses (enum values)
 * - Versioned edits (string format: EDITED_[timestamp])
 * - Final states (enum values)
 * Priority Hierarchy:
 * 4. FINAL_STATES: ADMIN_FINAL, TEAM_FINAL (immutable)
 * 3. VERSIONED_EDITS: EDITED_[timestamp] (latest wins) 
 * 2. USER_PROTECTED: USER_IN_PROCESS (worktime only)
 * 1. BASE_INPUTS: USER_INPUT, ADMIN_INPUT, TEAM_INPUT
 */
public enum UniversalStatus {

    // ========================================================================
    // LEVEL 2: USER PROTECTED STATUS (Worktime Only)
    // ========================================================================

    /**
     * User is actively editing - only applies to worktime entities.
     * Cannot be overridden by any external sync.
     * Only user can change this to USER_INPUT when complete.
     */
    USER_IN_PROCESS,

    // ========================================================================
    // LEVEL 1: BASE INPUT STATUSES  
    // ========================================================================

    /**
     * User created/completed entry - ready for review.
     * Default state for all user-created entries.
     */
    USER_INPUT,

    /**
     * Admin created entry directly or made input-level changes.
     * Takes precedence over USER_INPUT in merges.
     */
    ADMIN_INPUT,

    /**
     * Team leader created entry or made input-level changes.
     * Priority: ADMIN_INPUT > TEAM_INPUT > USER_INPUT
     */
    TEAM_INPUT,

    // ========================================================================
    // LEVEL 4: FINAL STATUSES (Highest Priority)
    // ========================================================================

    /**
     * Admin finalized - absolutely immutable.
     * No further changes allowed by anyone.
     */
    ADMIN_FINAL,

    /**
     * Team leader finalized for their scope.
     * Can still be overridden by ADMIN_FINAL if needed.
     */
    TEAM_FINAL,

    // ========================================================================
    // SPECIAL STATUSES
    // ========================================================================

    /**
     * Marks entry for deletion.
     * Used internally by merge logic - not stored in entities.
     */
    DELETE;

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Check if this status represents a final state
     */
    public boolean isFinalState() {
        return this == ADMIN_FINAL || this == TEAM_FINAL;
    }

    /**
     * Check if this status represents a base input
     */
    public boolean isBaseInput() {
        return this == USER_INPUT || this == ADMIN_INPUT || this == TEAM_INPUT;
    }

    /**
     * Check if this status is user-protected (worktime only)
     */
    public boolean isUserProtected() {
        return this == USER_IN_PROCESS;
    }

    /**
     * Check if this status marks deletion
     */
    public boolean isDelete() {
        return this == DELETE;
    }

    /**
     * Get priority level for merge decisions
     */
    public int getPriorityLevel() {
        return switch (this) {
            case ADMIN_FINAL, TEAM_FINAL -> 4; // Final states
            case USER_IN_PROCESS -> 2; // User protected (worktime only)
            case USER_INPUT, ADMIN_INPUT, TEAM_INPUT -> 1; // Base inputs
            case DELETE -> -1; // Special handling
        };
    }

    /**
     * Get base input priority within same level
     */
    public int getBaseInputPriority() {
        return switch (this) {
            case ADMIN_INPUT -> 3;
            case TEAM_INPUT -> 2;
            case USER_INPUT -> 1;
            default -> 0;
        };
    }

    /**
     * Get display name for UI
     */
    public String getDisplayName() {
        return switch (this) {
            case USER_IN_PROCESS -> "In Progress";
            case USER_INPUT -> "User Input";
            case ADMIN_INPUT -> "Admin Input";
            case TEAM_INPUT -> "Team Input";
            case ADMIN_FINAL -> "Admin Finalized";
            case TEAM_FINAL -> "Team Finalized";
            case DELETE -> "Deleted";
        };
    }

    /**
     * Get CSS class for styling
     */
    public String getCssClass() {
        return switch (this) {
            case USER_IN_PROCESS -> "status-in-progress";
            case USER_INPUT -> "status-user-input";
            case ADMIN_INPUT -> "status-admin-input";
            case TEAM_INPUT -> "status-team-input";
            case ADMIN_FINAL -> "status-admin-final";
            case TEAM_FINAL -> "status-team-final";
            case DELETE -> "status-deleted";
        };
    }
}