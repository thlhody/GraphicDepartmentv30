/**
 * Work Time Display Module - Handles cell display updates and special day work
 * Manages SN overtime display, work time calculations, and cell content formatting
 */

const WorkTimeDisplayModule = {

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize work time display functionality
     */
    initialize() {
        console.log('Initializing Work Time Display Module...');

        this.initializeSNOvertimeDisplay();
        this.initializeSpecialDayTooltips();

        console.log('✅ Work Time Display Module initialized');
    },

    // ========================================================================
    // CELL DISPLAY UPDATES
    // ========================================================================

    /**
     * Update cell display based on field type and value
     * @param {HTMLElement} cell - Cell element to update
     * @param {string} field - Field type
     * @param {string} value - New value
     * @param {Object} rawData - Optional raw data for special formatting
     */
    updateCellDisplay(cell, field, value, rawData = null) {
        const cellValue = window.UtilitiesModule?.getCellContentContainer(cell);
        if (!cellValue) return;

        // Route to appropriate handler based on field type
        switch (field) {
            case 'tempStop':
                this.updateTempStopDisplay(cellValue, value);
                break;
            case 'timeOff':
                this.updateTimeOffDisplay(cellValue, value, rawData);
                break;
            case 'workTime':
                this.updateWorkTimeDisplay(cellValue, value, rawData);
                break;
            case 'overtime':
                this.updateOvertimeDisplay(cellValue, value, rawData);
                break;
            case 'startTime':
            case 'endTime':
                this.updateTimeDisplay(cellValue, value, rawData);
                break;
            default:
                this.updateDefaultDisplay(cellValue, value);
                break;
        }

        // Update data attribute
        cell.setAttribute('data-original', value || '');
    },

    /**
     * Update temporary stop display
     */
    updateTempStopDisplay(cellValue, value) {
        const minutes = parseInt(value) || 0;

        if (minutes > 0) {
            cellValue.innerHTML = `<span class="text-info fw-medium temp-stop-display" title="Double-click to edit temporary stop time">${minutes}m</span>`;
        } else {
            cellValue.innerHTML = `<span class="text-muted temp-stop-display" title="Double-click to add temporary stop time">-</span>`;
        }
    },

    /**
     * Update time off display with special day work support
     */
    updateTimeOffDisplay(cellValue, value, rawData) {
        if (value && value !== '-') {
            // Check if this is an SN day with overtime work
            const isSnWithOvertime = value === 'SN' &&
            rawData &&
            rawData.totalOvertimeMinutes &&
            parseInt(rawData.totalOvertimeMinutes) > 0;

            if (isSnWithOvertime) {
                // Display SN with overtime hours (like "SN4")
                const overtimeHours = Math.floor(parseInt(rawData.totalOvertimeMinutes) / 60);
                const snDisplay = `SN${overtimeHours}`;

                cellValue.innerHTML = `<span class="sn-work-display" title="National Holiday with ${this.formatMinutesToHours(rawData.totalOvertimeMinutes)} overtime work">${snDisplay}</span>`;

                // Mark the row as SN work entry
                const row = cellValue.closest('tr');
                if (row) {
                    row.classList.add('sn-work-entry');
                }
            } else {
                // Regular time off display
                const timeOffClass = window.UtilitiesModule?.getTimeOffClass(value) || 'time-off-display';
                const timeOffLabel = window.UtilitiesModule?.getTimeOffLabel(value) || value;
                cellValue.innerHTML = `<span class="${timeOffClass}" title="${timeOffLabel}">${value}</span>`;
            }
        } else {
            cellValue.textContent = '-';
        }
    },

    /**
     * Update work time display
     */
    updateWorkTimeDisplay(cellValue, value, rawData) {
        if (rawData && rawData.timeOffType === 'SN') {
            // SN days show 0 work time (all time is overtime)
            cellValue.innerHTML = '<span class="text-muted small" title="No regular work time on holidays - all time is overtime">0:00</span>';
        } else if (value && value !== '-') {
            cellValue.innerHTML = `<span class="text-primary fw-medium">${value}</span>`;
        } else {
            cellValue.textContent = '-';
        }
    },

    /**
     * Update overtime display with special day highlighting
     */
    updateOvertimeDisplay(cellValue, value, rawData) {
        if (value && value !== '-') {
            const isSnOvertime = rawData && rawData.timeOffType === 'SN';
            const isCoOvertime = rawData && rawData.timeOffType === 'CO';
            const isCmOvertime = rawData && rawData.timeOffType === 'CM';
            const isWOvertime = rawData && rawData.timeOffType === 'W';

            let badgeClass, title;

            if (isSnOvertime) {
                badgeClass = 'badge bg-warning text-dark rounded-pill overtime-display small sn-overtime';
                title = `?v=291020251458 work: ${value}`;
            } else if (isCoOvertime) {
                badgeClass = 'badge bg-info text-white rounded-pill overtime-display small co-overtime';
                title = `Time Off overtime work: ${value}`;
            } else if (isCmOvertime) {
                badgeClass = 'badge bg-warning text-white rounded-pill overtime-display small cm-overtime';
                title = `Medical Leave overtime work: ${value}`;
            } else if (isWOvertime) {
                badgeClass = 'badge bg-secondary text-white rounded-pill overtime-display small w-overtime';
                title = `Weekend overtime work: ${value}`;
            } else {
                badgeClass = 'badge bg-success rounded-pill overtime-display small';
                title = `Overtime work: ${value}`;
            }

            cellValue.innerHTML = `<span class="${badgeClass}" title="${title}">${value}</span>`;
        } else {
            cellValue.textContent = '-';
        }
    },

    /**
     * Update time display with special day warnings
     */
    updateTimeDisplay(cellValue, value, rawData) {
        // Format the time properly for display
        let displayTime = '-';

        if (value) {
            // Ensure consistent 24-hour format for display
            displayTime = window.TimeInputModule?.formatTime(value, '24hour') || value;
        }

        if (rawData && rawData.timeOffType === 'SN') {
            cellValue.innerHTML = `<span class="text-warning" title="Working on national holiday - all time counts as overtime">${displayTime}</span>`;
        } else {
            cellValue.textContent = displayTime;
        }
    },

    /**
     * Update default display
     */
    updateDefaultDisplay(cellValue, value) {
        cellValue.textContent = value || '-';
    },

    // ========================================================================
    // SPECIAL DAY WORK DISPLAY
    // ========================================================================

    /**
     * Initialize SN overtime display enhancements
     */
    initializeSNOvertimeDisplay() {
        console.log('Initializing enhanced special day work display...');

        // Find all rows with special day work and apply appropriate styling
        document.querySelectorAll('.worktime-entry').forEach(row => {
            const rowData = this.getRowData(row);
            if (this.isSpecialDayWithWork(rowData)) {
                this.applySpecialDayWorkStyling(row, rowData);
            }
        });

        console.log('Enhanced special day work display initialized');
    },

    /**
     * Apply styling for special day work entries
     */
    applySpecialDayWorkStyling(row, rowData) {
        // Add appropriate work entry class
        const classMap = {
            'SN': 'sn-work-entry',
            'CO': 'co-work-entry',
            'CM': 'cm-work-entry',
            'W': 'w-work-entry'
        };

        const cssClass = classMap[rowData.timeOffType];
        if (cssClass) {
            row.classList.add(cssClass);
        }

        // Add enhanced data attributes for easier styling
        row.setAttribute('data-special-day-type', rowData.timeOffType);
        row.setAttribute('data-has-special-work', 'true');
    },

    /**
     * Initialize special day tooltips
     */
    initializeSpecialDayTooltips() {
        const specialDaySelectors = [
            '.sn-work-display',
            '.co-work-display',
            '.cm-work-display',
            '.w-work-display'
        ];

        specialDaySelectors.forEach(selector => {
            document.querySelectorAll(selector).forEach(element => {
                // Set tooltip based on type and hours
                const type = window.UtilitiesModule?.getSpecialDayTypeFromClass(element.className) || 'Special Day';
                const hours = window.UtilitiesModule?.extractHoursFromDisplay(element.textContent) || 0;
                const tooltip = window.UtilitiesModule?.generateSpecialDayTooltip(type, hours) || 'Special day work';

                element.setAttribute('title', tooltip);
                element.setAttribute('data-bs-toggle', 'tooltip');
                element.setAttribute('data-bs-placement', 'top');
            });
        });

        // Initialize Bootstrap tooltips
        if (window.UtilitiesModule) {
            window.UtilitiesModule.initializeTooltips();
        }
    },

    // ========================================================================
    // ROW DATA UTILITIES
    // ========================================================================

    /**
     * Get row data for display calculations
     * @param {HTMLElement} row - Table row element
     * @returns {Object|null} Row data object
     */
    getRowData(row) {
        if (!row) return null;

        const timeOffCell = row.querySelector('.timeoff-cell');

        // Detect time off type from cell classes or content
        let timeOffType = null;
        if (timeOffCell) {
            if (timeOffCell.querySelector('.sn-work-display, .holiday')) timeOffType = 'SN';
            else if (timeOffCell.querySelector('.co-work-display, .vacation')) timeOffType = 'CO';
            else if (timeOffCell.querySelector('.cm-work-display, .medical')) timeOffType = 'CM';
            else if (timeOffCell.querySelector('.w-work-display, .weekend')) timeOffType = 'W';
        }

        // Get overtime information
        const overtimeMinutes = row.getAttribute('data-overtime-minutes') ||
        (row.querySelector('.overtime-cell') ?
        window.UtilitiesModule?.extractOvertimeMinutes(row.querySelector('.overtime-cell').textContent) : 0);

        return {
            date: row.getAttribute('data-date'),
            timeOffType: timeOffType,
            totalOvertimeMinutes: parseInt(overtimeMinutes) || 0,
            hasWork: overtimeMinutes > 0,
            isSpecialDay: ['SN', 'CO', 'CM', 'W'].includes(timeOffType)
        };
    },

    /**
     * Check if this is a special day with work
     * @param {Object} rowData - Row data object
     * @returns {boolean} True if special day with work
     */
    isSpecialDayWithWork(rowData) {
        return rowData &&
        rowData.isSpecialDay &&
        rowData.hasWork &&
        rowData.totalOvertimeMinutes > 0;
    },

    /**
     * Refresh row display after update
     * @param {HTMLElement} row - Row to refresh
     * @param {Object} updatedData - Updated row data
     */
    refreshRowAfterUpdate(row, updatedData) {
        // Check if this became or stopped being an SN work day
        if (this.isSpecialDayWithWork(updatedData)) {
            row.classList.add('sn-work-entry', 'updated');

            // Add special indicator for SN work
            const timeOffCell = row.querySelector('.timeoff-cell .cell-value');
            if (timeOffCell && updatedData.timeOffType === 'SN') {
                this.updateCellDisplay(timeOffCell.closest('td'), 'timeOff', 'SN', updatedData);
            }

            // Update overtime display
            const overtimeCell = row.querySelector('.overtime-display');
            if (overtimeCell && updatedData.totalOvertimeMinutes) {
                const formattedOvertime = this.formatMinutesToHours(updatedData.totalOvertimeMinutes);
                this.updateCellDisplay(overtimeCell.closest('td'), 'overtime', formattedOvertime, updatedData);
            }

            // Remove the updated animation after 2 seconds
            setTimeout(() => {
                row.classList.remove('updated');
            }, 2000);
        } else {
            row.classList.remove('sn-work-entry');
        }
    },

    // ========================================================================
    // FORMATTING UTILITIES
    // ========================================================================

    /**
     * Format minutes to hours display
     * @param {number} minutes - Minutes to format
     * @returns {string} Formatted hours string
     */
    formatMinutesToHours(minutes) {
        if (window.UtilitiesModule && typeof window.UtilitiesModule.formatMinutesToReadable === 'function') {
            return window.UtilitiesModule.formatMinutesToReadable(minutes);
        }

        // Fallback implementation
        if (!minutes || minutes === 0) return '0h';

        const hours = Math.floor(minutes / 60);
        const mins = minutes % 60;

        if (mins === 0) {
            return `${hours}h`;
        }
        return `${hours}h ${mins}m`;
    },

    // ========================================================================
    // SPECIAL DAY WORK NOTIFICATIONS
    // ========================================================================

    /**
     * Show notification for SN work updates
     * @param {string} actionType - Type of action performed
     */
    showSNWorkNotification(actionType) {
        const messages = {
            'timeUpdated': 'Work time updated for national holiday. All time counts as overtime.',
            'workStarted': 'Started work on national holiday. All time will be overtime.',
            'workEnded': 'Completed work on national holiday. Time recorded as overtime.'
        };

        const message = messages[actionType] || 'National holiday work time updated.';

        if (window.showToast) {
            window.showToast('Holiday Work Updated', message, 'warning', { duration: 4000 });
        }
    },

    /**
     * Validate field editing for special days
     * @param {string} field - Field being edited
     * @param {string} value - New value
     * @param {Object} rowData - Row data
     * @returns {string|null} Error message or null if valid
     */
    validateFieldForSpecialDay(field, value, rowData) {
        if (!rowData || !rowData.timeOffType) {
            return null; // Not a special day, normal validation applies
        }

        switch (field) {
            case 'timeOff':
                if (rowData.timeOffType === 'SN') {
                    return 'Cannot modify time off type for national holidays. Contact admin if changes needed.';
                }
                break;
            case 'startTime':
            case 'endTime':
                if (rowData.timeOffType === 'SN') {
                    // Show confirmation for SN day work time changes
                    if (value && value !== rowData.originalValue) {
                        this.showSNWorkNotification('timeUpdated');
                    }
                }
                return null;
            default:
                return null;
        }

        return null;
    },

    // ========================================================================
    // EDITABILITY CHECKING
    // ========================================================================

    /**
     * Check if cell is editable based on time off conflicts
     * @param {HTMLElement} cell - Cell to check
     */
    async checkCellEditability(cell) {
        const date = cell.getAttribute('data-date');
        const field = cell.getAttribute('data-field');
        const originalValue = cell.getAttribute('data-original');

        if (!date || !field) return;

        // Get the row to check for time off values
        const row = cell.closest('tr');
        const timeOffCell = row?.querySelector('.editable-cell[data-field="timeOff"]');
        const timeOffValue = timeOffCell?.getAttribute('data-original');

        // Handle temp stop field (follows same rules as start/end time)
        if (field === 'tempStop') {
            try {
                console.log(`🔍 Checking temp stop editability for ${date}`);
                const response = await fetch(`/user/time-management/can-edit?date=${date}&field=startTime`);
                const result = await response.json();

                if (!result.canEdit) {
                    console.log(`❌ Temp stop disabled: ${result.reason}`);
                    cell.classList.add('disabled');
                    cell.setAttribute('title', result.reason || 'Cannot edit temporary stop');
                }
            } catch (error) {
                console.error('Error checking temp stop editability:', error);
                cell.classList.add('disabled');
            }
            return;
        }

        // Handle SN special case
        if (timeOffValue === 'SN') {
            if (field === 'timeOff') {
                // SN timeOffType: admin only
                cell.classList.add('disabled');
                cell.setAttribute('title', 'Only admin can edit SN timeoff type');
                return;
            } else if (field === 'startTime' || field === 'endTime') {
                // SN start/end times: users CAN edit (forgot to register work)
                console.log(`Allowing SN ${field} edit for ${date} (user can register forgotten work)`);
                // Continue with normal date validation below
            }
        } else if ((field === 'startTime' || field === 'endTime') && timeOffValue && timeOffValue.trim() !== '') {
            // Non-SN time off: disable start/end time editing
            cell.classList.add('non-editable');
            cell.setAttribute('title', `Cannot edit time fields when time off (${timeOffValue}) is set. Remove time off first.`);
            console.log(`Disabling ${field} because time off ${timeOffValue} is set for ${date}`);
            return;
        }

        // Time off field is always editable (can be added, changed, or removed)
        if (field === 'timeOff') {
            console.log(`Time off field is editable for ${date}`);
            return;
        }

        // Check server-side editability
        try {
            console.log(`🔍 Checking editability: ${field} on ${date}`);
            const response = await fetch(`/user/time-management/can-edit?date=${date}&field=${field}`);
            const result = await response.json();

            console.log(`✅ Can-edit result for ${date} ${field}:`, result);

            if (!result.canEdit) {
                console.log(`❌ Field disabled: ${result.reason}`);
                cell.classList.add('disabled');
                cell.setAttribute('title', result.reason || 'Cannot edit this field');

                // Add visual indicators for current/future days
                const cellDate = new Date(date);
                const today = new Date();
                today.setHours(0, 0, 0, 0);
                cellDate.setHours(0, 0, 0, 0);

                if (cellDate.getTime() === today.getTime()) {
                    cell.closest('tr')?.classList.add('current-day');
                } else if (cellDate.getTime() > today.getTime()) {
                    cell.closest('tr')?.classList.add('future-day');
                }
            }
        } catch (error) {
            console.error('Error checking editability:', error);
            cell.classList.add('disabled');
        }
    },

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Manually refresh special day displays
     */
    refreshSpecialDayDisplays() {
        this.initializeSNOvertimeDisplay();
        this.initializeSpecialDayTooltips();
    },

    /**
     * Update multiple cells in a row
     * @param {HTMLElement} row - Row containing cells to update
     * @param {Object} updates - Object with field-value pairs to update
     */
    updateRowCells(row, updates) {
        Object.entries(updates).forEach(([field, value]) => {
            const cell = row.querySelector(`.editable-cell[data-field="${field}"]`);
            if (cell) {
                this.updateCellDisplay(cell, field, value);
            }
        });
    },

    /**
     * Get formatted display value for a field
     * @param {string} field - Field type
     * @param {string} value - Raw value
     * @param {Object} context - Additional context data
     * @returns {string} Formatted display value
     */
    getFormattedDisplayValue(field, value, context = {}) {
        switch (field) {
            case 'tempStop':
                return value ? `${value}m` : '-';
            case 'timeOff':
                return value || '-';
            case 'startTime':
            case 'endTime':
                return window.TimeInputModule?.formatTime(value, '24hour') || value || '-';
            case 'overtime':
                return this.formatMinutesToHours(parseInt(value) || 0);
            default:
                return value || '-';
        }
    }
};

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = WorkTimeDisplayModule;
}

// Make available globally
window.WorkTimeDisplayModule = WorkTimeDisplayModule;