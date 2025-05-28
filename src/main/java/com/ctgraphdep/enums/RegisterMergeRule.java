package com.ctgraphdep.enums;

import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.utils.LoggerUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * Simplified RegisterMergeRule focused on actual business logic:
 * - Admin superiority in conflicts
 * - Status-based quick filtering
 * - Content comparison with admin CG precedence
 * - Entry propagation and removal handling
 */

public enum RegisterMergeRule {

    // ========================================================================
    // USER LOGIN MERGE RULES (Admin file -> User file)
    // ========================================================================

    // Step 3 & 7: User merges admin decisions: ADMIN_EDITED → USER_DONE
    USER_LOGIN_ADMIN_EDITED_TO_DONE(
            (adminEntry, userEntry) -> adminEntry != null &&
                    SyncStatusMerge.ADMIN_EDITED.name().equals(adminEntry.getAdminSync()),
            (adminEntry, userEntry) -> {
                LoggerUtil.debug(RegisterMergeRule.class, String.format(
                        "User login merge: Entry %d ADMIN_EDITED → USER_DONE",
                        adminEntry.getEntryId()));
                return copyEntryWithStatus(adminEntry, SyncStatusMerge.USER_DONE);
            }
    ),

    // Admin marked for removal → remove from user (keep for system compatibility)
    USER_LOGIN_ADMIN_REMOVAL(
            (adminEntry, userEntry) -> adminEntry != null &&
                    SyncStatusMerge.ADMIN_BLANK.name().equals(adminEntry.getAdminSync()),
            (adminEntry, userEntry) -> {
                LoggerUtil.debug(RegisterMergeRule.class, String.format(
                        "User login merge: Entry %d marked for removal",
                        adminEntry != null ? adminEntry.getEntryId() : 0));
                return null; // Remove entry
            }
    ),

    // New entry from admin → propagate to user
    USER_LOGIN_NEW_ADMIN_ENTRY(
            (adminEntry, userEntry) -> adminEntry != null && userEntry == null,
            (adminEntry, userEntry) -> {
                LoggerUtil.debug(RegisterMergeRule.class, String.format(
                        "User login merge: New admin entry %d propagated to user",
                        adminEntry.getEntryId()));
                return adminEntry;
            }
    ),

    // ========================================================================
    // ADMIN LOAD MERGE RULES (User file -> Admin file)
    // ========================================================================

    // New entry from user → propagate to admin as USER_INPUT
    ADMIN_LOAD_NEW_USER_ENTRY(
            (userEntry, adminEntry) -> userEntry != null && adminEntry == null,
            (userEntry, adminEntry) -> {
                LoggerUtil.debug(RegisterMergeRule.class, String.format(
                        "Admin load merge: New user entry %d → USER_INPUT",
                        userEntry.getEntryId()));
                return copyEntryWithStatus(userEntry, SyncStatusMerge.USER_INPUT);
            }
    ),

    // Entry removed by user → remove from admin
    ADMIN_LOAD_USER_REMOVAL(
            (userEntry, adminEntry) -> userEntry == null && adminEntry != null,
            (userEntry, adminEntry) -> {
                LoggerUtil.debug(RegisterMergeRule.class, String.format(
                        "Admin load merge: User removed entry %d",
                        adminEntry.getEntryId()));
                return null; // Remove entry
            }
    ),

    // Admin has resolved a conflict (ADMIN_EDITED) vs User still has edits (USER_EDITED) → Admin wins
    ADMIN_LOAD_ADMIN_RESOLVED_VS_USER_EDITED(
            (userEntry, adminEntry) -> userEntry != null && adminEntry != null &&
                    SyncStatusMerge.USER_EDITED.name().equals(userEntry.getAdminSync()) &&
                    SyncStatusMerge.ADMIN_EDITED.name().equals(adminEntry.getAdminSync()),
            (userEntry, adminEntry) -> {
                LoggerUtil.info(RegisterMergeRule.class, String.format(
                        "Admin load merge: Entry %d admin resolved conflict (ADMIN_EDITED) vs user edits (USER_EDITED) - keeping admin decision",
                        adminEntry.getEntryId()));
                return adminEntry; // Keep admin's resolved decision
            }
    ),

    // Step 5: User edited approved entry → Admin needs to check: USER_EDITED → ADMIN_CHECK
    ADMIN_LOAD_USER_EDITED_TO_CHECK(
            (userEntry, adminEntry) -> userEntry != null && adminEntry != null &&
                    SyncStatusMerge.USER_EDITED.name().equals(userEntry.getAdminSync()),
            (userEntry, adminEntry) -> {
                LoggerUtil.info(RegisterMergeRule.class, String.format(
                        "Admin load merge: Entry %d USER_EDITED → ADMIN_CHECK (needs admin review)",
                        userEntry.getEntryId()));
                return copyEntryWithStatus(userEntry, SyncStatusMerge.ADMIN_CHECK);
            }
    ),

