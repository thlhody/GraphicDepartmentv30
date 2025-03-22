/**
 * Worktime Display Utilities - Refactored
 *
 * This script handles the proper formatting and calculation of work hours for display,
 * ensuring business rules are consistently applied.
 */

// Initialize when DOM is fully loaded
document.addEventListener('DOMContentLoaded', function() {
    // Process all entries and fill calculated values
    processAllEntries();

    // Calculate and update summary totals
    updateWorkTimeSummary();

    // Initialize tooltips
    initializeTooltips();
});

/**
 * Process all worktime entries and fill in calculated values
 */
function processAllEntries() {
    document.querySelectorAll('.worktime-entry').forEach(function(row) {
        const rawMinutes = parseInt(row.getAttribute('data-minutes') || '0', 10);
        const hasLunchDeducted = row.getAttribute('data-lunch') === 'true';
        const tempStopMinutes = parseInt(row.getAttribute('data-breaks') || '0', 10);
        const overtimeMinutes = parseInt(row.getAttribute('data-overtime') || '0', 10);
        const timeOff = row.getAttribute('data-timeoff');

        // Skip if it's a time off entry
        if (timeOff) return;

        // Process only entries with worked minutes
        if (rawMinutes > 0) {
            // Calculate all values
            const calculationResult = calculateEntryValues(rawMinutes, hasLunchDeducted, overtimeMinutes);

            // Fill in raw work cell
            const rawWorkCell = row.querySelector('.raw-work-cell span');
            if (rawWorkCell) {
                rawWorkCell.textContent = formatTimeDisplay(rawMinutes);
            }

            // Fill in schedule work cell (capped at 8 hours = 480 minutes)
            const scheduleWorkCell = row.querySelector('.schedule-work-cell span');
            if (scheduleWorkCell) {
                const scheduledMinutes = Math.min(calculationResult.countedMinutes, 480);
                scheduleWorkCell.textContent = formatTimeDisplay(scheduledMinutes, true);

                // Set tooltip to show total worked time
                const totalWorkedMinutes = scheduledMinutes + overtimeMinutes;
                scheduleWorkCell.setAttribute('title', `Total work for day: ${formatTimeDisplay(totalWorkedMinutes)} (${totalWorkedMinutes} minutes)`);
            }

            // Fill in overtime cell
            const overtimeCell = row.querySelector('.overtime-display');
            if (overtimeCell && overtimeMinutes > 0) {
                overtimeCell.textContent = formatTimeDisplay(overtimeMinutes);
            }

            // Fill in discarded minutes cell
            const discardedMinutesCell = row.querySelector('.discarded-minutes-cell span');
            if (discardedMinutesCell) {
                discardedMinutesCell.textContent = calculationResult.discardedMinutes;
                if (calculationResult.discardedMinutes > 0) {
                    discardedMinutesCell.classList.add('text-warning');
                }
            }
        }
    });
}

/**
 * Updates the work time summary section with properly calculated totals
 */
