package com.ctgraphdep.enums;

import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.utils.LoggerUtil;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BiFunction;

/**
 * Merge rules for check register entries between user entries and team leader entries.
 * Similar to RegisterMergeRule but uses CheckingStatus values instead of SyncStatusMerge.
 */
public enum CheckRegisterMergeRule {

    /**
     * When team leader entry is null, use the user's entry
     */
    NEW_USER_ENTRY((user, teamLead) -> teamLead == null,
            (user, teamLead) -> user),

    /**
     * TL_BLANK status causes entry removal regardless of user entry status
     */
    TEAM_LEAD_BLANK((user, teamLead) -> teamLead != null &&
            teamLead.getAdminSync().equals(CheckingStatus.TL_BLANK.name()),
            (user, teamLead) -> null), // returning null removes the entry

    /**
     * Team leader with TL_EDITED status overwrites user entries with CHECKING_INPUT or TL_CHECK_DONE
     */
    TEAM_LEAD_EDITED((user, teamLead) -> teamLead != null &&
            teamLead.getAdminSync().equals(CheckingStatus.TL_EDITED.name()) &&
            (user.getAdminSync().equals(CheckingStatus.CHECKING_INPUT.name()) ||
                    user.getAdminSync().equals(CheckingStatus.TL_CHECK_DONE.name())),
            (user, teamLead) -> teamLead),

    /**
     * When both user and team leader have TL_EDITED status and entries are equal,
     * convert to TL_CHECK_DONE to indicate agreement
     */
    BOTH_EDITED((user, teamLead) -> teamLead != null &&
            user.getAdminSync().equals(CheckingStatus.TL_EDITED.name()) &&
            teamLead.getAdminSync().equals(CheckingStatus.TL_EDITED.name()) &&
            entriesAreEqual(user, teamLead),
            (user, teamLead) -> {
                RegisterCheckEntry result = copyEntry(teamLead);
                result.setAdminSync(CheckingStatus.TL_CHECK_DONE.name());
                return result;
            }),

    /**
     * TL_CHECK_DONE overwrites CHECKING_INPUT
     */
    TEAM_LEAD_APPROVED((user, teamLead) -> teamLead != null &&
            teamLead.getAdminSync().equals(CheckingStatus.TL_CHECK_DONE.name()) &&
            user.getAdminSync().equals(CheckingStatus.CHECKING_INPUT.name()),
            (user, teamLead) -> teamLead),

    /**
     * When user has modified an entry with TL_CHECK_DONE status, keep user changes
     * and reset status to CHECKING_INPUT to indicate it needs review
     */
    USER_MODIFIED_APPROVED((user, teamLead) -> teamLead != null &&
            teamLead.getAdminSync().equals(CheckingStatus.TL_CHECK_DONE.name()) &&
            !entriesAreEqual(user, teamLead),
            (user, teamLead) -> {
                user.setAdminSync(CheckingStatus.CHECKING_INPUT.name());
                return user;
            }),

    /**
     * When both entries have matching status and content, maintain status
     */
    MATCHING_ENTRIES((user, teamLead) -> teamLead != null &&
            entriesAreEqual(user, teamLead) &&
            user.getAdminSync().equals(teamLead.getAdminSync()),
            (user, teamLead) -> teamLead),

    /**
     * ADMIN_DONE entries should be preserved
     */
    ADMIN_PROCESSED((user, teamLead) ->
            (user != null && user.getAdminSync().equals(CheckingStatus.ADMIN_DONE.name())) ||
                    (teamLead != null && teamLead.getAdminSync().equals(CheckingStatus.ADMIN_DONE.name())),
            (user, teamLead) -> {
                if (teamLead != null && teamLead.getAdminSync().equals(CheckingStatus.ADMIN_DONE.name())) {
                    return teamLead;
                }
                return user;
            }),

    /**
     * Default rule - keep user entry if no other rule matches
     */
    DEFAULT((user, teamLead) -> true, (user, teamLead) -> user);

    private final BiPredicate<RegisterCheckEntry, RegisterCheckEntry> condition;
    private final BiFunction<RegisterCheckEntry, RegisterCheckEntry, RegisterCheckEntry> action;

    CheckRegisterMergeRule(BiPredicate<RegisterCheckEntry, RegisterCheckEntry> condition,
                           BiFunction<RegisterCheckEntry, RegisterCheckEntry, RegisterCheckEntry> action) {
        this.condition = condition;
        this.action = action;
    }

    /**
     * Apply merge rules to determine which entry should be kept.
     *
     * @param user User's check register entry
     * @param teamLead Team leader's check register entry
     * @return The merged entry, or null if the entry should be removed
     */
    public static RegisterCheckEntry apply(RegisterCheckEntry user, RegisterCheckEntry teamLead) {

        // Special case: If teamLead has TL_BLANK status, remove entry regardless of user entry status
        if (teamLead != null && CheckingStatus.TL_BLANK.name().equals(teamLead.getAdminSync())) {
            LoggerUtil.debug(CheckRegisterMergeRule.class, "TL_BLANK rule matched - entry will be removed");
            return null;
        }

        // If both entries are null, return null
        if (user == null && teamLead == null) {
            return null;
        }

        // If user is null but teamLead exists (and not TL_BLANK), use teamLead
        if (user == null) {
            return teamLead;
        }

        // Apply rules in sequence
        return Arrays.stream(values())
                .filter(rule -> rule.condition.test(user, teamLead))
                .findFirst()
                .map(rule -> rule.action.apply(user, teamLead))
                .orElse(user);
    }

    /**
     * Check if two entries have equal content (excluding adminSync status)
     */
    private static boolean entriesAreEqual(RegisterCheckEntry entry1, RegisterCheckEntry entry2) {
        if (entry1 == null || entry2 == null) return false;

        return Objects.equals(entry1.getDate(), entry2.getDate()) &&
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

    /**
     * Create a deep copy of a RegisterCheckEntry
     */
    private static RegisterCheckEntry copyEntry(RegisterCheckEntry source) {
        if (source == null) return null;

        return RegisterCheckEntry.builder()
                .entryId(source.getEntryId())
                .date(source.getDate())
                .omsId(source.getOmsId())
                .designerName(source.getDesignerName())
                .productionId(source.getProductionId())
                .checkType(source.getCheckType())
                .articleNumbers(source.getArticleNumbers())
                .filesNumbers(source.getFilesNumbers())
                .errorDescription(source.getErrorDescription())
                .approvalStatus(source.getApprovalStatus())
                .orderValue(source.getOrderValue())
                .adminSync(source.getAdminSync())
                .build();
    }
}