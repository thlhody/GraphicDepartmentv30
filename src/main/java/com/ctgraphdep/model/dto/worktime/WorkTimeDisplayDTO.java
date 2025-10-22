package com.ctgraphdep.model.dto.worktime;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.status.GeneralDataStatusDTO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * DTO for frontend worktime display with pre-calculated display values.
 * Eliminates frontend calculation inconsistencies by processing everything in backend.
 * THIS IS A PURE DATA CONTAINER - No business logic allowed!
 * All creation logic has been moved to WorkTimeDisplayDTOFactory service.
 * Contains:
 * - Pre-calculated display text (what users see in cells)
 * - CSS classes for styling
 * - Raw data for editing functionality
 * - Metadata for frontend logic
 *
 * @see com.ctgraphdep.service.dto.WorkTimeDisplayDTOFactory for creation logic
 */
@Data
@Builder
public class WorkTimeDisplayDTO {

    // ========================================================================
    // DISPLAY VALUES (Pre-calculated by backend)
    // ========================================================================

    /** What users see in the cell (e.g., "10h", "SN5", "CO", "-") */
    private String displayText;

    /** CSS class for cell styling (e.g., "holiday", "vacation", "medical", "sn-work-display") */
    private String cssClass;

    /** Tooltip text with detailed information */
    private String tooltipText;

    // ========================================================================
    // RAW DATA (For editing functionality)
    // ========================================================================

    /** Original WorkTimeTable entry (null if no entry exists) */
    private WorkTimeTable rawEntry;

    /** User ID for this cell */
    private Integer userId;

    /** Date for this cell */
    private LocalDate date;

    /** Formatted date string for frontend attributes */
    private String dateString;

    // ========================================================================
    // STATUS INFORMATION
    // ========================================================================

    /** Complete status information decoded from Universal Merge status */
    private GeneralDataStatusDTO statusInfo;

    // ========================================================================
    // METADATA (For frontend logic)
    // ========================================================================

    /** Whether this cell has any entry */
    private boolean hasEntry;

    /** Whether this is a time off entry */
    private boolean isTimeOff;

    /** Whether this is SN with work hours */
    private boolean isSNWithWork;

    /** Whether this cell is editable */
    private boolean isEditable;

    /** Whether this is a weekend day */
    private boolean isWeekend;

    // ========================================================================
    // CALCULATED VALUES (For consistency verification)
    // ========================================================================

    /** Processed work hours that contribute to totals */
    private Integer contributedRegularMinutes;

    /** Processed overtime minutes that contribute to totals */
    private Integer contributedOvertimeMinutes;

    /** Total minutes that contribute to display totals */
    private Integer totalContributedMinutes;

    // ========================================================================
    // NOTE: All factory methods removed - use WorkTimeDisplayDTOFactory
    // ========================================================================
}