    // Both USER_DONE and stable → no change needed
    ADMIN_LOAD_BOTH_STABLE(
            (userEntry, adminEntry) -> userEntry != null && adminEntry != null &&
                    SyncStatusMerge.USER_DONE.name().equals(userEntry.getAdminSync()) &&
                    SyncStatusMerge.USER_DONE.name().equals(adminEntry.getAdminSync()),
            (userEntry, adminEntry) -> {
                LoggerUtil.debug(RegisterMergeRule.class, String.format(
                        "Admin load merge: Entry %d both USER_DONE - stable, no change",
                        adminEntry.getEntryId()));
                return adminEntry; // Keep admin version
            }
    ),

    // Admin has ADMIN_EDITED, user has USER_DONE → content comparison
    ADMIN_LOAD_ADMIN_EDITED_VS_USER_DONE(
            (userEntry, adminEntry) -> userEntry != null && adminEntry != null &&
                    SyncStatusMerge.USER_DONE.name().equals(userEntry.getAdminSync()) &&
                    SyncStatusMerge.ADMIN_EDITED.name().equals(adminEntry.getAdminSync()),
            (userEntry, adminEntry) -> {
                // Check if content matches (with admin CG precedence)
                if (entriesAreContentEqual(adminEntry, userEntry)) {
                    LoggerUtil.info(RegisterMergeRule.class, String.format(
                            "Admin load merge: Entry %d content matches - synchronized, changing ADMIN_EDITED → USER_DONE",
                            adminEntry.getEntryId()));
                    return copyEntryWithStatus(adminEntry, SyncStatusMerge.USER_DONE); // FIX: Change to USER_DONE when synchronized
                } else {
                    LoggerUtil.info(RegisterMergeRule.class, String.format(
                            "Admin load merge: Entry %d content differs - flagging for ADMIN_CHECK",
                            adminEntry.getEntryId()));
                    return copyEntryWithStatus(adminEntry, SyncStatusMerge.ADMIN_CHECK);
                }
            }
    ),

    // User has USER_INPUT, admin exists → check if already processed
    ADMIN_LOAD_USER_INPUT_CHECK(
            (userEntry, adminEntry) -> userEntry != null && adminEntry != null &&
                    SyncStatusMerge.USER_INPUT.name().equals(userEntry.getAdminSync()),
            (userEntry, adminEntry) -> {
                // If admin already has ADMIN_EDITED for this entry, it was already processed
                if (SyncStatusMerge.ADMIN_EDITED.name().equals(adminEntry.getAdminSync())) {
                    LoggerUtil.debug(RegisterMergeRule.class, String.format(
                            "Admin load merge: Entry %d already processed - keeping ADMIN_EDITED",
                            adminEntry.getEntryId()));
                    return adminEntry;
                } else {
                    LoggerUtil.debug(RegisterMergeRule.class, String.format(
                            "Admin load merge: Entry %d USER_INPUT update",
                            userEntry.getEntryId()));
                    return copyEntryWithStatus(userEntry, SyncStatusMerge.USER_INPUT);
                }
            }
    ),

    // ========================================================================
    // DEFAULT FALLBACK RULES
    // ========================================================================

    USER_LOGIN_DEFAULT(
            (adminEntry, userEntry) -> userEntry != null,
            (adminEntry, userEntry) -> {
                LoggerUtil.debug(RegisterMergeRule.class, String.format(
                        "User login merge: Entry %d using default - keeping user version",
                        userEntry.getEntryId()));
                return userEntry;
            }
    ),

    ADMIN_LOAD_DEFAULT(
            (userEntry, adminEntry) -> adminEntry != null,
            (userEntry, adminEntry) -> {
                LoggerUtil.debug(RegisterMergeRule.class, String.format(
                        "Admin load merge: Entry %d using default - keeping admin version",
                        adminEntry.getEntryId()));
                return adminEntry;
            }
    );

    private final BiPredicate<RegisterEntry, RegisterEntry> condition;
    private final BiFunction<RegisterEntry, RegisterEntry, RegisterEntry> action;

    RegisterMergeRule(BiPredicate<RegisterEntry, RegisterEntry> condition,
                      BiFunction<RegisterEntry, RegisterEntry, RegisterEntry> action) {
        this.condition = condition;
        this.action = action;
    }

    // ========================================================================
    // PUBLIC API METHODS (unchanged)
    // ========================================================================

