// ============================================================================
// UNIVERSAL MERGE SERVICE - High-Level Operations
// ============================================================================

package com.ctgraphdep.merge.service;

import com.ctgraphdep.merge.engine.UniversalMergeEngine;
import com.ctgraphdep.merge.engine.UniversalMergeEngine.UniversalMergeableEntity;
import com.ctgraphdep.merge.enums.EntityType;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Universal Merge Service - High-level merge operations for all entity types.
 * Provides:
 * - User login merges (admin file → user file)
 * - Admin consolidation (user files → admin file)
 * - Team checking merges (user file → team file)
 * - Bulk operations and finalization
 * - Delete handling and cleanup
 */
@Service
public class UniversalMergeService {

    // ========================================================================
    // USER LOGIN MERGE OPERATIONS
    // ========================================================================

    /**
     * Perform user login merge for any entity type
     */
    public <T extends UniversalMergeableEntity> List<T> performUserLoginMerge(
            List<T> userEntries,
            List<T> adminEntries,
            EntityType entityType,
            String username) {

        try {
            LoggerUtil.info(this.getClass(),
                    String.format("Starting user login merge [%s] for %s", entityType, username));

            List<T> mergedEntries = mergeEntryLists(userEntries, adminEntries, entityType,
                    MergeDirection.ADMIN_TO_USER);

            LoggerUtil.info(this.getClass(),
                    String.format("User login merge [%s] completed for %s: %d entries",
                            entityType, username, mergedEntries.size()));

            return mergedEntries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error during user login merge [%s] for %s: %s",
                            entityType, username, e.getMessage()), e);
            throw new RuntimeException("User login merge failed", e);
        }
    }

    /**
     * Perform admin consolidation for any entity type
     */
    public <T extends UniversalMergeableEntity> List<T> performAdminConsolidation(
            List<List<T>> allUserEntries,
            List<T> currentAdminEntries,
            EntityType entityType) {

        try {
            LoggerUtil.info(this.getClass(),
                    String.format("Starting admin consolidation [%s]", entityType));

            // Flatten all user entries
            List<T> consolidatedUserEntries = allUserEntries.stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            List<T> consolidatedEntries = mergeEntryLists(consolidatedUserEntries,
                    currentAdminEntries, entityType, MergeDirection.USER_TO_ADMIN);

            LoggerUtil.info(this.getClass(),
                    String.format("Admin consolidation [%s] completed: %d entries",
                            entityType, consolidatedEntries.size()));

            return consolidatedEntries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error during admin consolidation [%s]: %s", entityType, e.getMessage()), e);
            throw new RuntimeException("Admin consolidation failed", e);
        }
    }

    /**
     * Perform team checking merge
     */
    public <T extends UniversalMergeableEntity> List<T> performTeamCheckingMerge(
            List<T> userEntries,
            List<T> teamEntries,
            EntityType entityType,
            String teamLeaderName) {

        try {
            LoggerUtil.info(this.getClass(),
                    String.format("Starting team checking merge [%s] by %s", entityType, teamLeaderName));

            List<T> mergedEntries = mergeEntryLists(userEntries, teamEntries, entityType,
                    MergeDirection.TEAM_CHECKING);

            LoggerUtil.info(this.getClass(),
                    String.format("Team checking merge [%s] completed by %s: %d entries",
                            entityType, teamLeaderName, mergedEntries.size()));

            return mergedEntries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error during team checking merge [%s] by %s: %s",
                            entityType, teamLeaderName, e.getMessage()), e);
            throw new RuntimeException("Team checking merge failed", e);
        }
    }

    // ========================================================================
    // CORE MERGE LOGIC
    // ========================================================================

    /**
     * Universal merge logic for any entity type
     */
    private <T extends UniversalMergeableEntity> List<T> mergeEntryLists(
            List<T> primaryEntries,
            List<T> secondaryEntries,
            EntityType entityType,
            MergeDirection direction) {

        LoggerUtil.debug(this.getClass(),
                String.format("Merging entry lists [%s]: %d primary, %d secondary, direction=%s",
                        entityType,
                        primaryEntries != null ? primaryEntries.size() : 0,
                        secondaryEntries != null ? secondaryEntries.size() : 0,
                        direction));

        // Create maps for efficient lookup by identifier
        Map<Object, T> primaryMap = createEntriesMap(primaryEntries);
        Map<Object, T> secondaryMap = createEntriesMap(secondaryEntries);

        // Get all unique identifiers
        Set<Object> allIdentifiers = new HashSet<>();
        allIdentifiers.addAll(primaryMap.keySet());
        allIdentifiers.addAll(secondaryMap.keySet());

        List<T> mergedEntries = new ArrayList<>();
        int mergeCount = 0;
        int deleteCount = 0;

        for (Object identifier : allIdentifiers) {
            T primaryEntry = primaryMap.get(identifier);
            T secondaryEntry = secondaryMap.get(identifier);

            // Apply universal merge logic
            T mergedEntry = UniversalMergeEngine.merge(primaryEntry, secondaryEntry, entityType);

            if (mergedEntry != null) {
                mergedEntries.add(mergedEntry);
                mergeCount++;
            } else {
                deleteCount++;
                LoggerUtil.debug(this.getClass(),
                        String.format("Entry %s deleted during merge", identifier));
            }
        }

        LoggerUtil.debug(this.getClass(),
                String.format("Merge statistics [%s]: %d merged, %d deleted",
                        entityType, mergeCount, deleteCount));

        return mergedEntries;
    }

    /**
     * Create map of entries by identifier
     */
    private <T extends UniversalMergeableEntity> Map<Object, T> createEntriesMap(List<T> entries) {
        if (entries == null) {
            return new HashMap<>();
        }

        return entries.stream()
                .collect(Collectors.toMap(
                        UniversalMergeableEntity::getIdentifier,
                        entry -> entry,
                        (existing, replacement) -> {
                            // Use universal merge to resolve duplicates
                            return existing; // Keep first occurrence
                        }
                ));
    }

    // ========================================================================
    // STATUS MANAGEMENT OPERATIONS
    // ========================================================================

