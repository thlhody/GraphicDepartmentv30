package com.ctgraphdep.merge.engine;

import com.ctgraphdep.merge.enums.EntityType;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.utils.LoggerUtil;

import java.util.Arrays;

/**
 * Universal Merge Engine - Enhanced with proper admin-wins conflict resolution.
 * Core Principles:
 * 1. Priority-based decision tree (Final > Versioned > Protected > Base)
 * 2. Timestamp versioning for conflict resolution with admin-wins for identical timestamps
 * 3. Entity-type awareness (worktime has USER_IN_PROCESS, others don't)
 * 4. Universal delete handling
 * 5. Deterministic results for same inputs
 * NEW: Handles USER_EDITED_[epoch], ADMIN_EDITED_[epoch], TEAM_EDITED_[epoch] with proper conflict resolution
 */
public enum UniversalMergeEngine {

    // ========================================================================
    // LEVEL 4: FINAL STATE RULES (Highest Priority)
    // ========================================================================

    FINAL_STATE_ABSOLUTE(
            (entry1, entry2, entityType) -> isFinalState(entry1) || isFinalState(entry2),
            (entry1, entry2, entityType) -> {
                if (isFinalState(entry1) && isFinalState(entry2)) {
                    // Both final - ADMIN_FINAL beats TEAM_FINAL
                    String status1 = getStatusString(entry1);

                    if (MergingStatusConstants.ADMIN_FINAL.equals(status1)) {
                        LoggerUtil.debug(UniversalMergeEngine.class, "Final state rule: ADMIN_FINAL beats TEAM_FINAL");
                        return entry1;
                    } else {
                        LoggerUtil.debug(UniversalMergeEngine.class, "Final state rule: keeping TEAM_FINAL");
                        return entry2;
                    }
                } else if (isFinalState(entry1)) {
                    LoggerUtil.debug(UniversalMergeEngine.class, String.format("Final state rule: entry1 is final (%s)", getStatusString(entry1)));
                    return entry1;
                } else {
                    LoggerUtil.debug(UniversalMergeEngine.class, String.format("Final state rule: entry2 is final (%s)", getStatusString(entry2)));
                    return entry2;
                }
            }
    ),

    // ========================================================================
    // LEVEL 3: VERSIONED EDIT RULES (Enhanced with Admin-Wins)
    // ========================================================================

    VERSIONED_EDIT_COMPARISON(
            (entry1, entry2, entityType) -> isVersionedEdit(entry1) || isVersionedEdit(entry2),
            (entry1, entry2, entityType) -> {
                long timestamp1 = extractTimestamp(entry1);
                long timestamp2 = extractTimestamp(entry2);

                UniversalMergeableEntity winner;

                if (timestamp1 == timestamp2) {
                    // IDENTICAL TIMESTAMPS: Admin wins conflict resolution
                    winner = resolveTimestampConflict(entry1, entry2);

                    LoggerUtil.info(UniversalMergeEngine.class,
                            String.format("Timestamp conflict resolved: timestamp=%d, winner=%s (admin-wins rule)",
                                    timestamp1, getStatusString(winner)));
                } else {
                    // Different timestamps: newest wins
                    winner = timestamp1 > timestamp2 ? entry1 : entry2;

                    LoggerUtil.debug(UniversalMergeEngine.class,
                            String.format("Versioned edit rule: timestamp1=%d, timestamp2=%d, winner timestamp=%d",
                                    timestamp1, timestamp2, extractTimestamp(winner)));
                }

                return winner;
            }
    ),

    // ========================================================================
    // LEVEL 2: USER COMPLETION RULES (Worktime Only)
    // ========================================================================

    USER_INPUT_OVERRIDES_IN_PROCESS(
            (entry1, entry2, entityType) ->
                    entityType == EntityType.WORKTIME &&
                    ((isUserInput(entry1) && isUserInProcess(entry2)) ||
                     (isUserInProcess(entry1) && isUserInput(entry2))),
            (entry1, entry2, entityType) -> {
                UniversalMergeableEntity winner = isUserInput(entry1) ? entry1 : entry2;
                LoggerUtil.debug(UniversalMergeEngine.class,
                        "User completion rule: USER_INPUT overrides USER_IN_PROCESS (completed work beats in-progress)");
                return winner;
            }
    ),

    USER_IN_PROCESS_PROTECTION(
            (entry1, entry2, entityType) ->
                    entityType == EntityType.WORKTIME && (isUserInProcess(entry1) || isUserInProcess(entry2)) &&
                    !(isUserInput(entry1) || isUserInput(entry2)), // Don't protect if USER_INPUT is present
            (entry1, entry2, entityType) -> {
                if (isUserInProcess(entry1) && isUserInProcess(entry2)) {
                    LoggerUtil.warn(UniversalMergeEngine.class,
                            "Both entries USER_IN_PROCESS, keeping entry1");
                    return entry1;
                } else if (isUserInProcess(entry1)) {
                    LoggerUtil.debug(UniversalMergeEngine.class,
                            "User protection rule: entry1 is USER_IN_PROCESS (protected)");
                    return entry1;
                } else {
                    LoggerUtil.debug(UniversalMergeEngine.class,
                            "User protection rule: entry2 is USER_IN_PROCESS (protected)");
                    return entry2;
                }
            }
    ),

