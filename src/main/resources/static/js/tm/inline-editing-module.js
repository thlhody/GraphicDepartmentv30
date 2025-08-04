/**
 * Inline Editing Module - Handles all inline cell editing functionality
 * Manages cell editors, validation, saving, and status-based restrictions
 */

const InlineEditingModule = {

    // ========================================================================
    // STATE MANAGEMENT
    // ========================================================================

    state: {
        currentlyEditing: null,
        editingTimeout: null,
        isSaving: false,
        isInitialized: false
    },

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize inline editing functionality
     */
    initialize() {
        // Prevent multiple initializations
        if (this.state.isInitialized) {
            console.warn('Inline editing already initialized, skipping...');
            return;
        }

        const table = document.querySelector('.table');
        if (!table) {
            console.warn('Table not found, skipping inline editing initialization');
            return;
        }

        console.log('Initializing enhanced inline editing with status-based restrictions');

        this.setupEditableCells(table);
        this.setupGlobalEventHandlers();

        // Mark as initialized
        this.state.isInitialized = true;
        console.log('✅ Inline Editing Module initialized');
    },

    /**
     * Set up editable cells with event handlers
     */
    setupEditableCells(table) {
        const editableCells = table.querySelectorAll('.editable-cell');
        editableCells.forEach(cell => {
            // Check status-based editability
            if (window.StatusDisplayModule) {
                window.StatusDisplayModule.checkStatusBasedEditability(cell);
            }

            // Remove any existing event listeners to prevent duplicates
            cell.removeEventListener('dblclick', this.handleCellDoubleClick);

            // Add double-click handler
            cell.addEventListener('dblclick', (e) => {
                e.preventDefault();
                e.stopPropagation();
                this.handleCellDoubleClick(cell);
            });

            // Add hover effects for editable cells only
            cell.addEventListener('mouseenter', () => {
                if (!cell.classList.contains('status-locked')) {
                    const editIcon = cell.querySelector('.edit-icon');
                    if (editIcon && !editIcon.classList.contains('d-none-force')) {
                        editIcon.classList.remove('d-none');
                    }
                }
            });

            cell.addEventListener('mouseleave', () => {
                const editIcon = cell.querySelector('.edit-icon');
                if (editIcon) {
                    editIcon.classList.add('d-none');
                }
            });
        });
    },

    /**
     * Set up global event handlers
     */
    setupGlobalEventHandlers() {
        // Handle clicks outside to cancel editing
        document.addEventListener('click', (e) => {
            if (this.state.currentlyEditing) {
                const isInsideEditor = this.state.currentlyEditing.contains(e.target) ||
                e.target.closest('.inline-editor') ||
                e.target.classList.contains('inline-editor');

                if (!isInsideEditor) {
                    console.log('👆 Click outside detected, canceling edit');
                    setTimeout(() => {
                        if (this.state.currentlyEditing && !this.state.isSaving) {
                            this.cancelEditing();
                        }
                    }, 50);
                }
            }
        });

        // Handle escape key
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && this.state.currentlyEditing) {
                e.preventDefault();
                this.cancelEditing();
            }
        });
    },

    // ========================================================================
    // CELL EDITING WORKFLOW
    // ========================================================================

    /**
     * Handle double-click on editable cell
     * @param {HTMLElement} cell - Cell element that was double-clicked
     */
    handleCellDoubleClick(cell) {
        // Prevent multiple simultaneous editing attempts
        if (this.state.isSaving) {
            console.log('⏳ Save in progress, ignoring double-click...');
            return;
        }

        // If already editing this exact cell, ignore
        if (this.state.currentlyEditing === cell) {
            console.log('🔄 Already editing this cell, ignoring duplicate double-click...');
            return;
        }

        // Check various edit restrictions
        if (!this.canEditCell(cell)) {
            return;
        }

        // Cancel any existing editing before starting new one
        if (this.state.currentlyEditing && this.state.currentlyEditing !== cell) {
            console.log('🔄 Canceling previous edit to start new one...');
            this.cancelEditing();
        }

        // Add small delay to ensure clean start
        setTimeout(() => {
            this.startEditing(cell);
        }, 50);
    },

    /**
     * Check if cell can be edited
     * @param {HTMLElement} cell - Cell to check
     * @returns {boolean} True if cell can be edited
     */
    canEditCell(cell) {
        // Check if cell is disabled
        if (cell.classList.contains('disabled')) {
            this.showEditError('Cannot Edit', cell.getAttribute('title') || 'This field cannot be edited', 'warning');
            return false;
        }

        // Check if cell is non-editable (time off conflicts)
        if (cell.classList.contains('non-editable')) {
            this.handleTimeOffConflict(cell);
            return false;
        }

        // Check if cell is locked by status
        if (cell.classList.contains('status-locked')) {
            this.handleStatusLocked(cell);
            return false;
        }

        return true;
    },

    /**
     * Handle time off editing conflicts
     */
    handleTimeOffConflict(cell) {
        const field = cell.getAttribute('data-field');
        const row = cell.closest('tr');
        const timeOffCell = row?.querySelector('.editable-cell[data-field="timeOff"]');
        const timeOffValue = timeOffCell?.getAttribute('data-original');

        if ((field === 'startTime' || field === 'endTime') && timeOffValue) {
            this.showEditError('Remove Time Off First',
                `Cannot edit ${field} while ${timeOffValue} is set. Remove time off to enable time editing.`,
                'warning', { duration: 4000 });
        } else {
            this.showEditError('Read-Only Field',
                cell.getAttribute('title') || 'This field cannot be modified',
                'warning');
        }
    },

    /**
     * Handle status-locked cells
     */
    handleStatusLocked(cell) {
        const row = cell.closest('tr');
        const statusInfo = window.StatusDisplayModule?.getRowStatusInfo(row);

        this.showEditError('Cannot Edit',
            statusInfo?.fullDescription || 'This field cannot be edited due to its current status',
            'warning',
            { duration: 4000 });
    },

    /**
     * Show edit error message
     */
    showEditError(title, message, type, options = {}) {
        if (window.showToast) {
            window.showToast(title, message, type, options);
        } else {
            alert(`${title}: ${message}`);
        }
    },

    // ========================================================================
    // EDITOR CREATION AND MANAGEMENT
    // ========================================================================

    /**
     * Start editing a cell
     * @param {HTMLElement} cell - Cell to edit
     */
    startEditing(cell) {
        const field = cell.getAttribute('data-field');
        console.log('Starting edit for cell:', field);

        // Force cleanup of any existing editors in this cell
        this.cleanupExistingEditors(cell);

        this.state.currentlyEditing = cell;
        cell.classList.add('editing');

        const currentValue = cell.getAttribute('data-original') || '';
        const cellValue = window.UtilitiesModule?.getCellContentContainer(cell);

        if (!cellValue) {
            console.error('Could not find cell content container in cell:', cell);
            return;
        }

        // Create appropriate editor based on field type
        const editor = this.createEditor(field, currentValue);
        if (!editor) {
            console.error('Failed to create editor for field:', field);
            return;
        }

        // Set up editor
        editor.setAttribute('data-field-editor', field);
        editor.setAttribute('data-editor-id', Date.now());

        // Replace cell content with editor
        cellValue.style.display = 'none';
        cell.appendChild(editor);

        // Focus with delay to prevent immediate blur
        setTimeout(() => {
            if (editor.parentNode) {
                editor.focus();
                if (editor.select) {
                    editor.select();
                }
            }
        }, 100);

        // Set up editor event handlers
        this.setupEditorHandlers(editor, cell);

        // Add help text
        this.showEditingHelp(cell, field);
    },

    /**
     * Clean up any existing editors in cell
     */
    cleanupExistingEditors(cell) {
        const existingEditors = cell.querySelectorAll('.inline-editor');
        existingEditors.forEach(editor => {
            console.log('🧹 Removing existing editor before creating new one');
            editor.remove();
        });

        const existingHelp = cell.querySelectorAll('.editing-help');
        existingHelp.forEach(help => {
            help.remove();
        });
    },

    /**
     * Create editor based on field type
     * @param {string} field - Field type
     * @param {string} currentValue - Current field value
     * @returns {HTMLElement} Editor element
     */
    createEditor(field, currentValue) {
        switch (field) {
            case 'timeOff':
                return this.createTimeOffEditor(currentValue);
            case 'startTime':
            case 'endTime':
                return this.createTimeEditor(currentValue);
            case 'tempStop':
                return this.createTempStopEditor(currentValue);
            default:
                console.error('Unknown field type:', field);
                return null;
        }
    },

    /**
     * Create time editor using TimeInputModule
     */
    createTimeEditor(currentValue) {
        if (window.TimeInputModule && typeof window.TimeInputModule.create24HourEditor === 'function') {
            return window.TimeInputModule.create24HourEditor(currentValue, {
                helpText: 'Enter time in 24-hour format (e.g., 08:30, 13:45). You can type 0830 or 1345',
                width: '100px'
            });
        } else {
            // Fallback implementation
            const editor = document.createElement('input');
            editor.className = 'inline-editor form-control form-control-sm';
            editor.type = 'time';
            editor.value = currentValue || '';
            editor.style.width = '100px';
            return editor;
        }
    },

    /**
     * Create time off type editor
     */
    createTimeOffEditor(currentValue) {
        const editor = document.createElement('select');
        editor.className = 'inline-editor form-select form-select-sm';

        const options = [
            { value: '', text: '-' },
            { value: 'CO', text: 'CO (Vacation)' },
            { value: 'CM', text: 'CM (Medical)' }
        ];

        options.forEach(opt => {
            const option = document.createElement('option');
            option.value = opt.value;
            option.textContent = opt.text;
            if (opt.value === currentValue) {
                option.selected = true;
            }
            editor.appendChild(option);
        });

        return editor;
    },

    /**
     * Create temporary stop editor
     */
    createTempStopEditor(currentValue) {
        const editor = document.createElement('input');
        editor.className = 'inline-editor form-control form-control-sm';
        editor.type = 'number';
        editor.min = '0';
        editor.max = '720';  // 12 hours = 720 minutes
        editor.step = '1';
        editor.value = currentValue || '0';
        editor.placeholder = 'Minutes';
        editor.style.width = '80px';
        editor.style.textAlign = 'center';
        return editor;
    },

    // ========================================================================
    // EDITOR EVENT HANDLING
    // ========================================================================

    /**
     * Set up event handlers for editor
     * @param {HTMLElement} editor - Editor element
     * @param {HTMLElement} cell - Parent cell element
     */
    setupEditorHandlers(editor, cell) {
        const field = cell.getAttribute('data-field');

        // Prevent immediate event firing
        let isInitializing = true;
        setTimeout(() => {
            isInitializing = false;
        }, 200);

        // Handle Enter key
        editor.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                e.stopPropagation();
                console.log('📝 Manual save triggered by Enter key');
                this.saveEdit(cell, editor.value);
            } else if (e.key === 'Escape') {
                e.preventDefault();
                e.stopPropagation();
                console.log('🚫 Edit cancelled by Escape key');
                this.cancelEditing();
            }
        });

        // Handle blur with initialization check
        editor.addEventListener('blur', (e) => {
            if (isInitializing) {
                console.log('⏭️ Skipping blur save during initialization');
                return;
            }

            setTimeout(() => {
                if (this.state.currentlyEditing === cell && !this.state.isSaving) {
                    console.log('📝 Manual save triggered by blur event');
                    this.saveEdit(cell, editor.value);
                }
            }, 100);
        });

        // Auto-save after pause in typing (for time inputs only)
        if (editor.type === 'time') {
            this.setupAutoSave(editor, cell);
        }

        // Prevent click events on editor from bubbling up
        ['click', 'mousedown', 'mouseup'].forEach(eventType => {
            editor.addEventListener(eventType, (e) => {
                e.stopPropagation();
            });
        });
    },

    /**
     * Set up auto-save for time inputs
     */
    setupAutoSave(editor, cell) {
        let autoSaveTimeout;

        editor.addEventListener('input', () => {
            // Don't auto-save during initialization
            if (editor.hasAttribute('data-initializing')) return;

            clearTimeout(autoSaveTimeout);
            autoSaveTimeout = setTimeout(() => {
                if (this.state.currentlyEditing === cell && !this.state.isSaving) {
                    console.log('⏱️ Auto-save triggered after 2 seconds of inactivity');
                    this.saveEdit(cell, editor.value);
                }
            }, 2000);
        });

        // Clear timeout when editor is removed
        editor.addEventListener('blur', () => {
            clearTimeout(autoSaveTimeout);
        });
    },

    // ========================================================================
    // HELP TEXT DISPLAY
    // ========================================================================

    /**
     * Show editing help text
     * @param {HTMLElement} cell - Cell being edited
     * @param {string} field - Field type
     */
    showEditingHelp(cell, field) {
        // Remove any existing help text
        const existingHelp = cell.querySelectorAll('.editing-help');
        existingHelp.forEach(help => help.remove());

        let helpElement;

        if ((field === 'startTime' || field === 'endTime') && window.TimeInputModule) {
            // Use TimeInputModule for time fields
            helpElement = window.TimeInputModule.createHelpText(field);
        } else {
            // Create standard help for other fields
            helpElement = this.createStandardHelpText(field);
        }

        // Position relative to cell
        cell.style.position = 'relative';
        cell.appendChild(helpElement);

        // Remove help text after 5 seconds
        setTimeout(() => {
            if (helpElement.parentNode) {
                helpElement.remove();
            }
        }, 5000);
    },

    /**
     * Create standard help text element
     */
    createStandardHelpText(field) {
        const helpText = document.createElement('div');
        helpText.className = 'editing-help';

        const helpTexts = {
            'timeOff': 'Select CO for vacation, CM for medical leave',
            'tempStop': 'Enter temporary stop minutes (0-720, max 12 hours)'
        };

        const helpMessage = helpTexts[field] || 'Enter new value';
        helpText.innerHTML = `${helpMessage}<br><small>Press Enter to save, Escape to cancel</small>`;

        // Standard help text styling
        Object.assign(helpText.style, {
            position: 'absolute',
            bottom: '-45px',
            left: '50%',
            transform: 'translateX(-50%)',
            backgroundColor: '#212529',
            color: 'white',
            padding: '0.5rem',
            borderRadius: '0.375rem',
            fontSize: '0.75rem',
            whiteSpace: 'nowrap',
            zIndex: '1000',
            boxShadow: '0 2px 8px rgba(0,0,0,0.2)'
        });

        return helpText;
    },

    // ========================================================================
    // SAVE AND CANCEL OPERATIONS
    // ========================================================================

    /**
     * Save cell edit
     * @param {HTMLElement} cell - Cell being edited
     * @param {string} value - New value
     */
    async saveEdit(cell, value) {
        // Prevent multiple simultaneous saves
        if (this.state.isSaving) {
            console.log('⏳ Save already in progress, skipping duplicate save attempt...');
            return;
        }

        this.state.isSaving = true;

        try {
            // Extract and validate data
            const { row, date, field, originalValue } = this.extractCellData(cell);

            console.log('💾 Saving edit:', { date, field, value, originalValue });

            // Show saving indicator
            cell.classList.add('cell-saving');
            this.addFieldStatus(cell, 'saving');

            // Client-side validation
            const validationError = window.UtilitiesModule?.validateFieldValue(field, value);
            if (validationError) {
                throw new Error(validationError);
            }

            // Send to server
            const result = await this.submitFieldUpdate(date, field, value);

            if (result.success) {
                await this.handleSaveSuccess(cell, field, value, result, row);
            } else {
                throw new Error(result.error || result.message || 'Update failed - unknown error');
            }

        } catch (error) {
            this.handleSaveError(cell, field, error);
        } finally {
            cell.classList.remove('cell-saving');
            this.finishEditing(cell);
            this.state.isSaving = false;
        }
    },

    /**
     * Extract data from cell for saving
     */
    extractCellData(cell) {
        const row = cell.closest('tr');
        if (!row) {
            throw new Error('Cannot find parent row for this cell');
        }

        let date = row.getAttribute('data-date') || row.dataset.date;
        if (!date) {
            throw new Error('Cannot determine date for this row. Row may be missing data-date attribute.');
        }

        const field = cell.getAttribute('data-field');
        if (!field) {
            throw new Error('Cell is missing data-field attribute');
        }

        // Validate date format
        if (!window.UtilitiesModule?.validateDateFormat(date)) {
            throw new Error(`Invalid date format: "${date}". Expected YYYY-MM-DD format.`);
        }

        const originalValue = cell.getAttribute('data-original') || '';

        return { row, date, field, originalValue };
    },

    /**
     * Submit field update to server
     */
    async submitFieldUpdate(date, field, value) {
        const formData = new URLSearchParams();
        formData.append('date', date);
        formData.append('field', field);
        formData.append('value', value || '');

        console.log(`📤 Sending data to server:`, { date, field, value: value || '' });

        const response = await fetch('/user/time-management/update-field', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: formData
        });

        console.log(`📥 Response status: ${response.status}`);

        if (!response.ok) {
            let errorMessage = `Server returned ${response.status}`;
            try {
                const errorText = await response.text();
                console.error(`❌ Server error (${response.status}):`, errorText);
                errorMessage = `Server error: ${errorText}`;
            } catch (parseError) {
                console.error(`❌ Could not parse error response:`, parseError);
            }
            throw new Error(errorMessage);
        }

        return await response.json();
    },

    /**
     * Handle successful save
     */
    async handleSaveSuccess(cell, field, value, result, row) {
        // Update cell display
        if (window.WorkTimeDisplayModule?.updateCellDisplay) {
            window.WorkTimeDisplayModule.updateCellDisplay(cell, field, value);
        } else {
            // Fallback update
            cell.setAttribute('data-original', value || '');
            const cellValue = window.UtilitiesModule?.getCellContentContainer(cell);
            if (cellValue) {
                cellValue.textContent = value || '-';
            }
        }

        this.addFieldStatus(cell, 'success');

        // Special handling for time off changes
        if (field === 'timeOff') {
            this.handleTimeOffSuccess(row, value);
        } else {
            this.showSuccessMessage(field);
        }

        // Schedule page refresh after successful update
        setTimeout(() => {
            console.log('🔄 Auto-refreshing page after successful field update...');
            window.location.reload();
        }, 1500);
    },

    /**
     * Handle time off field success
     */
    handleTimeOffSuccess(row, value) {
        const startTimeCell = row.querySelector('.editable-cell[data-field="startTime"]');
        const endTimeCell = row.querySelector('.editable-cell[data-field="endTime"]');

        // Refresh editability for start and end time fields
        if (window.StatusDisplayModule) {
            if (startTimeCell) window.StatusDisplayModule.refreshCellEditability(startTimeCell);
            if (endTimeCell) window.StatusDisplayModule.refreshCellEditability(endTimeCell);
        }

        if (value && value.trim() !== '') {
            this.showSuccessMessage('timeOff', `Time off set to ${value}. Start/End time editing disabled.`, 3000);
        } else {
            this.showSuccessMessage('timeOff', 'Time off cleared. Start/End time editing enabled.', 3000);
        }
    },

    /**
     * Show success message
     */
    showSuccessMessage(field, customMessage = null, duration = 2000) {
        const message = customMessage || `${field} updated successfully`;
        if (window.showToast) {
            window.showToast('Field Updated', message, 'success', { duration });
        }
    },

    /**
     * Handle save error
     */
    handleSaveError(cell, field, error) {
        console.error('❌ Save error:', error);

        this.addFieldStatus(cell, 'error');

        // Show user-friendly error message
        const userMessage = window.UtilitiesModule?.handleError ?
        window.UtilitiesModule._getUserFriendlyErrorMessage(error, 'save') :
        error.message;

        if (window.showToast) {
            window.showToast('Update Failed', userMessage, 'error', { duration: 4000 });
        }

        // Clear field on error to show original value
        if (window.WorkTimeDisplayModule?.updateCellDisplay) {
            window.WorkTimeDisplayModule.updateCellDisplay(cell, field, '');
        }
    },

    /**
     * Cancel current editing
     */
    cancelEditing() {
        if (!this.state.currentlyEditing) return;

        console.log('🚫 Cancelling edit');
        this.finishEditing(this.state.currentlyEditing);
    },

    /**
     * Finish editing cleanup
     */
    finishEditing(cell) {
        if (!cell) return;

        console.log('🏁 Finishing edit for cell');

        // Remove all editors and help text
        const editors = cell.querySelectorAll('.inline-editor');
        const helpTexts = cell.querySelectorAll('.editing-help');

        editors.forEach(editor => {
            console.log('🗑️ Removing editor:', editor.getAttribute('data-editor-id'));
            editor.remove();
        });

        helpTexts.forEach(help => {
            help.remove();
        });

        // Restore cell content visibility
        const cellValue = window.UtilitiesModule?.getCellContentContainer(cell);
        if (cellValue) {
            cellValue.style.display = '';
        }

        cell.classList.remove('editing');

        // Clear timeouts
        if (this.state.editingTimeout) {
            clearTimeout(this.state.editingTimeout);
        }

        // Clear currently editing reference
        if (this.state.currentlyEditing === cell) {
            this.state.currentlyEditing = null;
        }
    },

    // ========================================================================
    // VISUAL FEEDBACK
    // ========================================================================

    /**
     * Add field status indicator
     * @param {HTMLElement} cell - Cell element
     * @param {string} status - Status type (saving, success, error)
     */
    addFieldStatus(cell, status) {
        // Remove existing status
        const existingStatus = cell.querySelector('.field-status');
        if (existingStatus) existingStatus.remove();

        // Add new status indicator
        const statusIndicator = document.createElement('div');
        statusIndicator.className = `field-status ${status}`;
        cell.appendChild(statusIndicator);

        // Remove status after 3 seconds
        setTimeout(() => {
            if (statusIndicator.parentNode) {
                statusIndicator.remove();
            }
        }, 3000);
    },

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Get current editing state
     * @returns {Object} Current state
     */
    getCurrentState() {
        return {
            isEditing: !!this.state.currentlyEditing,
            currentCell: this.state.currentlyEditing,
            isSaving: this.state.isSaving
        };
    },

    /**
     * Force cancel any current editing
     */
    forceCancel() {
        if (this.state.currentlyEditing) {
            this.cancelEditing();
        }
    }
};

// Make handleCellDoubleClick available globally for onclick handlers
window.handleCellDoubleClick = function(cell) {
    InlineEditingModule.handleCellDoubleClick(cell);
};

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = InlineEditingModule;
}

// Make available globally
window.InlineEditingModule = InlineEditingModule;