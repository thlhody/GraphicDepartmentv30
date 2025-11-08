/**
 * AdminRegisterView.js
 * UI layer for admin register management
 *
 * Handles:
 * - Form initialization and event handling
 * - Inline CG editing
 * - Table display and updates
 * - Save workflow orchestration
 * - Conflict resolution (ADMIN_CHECK entries)
 * - Export functionality
 * - User feedback (alerts, messages)
 *
 * @module features/register/admin/AdminRegisterView
 */

import { API } from '../../../core/api.js';
import { ToastNotification } from '../../../components/ToastNotification.js';

/**
 * AdminRegisterView - UI management and event handling
 */
export class AdminRegisterView {

    /**
     * Create an AdminRegisterView instance
     * @param {AdminRegisterState} state - State management instance
     */
    constructor(state) {
        this.state = state;
        this.toast = new ToastNotification();

        this.initializeElements();
    }

    /**
     * Initialize DOM element references
     * @private
     */
    initializeElements() {
        this.userSelect = document.getElementById('userSelect');
        this.yearSelect = document.getElementById('yearSelect');
        this.monthSelect = document.getElementById('monthSelect');
        this.loadButton = document.getElementById('loadData');
        this.clearButton = document.getElementById('clearTable');
        this.saveButton = document.getElementById('saveChanges');
        this.exportButton = document.getElementById('exportButton');
        this.confirmButton = document.getElementById('confirmChanges');
        this.selectAllCheckbox = document.getElementById('selectAllEntries');
        this.registerTable = document.getElementById('registerTable');
    }

    /**
     * Initialize all event handlers
     * @public
     */
    initialize() {
        this.syncFormWithUrlParams();
        this.initializeFormHandling();
        this.initializeControllers();
        this.initializeEventListeners();
        this.initializeEditableCG();
        this.initializeFormValidation();
        this.loadRegisterSummary();

        console.log('AdminRegisterView initialized');
    }

    /**
     * Sync form dropdowns with URL parameters
     * @private
     */
    syncFormWithUrlParams() {
        const urlParams = new URLSearchParams(window.location.search);

        const userId = urlParams.get('userId');
        const year = urlParams.get('year');
        const month = urlParams.get('month');

        if (userId && this.userSelect) {
            this.userSelect.value = userId;
        }

        if (year && this.yearSelect) {
            this.yearSelect.value = year;
        }

        if (month && this.monthSelect) {
            this.monthSelect.value = month;
        }

        this.markFormFieldsAsFilled();
    }

    /**
     * Initialize form load handling
     * @private
     */
    initializeFormHandling() {
        if (!this.loadButton) return;

        this.loadButton.addEventListener('click', (e) => {
            e.preventDefault();

            const userId = this.userSelect?.value;
            const year = this.yearSelect?.value;
            const month = this.monthSelect?.value;

            if (!userId) {
                this.showError('Please select a user');
                return;
            }

            if (!year) {
                this.showError('Please select a year');
                return;
            }

            if (!month) {
                this.showError('Please select a month');
                return;
            }

            // Navigate to the page with parameters
            const url = `/admin/register?userId=${userId}&year=${year}&month=${month}`;
            window.location.href = url;
        });
    }

    /**
     * Initialize select-all checkbox
     * @private
     */
    initializeControllers() {
        if (!this.selectAllCheckbox) return;

        this.selectAllCheckbox.addEventListener('change', (e) => {
            const checked = e.target.checked;
            const checkboxes = document.querySelectorAll('.entry-select');

            checkboxes.forEach(checkbox => {
                checkbox.checked = checked;
            });

            console.log(`Select all: ${checked ? 'checked' : 'unchecked'} ${checkboxes.length} entries`);
        });
    }

