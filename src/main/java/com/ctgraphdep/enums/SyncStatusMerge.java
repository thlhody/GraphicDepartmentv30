package com.ctgraphdep.enums;

/**
 * Enhanced SyncStatusMerge enum to handle all register entry states in the merge system.
 *
 * Status Flow:
 * USER_INPUT → USER_DONE (admin approves)
 * USER_DONE → USER_EDITED (user modifies after approval)
 * ADMIN_EDITED → USER_DONE (admin finalizes changes)
 * Conflicts → ADMIN_CHECK (requires admin review)
 * ADMIN_BLANK → Remove entry
 */
public enum SyncStatusMerge {
    /**
     * Admin has modified this entry - takes precedence in merges
     */
    ADMIN_EDITED,

    /**
     * User has created/modified this entry - needs admin review
     */
    USER_INPUT,

    /**
     * Admin has approved/finalized this entry - stable state
     */
    USER_DONE,

    /**
     * Admin has marked this entry for removal - triggers deletion in user file
     */
    ADMIN_BLANK,

    /**
     * Entry is being processed by user - not available for admin sync
     */
    USER_IN_PROCESS,

    /**
     * User has modified an already approved entry - needs admin attention
     */
    USER_EDITED,

    /**
     * Conflict detected between user and admin changes - requires manual review
     */
    ADMIN_CHECK
}