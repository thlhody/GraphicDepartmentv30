/**
 * AdminRegisterState.js
 * Centralized state management for admin register
 *
 * Manages:
 * - User context (userId, year, month)
 * - Register entries data
 * - Bonus calculation data (metrics)
 * - Data extraction from table
 * - Entry status processing
 * - Validation logic
 *
 * @module features/register/admin/AdminRegisterState
 */

/**
 * AdminRegisterState - State management and data processing
 */
export class AdminRegisterState {

    /**
     * Create an AdminRegisterState instance
     * @param {Object} serverData - Initial data from server (window.serverData)
     */
    constructor(serverData = {}) {
        // User context
        this.currentUser = serverData.currentUser || null;
        this.currentYear = serverData.currentYear || new Date().getFullYear();
        this.currentMonth = serverData.currentMonth || (new Date().getMonth() + 1);

        // Register entries
        this.entries = serverData.entries || [];
        this.filteredEntries = [...this.entries];

        // Bonus calculation data - use server values or calculate from entries
        const hasServerCalculations = serverData.totalEntries && serverData.totalEntries > 0;

        if (hasServerCalculations) {
            // Use server-provided calculations
            this.bonusCalculationData = {
                totalEntries: serverData.totalEntries || 0,
                averageArticleNumbers: serverData.averageArticleNumbers || 0,
                averageGraphicComplexity: serverData.averageGraphicComplexity || 0,
                workedDays: serverData.workedDays || 0
            };
        } else if (this.entries.length > 0) {
            // Calculate from entries if server didn't provide or provided zeros
            const calculated = this.calculateSummaryFromEntries(this.entries);
            this.bonusCalculationData = {
                totalEntries: calculated.totalEntries,
                averageArticleNumbers: parseFloat(calculated.averageArticleNumbers),
                averageGraphicComplexity: parseFloat(calculated.averageGraphicComplexity),
                workedDays: serverData.workedDays || 0
            };
        } else {
            // No entries, use zeros
            this.bonusCalculationData = {
                totalEntries: 0,
                averageArticleNumbers: 0,
                averageGraphicComplexity: 0,
                workedDays: 0
            };
        }

        console.log('AdminRegisterState initialized:', this.getContext());
        console.log('Bonus calculation data:', this.bonusCalculationData);
    }

    /**
     * Get current context
     * @returns {Object} Context object
     * @public
     */
    getContext() {
        return {
            currentUser: this.currentUser,
            currentYear: this.currentYear,
            currentMonth: this.currentMonth,
            entriesCount: this.entries.length
        };
    }

    /**
     * Set user
     * @param {Object} user - User object
     * @public
     */
    setUser(user) {
        this.currentUser = user;
    }

    /**
     * Set year
     * @param {number} year - Year
     * @public
     */
    setYear(year) {
        this.currentYear = year;
    }

    /**
     * Set month
     * @param {number} month - Month (1-12)
     * @public
     */
    setMonth(month) {
        this.currentMonth = month;
    }

    /**
     * Set entries
     * @param {Array} entries - Array of register entries
     * @public
     */
    setEntries(entries) {
        this.entries = entries;
        this.filteredEntries = [...entries];
    }

    /**
     * Set bonus calculation data
     * @param {Object} data - Bonus calculation data
     * @public
     */
    setBonusCalculationData(data) {
        this.bonusCalculationData = { ...this.bonusCalculationData, ...data };
    }

    /**
     * Validate user context (user selected, year, month)
     * @returns {Object} Validation result {valid: boolean, error: string}
     * @public
     */
    validateUserContext() {
        if (!this.currentUser || !this.currentUser.userId) {
            return { valid: false, error: 'No user selected. Please select a user from the dropdown.' };
        }

        if (!this.currentYear || this.currentYear < 2020) {
            return { valid: false, error: 'Invalid year selected.' };
        }

        if (!this.currentMonth || this.currentMonth < 1 || this.currentMonth > 12) {
            return { valid: false, error: 'Invalid month selected.' };
        }

        return { valid: true };
    }

