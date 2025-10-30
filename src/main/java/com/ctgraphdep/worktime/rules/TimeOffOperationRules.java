package com.ctgraphdep.worktime.rules;

import com.ctgraphdep.config.WorkCode;
import org.springframework.stereotype.Component;

/**
 * Centralized business rules for time-off type operations.
 * This class defines THE SINGLE SOURCE OF TRUTH for:
 * - Which time-off types can be added over existing worktime
 * - Which time-off types can be removed by users
 * - Special logic when removing certain types (e.g., CR refills overtime)
 * Business Rules Summary:
 * ┌──────┬─────────────────────────┬──────────────┬─────────────────────────────┐
 * │ Type │ Can Add Over Worktime?  │ Can Remove?  │ Special Logic               │
 * ├──────┼─────────────────────────┼──────────────┼─────────────────────────────┤
 * │ CO   │ ✅ Yes → holiday OT     │ ✅ Yes       │ Converts work to holiday OT │
 * │ CE   │ ✅ Yes → holiday OT     │ ✅ Yes       │ Converts work to holiday OT │
 * │ W    │ ✅ Yes → holiday OT     │ ✅ Yes       │ **Tombstone (full reset)**  │
 * │ CM   │ ✅ Yes → holiday OT     │ ✅ Yes       │ Converts work to holiday OT │
 * │ SN   │ ❌ No (only modify hrs) │ ❌ No        │ Admin-controlled, fixed     │
 * │ D    │ ❌ No (clear first)     │ ✅ Yes       │ Normal removal              │
 * │ CR   │ ❌ No (clear first)     │ ✅ Yes       │ **Refills overtime**        │
 * │ CN   │ ❌ No (clear first)     │ ✅ Yes       │ Normal removal              │
 * │ ZS   │ 🤖 AUTO-DETECTED        │ 🤖 AUTO     │ Created/removed by system   │
 * └──────┴─────────────────────────┴──────────────┴─────────────────────────────┘
 * W (Weekend) Special Behavior:
 * - Can be added over work (work becomes holiday overtime)
 * - Removing W = Complete tombstone (full entry reset, all data cleared)
 * - **CANNOT be changed to other types** (must remove W first, then add new type)
 * - This is enforced in AddTimeOffCommand via "Weekend Lock" validation
 * ZS (Short Day) Special Behavior:
 * - Automatically created when user ends day without reaching schedule (e.g., "ZS-3" = 3 hours short)
 * - Automatically removed when:
 *   → User completes the day (reaches full schedule)
 *   → User resumes and extends the day
 *   → User modifies start/end time to complete schedule
 * - NOT manually added by users
 */
@Component
public class TimeOffOperationRules {

    /**
     * Check if a time-off type is automatically managed by the system.
     * Auto-managed types:
     * - ZS (Short Day): Auto-created when day ends incomplete, auto-removed when completed
     * @param timeOffType Time-off type code
     * @return true if this type is automatically managed (not user-controlled)
     */
    public boolean isAutoManaged(String timeOffType) {
        if (timeOffType == null || timeOffType.trim().isEmpty()) {
            return false;
        }

        // ZS and its variants (ZS-3, ZS-5, etc.) are auto-managed
        String upperType = timeOffType.trim().toUpperCase();
        return upperType.equals(WorkCode.SHORT_DAY_CODE) || upperType.startsWith(WorkCode.SHORT_DAY_CODE + "-");
    }

    /**
     * Check if a time-off type can be added over existing worktime.
     * These types convert existing work hours to "holiday overtime":
     * - CO (Vacation): User takes vacation but worked anyway
     * - CE (Special Event): Special event but worked anyway
     * - W (Weekend): Weekend work
     * - CM (Medical Leave): Medical leave but worked anyway
     * @param timeOffType Time-off type code (e.g., "CO", "SN", "D")
     * @return true if this type can overlay existing worktime, false if worktime must be cleared first
     */
    public boolean canAddOverWorktime(String timeOffType) {
        if (timeOffType == null || timeOffType.trim().isEmpty()) {
            return false;
        }

        // Auto-managed types cannot be manually added
        if (isAutoManaged(timeOffType)) {
            return false;
        }

        return switch (timeOffType.toUpperCase()) {
            case WorkCode.TIME_OFF_CODE,           // CO - Vacation with work
                 WorkCode.SPECIAL_EVENT_CODE,      // CE - Special event with work
                 WorkCode.WEEKEND_CODE,            // W - Weekend work
                 WorkCode.MEDICAL_LEAVE_CODE       // CM - Medical leave with work
                 -> true;

            case WorkCode.NATIONAL_HOLIDAY_CODE,   // SN - Cannot modify (admin-set)
                 WorkCode.DELEGATION_CODE,         // D - Must clear worktime first
                 WorkCode.RECOVERY_LEAVE_CODE,     // CR - Must clear worktime first
                 WorkCode.UNPAID_LEAVE_CODE        // CN - Must clear worktime first
                 -> false;

            default -> false;  // Unknown types: be conservative, require clear
        };
    }