    /**
     * Initialize event listeners for main buttons
     * @private
     */
    initializeEventListeners() {
        // Clear button
        if (this.clearButton) {
            this.clearButton.addEventListener('click', () => this.handleClear());
        }

        // Save button
        if (this.saveButton) {
            this.saveButton.addEventListener('click', () => this.handleSaveChanges());
        }

        // Export button
        if (this.exportButton) {
            this.exportButton.addEventListener('click', () => this.handleExport());
        }

        // Confirm changes button
        if (this.confirmButton) {
            this.confirmButton.addEventListener('click', () => this.handleConfirmAllChanges());
        }
    }

    /**
     * Initialize inline CG editing
     * @private
     */
    initializeEditableCG() {
        const cgCells = document.querySelectorAll('.cg-editable');

        cgCells.forEach(cell => {
            // Click to edit
            cell.addEventListener('click', () => {
                if (!cell.querySelector('input')) {
                    this.createCGEditor(cell);
                }
            });

            // Highlight ADMIN_CHECK entries
            const row = cell.closest('tr');
            if (row) {
                const statusBadge = row.querySelector('.badge');
                if (statusBadge && statusBadge.textContent.trim() === 'ADMIN_CHECK') {
                    row.classList.add('table-warning', 'admin-check-entry');
                    cell.classList.add('admin-attention');

                    // Auto-check row checkbox
                    const checkbox = row.querySelector('.entry-select');
                    if (checkbox) {
                        checkbox.checked = true;
                    }
                }
            }
        });

        console.log(`Initialized ${cgCells.length} editable CG cells`);
    }

