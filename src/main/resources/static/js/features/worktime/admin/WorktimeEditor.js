/**
 * WorktimeEditor.js
 *
 * Manages the worktime editor popup UI for admin worktime management.
 * Handles showing/hiding editors, positioning, visual feedback, and entry information display.
 *
 * @module features/worktime/admin/WorktimeEditor
 */

import { TimeOffService } from '../../../services/timeOffService.js';
import { StatusService } from '../../../services/statusService.js';
import { formatMinutesToHours, formatDateTime } from '../../../core/utils.js';

/**
 * WorktimeEditor class
 * Manages editor popups for worktime cells
 */
export class WorktimeEditor {
    /**
     * @param {Object} options - Configuration options
     * @param {WorktimeValidator} options.validator - Validator instance
     * @param {WorktimeDataService} options.dataService - Data service instance
     */
    constructor({ validator, dataService }) {
        this.validator = validator;
        this.dataService = dataService;
        this.currentEditor = null;

        this.initializeEventHandlers();
    }

    /**
     * Initialize global event handlers
     */
    initializeEventHandlers() {
        // Click-outside handler to close editors
        document.addEventListener('click', (event) => {
            if (!event.target.closest('.worktime-cell')) {
                this.hideAllEditors();
            }
        });

        // Escape key handler
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') {
                this.hideAllEditors();
            }
        });

        console.log('WorktimeEditor event handlers initialized');
    }

    /**
     * Show editor popup for worktime cell
     * @param {HTMLElement} cell - Worktime cell element
     */
    showEditor(cell) {
        // Hide all other editors first
        this.hideAllEditors();

        const editor = cell.querySelector('.worktime-editor');
        if (!editor) {
            console.error('No editor found in cell');
            return;
        }

        // Populate dynamic entry information
        this.populateEntryInfo(editor, cell);

        // Position editor relative to cell
        this.positionEditor(cell, editor);

        // Show the editor
        editor.style.display = 'block';
        editor.classList.add('show');
        this.currentEditor = editor;

        // Focus on input field for better UX
        const input = editor.querySelector('input[type="text"]');
        if (input) {
            setTimeout(() => input.focus(), 100);
        }

        console.log('Editor shown for cell:', cell.dataset);
    }

    /**
     * Position editor relative to cell
     * Uses fixed positioning for better viewport control
     * @param {HTMLElement} cell - Cell element
     * @param {HTMLElement} editor - Editor element
     */
    positionEditor(cell, editor) {
        const rect = cell.getBoundingClientRect();
        const editorHeight = 200; // Approximate editor height

        // Use fixed positioning for better control
        editor.style.position = 'fixed';

        // Position horizontally (centered on cell, but keep within viewport)
        let leftPos = rect.left + (rect.width / 2) - 150; // 150px is half of editor width
        leftPos = Math.max(10, Math.min(leftPos, window.innerWidth - 320)); // Keep within viewport
        editor.style.left = leftPos + 'px';

        // Position vertically (above cell if space, below if not)
        if (rect.top - editorHeight > 10) {
            // Position above cell
            editor.style.top = (rect.top - editorHeight - 10) + 'px';
        } else {
            // Position below cell
            editor.style.top = (rect.bottom + 10) + 'px';
        }
    }

    /**
     * Hide all editors
     */
    hideAllEditors() {
        document.querySelectorAll('.worktime-editor').forEach(editor => {
            editor.classList.remove('show');
            editor.style.display = 'none';
        });
        this.currentEditor = null;
    }

    /**
     * Handle quick action button click
     * @param {HTMLElement} btn - Button element
     * @param {string} value - Worktime value
     */
    async setWorktime(btn, value) {
        const cell = btn.closest('.worktime-cell');
        if (!cell) {
            console.error('Cannot find worktime cell');
            return;
        }

        console.log('Quick action selected:', value);
        await this.submitWorktimeUpdate(cell, value);
    }

    /**
     * Save custom worktime from input field
     * @param {HTMLElement} btn - Save button element
     */
    async saveWorktime(btn) {
        const editor = btn.closest('.worktime-editor');
        const input = editor.querySelector('input[type="text"]');

        if (!input) {
            console.error('Cannot find input field in editor');
            alert('Error: Cannot find input field');
            return;
        }

        const value = input.value.trim();

        if (!value) {
            alert('Please enter a value');
            return;
        }

        console.log('Custom value entered:', value);

        // Validate value
        if (!this.validator.validateWorktimeValue(value)) {
            return; // Validation failed, user already alerted
        }

        const cell = btn.closest('.worktime-cell');
        if (!cell) {
            console.error('Cannot find worktime cell');
            return;
        }

        await this.submitWorktimeUpdate(cell, value);
    }

    /**
     * Submit worktime update
     * @param {HTMLElement} cell - Cell element
     * @param {string} value - Worktime value
     */
    async submitWorktimeUpdate(cell, value) {
        this.showLoadingIndicator(cell);

        try {
            const success = await this.dataService.submitWorktimeUpdate(cell, value);

            if (success) {
                this.showSuccessIndicator(cell);
                // DataService handles page reload
            } else {
                this.showErrorIndicator(cell);
            }
        } catch (error) {
            this.hideLoadingIndicator(cell);
            this.showErrorIndicator(cell);
            console.error('Submit error:', error);
        }
    }

    /**
     * Populate dynamic entry information in the editor
     * @param {HTMLElement} editor - Editor element
     * @param {HTMLElement} cell - Cell element
     */
    async populateEntryInfo(editor, cell) {
        const entryDetailsContainer = editor.querySelector('.entry-details');
        if (!entryDetailsContainer) return;

        const userId = cell.dataset.userId;
        const date = cell.dataset.date;

        if (!userId || !date) {
            entryDetailsContainer.innerHTML = `<small class="text-muted no-entry">
                <i class="bi bi-exclamation-triangle entry-icon"></i>Missing cell data
            </small>`;
            return;
        }

        // Show loading state
        entryDetailsContainer.innerHTML = `<small class="text-muted entry-loading">
            <i class="bi bi-hourglass-split entry-icon"></i>Loading entry details...
        </small>`;

        try {
            const entryData = await this.dataService.fetchEntryData(userId, date);
            const entryInfoHTML = this.generateEntryInfoHTML(entryData, cell);
            entryDetailsContainer.innerHTML = entryInfoHTML;
        } catch (error) {
            console.error('Error fetching entry data:', error);
            // Show fallback with current cell display
            const fallbackData = this.extractEntryFromCell(userId, date);
            const entryInfoHTML = this.generateEntryInfoHTML(fallbackData, cell);
            entryDetailsContainer.innerHTML = entryInfoHTML;
        }
    }

    /**
     * Fallback method to extract entry info from cell display
     * @param {string} userId - User ID
     * @param {string} date - Date string
     * @returns {Object} Entry data
     */
    extractEntryFromCell(userId, date) {
        const cell = document.querySelector(`[data-user-id="${userId}"][data-date="${date}"]`);
        if (!cell) {
            return { hasEntry: false };
        }

        const cellContent = cell.textContent.trim();
        const entryData = {
            hasEntry: cellContent !== '-',
            displayValue: cellContent
        };

        // Try to parse different formats using TimeOffService
        if (TimeOffService.isTimeOffType(cellContent)) {
            entryData.timeOffType = cellContent;
            entryData.timeOffLabel = TimeOffService.getLabel(cellContent);
        } else if (cellContent.startsWith('ZS-')) {
            entryData.timeOffType = cellContent;
            entryData.timeOffLabel = TimeOffService.getLabel(cellContent);
        } else if (/^(SN|CO|CM|CE|W)\d+$/.test(cellContent)) {
            // Handle TYPE+hours format (SN4, CO6, etc.)
            const type = cellContent.match(/^[A-Z]+/)[0];
            const hours = cellContent.match(/\d+$/)[0];
            entryData.timeOffType = type;
            entryData.timeOffLabel = TimeOffService.getLabel(type);
            entryData.overtimeHours = hours + 'h';
        } else if (cellContent.includes('h') || /^\d+$/.test(cellContent)) {
            // Regular work hours
            entryData.workHours = cellContent.includes('h') ? cellContent : cellContent + 'h';
        }

        return entryData;
    }

    /**
     * Generate HTML for entry information display
     * @param {Object} entryData - Entry data
     * @param {HTMLElement} cell - Cell element
     * @returns {string} HTML string
     */
    generateEntryInfoHTML(entryData, cell) {
        if (!entryData || !entryData.hasEntry) {
            return `<small class="text-muted no-entry">
                <i class="bi bi-calendar-x entry-icon"></i>No entry for this date
            </small>`;
        }

        let html = '';
        let hasData = false;

        // Start time
        if (entryData.dayStartTime || entryData.startTime) {
            const startTime = entryData.dayStartTime || entryData.startTime;
            html += `<small class="text-success">
                <i class="bi bi-play-circle entry-icon"></i><strong>Start:</strong> ${startTime}
            </small>`;
            hasData = true;
        }

        // End time
        if (entryData.dayEndTime || entryData.endTime) {
            const endTime = entryData.dayEndTime || entryData.endTime;
            html += `<small class="text-danger">
                <i class="bi bi-stop-circle entry-icon"></i><strong>End:</strong> ${endTime}
            </small>`;
            hasData = true;
        }

        // Temporary stops details
        if (entryData.temporaryStops && Array.isArray(entryData.temporaryStops) && entryData.temporaryStops.length > 0) {
            html += `<small class="text-warning d-block mb-1">
                <i class="bi bi-pause-circle entry-icon"></i><strong>Temporary Stops:</strong> ${entryData.temporaryStops.length} stops (${entryData.totalTemporaryStopMinutes || 0} min total)
            </small>`;

            // Add each temporary stop detail
            entryData.temporaryStops.forEach((stop, index) => {
                const startTime = stop.startTime ? formatDateTime(stop.startTime) : '--:--';
                const endTime = stop.endTime ? formatDateTime(stop.endTime) : '--:--';
                const duration = stop.duration || 0;

                html += `<small class="text-muted d-block ms-3" style="font-size: 0.85em;">
                    <span class="badge bg-warning text-dark me-1" style="font-size: 0.75em;">TS${index + 1}</span>
                    ${startTime} - ${endTime} <span class="badge bg-info" style="font-size: 0.7em;">${duration}min</span>
                </small>`;
            });
            hasData = true;
        } else if (entryData.totalTemporaryStopMinutes && parseInt(entryData.totalTemporaryStopMinutes) > 0) {
            html += `<small class="text-warning">
                <i class="bi bi-pause-circle entry-icon"></i><strong>Temp stops:</strong> ${entryData.totalTemporaryStopMinutes} minutes
            </small>`;
            hasData = true;
        }

        // Lunch break
        if (entryData.lunchBreakDeducted === true || entryData.lunchBreakDeducted === 'true') {
            html += `<small class="text-info">
                <i class="bi bi-cup-hot entry-icon"></i><strong>Lunch:</strong> Deducted
            </small>`;
            hasData = true;
        }

        // Time off type
        if (entryData.timeOffType && entryData.timeOffType !== 'null') {
            const timeOffLabel = entryData.timeOffLabel || TimeOffService.getLabel(entryData.timeOffType);
            const iconClass = TimeOffService.getIcon(entryData.timeOffType);
            const description = TimeOffService.getDescription(entryData.timeOffType);

            html += `<small class="text-primary">
                <i class="${iconClass} entry-icon"></i><strong>Type:</strong> ${timeOffLabel}
            </small>`;

            // Add helpful description
            if (description) {
                html += `<small class="text-muted d-block ms-3" style="font-size: 0.85em; font-style: italic; line-height: 1.3;">
                    <i class="bi bi-info-circle-fill entry-icon text-info"></i>${description}
                </small>`;
            }

            hasData = true;
        }

        // Work hours
        if (entryData.totalWorkedMinutes && parseInt(entryData.totalWorkedMinutes) > 0) {
            const hours = formatMinutesToHours(parseInt(entryData.totalWorkedMinutes));
            html += `<small class="text-success">
                <i class="bi bi-clock entry-icon"></i><strong>Work time:</strong> ${hours}
            </small>`;
            hasData = true;
        } else if (entryData.workHours) {
            html += `<small class="text-success">
                <i class="bi bi-clock entry-icon"></i><strong>Work time:</strong> ${entryData.workHours}
            </small>`;
            hasData = true;
        }

        // Overtime
        if (entryData.totalOvertimeMinutes && parseInt(entryData.totalOvertimeMinutes) > 0) {
            const hours = formatMinutesToHours(parseInt(entryData.totalOvertimeMinutes));
            const cellDate = cell.dataset.date;
            const overtimeLabel = this.getOvertimeTypeLabel(cellDate, entryData.timeOffType);
            html += `<small class="text-danger">
                <i class="bi bi-clock-history entry-icon"></i><strong>${overtimeLabel}:</strong> ${hours}
            </small>`;
            hasData = true;
        } else if (entryData.overtimeHours) {
            const cellDate = cell.dataset.date;
            const overtimeLabel = this.getOvertimeTypeLabel(cellDate, entryData.timeOffType);
            html += `<small class="text-danger">
                <i class="bi bi-clock-history entry-icon"></i><strong>${overtimeLabel}:</strong> ${entryData.overtimeHours}
            </small>`;
            hasData = true;
        }

        // Show current display value as fallback
        if (!hasData && entryData.displayValue) {
            html += `<small class="text-info">
                <i class="bi bi-eye entry-icon"></i><strong>Current:</strong> ${entryData.displayValue}
            </small>`;
            hasData = true;
        }

        // Date info
        const cellDate = cell.dataset.date;
        if (cellDate) {
            html += `<small class="text-muted">
                <i class="bi bi-calendar entry-icon"></i><strong>Date:</strong> ${cellDate}
            </small>`;
            hasData = true;
        }

        // Status
        if (entryData.adminSync && entryData.adminSync !== 'null') {
            const statusLabel = StatusService.getLabel(entryData.adminSync);
            const statusClass = StatusService.getClass(entryData.adminSync);
            html += `<small class="${statusClass}">
                <i class="bi bi-info-circle entry-icon"></i><strong>Status:</strong> ${statusLabel}
            </small>`;
            hasData = true;
        }

        if (!hasData) {
            return `<small class="text-muted no-entry">
                <i class="bi bi-info-circle entry-icon"></i>Entry exists but no details available
            </small>`;
        }

        return html;
    }

    /**
     * Determine the correct overtime type label based on date and time-off type
     * @param {string} dateString - Date string (YYYY-MM-DD)
     * @param {string} timeOffType - Time off type
     * @returns {string} Overtime label
     */
    getOvertimeTypeLabel(dateString, timeOffType) {
        const date = new Date(dateString);
        const dayOfWeek = date.getDay(); // 0 = Sunday, 6 = Saturday

        // Check if it's a holiday/vacation/event with work
        if (timeOffType) {
            const upperType = timeOffType.toUpperCase();
            if (['SN', 'CO', 'CE', 'CM'].some(type => upperType === type || upperType.startsWith(type + ':'))) {
                return 'Holiday Overtime';
            }

            // Weekend work
            if (upperType === 'W' || upperType.startsWith('W:')) {
                return 'Weekend Overtime';
            }
        }

        // Check if it's a weekend day
        if (dayOfWeek === 0 || dayOfWeek === 6) {
            return 'Weekend Overtime';
        }

        // Normal weekday overtime
        return 'Overtime';
    }

    /**
     * Visual feedback methods
     */
    showLoadingIndicator(cell) {
        cell.style.backgroundColor = '#fff3cd';
        cell.style.opacity = '0.7';
    }

    hideLoadingIndicator(cell) {
        cell.style.backgroundColor = '';
        cell.style.opacity = '';
    }

    showSuccessIndicator(cell) {
        this.hideLoadingIndicator(cell);
        cell.style.backgroundColor = '#d4edda';
        setTimeout(() => {
            cell.style.backgroundColor = '';
        }, 2000);
    }

    showErrorIndicator(cell) {
        this.hideLoadingIndicator(cell);
        cell.style.backgroundColor = '#f8d7da';
        setTimeout(() => {
            cell.style.backgroundColor = '';
        }, 3000);
    }
}
