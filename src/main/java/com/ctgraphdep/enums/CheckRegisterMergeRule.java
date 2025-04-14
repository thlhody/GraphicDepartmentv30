package com.ctgraphdep.enums;

import com.ctgraphdep.model.RegisterCheckEntry;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BiFunction;

/**
 * Defines rules for merging user check entries with team lead entries
 * Similar to RegisterMergeRule but with additional team lead layer
 */
public enum CheckRegisterMergeRule {

    // When there's a new user entry with no corresponding team lead entry
    NEW_USER_ENTRY((user, teamLead) -> teamLead == null,
            (user, teamLead) -> user),

    // When user accepts team lead edits
    USER_ACCEPTS_TEAM_LEAD((user, teamLead) -> teamLead.getAdminSync().equals(CheckingStatus.TL_EDITED.name())
            && user.getAdminSync().equals(CheckingStatus.CHECKING_DONE.name())
            && entriesAreEqual(user, teamLead),
            (user, teamLead) -> {
                teamLead.setAdminSync(CheckingStatus.CHECKING_DONE.name());
                return teamLead;
            }),

    // When team lead modifies a user entry that was already done
    TEAM_LEAD_MODIFIED_USER_DONE((user, teamLead) -> user.getAdminSync().equals(CheckingStatus.CHECKING_DONE.name())
            && !entriesAreEqual(user, teamLead),
            (user, teamLead) -> {
                teamLead.setAdminSync(CheckingStatus.TL_EDITED.name());
                return teamLead;
            }),

    // When team lead has edited an entry (take team lead version)
    TEAM_LEAD_EDITED((user, teamLead) -> teamLead.getAdminSync().equals(CheckingStatus.TL_EDITED.name()),
            (user, teamLead) -> teamLead),

    // When an entry is still in user input stage (take user version)
    USER_INPUT((user, teamLead) -> user.getAdminSync().equals(CheckingStatus.CHECKING_INPUT.name()),
            (user, teamLead) -> user),

    // When admin has edited an entry (admin overrides team lead)
    ADMIN_EDITED((user, teamLead) -> teamLead.getAdminSync().equals(CheckingStatus.ADMIN_EDITED.name()),
            (user, teamLead) -> teamLead),

    // When admin has finalized an entry
    ADMIN_DONE((user, teamLead) -> teamLead.getAdminSync().equals(CheckingStatus.ADMIN_DONE.name()),
            (user, teamLead) -> teamLead),

    // When admin has marked an entry for removal
    ADMIN_BLANK((user, teamLead) -> teamLead.getAdminSync().equals(CheckingStatus.ADMIN_BLANK.name()),
            (user, teamLead) -> null), // Return null to indicate removal

    // Default rule if no other rule matches
    DEFAULT((user, teamLead) -> true, (user, teamLead) -> user);

    private final BiPredicate<RegisterCheckEntry, RegisterCheckEntry> condition;
    private final BiFunction<RegisterCheckEntry, RegisterCheckEntry, RegisterCheckEntry> action;

    CheckRegisterMergeRule(BiPredicate<RegisterCheckEntry, RegisterCheckEntry> condition,
                           BiFunction<RegisterCheckEntry, RegisterCheckEntry, RegisterCheckEntry> action) {
        this.condition = condition;
        this.action = action;
    }

    /**
     * Apply the appropriate merge rule based on entries' states
     *
     * @param user The user check entry
     * @param teamLead The team lead check entry
     * @return The resulting merged entry or null if entry should be removed
     */
    public static RegisterCheckEntry apply(RegisterCheckEntry user, RegisterCheckEntry teamLead) {
        return Arrays.stream(values())
                .filter(rule -> rule.condition.test(user, teamLead))
                .findFirst()
                .map(rule -> rule.action.apply(user, teamLead))
                .orElse(user);
    }

    /**
     * Check if two register check entries have identical content
     */
    private static boolean entriesAreEqual(RegisterCheckEntry entry1, RegisterCheckEntry entry2) {
        if (entry1 == null || entry2 == null) return false;

        return Objects.equals(entry1.getDate(), entry2.getDate()) &&
                Objects.equals(entry1.getOrderId(), entry2.getOrderId()) &&
                Objects.equals(entry1.getOmsId(), entry2.getOmsId()) &&
                Objects.equals(entry1.getProductionId(), entry2.getProductionId()) &&
                Objects.equals(entry1.getDesignerName(), entry2.getDesignerName()) &&
                Objects.equals(entry1.getCheckType(), entry2.getCheckType()) &&
                Objects.equals(entry1.getArticleNumbers(), entry2.getArticleNumbers()) &&
                Objects.equals(entry1.getFilesNumbers(), entry2.getFilesNumbers()) &&
                Objects.equals(entry1.getErrorDescription(), entry2.getErrorDescription()) &&
                Objects.equals(entry1.getApprovalStatus(), entry2.getApprovalStatus()) &&
                Objects.equals(entry1.getOrderValue(), entry2.getOrderValue());
    }
}