    /**
     * Create inline CG editor
     * @param {HTMLElement} cell - Table cell to edit
     * @private
     */
    createCGEditor(cell) {
        const originalValue = cell.textContent.trim();
        const row = cell.closest('tr');

        // Create input element
        const input = document.createElement('input');
        input.type = 'number';
        input.step = '0.1';
        input.min = '0.0';
        input.max = '9.0';
        input.value = originalValue;
        input.className = 'form-control form-control-sm';
        input.style.width = '80px';

        // Replace cell content with input
        cell.innerHTML = '';
        cell.appendChild(input);
        input.focus();
        input.select();

        // Save on blur
        input.addEventListener('blur', () => {
            this.saveCGValue(cell, input, originalValue);
        });

        // Save on Enter, cancel on Escape
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                this.saveCGValue(cell, input, originalValue);

                // Move to next editable cell (arrow down behavior)
                setTimeout(() => {
                    const nextRow = row.nextElementSibling;
                    if (nextRow) {
                        const nextCGCell = nextRow.querySelector('.cg-editable');
                        if (nextCGCell) {
                            nextCGCell.click();
                        }
                    }
                }, 50);
            } else if (e.key === 'Escape') {
                cell.textContent = originalValue;
            } else if (e.key === 'ArrowDown') {
                e.preventDefault();
                this.saveCGValue(cell, input, originalValue);

                setTimeout(() => {
                    const nextRow = row.nextElementSibling;
                    if (nextRow) {
                        const nextCGCell = nextRow.querySelector('.cg-editable');
                        if (nextCGCell) {
                            nextCGCell.click();
                        }
                    }
                }, 50);
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                this.saveCGValue(cell, input, originalValue);

                setTimeout(() => {
                    const prevRow = row.previousElementSibling;
                    if (prevRow) {
                        const prevCGCell = prevRow.querySelector('.cg-editable');
                        if (prevCGCell) {
                            prevCGCell.click();
                        }
                    }
                }, 50);
            }
        });
    }

    /**
     * Save CG value from inline editor
     * @param {HTMLElement} cell - Table cell
     * @param {HTMLInputElement} input - Input element
     * @param {string} originalValue - Original value
     * @private
     */
    saveCGValue(cell, input, originalValue) {
        const newValue = parseFloat(input.value);
        const row = cell.closest('tr');

        // Validate
        if (isNaN(newValue) || newValue < 0.0 || newValue > 9.0) {
            this.showError('Graphic complexity must be between 0.0 and 9.0');
            cell.textContent = originalValue;
            return;
        }

        // Update cell
        cell.textContent = newValue.toFixed(1);

        // Mark as edited if value changed
        if (newValue !== parseFloat(originalValue)) {
            cell.classList.add('field-edited');

            // Auto-check row checkbox
            const checkbox = row?.querySelector('.entry-select');
            if (checkbox) {
                checkbox.checked = true;
            }

            console.log(`CG updated: ${originalValue} → ${newValue.toFixed(1)}`);
        }
    }

    /**
     * Initialize form field validation styling
     * @private
     */
    initializeFormValidation() {
        [this.userSelect, this.yearSelect, this.monthSelect].forEach(select => {
            if (!select) return;

            const updateClass = () => {
                if (select.value) {
                    select.classList.add('field-filled');
                    select.classList.remove('field-empty');
                } else {
                    select.classList.add('field-empty');
                    select.classList.remove('field-filled');
                }
            };

            select.addEventListener('change', updateClass);
            updateClass(); // Initial state
        });
    }

    /**
     * Mark form fields as filled
     * @private
     */
    markFormFieldsAsFilled() {
        [this.userSelect, this.yearSelect, this.monthSelect].forEach(select => {
            if (select && select.value) {
                select.classList.add('field-filled');
                select.classList.remove('field-empty');
            }
        });
    }

    /**
     * Load register summary data
     * @private
     */
    async loadRegisterSummary() {
        const validation = this.state.validateUserContext();
        if (!validation.valid) {
            return; // Silently return if no user selected yet
        }

        try {
            const { userId } = this.state.currentUser;
            const { currentYear, currentMonth } = this.state;

            // Load worked days
            const response = await API.get(`/admin/register/worked-days`, {
                userId,
                year: currentYear,
                month: currentMonth
            });

            if (response.ok) {
                const workedDays = await response.json();
                this.state.bonusCalculationData.workedDays = workedDays;
                this.updateSummaryDisplay();
            }

        } catch (error) {
            console.error('Error loading register summary:', error);
        }
    }

    /**
     * Update summary display fields
     * @public
     */
    updateSummaryDisplay() {
        const data = this.state.bonusCalculationData;

        this.updateFieldValue('totalEntries', data.totalEntries);
        this.updateFieldValue('averageArticles', data.averageArticleNumbers);
        this.updateFieldValue('averageComplexity', data.averageGraphicComplexity);
        this.updateFieldValue('workedDays', data.workedDays);
    }

    /**
     * Update a form field value
     * @param {string} fieldId - Field ID
     * @param {string|number} value - Value to set
     * @private
     */
    updateFieldValue(fieldId, value) {
        const field = document.getElementById(fieldId);
        if (field) {
            field.value = value;
        }
    }

    /**
     * Handle save changes
     * @private
     */
    async handleSaveChanges() {
        const validation = this.state.validateSaveContext();
        if (!validation.valid) {
            this.showError(validation.error);
            return;
        }

        try {
            // Collect entries
            const tableEntries = this.state.collectTableEntries();

            // Process statuses
            const processedEntries = this.state.processEntryStatuses(tableEntries);

            // Analyze changes
            const changesSummary = this.state.analyzeChanges(processedEntries);

            if (!changesSummary.hasChanges) {
                this.showWarning('No changes to save. Please select entries or edit values.');
                return;
            }

            console.log('Saving changes:', changesSummary);

            // Build payload
            const payload = {
                userId: this.state.currentUser.userId,
                year: this.state.currentYear,
                month: this.state.currentMonth,
                entries: processedEntries,
                summary: changesSummary
            };

            // Execute save
            const response = await API.post('/admin/register/save', payload);

            if (response.ok) {
                this.handleSaveSuccess(changesSummary);
            } else {
                const errorText = await response.text();
                this.handleSaveError(new Error(errorText || 'Save failed'));
            }

        } catch (error) {
            this.handleSaveError(error);
        }
    }

    /**
     * Handle save success
     * @param {Object} summary - Changes summary
     * @private
     */
    handleSaveSuccess(summary) {
        this.showSuccess(`Changes saved successfully! ${summary.summary}`);
        this.resetEditState();
        this.schedulePageReload();
    }

    /**
     * Handle save error
     * @param {Error} error - Error object
     * @private
     */
    handleSaveError(error) {
        console.error('Save error:', error);

        let message = 'Failed to save changes';

        if (error.message.includes('404')) {
            message = 'Save endpoint not found. Please check server configuration.';
        } else if (error.message.includes('403')) {
            message = 'Access denied. You may not have permission to save changes.';
        } else if (error.message.includes('500')) {
            message = 'Server error occurred. Please try again or contact support.';
        } else if (error.message) {
            message = error.message;
        }

        this.showError(message);
    }

    /**
     * Reset edit state (clear edited marks, uncheck boxes)
     * @private
     */
    resetEditState() {
        // Clear edited marks
        document.querySelectorAll('.field-edited').forEach(el => {
            el.classList.remove('field-edited');
        });

        // Uncheck all checkboxes
        document.querySelectorAll('.entry-select').forEach(checkbox => {
            checkbox.checked = false;
        });

        if (this.selectAllCheckbox) {
            this.selectAllCheckbox.checked = false;
        }
    }

    /**
     * Schedule page reload after delay
     * @private
     */
    schedulePageReload() {
        setTimeout(() => {
            const currentParams = new URLSearchParams(window.location.search);
            const userId = currentParams.get('userId');
            const year = currentParams.get('year');
            const month = currentParams.get('month');

            if (userId && year && month) {
                window.location.href = `/admin/register?userId=${userId}&year=${year}&month=${month}`;
            } else {
                window.location.reload();
            }
        }, 1500);
    }

    /**
     * Handle confirm all changes (resolve ADMIN_CHECK conflicts)
     * @private
     */
    async handleConfirmAllChanges() {
        const adminCheckRows = document.querySelectorAll('.admin-check-entry');

        if (adminCheckRows.length === 0) {
            this.showWarning('No conflicts to resolve.');
            return;
        }

        if (!confirm(`Resolve ${adminCheckRows.length} conflict(s)?`)) {
            return;
        }

        try {
            const { userId } = this.state.currentUser;
            const { currentYear, currentMonth } = this.state;

            const response = await API.post('/admin/register/confirm-all-changes', {
                userId,
                year: currentYear,
                month: currentMonth
            });

            if (response.ok) {
                this.showSuccess(`${adminCheckRows.length} conflict(s) resolved successfully!`);
                this.schedulePageReload();
            } else {
                throw new Error('Failed to confirm changes');
            }

        } catch (error) {
            this.handleSaveError(error);
        }
    }

    /**
     * Handle clear button - reset form and reload without parameters
     * @private
     */
    handleClear() {
        if (confirm('Clear the table and reset selection?')) {
            window.location.href = '/admin/register';
        }
    }

    /**
     * Handle export to Excel
     * @private
     */
    handleExport() {
        const { userId } = this.state.currentUser || {};
        const { currentYear, currentMonth } = this.state;

        if (!userId) {
            this.showError('Please select a user first');
            return;
        }

        const exportUrl = `/admin/register/export?userId=${userId}&year=${currentYear}&month=${currentMonth}`;
        window.location.href = exportUrl;
    }

    /**
     * Show error alert
     * @param {string} message - Error message
     * @public
     */
    showError(message) {
        this.toast.error('Error', message);
    }

    /**
     * Show warning alert
     * @param {string} message - Warning message
     * @public
     */
    showWarning(message) {
        this.toast.warning('Warning', message);
    }

    /**
     * Show success alert
     * @param {string} message - Success message
     * @public
     */
    showSuccess(message) {
        this.toast.success('Success', message);
    }
}
