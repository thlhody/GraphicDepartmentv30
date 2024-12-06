package com.ctgraphdep.enums;

import com.ctgraphdep.model.WorkTimeTable;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BiFunction;

public enum WorktimeMergeRule {
    // Keep user in-process entries as is
    USER_IN_PROCESS((user, admin) ->
            SyncStatus.USER_IN_PROCESS.equals(user.getAdminSync()),
            (user, admin) -> user),

    // Admin has marked entry as blank - should be removed
    // Update the ADMIN_BLANK rule to explicitly return null
    ADMIN_BLANK((user, admin) ->
            admin != null && SyncStatus.ADMIN_BLANK.equals(admin.getAdminSync()),
            (user, admin) -> null), // This null return will cause the entry to be removed

    // Admin has edited the entry - overwrites user entry unless it's in process
    ADMIN_EDITED((user, admin) ->
            admin != null && SyncStatus.ADMIN_EDITED.equals(admin.getAdminSync()) &&
                    (user == null || !SyncStatus.USER_IN_PROCESS.equals(user.getAdminSync())),
            (user, admin) -> {
                WorkTimeTable result = copyWorkTimeEntry(admin);
                result.setAdminSync(SyncStatus.USER_DONE);
                return result;
            }),

    // Keep user entry if it matches admins previous edit
    USER_ACCEPTS_ADMIN((user, admin) ->
            user != null && admin != null &&
                    SyncStatus.USER_DONE.equals(user.getAdminSync()) &&
                    entriesAreEqual(user, admin),
            (user, admin) -> user),

    // Keep regular user input if no admin edit exists
    USER_INPUT((user, admin) ->
            user != null && SyncStatus.USER_INPUT.equals(user.getAdminSync()) &&
                    (admin == null || !SyncStatus.ADMIN_EDITED.equals(admin.getAdminSync())),
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