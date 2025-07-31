// ============================================================================
// UNIVERSAL FINALIZATION UTILITY - Role-based bulk finalization
// ============================================================================

package com.ctgraphdep.merge.util;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.merge.engine.UniversalMergeEngine.UniversalMergeableEntity;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.util.List;
import java.util.function.Predicate;

/**
 * Universal utility for finalizing entries across all entity types.
 * Supports bulk operations based on user role and filtering criteria.
 */
public class UniversalFinalizationUtil {

    public enum UserRole {
        USER, ADMIN, TEAM
    }

    @Getter
    public static class FinalizationResult {
        private final int totalProcessed;
        private final int totalFinalized;
        private final int skippedAlreadyFinal;
        private final int skippedNotModifiable;

        public FinalizationResult(int totalProcessed, int totalFinalized, int skippedAlreadyFinal, int skippedNotModifiable) {
            this.totalProcessed = totalProcessed;
            this.totalFinalized = totalFinalized;
            this.skippedAlreadyFinal = skippedAlreadyFinal;
            this.skippedNotModifiable = skippedNotModifiable;
        }

        @Override
        public String toString() {
            return String.format("FinalizationResult[processed=%d, finalized=%d, skippedFinal=%d, skippedNotModifiable=%d]",
                    totalProcessed, totalFinalized, skippedAlreadyFinal, skippedNotModifiable);
        }
    }

    // ========================================================================
    // BULK FINALIZATION METHODS
    // ========================================================================

    /**
     * Finalize entries for specific user based on role.
     * Used for bulk user/month operations from controller.
     *
     * @param entries - list of any entity type
     * @param role - ADMIN or TEAM (USER cannot finalize)
     * @param filterPredicate - additional filtering (e.g., userId, month)
     * @param finalizedBy - username of person doing finalization
     * @return FinalizationResult with statistics
     */
    public static <T extends UniversalMergeableEntity> FinalizationResult finalizeEntries(
            List<T> entries,
            UserRole role,
            Predicate<T> filterPredicate,
            String finalizedBy) {

        if (role == UserRole.USER) {
            throw new IllegalArgumentException("USER role cannot finalize entries");
        }

        String finalStatus = role == UserRole.ADMIN ?
                MergingStatusConstants.ADMIN_FINAL :
                MergingStatusConstants.TEAM_FINAL;

        LoggerUtil.info(UniversalFinalizationUtil.class,
                String.format("Starting bulk finalization: role=%s, finalStatus=%s, finalizedBy=%s",
                        role, finalStatus, finalizedBy));

        int totalProcessed = 0;
        int totalFinalized = 0;
        int skippedAlreadyFinal = 0;
        int skippedNotModifiable = 0;

        for (T entry : entries) {
            if (!filterPredicate.test(entry)) {
                continue; // Skip entries that don't match filter
            }

            totalProcessed++;

            String currentStatus = entry.getUniversalStatus();

            // Skip if already finalized
            if (MergingStatusConstants.isFinalStatus(currentStatus)) {
                skippedAlreadyFinal++;
                LoggerUtil.debug(UniversalFinalizationUtil.class,
                        String.format("Skipping already final entry %s: %s", entry.getIdentifier(), currentStatus));
                continue;
            }

            // Check if entry can be modified (not in USER_IN_PROCESS)
            if (!canModifyEntry(entry)) {
                skippedNotModifiable++;
                LoggerUtil.debug(UniversalFinalizationUtil.class,
                        String.format("Skipping non-modifiable entry %s: %s", entry.getIdentifier(), currentStatus));
                continue;
            }

            // Finalize the entry
            entry.setUniversalStatus(finalStatus);
            totalFinalized++;

            LoggerUtil.debug(UniversalFinalizationUtil.class,
                    String.format("Finalized entry %s: %s → %s", entry.getIdentifier(), currentStatus, finalStatus));
        }

        FinalizationResult result = new FinalizationResult(totalProcessed, totalFinalized, skippedAlreadyFinal, skippedNotModifiable);

        LoggerUtil.info(UniversalFinalizationUtil.class,
                String.format("Bulk finalization completed by %s: %s", finalizedBy, result));

        return result;
    }

    /**
     * Finalize specific entries by identifier list
     */
    public static <T extends UniversalMergeableEntity> FinalizationResult finalizeSpecificEntries(
            List<T> entries,
            UserRole role,
            List<Object> identifiers,
            String finalizedBy) {

        return finalizeEntries(entries, role,
                entry -> identifiers.contains(entry.getIdentifier()),
                finalizedBy);
    }

    /**
     * Finalize all entries for a user (bulk user operation)
     */
    public static <T extends UniversalMergeableEntity> FinalizationResult finalizeUserEntries(
            List<T> entries,
            UserRole role,
            Integer userId,
            String finalizedBy) {

        return finalizeEntries(entries, role,
                entry -> matchesUserId(entry, userId),
                finalizedBy);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Check if entry can be modified (not final, not in process)
     */
    private static boolean canModifyEntry(UniversalMergeableEntity entry) {
        String status = entry.getUniversalStatus();

        // Cannot modify final entries
        return !MergingStatusConstants.isFinalStatus(status);
    }

    /**
     * Check if entry belongs to specific user.
     * Uses identifier parsing since different entities have different structures.
     */
    private static boolean matchesUserId(UniversalMergeableEntity entry, Integer userId) {
        Object identifier = entry.getIdentifier();

        if (identifier == null || userId == null) {
            return false;
        }

        String idStr = identifier.toString();

        // Handle different identifier formats:
        // WorkTime: "userId_date" 
        // Register: "userId_date_entryId"
        // CheckRegister: "entryId_date" (may not have userId)

        if (idStr.contains("_")) {
            String firstPart = idStr.split("_")[0];
            try {
                Integer entryUserId = Integer.parseInt(firstPart);
                return userId.equals(entryUserId);
            } catch (NumberFormatException e) {
                // First part is not userId (e.g., CheckRegister)
                return false;
            }
        }

        return false;
    }

    /**
     * Get appropriate final status for role
     */
    public static String getFinalStatusForRole(UserRole role) {
        return switch (role) {
            case ADMIN -> MergingStatusConstants.ADMIN_FINAL;
            case TEAM -> MergingStatusConstants.TEAM_FINAL;
            case USER -> throw new IllegalArgumentException("USER role cannot create final status");
        };
    }

    /**
     * Check if user role can finalize entries
     */
    public static boolean canFinalize(UserRole role) {
        return role == UserRole.ADMIN || role == UserRole.TEAM;
    }
}