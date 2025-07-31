package com.ctgraphdep.worktime.accessor;

import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.WorkTimeTable;

import java.util.List;

/**
 * FINAL CORRECTED: Data accessor interface for worktime package operations.

 * PURPOSE: Worktime package is for 3 main user categories:
 * 1. Those who USE worktime (read worktime)
 * 2. Those who EDIT worktime (read/write worktime)
 * 3. Those who VIEW worktime (read worktime + status data for display)

 * WORKTIME OPERATIONS:
 * - Full read/write support (the main purpose of this package)

 * STATUS OPERATIONS (Connected to StatusController):
 * - Register/CheckRegister/TimeOff are READ-ONLY for viewing/status display
 * - Their actual editing happens in other packages/controllers
 * - TimeOff logic is mostly handled when user writes worktime entries

 * IMPLEMENTATION SUPPORT:
 * - AdminOwnDataAccessor: Only worktime read/write, others throw UnsupportedOperationException
 * - UserOwnDataAccessor: All operations (worktime read/write + status reads via cache)
 * - NetworkOnlyAccessor: All read operations for viewing others' data
 */
public interface WorktimeDataAccessor {

    // ========================================================================
    // WORKTIME OPERATIONS (MAIN PURPOSE - READ/WRITE)
    // ========================================================================

    /**
     * Read worktime entries for user/month
     */
    List<WorkTimeTable> readWorktime(String username, int year, int month);

    /**
     * NEW: Write worktime entries with automatic status management.
     * This method:
     * 1. Determines appropriate status for each entry based on user role and existing status
     * 2. Sets the status on entries before saving
     * 3. Performs the write operation
     * STATUS LOGIC:
     * - For new entries: USER_INPUT, ADMIN_INPUT, or TEAM_INPUT based on role
     * - For existing entries: Timestamped edit status (USER_EDITED_xxx, etc.)
     * - Prevents modification of final status entries

     * @param username Target username
     * @param entries Entries to save (will be modified with status)
     * @param year Year
     * @param month Month
     * @param userRole Current user's role (USER, ADMIN, TL_CHECKING)
     * @throws UnsupportedOperationException if accessor doesn't support writes
     * @throws IllegalStateException if attempting to modify final status entries
     */
    void writeWorktimeWithStatus(String username, List<WorkTimeTable> entries,
                                 int year, int month, String userRole);

    /**
     * NEW: Write single worktime entry with automatic status management.
     * Convenience method for single entry operations.
     * Same status logic as writeWorktimeWithStatus() but for individual entries.
     * @param username Target username
     * @param entry Entry to save (will be modified with status)
     * @param userRole Current user's role
     */
    void writeWorktimeEntryWithStatus(String username, WorkTimeTable entry, String userRole);

    // ========================================================================
    // STATUS OPERATIONS (READ-ONLY FOR VIEWING/DISPLAY)
    // ========================================================================

    /**
     * Read register entries for status display (READ-ONLY)
     * Used by StatusController for viewing register data
     * @throws UnsupportedOperationException if accessor doesn't support this data type
     */
    List<RegisterEntry> readRegister(String username, Integer userId, int year, int month);

    /**
     * Read check register entries for status display (READ-ONLY)
     * Used by StatusController for viewing check register data
     * @throws UnsupportedOperationException if accessor doesn't support this data type
     */
    List<RegisterCheckEntry> readCheckRegister(String username, Integer userId, int year, int month);

    /**
     * Read time off tracker for status display (READ-ONLY)
     * Used by StatusController for viewing time off data
     * TimeOff logic is mostly handled when user writes worktime entries
     * @throws UnsupportedOperationException if accessor doesn't support this data type
     */
    TimeOffTracker readTimeOffTracker(String username, Integer userId, int year);

    // ========================================================================
    // METADATA & CAPABILITIES
    // ========================================================================

    /**
     * Get the access type for logging/debugging
     */
    String getAccessType();

    /**
     * Check if this accessor supports write operations
     * Only worktime has written operations in this package
     */
    boolean supportsWrite();
}

// ========================================================================
// IMPLEMENTATION MATRIX
// ========================================================================

/**
 * IMPLEMENTATION SUPPORT MATRIX:

 * | Operation              | AdminOwnDataAccessor | UserOwnDataAccessor | NetworkOnlyAccessor |
 * |------------------------|---------------------|-------------------|-------------------|
 * | readWorktime()         | ✅ (admin files)     | ✅ (cache)         | ✅ (network)       |
 * | writeWorktime()        | ✅ (admin files)     | ✅ (cache)         | ❌ (throws)        |
 * | readRegister()         | ❌ (throws)          | ✅ (cache)         | ✅ (network)       |
 * | readCheckRegister()    | ❌ (throws)          | ✅ (cache)         | ✅ (network)       |
 * | readTimeOffTracker()   | ❌ (throws)          | ✅ (cache)         | ✅ (network)       |

 * RATIONALE:
 * - AdminOwnDataAccessor: Admin files only contain worktime data
 * - UserOwnDataAccessor: Uses cache for all data types (own data)
 * - NetworkOnlyAccessor: Network-only reads for viewing others' data

// ========================================================================
// USAGE EXAMPLES
// ========================================================================

 * EXAMPLE USAGE IN WORKTIME PACKAGE:

 * // Worktime editing (main purpose)
 * WorktimeDataAccessor accessor = context.getDataAccessor(username);
 * List<WorkTimeTable> entries = accessor.readWorktime(username, year, month);
 * accessor.writeWorktime(username, updatedEntries, year, month);

 * // Status viewing (connected to StatusController)
 * WorktimeDataAccessor accessor = context.getDataAccessor(targetUsername);
 * List<RegisterEntry> register = accessor.readRegister(targetUsername, userId, year, month);
 * TimeOffTracker timeOff = accessor.readTimeOffTracker(targetUsername, userId, year);
 * // Exception handling for unsupported operations
 * try {
 *     adminAccessor.readRegister(username, userId, year, month);
 * } catch (UnsupportedOperationException e) {
 *     // Admin files don't contain register data
 *     // Use NetworkOnlyAccessor to view user register data
 * }
 */