function updateWorkTimeSummary() {
    // Get all worktime entries
    const entries = [];
    document.querySelectorAll('.worktime-entry').forEach(function(row) {
        const rawMinutes = parseInt(row.getAttribute('data-minutes') || '0', 10);
        const hasLunchDeducted = row.getAttribute('data-lunch') === 'true';
        const overtimeMinutes = parseInt(row.getAttribute('data-overtime') || '0', 10);
        const timeOff = row.getAttribute('data-timeoff');

        if (!timeOff && rawMinutes > 0) { // Only include actual work entries, not time off
            // Calculate entry values
            const calculationResult = calculateEntryValues(rawMinutes, hasLunchDeducted, overtimeMinutes);

            entries.push({
                rawMinutes: rawMinutes,
                processedMinutes: calculationResult.processedMinutes,
                countedMinutes: calculationResult.countedMinutes,
                scheduledMinutes: Math.min(calculationResult.countedMinutes, 480), // Cap at 8 hours
                overtimeMinutes: overtimeMinutes,
                discardedMinutes: calculationResult.discardedMinutes
            });
        }
    });

    // Calculate totals
    let totalRegularMinutes = 0;
    let totalOvertimeMinutes = 0;
    let totalDiscardedMinutes = 0;

    entries.forEach(function(entry) {
        totalRegularMinutes += entry.scheduledMinutes;
        totalOvertimeMinutes += entry.overtimeMinutes;
        totalDiscardedMinutes += entry.discardedMinutes;
    });

    // Format and display summary
    const regularHoursElement = document.getElementById('summary-regular-hours');
    const overtimeHoursElement = document.getElementById('summary-overtime-hours');
    const totalHoursElement = document.getElementById('summary-total-hours');
    const discardedMinutesElement = document.getElementById('summary-discarded-minutes');

    if (regularHoursElement) {
        regularHoursElement.textContent = formatTimeDisplay(totalRegularMinutes, true);
    }

    if (overtimeHoursElement) {
        overtimeHoursElement.textContent = formatTimeDisplay(totalOvertimeMinutes, true);
    }

    if (totalHoursElement) {
        totalHoursElement.textContent = formatTimeDisplay(totalRegularMinutes + totalOvertimeMinutes, true);
    }

    if (discardedMinutesElement) {
        discardedMinutesElement.textContent = totalDiscardedMinutes;
    }
}

/**
 * Calculates all values for a worktime entry
 *
 * @param {number} rawMinutes - Raw worked minutes
 * @param {boolean} hasLunchDeducted - Whether lunch is deducted
 * @param {number} overtimeMinutes - Overtime minutes
 * @returns {Object} Object with calculated values
 */
function calculateEntryValues(rawMinutes, hasLunchDeducted, overtimeMinutes) {
    // Step 1: Apply lunch deduction if applicable
    let processedMinutes = rawMinutes;
    if (hasLunchDeducted && isLunchApplicable(rawMinutes)) {
        processedMinutes -= 30; // Deduct 30 minutes for lunch break
    }

    // Step 2: Count only full hours
    const fullHours = Math.floor(processedMinutes / 60);
    const countedMinutes = fullHours * 60;

    // Step 3: Calculate discarded minutes (partial hour)
    const discardedMinutes = processedMinutes % 60;

    return {
        rawMinutes: rawMinutes,
        processedMinutes: processedMinutes,
        fullHours: fullHours,
        countedMinutes: countedMinutes,
        discardedMinutes: discardedMinutes
    };
}

/**
 * Checks if lunch deduction is applicable based on business rules
 *
 * @param {number} minutes - Total minutes worked
 * @returns {boolean} Whether lunch deduction should apply
 */
function isLunchApplicable(minutes) {
    // Lunch is deducted for 4-11 hours of work (240-660 minutes)
    const hours = minutes / 60;
    return hours > 4 && hours <= 11;
}

/**
 * Formats time in minutes to HH:MM format with business rule application
 *
 * @param {number} minutes - Minutes to format
 * @param {boolean} roundForSummary - Whether to round hours for summary display
 * @returns {string} Formatted time string
 */
function formatTimeDisplay(minutes, roundForSummary = false) {
    if (minutes <= 0) return "00:00";

    let hours = Math.floor(minutes / 60);
    let mins = minutes % 60;

    // For summary display, show full hours only
    if (roundForSummary) {
        // Round down to whole hours for regular display
        return `${hours.toString().padStart(2, '0')}:00`;
    }

    // Regular display shows hours and minutes
    return `${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}`;
}

/**
 * Initialize tooltips for detailed work time information
 */
function initializeTooltips() {
    if (typeof bootstrap !== 'undefined') {
        const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
        tooltipTriggerList.map(function (tooltipTriggerEl) {
            return new bootstrap.Tooltip(tooltipTriggerEl, {
                html: true
            });
        });
    }
}

/**
 * Example calculation for the March 10 entry
 * Raw: 440 minutes
 * Lunch deducted: Yes (30 minutes)
 * Processed: 410 minutes
 * Counted: 6 hours = 360 minutes
 * Discarded: 50 minutes
 * Overtime: 0 minutes
 * Schedule work: 6 hours = 360 minutes (below 8-hour cap)
 */