    /**
     * Validate save context (entries exist)
     * @returns {Object} Validation result {valid: boolean, error: string}
     * @public
     */
    validateSaveContext() {
        const userValidation = this.validateUserContext();
        if (!userValidation.valid) {
            return userValidation;
        }

        const visibleEntries = this.collectTableEntries();
        if (!visibleEntries || visibleEntries.length === 0) {
            return { valid: false, error: 'No entries to save. Please load data first.' };
        }

        return { valid: true };
    }

    /**
     * Collect all entries from table
     * @returns {Array} Array of entry objects with edit state
     * @public
     */
    collectTableEntries() {
        const rows = document.querySelectorAll('#registerTable tbody tr');
        const entries = [];

        rows.forEach(row => {
            // Skip empty placeholder rows
            if (row.querySelector('.text-muted') || row.cells.length < 12) {
                return;
            }

            const entry = this.extractRowData(row);
            if (entry) {
                entries.push(entry);
            }
        });

        return entries;
    }

    /**
     * Extract data from single table row
     * @param {HTMLTableRowElement} row - Table row
     * @returns {Object|null} Entry object
     * @private
     */
    extractRowData(row) {
        try {
            const cells = row.cells;

            // Parse date (format: dd/MM/yyyy)
            const dateParts = this.getTextContent(row, 1).split('/');
            const date = dateParts.length === 3
                ? `${dateParts[2]}-${dateParts[1].padStart(2, '0')}-${dateParts[0].padStart(2, '0')}`
                : '';

            const entry = {
                entryId: parseInt(this.getTextContent(row, 0)) || null,
                userId: this.currentUser?.userId || null,
                date: date,
                orderId: this.getTextContent(row, 2),
                productionId: this.getTextContent(row, 3),
                omsId: this.getTextContent(row, 4),
                clientName: this.getTextContent(row, 5),
                actionType: this.getTextContent(row, 6),
                printPrepTypes: this.extractPrintPrepTypes(row),
                colorsProfile: this.getTextContent(row, 8),
                articleNumbers: parseInt(this.getTextContent(row, 9)) || 0,
                graphicComplexity: parseFloat(this.getTextContent(row, 10)) || 0.0,
                observations: this.getTextContent(row, 11)
            };

            // Extract current status
            const currentStatus = this.extractCurrentStatus(row);
            entry.adminSync = currentStatus;

            // Analyze edit state
            const editState = this.analyzeRowEditState(row);
            entry._wasEdited = editState.wasEdited;
            entry._wasSelected = editState.wasSelected;
            entry._originalStatus = currentStatus;

            return entry;

        } catch (error) {
            console.error('Error extracting row data:', error, row);
            return null;
        }
    }

    /**
     * Extract print prep types from row
     * @param {HTMLTableRowElement} row - Table row
     * @returns {Array} Array of print prep type strings
     * @private
     */
    extractPrintPrepTypes(row) {
        const cell = row.cells[7];
        if (!cell) return ['DIGITAL'];

        const text = cell.textContent.trim();
        if (!text || text === '') return ['DIGITAL'];

        return text.split(',').map(s => s.trim()).filter(s => s.length > 0);
    }

    /**
     * Extract current status from row
     * @param {HTMLTableRowElement} row - Table row
     * @returns {string} Status value
     * @private
     */
    extractCurrentStatus(row) {
        const statusBadge = row.querySelector('.badge');
        return statusBadge ? statusBadge.textContent.trim() : 'USER_INPUT';
    }

    /**
     * Analyze row edit state
     * @param {HTMLTableRowElement} row - Table row
     * @returns {Object} Edit state {wasEdited: boolean, wasSelected: boolean}
     * @private
     */
    analyzeRowEditState(row) {
        const checkbox = row.querySelector('.entry-select');
        const editedCell = row.querySelector('.field-edited');

        return {
            wasEdited: !!editedCell,
            wasSelected: checkbox ? checkbox.checked : false
        };
    }