    /**
     * Apply user login merge rules (Admin file -> User file)
     */
    public static RegisterEntry applyUserLoginMerge(RegisterEntry adminEntry, RegisterEntry userEntry) {
        return Arrays.stream(values())
                .filter(rule -> rule.name().startsWith("USER_LOGIN_"))
                .filter(rule -> rule.condition.test(adminEntry, userEntry))
                .findFirst()
                .map(rule -> rule.action.apply(adminEntry, userEntry))
                .orElse(userEntry); // Default: keep user entry
    }

    /**
     * Apply admin load merge rules (User file -> Admin file)
     */
    public static RegisterEntry applyAdminLoadMerge(RegisterEntry userEntry, RegisterEntry adminEntry) {
        return Arrays.stream(values())
                .filter(rule -> rule.name().startsWith("ADMIN_LOAD_"))
                .filter(rule -> rule.condition.test(userEntry, adminEntry))
                .findFirst()
                .map(rule -> rule.action.apply(userEntry, adminEntry))
                .orElse(adminEntry); // Default: keep admin entry
    }

    // ========================================================================
    // UTILITY METHODS (unchanged but with logging)
    // ========================================================================

    /**
     * Content comparison with admin CG precedence
     */
    private static boolean entriesAreContentEqual(RegisterEntry admin, RegisterEntry user) {
        if (admin == null || user == null) return false;

        // Create admin-truth version (admin CG wins)
        RegisterEntry adminTruthUser = copyEntryWithAdminCG(user, admin.getGraphicComplexity());

        // Compare all fields except adminSync status
        boolean isEqual = Objects.equals(admin.getDate(), adminTruthUser.getDate()) &&
                Objects.equals(admin.getOrderId(), adminTruthUser.getOrderId()) &&
                Objects.equals(admin.getProductionId(), adminTruthUser.getProductionId()) &&
                Objects.equals(admin.getOmsId(), adminTruthUser.getOmsId()) &&
                Objects.equals(admin.getClientName(), adminTruthUser.getClientName()) &&
                Objects.equals(admin.getActionType(), adminTruthUser.getActionType()) &&
                Objects.equals(admin.getPrintPrepTypes(), adminTruthUser.getPrintPrepTypes()) &&
                Objects.equals(admin.getColorsProfile(), adminTruthUser.getColorsProfile()) &&
                Objects.equals(admin.getArticleNumbers(), adminTruthUser.getArticleNumbers()) &&
                Objects.equals(admin.getGraphicComplexity(), adminTruthUser.getGraphicComplexity()) &&
                Objects.equals(admin.getObservations(), adminTruthUser.getObservations());

        LoggerUtil.debug(RegisterMergeRule.class, String.format(
                "Content comparison for entry %d: %s",
                admin.getEntryId(), isEqual ? "EQUAL" : "DIFFERENT"));

        return isEqual;
    }

    /**
     * Create a copy of entry with new sync status
     */
    private static RegisterEntry copyEntryWithStatus(RegisterEntry source, SyncStatusMerge newStatus) {
        if (source == null) return null;

        return RegisterEntry.builder()
                .entryId(source.getEntryId())
                .userId(source.getUserId())
                .date(source.getDate())
                .orderId(source.getOrderId())
                .productionId(source.getProductionId())
                .omsId(source.getOmsId())
                .clientName(source.getClientName())
                .actionType(source.getActionType())
                .printPrepTypes(source.getPrintPrepTypes() != null ?
                        List.copyOf(source.getPrintPrepTypes()) : null)
                .colorsProfile(source.getColorsProfile())
                .articleNumbers(source.getArticleNumbers())
                .graphicComplexity(source.getGraphicComplexity())
                .observations(source.getObservations())
                .adminSync(newStatus.name())
                .build();
    }

    /**
     * Create a copy of user entry with admin's CG value
     */
    private static RegisterEntry copyEntryWithAdminCG(RegisterEntry userEntry, Double adminCG) {
        if (userEntry == null) return null;

        return RegisterEntry.builder()
                .entryId(userEntry.getEntryId())
                .userId(userEntry.getUserId())
                .date(userEntry.getDate())
                .orderId(userEntry.getOrderId())
                .productionId(userEntry.getProductionId())
                .omsId(userEntry.getOmsId())
                .clientName(userEntry.getClientName())
                .actionType(userEntry.getActionType())
                .printPrepTypes(userEntry.getPrintPrepTypes() != null ?
                        List.copyOf(userEntry.getPrintPrepTypes()) : null)
                .colorsProfile(userEntry.getColorsProfile())
                .articleNumbers(userEntry.getArticleNumbers())
                .graphicComplexity(adminCG) // Use admin CG
                .observations(userEntry.getObservations())
                .adminSync(userEntry.getAdminSync())
                .build();
    }
}