    // ========================================================================
    // LEVEL 1: BASE INPUT RULES
    // ========================================================================

    BASE_INPUT_HIERARCHY(
            (entry1, entry2, entityType) -> isBaseInput(entry1) && isBaseInput(entry2),
            (entry1, entry2, entityType) -> {
                int priority1 = getBaseInputPriority(entry1);
                int priority2 = getBaseInputPriority(entry2);

                UniversalMergeableEntity winner = priority1 >= priority2 ? entry1 : entry2;

                LoggerUtil.debug(UniversalMergeEngine.class,
                        String.format("Base input rule: %s(priority=%d) vs %s(priority=%d), winner=%s",
                                getStatusString(entry1), priority1,
                                getStatusString(entry2), priority2,
                                getStatusString(winner)));

                return winner;
            }
    ),

    // ========================================================================
    // MIXED PRIORITY RULES
    // ========================================================================

    VERSIONED_BEATS_BASE(
            (entry1, entry2, entityType) ->
                    (isVersionedEdit(entry1) && isBaseInput(entry2)) ||
                            (isBaseInput(entry1) && isVersionedEdit(entry2)),
            (entry1, entry2, entityType) -> {
                UniversalMergeableEntity winner = isVersionedEdit(entry1) ? entry1 : entry2;
                LoggerUtil.debug(UniversalMergeEngine.class,
                        "Mixed priority rule: versioned edit beats base input");
                return winner;
            }
    ),

    PROTECTED_BEATS_BASE(
            (entry1, entry2, entityType) ->
                    entityType == EntityType.WORKTIME &&
                            ((isUserInProcess(entry1) && isBaseInput(entry2) && !isUserInput(entry2)) ||
                                    (isBaseInput(entry1) && !isUserInput(entry1) && isUserInProcess(entry2))),
            (entry1, entry2, entityType) -> {
                UniversalMergeableEntity winner = isUserInProcess(entry1) ? entry1 : entry2;
                LoggerUtil.debug(UniversalMergeEngine.class,
                        "Mixed priority rule: USER_IN_PROCESS beats base input (excluding USER_INPUT)");
                return winner;
            }
    ),

    // ========================================================================
    // FALLBACK RULES
    // ========================================================================

    SINGLE_ENTRY_FALLBACK(
            (entry1, entry2, entityType) -> entry1 == null || entry2 == null,
            (entry1, entry2, entityType) -> {
                UniversalMergeableEntity result = entry1 != null ? entry1 : entry2;
                LoggerUtil.debug(UniversalMergeEngine.class,
                        String.format("Single entry fallback: returning %s", getStatusString(result)));
                return result;
            }
    ),

    DEFAULT_FALLBACK(
            (entry1, entry2, entityType) -> true,
            (entry1, entry2, entityType) -> {
                LoggerUtil.warn(UniversalMergeEngine.class,
                        String.format("Using default fallback for %s vs %s - returning entry1",
                                getStatusString(entry1), getStatusString(entry2)));
                return entry1;
            }
    );

    // ========================================================================
    // ENUM INFRASTRUCTURE
    // ========================================================================

    private final TriPredicate<UniversalMergeableEntity, UniversalMergeableEntity, EntityType> condition;
    private final TriFunction<UniversalMergeableEntity, UniversalMergeableEntity, EntityType, UniversalMergeableEntity> action;