//    /**
//     * Mark entry as edited with versioned timestamp
//     */
//    public <T extends UniversalMergeableEntity> void markAsEdited(T entry, String modifiedBy) {
//        if (entry == null) return;
//
//        String editedStatus = UniversalStatusHandler.createVersionedStatus();
//        entry.setUniversalStatus(editedStatus);
//
//        LoggerUtil.info(this.getClass(),
//                String.format("Marked entry %s as edited by %s: %s",
//                        entry.getIdentifier(), modifiedBy, editedStatus));
//    }

    /**
     * Mark entries as finalized
     */
    public <T extends UniversalMergeableEntity> List<T> finalizeEntries(
            List<T> entries,
            String finalStatus,
            String finalizedBy) {

        if (entries == null) return new ArrayList<>();

        LoggerUtil.info(this.getClass(),
                String.format("Finalizing %d entries with status %s by %s",
                        entries.size(), finalStatus, finalizedBy));

        return entries.stream()
                .peek(entry -> entry.setUniversalStatus(finalStatus))
                .collect(Collectors.toList());
    }

    /**
     * Mark entry for deletion
     */
    public <T extends UniversalMergeableEntity> void markForDeletion(T entry, String deletedBy) {
        if (entry == null) return;

        entry.setUniversalStatus("DELETE");

        LoggerUtil.info(this.getClass(),
                String.format("Marked entry %s for deletion by %s",
                        entry.getIdentifier(), deletedBy));
    }

    /**
     * Check if entry can be modified
     */
    public <T extends UniversalMergeableEntity> boolean canModifyEntry(T entry) {
        if (entry == null) return false;

        String status = entry.getUniversalStatus();
        boolean canModify = !"ADMIN_FINAL".equals(status) && !"TEAM_FINAL".equals(status);

        LoggerUtil.debug(this.getClass(),
                String.format("Entry modification check for %s: status=%s, canModify=%s",
                        entry.getIdentifier(), status, canModify));

        return canModify;
    }

    /**
     * Merge direction for logging
     */
    private enum MergeDirection {
        ADMIN_TO_USER,     // User login merge
        USER_TO_ADMIN,     // Admin consolidation
        TEAM_CHECKING      // Team checking operations
    }
}