    /**
     * Check if a time-off type CANNOT be added over existing worktime (requires clear worktime first).
     * This is the inverse of canAddOverWorktime() - provided for clearer calling code.
     * @param timeOffType Time-off type code
     * @return true if worktime must be cleared first, false if you can add over worktime
     */
    public boolean requiresClearWorktime(String timeOffType) {
        return !canAddOverWorktime(timeOffType);
    }

    /**
     * Check if a time-off type can be removed by the user.
     * Cannot be removed:
     * - SN (National Holiday): Fixed by admin, users cannot remove
     * - ZS (Short Day): Auto-managed by system, removed automatically when day completes
     * Can be removed:
     * - CO, CE, W, CM: Reverses holiday overtime conversion
     * - D, CR, CN: Normal removal (CR has special logic to refill overtime)
     * @param timeOffType Time-off type code
     * @return true if user can remove this type, false if locked/auto-managed
     */
    public boolean canRemove(String timeOffType) {
        if (timeOffType == null || timeOffType.trim().isEmpty()) {
            return false;
        }

        // Auto-managed types cannot be manually removed
        if (isAutoManaged(timeOffType)) {
            return false;
        }

        // SN is admin-controlled and cannot be removed by users
        return !WorkCode.NATIONAL_HOLIDAY_CODE.equalsIgnoreCase(timeOffType.trim());
    }

    /**
     * Check if a time-off type is blocked from removal (cannot be removed by user).
     * This is the inverse of canRemove() - provided for clearer calling code.
     * @param timeOffType Time-off type code
     * @return true if removal is blocked, false if you can be removed
     */
    public boolean isRemovalBlocked(String timeOffType) {
        return !canRemove(timeOffType);
    }

    /**
     * Check if removing this time-off type requires special handling.
     * Special cases:
     * - CR (Recovery Leave): Must refill overtime when removed (user took overtime back)
     * - W (Weekend): Must tombstone entry (complete reset to empty)
     * - All others: Normal removal, no special logic
     * @param timeOffType Time-off type code
     * @return true if removal requires special logic
     */
    public boolean requiresSpecialRemovalLogic(String timeOffType) {
        if (timeOffType == null || timeOffType.trim().isEmpty()) {
            return false;
        }

        return WorkCode.RECOVERY_LEAVE_CODE.equalsIgnoreCase(timeOffType.trim()) ||
               WorkCode.WEEKEND_CODE.equalsIgnoreCase(timeOffType.trim());
    }

    /**
     * Get user-friendly explanation for why a time-off type cannot be added over worktime.
     * @param timeOffType Time-off type code
     * @return Explanation message for the user
     */
    public String getCannotAddOverWorktimeReason(String timeOffType) {
        if (timeOffType == null || timeOffType.trim().isEmpty()) {
            return "Invalid time-off type";
        }

        // Check if auto-managed first
        if (isAutoManaged(timeOffType)) {
            return "Short Day (ZS) is automatically managed by the system. It's created when you end the day without completing your schedule, and removed when you complete the day.";
        }

        return switch (timeOffType.toUpperCase()) {
            case WorkCode.NATIONAL_HOLIDAY_CODE ->
                "National holidays (SN) are set by admin and cannot be modified. You can only change work hours.";

            case WorkCode.DELEGATION_CODE ->
                "Delegation (D) requires an empty day. Please remove existing worktime first.";

            case WorkCode.RECOVERY_LEAVE_CODE ->
                "Recovery Leave (CR) uses overtime to cover a full day. Please remove existing worktime first.";

            case WorkCode.UNPAID_LEAVE_CODE ->
                "Unpaid Leave (CN) requires an empty day. Please remove existing worktime first.";

            default ->
                "This time-off type cannot be added over existing worktime. Please clear the entry first.";
        };
    }

    /**
     * Get user-friendly explanation for why a time-off type cannot be removed.
     * @param timeOffType Time-off type code
     * @return Explanation message for the user
     */
    public String getCannotRemoveReason(String timeOffType) {
        if (timeOffType == null || timeOffType.trim().isEmpty()) {
            return "Invalid time-off type";
        }

        // Check if auto-managed first
        if (isAutoManaged(timeOffType)) {
            return "Short Day (ZS) is automatically managed. To remove it, complete your work day by resuming the session or adjusting start/end times to reach your schedule.";
        }

        if (WorkCode.NATIONAL_HOLIDAY_CODE.equalsIgnoreCase(timeOffType.trim())) {
            return "National holidays (SN) are set by admin and cannot be removed by users.";
        }

        return "This time-off type cannot be removed.";
    }
}