    UniversalMergeEngine(
            TriPredicate<UniversalMergeableEntity, UniversalMergeableEntity, EntityType> condition,
            TriFunction<UniversalMergeableEntity, UniversalMergeableEntity, EntityType, UniversalMergeableEntity> action) {
        this.condition = condition;
        this.action = action;
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Universal merge method - works for ALL entity types
     */
    public static <T extends UniversalMergeableEntity> T merge(T entry1, T entry2, EntityType entityType) {
        LoggerUtil.debug(UniversalMergeEngine.class,
                String.format("Universal merge [%s]: entry1=%s, entry2=%s",
                        entityType, getStatusString(entry1), getStatusString(entry2)));

        if (entry1 == null && entry2 == null) {
            LoggerUtil.debug(UniversalMergeEngine.class, "Both entries null, returning null");
            return null;
        }

        @SuppressWarnings("unchecked")
        T result = (T) Arrays.stream(values())
                .filter(rule -> rule.condition.test(entry1, entry2, entityType))
                .findFirst()
                .map(rule -> rule.action.apply(entry1, entry2, entityType))
                .orElse(null);

        LoggerUtil.debug(UniversalMergeEngine.class,
                String.format("Merge result [%s]: %s", entityType, getStatusString(result)));

        return result;
    }

    // ========================================================================
    // ENHANCED HELPER METHODS
    // ========================================================================

    private static boolean isFinalState(UniversalMergeableEntity entry) {
        if (entry == null) return false;
        String status = entry.getUniversalStatus();
        return MergingStatusConstants.isFinalStatus(status);
    }

    private static boolean isVersionedEdit(UniversalMergeableEntity entry) {
        if (entry == null) return false;
        return MergingStatusConstants.isTimestampedEditStatus(entry.getUniversalStatus());
    }

    private static boolean isUserInProcess(UniversalMergeableEntity entry) {
        if (entry == null) return false;
        return MergingStatusConstants.USER_IN_PROCESS.equals(entry.getUniversalStatus());
    }

    private static boolean isUserInput(UniversalMergeableEntity entry) {
        if (entry == null) return false;
        return MergingStatusConstants.USER_INPUT.equals(entry.getUniversalStatus());
    }

    private static boolean isBaseInput(UniversalMergeableEntity entry) {
        if (entry == null) return false;
        return MergingStatusConstants.isBaseInputStatus(entry.getUniversalStatus());
    }

    private static long extractTimestamp(UniversalMergeableEntity entry) {
        if (entry == null) return 0L;
        return MergingStatusConstants.extractTimestamp(entry.getUniversalStatus());
    }

    private static int getBaseInputPriority(UniversalMergeableEntity entry) {
        if (entry == null) return 0;

        String status = entry.getUniversalStatus();
        return switch (status) {
            case MergingStatusConstants.ADMIN_INPUT -> 3;
            case MergingStatusConstants.TEAM_INPUT -> 2;
            case MergingStatusConstants.USER_INPUT -> 1;
            default -> 0;
        };
    }

    /**
     * UPDATED: Resolve timestamp conflicts with enhanced priority rule
     * 1. Latest timestamp wins
     * 2. If timestamps equal: ADMIN > TEAM > USER
     */
    private static UniversalMergeableEntity resolveTimestampConflict(UniversalMergeableEntity entry1, UniversalMergeableEntity entry2) {
        String status1 = getStatusString(entry1);
        String status2 = getStatusString(entry2);

        long timestamp1 = extractTimestamp(entry1);
        long timestamp2 = extractTimestamp(entry2);

        // Rule 1: Latest timestamp wins
        if (timestamp1 > timestamp2) {
            LoggerUtil.debug(UniversalMergeEngine.class,
                    String.format("Latest timestamp wins: %s (t=%d) beats %s (t=%d)",
                            status1, timestamp1, status2, timestamp2));
            return entry1;
        } else if (timestamp2 > timestamp1) {
            LoggerUtil.debug(UniversalMergeEngine.class,
                    String.format("Latest timestamp wins: %s (t=%d) beats %s (t=%d)",
                            status2, timestamp2, status1, timestamp1));
            return entry2;
        }

        // Rule 2: Equal timestamps - use editor priority (ADMIN > TEAM > USER)
        int priority1 = getEditorPriority(status1);
        int priority2 = getEditorPriority(status2);

        if (priority1 > priority2) {
            LoggerUtil.debug(UniversalMergeEngine.class,
                    String.format("Equal timestamps, editor priority wins: %s (priority=%d) beats %s (priority=%d)",
                            status1, priority1, status2, priority2));
            return entry1;
        } else if (priority2 > priority1) {
            LoggerUtil.debug(UniversalMergeEngine.class,
                    String.format("Equal timestamps, editor priority wins: %s (priority=%d) beats %s (priority=%d)",
                            status2, priority2, status1, priority1));
            return entry2;
        } else {
            // Same timestamp AND same editor type - default to entry1
            LoggerUtil.debug(UniversalMergeEngine.class,
                    String.format("Identical timestamps and priorities (%d), defaulting to entry1", priority1));
            return entry1;
        }
    }

    /**
     * NEW: Get editor priority for conflict resolution
     * Higher number = higher priority in conflicts
     */
    private static int getEditorPriority(String status) {
        if (MergingStatusConstants.isAdminEditedStatus(status)) return 3; // ADMIN highest
        if (MergingStatusConstants.isTeamEditedStatus(status)) return 2;  // TEAM middle
        if (MergingStatusConstants.isUserEditedStatus(status)) return 1;  // USER lowest
        return 0; // Unknown or non-timestamped
    }

    private static String getStatusString(UniversalMergeableEntity entry) {
        return entry != null ? entry.getUniversalStatus() : "null";
    }

    // ========================================================================
    // SUPPORTING INTERFACES AND ENUMS
    // ========================================================================

    /**
     * Interface that all mergeable entities must implement.
     * Read-only interface - merge engine selects winner without modification.
     */
    public interface UniversalMergeableEntity {
        String getUniversalStatus();
        Object getIdentifier(); // For logging/debugging
    }

    /**
     * Functional interfaces for tri-functions
     */
    @FunctionalInterface
    public interface TriPredicate<T, U, V> {
        boolean test(T t, U u, V v);
    }

    @FunctionalInterface
    public interface TriFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }
}