    /**
     * Get text content from table cell
     * @param {HTMLTableRowElement} row - Table row
     * @param {number} cellIndex - Cell index
     * @returns {string} Trimmed text content
     * @private
     */
    getTextContent(row, cellIndex) {
        const cell = row.cells[cellIndex];
        return cell ? cell.textContent.trim() : '';
    }

    /**
     * Process entry statuses based on edit state
     * @param {Array} entries - Array of entries
     * @returns {Array} Entries with updated statuses
     * @public
     */
    processEntryStatuses(entries) {
        return entries.map(entry => {
            const newStatus = this.determineNewStatus(entry._originalStatus, {
                wasEdited: entry._wasEdited,
                wasSelected: entry._wasSelected
            });

            return {
                ...entry,
                adminSync: newStatus
            };
        });
    }

    /**
     * Determine new status based on original status and edit state
     * @param {string} originalStatus - Original status
     * @param {Object} editState - Edit state {wasEdited, wasSelected}
     * @returns {string} New status
     * @private
     */
    determineNewStatus(originalStatus, editState) {
        const { wasEdited, wasSelected } = editState;

        // If edited or selected, mark as ADMIN_EDITED
        if (wasEdited || wasSelected) {
            return 'ADMIN_EDITED';
        }

        // Handle ADMIN_CHECK status (conflict resolution)
        if (originalStatus === 'ADMIN_CHECK') {
            return 'ADMIN_EDITED';
        }

        // Preserve other statuses
        return originalStatus;
    }

    /**
     * Analyze changes in processed entries
     * @param {Array} processedEntries - Processed entries with new statuses
     * @returns {Object} Changes summary
     * @public
     */
    analyzeChanges(processedEntries) {
        let approvals = 0;
        let edits = 0;
        let conflictResolutions = 0;

        processedEntries.forEach(entry => {
            const original = entry._originalStatus;
            const newStatus = entry.adminSync;

            if (newStatus === 'ADMIN_EDITED') {
                if (original === 'USER_INPUT') {
                    approvals++;
                } else if (original === 'ADMIN_CHECK') {
                    conflictResolutions++;
                }

                if (entry._wasEdited) {
                    edits++;
                }
            }
        });

        const total = processedEntries.length;
        const hasChanges = approvals > 0 || edits > 0 || conflictResolutions > 0;

        const summary = hasChanges
            ? `Approving ${approvals} entries, ${edits} edits, resolving ${conflictResolutions} conflicts`
            : 'No changes to save';

        return {
            hasChanges,
            approvals,
            edits,
            conflictResolutions,
            total,
            summary
        };
    }

    /**
     * Calculate summary from entries
     * @param {Array} entries - Array of entries
     * @returns {Object} Summary data
     * @public
     */
    calculateSummaryFromEntries(entries) {
        const validEntries = entries.filter(e => e.actionType !== 'IMPOSTARE');

        const totalEntries = validEntries.length;
        const avgArticles = totalEntries > 0
            ? validEntries.reduce((sum, e) => sum + e.articleNumbers, 0) / totalEntries
            : 0;
        const avgComplexity = totalEntries > 0
            ? validEntries.reduce((sum, e) => sum + e.graphicComplexity, 0) / totalEntries
            : 0;

        return {
            totalEntries,
            averageArticleNumbers: avgArticles.toFixed(1),
            averageGraphicComplexity: avgComplexity.toFixed(1),
            workedDays: this.bonusCalculationData.workedDays
        };
    }

    /**
     * Update summary from entries while preserving worked days
     * @param {Array} entries - Array of entries
     * @public
     */
    updateSummaryFromEntries(entries) {
        const summary = this.calculateSummaryFromEntries(entries);
        this.bonusCalculationData = { ...this.bonusCalculationData, ...summary };
    }
}
