package com.ctgraphdep.enums;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BiFunction;

public enum WorktimeMergeRule {
    // Keep user in-process entries untouched
    USER_IN_PROCESS((user, admin) ->
            user != null && SyncStatus.USER_IN_PROCESS.equals(user.getAdminSync()),
            (user, admin) -> user),

    // Handle new user entries
    NEW_USER_ENTRY((user, admin) -> admin == null && user != null,
            (user, admin) -> {
                if (SyncStatus.USER_IN_PROCESS.equals(user.getAdminSync())) {
                    return user;
                }
                user.setAdminSync(SyncStatus.USER_INPUT);
                return user;
            }),

    // Admin edited entry takes precedence over user entries unless their USER_IN_PROCESS or USER_EDITED
    ADMIN_EDITED((user, admin) ->
            admin != null &&
                    SyncStatus.ADMIN_EDITED.equals(admin.getAdminSync()) &&
                    (user == null ||
                            (!SyncStatus.USER_IN_PROCESS.equals(user.getAdminSync()) &&
                                    !SyncStatus.USER_EDITED.equals(user.getAdminSync()))),
            (user, admin) -> {
                WorkTimeTable result = copyWorkTimeEntry(admin);
                result.setAdminSync(SyncStatus.USER_DONE);
                return result;
            }),

    // USER_EDITED entries cannot be overwritten by ADMIN_BLANK
    USER_EDITED_PROTECTION((user, admin) ->
            user != null &&
                    SyncStatus.USER_EDITED.equals(user.getAdminSync()) &&
                    admin != null &&
                    SyncStatus.ADMIN_BLANK.equals(admin.getAdminSync()),
            (user, admin) -> user),

    // ADMIN_BLANK entries should not be displayed (return null to remove entry)
    ADMIN_BLANK((user, admin) ->
            admin != null &&
                    SyncStatus.ADMIN_BLANK.equals(admin.getAdminSync()) &&
                    (user == null || !SyncStatus.USER_EDITED.equals(user.getAdminSync())),
            (user, admin) -> null),

    // Convert USER_EDITED to USER_DONE when it matches admin entry
    USER_EDITED_TO_DONE((user, admin) ->
            user != null &&
                    SyncStatus.USER_EDITED.equals(user.getAdminSync()) &&
                    admin != null &&
                    entriesAreEqual(user, admin),
            (user, admin) -> {
                user.setAdminSync(SyncStatus.USER_DONE);
                return user;
            }),

    // USER_INPUT entries should become USER_DONE during consolidation
    USER_INPUT_TO_DONE((user, admin) ->
            user != null &&
                    SyncStatus.USER_INPUT.equals(user.getAdminSync()) &&
                    (admin == null || !SyncStatus.ADMIN_EDITED.equals(admin.getAdminSync())),
            (user, admin) -> {
                user.setAdminSync(SyncStatus.USER_DONE);
                return user;
            }),

    // Keep user entry if no admin edit exists
    USER_ENTRY((user, admin) -> user != null && admin == null,
            (user, admin) -> user),

    // Default case - prefer admin entry if it exists, otherwise keep user entry
    DEFAULT((user, admin) -> true,
            (user, admin) -> admin != null ? admin : user);

    private final BiPredicate<WorkTimeTable, WorkTimeTable> condition;
    private final BiFunction<WorkTimeTable, WorkTimeTable, WorkTimeTable> action;

    WorktimeMergeRule(BiPredicate<WorkTimeTable, WorkTimeTable> condition,
                      BiFunction<WorkTimeTable, WorkTimeTable, WorkTimeTable> action) {
        this.condition = condition;
        this.action = action;
    }

    public static WorkTimeTable apply(WorkTimeTable user, WorkTimeTable admin) {
        LoggerUtil.debug(WorktimeMergeRule.class,
                String.format("Merging - User: %s, Admin: %s", user != null ? user.getAdminSync() : "null", admin != null ? admin.getAdminSync() : "null"));

        // If both entries are null, return null
        if (user == null && admin == null) {
            return null;
        }

        // Handle new user entries
        if (admin == null && user != null) {
            if (!SyncStatus.USER_IN_PROCESS.equals(user.getAdminSync())) {
                user.setAdminSync(SyncStatus.USER_INPUT);
            }
            return user;
        }

        // Use existing rule-based approach for other cases
        return Arrays.stream(values())
                .filter(rule -> rule.condition.test(user, admin))
                .findFirst()
                .map(rule -> rule.action.apply(user, admin))
                .orElse(user);
    }

    private static boolean entriesAreEqual(WorkTimeTable entry1, WorkTimeTable entry2) {
        if (entry1 == null || entry2 == null) return false;

        return Objects.equals(entry1.getWorkDate(), entry2.getWorkDate()) &&
                Objects.equals(entry1.getDayStartTime(), entry2.getDayStartTime()) &&
                Objects.equals(entry1.getDayEndTime(), entry2.getDayEndTime()) &&
                Objects.equals(entry1.getTemporaryStopCount(), entry2.getTemporaryStopCount()) &&
                Objects.equals(entry1.isLunchBreakDeducted(), entry2.isLunchBreakDeducted()) &&
                Objects.equals(entry1.getTimeOffType(), entry2.getTimeOffType()) &&
                Objects.equals(entry1.getTotalWorkedMinutes(), entry2.getTotalWorkedMinutes()) &&
                Objects.equals(entry1.getTotalTemporaryStopMinutes(), entry2.getTotalTemporaryStopMinutes()) &&
                Objects.equals(entry1.getTotalOvertimeMinutes(), entry2.getTotalOvertimeMinutes());
    }

    private static WorkTimeTable copyWorkTimeEntry(WorkTimeTable source) {
        WorkTimeTable copy = new WorkTimeTable();
        copy.setUserId(source.getUserId());
        copy.setWorkDate(source.getWorkDate());
        copy.setDayStartTime(source.getDayStartTime());
        copy.setDayEndTime(source.getDayEndTime());
        copy.setTemporaryStopCount(source.getTemporaryStopCount());
        copy.setLunchBreakDeducted(source.isLunchBreakDeducted());
        copy.setTimeOffType(source.getTimeOffType());
        copy.setTotalWorkedMinutes(source.getTotalWorkedMinutes());
        copy.setTotalTemporaryStopMinutes(source.getTotalTemporaryStopMinutes());
        copy.setTotalOvertimeMinutes(source.getTotalOvertimeMinutes());
        copy.setAdminSync(source.getAdminSync());
        return copy;
    }
}