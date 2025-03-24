package com.ctgraphdep.enums;

import com.ctgraphdep.model.RegisterEntry;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BiFunction;

public enum RegisterMergeRule {
    NEW_USER_ENTRY((user, admin) -> admin == null,
            (user, admin) -> user),

    USER_ACCEPTS_ADMIN((user, admin) -> admin.getAdminSync().equals(SyncStatusWorktime.ADMIN_EDITED.name())
            && user.getAdminSync().equals(SyncStatusWorktime.USER_DONE.name())
            && entriesAreEqual(user, admin),
            (user, admin) -> {
                admin.setAdminSync(SyncStatusWorktime.USER_DONE.name());
                return admin;
            }),

    ADMIN_MODIFIED_USER_DONE((user, admin) -> user.getAdminSync().equals(SyncStatusWorktime.USER_DONE.name())
            && !entriesAreEqual(user, admin),
            (user, admin) -> {
                admin.setAdminSync(SyncStatusWorktime.ADMIN_EDITED.name());
                return admin;
            }),

    ADMIN_EDITED((user, admin) -> admin.getAdminSync().equals(SyncStatusWorktime.ADMIN_EDITED.name()),
            (user, admin) -> admin),

    USER_INPUT((user, admin) -> user.getAdminSync().equals(SyncStatusWorktime.USER_INPUT.name()),
            (user, admin) -> user),

    DEFAULT((user, admin) -> true, (user, admin) -> user);

    private final BiPredicate<RegisterEntry, RegisterEntry> condition;
    private final BiFunction<RegisterEntry, RegisterEntry, RegisterEntry> action;

    RegisterMergeRule(BiPredicate<RegisterEntry, RegisterEntry> condition,
                      BiFunction<RegisterEntry, RegisterEntry, RegisterEntry> action) {
        this.condition = condition;
        this.action = action;
    }

    public static RegisterEntry apply(RegisterEntry user, RegisterEntry admin) {
        return Arrays.stream(values())
                .filter(rule -> rule.condition.test(user, admin))
                .findFirst()
                .map(rule -> rule.action.apply(user, admin))
                .orElse(user);
    }

    private static boolean entriesAreEqual(RegisterEntry entry1, RegisterEntry entry2) {
        if (entry1 == null || entry2 == null) return false;

        return Objects.equals(entry1.getDate(), entry2.getDate()) &&
                Objects.equals(entry1.getOrderId(), entry2.getOrderId()) &&
                Objects.equals(entry1.getProductionId(), entry2.getProductionId()) &&
                Objects.equals(entry1.getOmsId(), entry2.getOmsId()) &&
                Objects.equals(entry1.getClientName(), entry2.getClientName()) &&
                Objects.equals(entry1.getActionType(), entry2.getActionType()) &&
                Objects.equals(entry1.getPrintPrepTypes(), entry2.getPrintPrepTypes()) && // Changed to compare Lists
                Objects.equals(entry1.getColorsProfile(), entry2.getColorsProfile()) &&
                Objects.equals(entry1.getArticleNumbers(), entry2.getArticleNumbers()) &&
                Objects.equals(entry1.getGraphicComplexity(), entry2.getGraphicComplexity()) &&
                Objects.equals(entry1.getObservations(), entry2.getObservations());
    }
}