/**
 * Time Off Management Module - Recyclebin Hover/Click Approach
 * Handles time off requests and CO/CM removal with simple X → Recyclebin → Remove flow
 * NO MODALS - Simple hover and click interactions
 */

const TimeOffManagementModule = {

    // ========================================================================
    // STATE MANAGEMENT
    // ========================================================================

    state: {
        confirmationTimeouts: new Map(), // Track confirmation timeouts by cell
        isInitialized: false
    },

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
   * Initialize time off management functionality
   */
    initialize() {
        // Prevent double initialization
        if (this.state.isInitialized) {
            console.log('⚠️ TimeOffManagementModule already initialized, skipping...');
            return;
        }

        console.log('Initializing Time Off Management Module with recyclebin approach...');

        this.initializeTimeOffForm();
        this.initializeRecyclebinDeletion();

        this.state.isInitialized = true;
        console.log('✅ Time Off Management Module initialized');
    },

    // ========================================================================
    // TIME OFF FORM MANAGEMENT (UNCHANGED)
    // ========================================================================

    /**
     * Initialize the compact time off request form
     */
    initializeTimeOffForm() {
        const form = document.getElementById('timeoffForm');
        const startDateInput = form?.querySelector('input[name="startDate"]');
        const endDateInput = form?.querySelector('input[name="endDate"]');
        const singleDayCheckbox = document.getElementById('singleDayRequest');
        const endDateContainer = document.getElementById('endDateContainer');

        if (!form) {
            console.error('❌ Time off form not found!');
            return;
        }

        console.log('✅ Time off form found:', form);

        // Set up single day toggle functionality
        this.setupSingleDayToggle(singleDayCheckbox, endDateContainer, startDateInput, endDateInput);

        // Set up date change handlers
        this.setupDateChangeHandlers(startDateInput, endDateInput, singleDayCheckbox);

        // Set up form submission
        this.setupFormSubmission(form, startDateInput, endDateInput, singleDayCheckbox);
    },

    /**
     * Set up single day request toggle
     */
    setupSingleDayToggle(singleDayCheckbox, endDateContainer, startDateInput, endDateInput) {
        if (!singleDayCheckbox) return;

        singleDayCheckbox.addEventListener('change', function () {
            const singleDayValue = document.getElementById('singleDayValue');

            if (this.checked) {
                endDateContainer.style.display = 'none';
                endDateInput.value = startDateInput.value;
                if (singleDayValue) singleDayValue.value = 'true';
            } else {
                endDateContainer.style.display = 'block';
                if (singleDayValue) singleDayValue.value = 'false';
            }
        });
    },

    /**
     * Set up date change handlers
     */
    setupDateChangeHandlers(startDateInput, endDateInput, singleDayCheckbox) {
        if (!startDateInput) return;

        startDateInput.addEventListener('change', function () {
            if (singleDayCheckbox.checked) {
                endDateInput.value = this.value;
            }
        });
    },

    /**
     * Set up form submission with validation
     */
    setupFormSubmission(form, startDateInput, endDateInput, singleDayCheckbox) {
        form.addEventListener('submit', (e) => {
            console.log('🚀 Form submit event triggered!');

            const formData = {
                startDate: startDateInput.value,
                endDate: endDateInput.value,
                timeOffType: form.querySelector('[name="timeOffType"]').value,
                singleDay: singleDayCheckbox.checked
            };

            console.log('📝 Form data:', formData);

            // Validate form data
            const validationError = this.validateTimeOffForm(formData);
            if (validationError) {
                console.error('❌ Validation failed:', validationError);
                e.preventDefault();
                this.showValidationError(validationError);
                return;
            }

            console.log('✅ Form validation passed, submitting...');

            if (window.UtilitiesModule) {
                window.UtilitiesModule.showLoadingOverlay();
            }

            // Show immediate feedback
            this.showProcessingMessage();
        });
    },

    /**
     * Validate time off form data
     */
    validateTimeOffForm(formData) {
        if (!formData.startDate) {
            return 'Start date is required';
        }

        if (!formData.singleDay && !formData.endDate) {
            return 'End date is required for multi-day requests';
        }

        // Validate date format
        if (!this.isValidDate(formData.startDate)) {
            return 'Invalid start date format';
        }

        if (!formData.singleDay && !this.isValidDate(formData.endDate)) {
            return 'Invalid end date format';
        }

        // Validate date range
        if (!formData.singleDay) {
            const startDate = new Date(formData.startDate);
            const endDate = new Date(formData.endDate);

            if (endDate < startDate) {
                return 'End date cannot be before start date';
            }
        }

        // Validate time off type
        // Note: CR (Recovery Leave - paid from overtime), CN (Unpaid Leave)
        if (!formData.timeOffType || !['CO', 'CM', 'CR', 'CN','CE','D'].includes(formData.timeOffType)) {
            return 'Please select a valid time off type';
        }

        return null;
    },

    /**
     * Check if date string is valid
     */
    isValidDate(dateString) {
        const date = new Date(dateString);
        return date instanceof Date && !isNaN(date) && dateString.match(/^\d{4}-\d{2}-\d{2}$/);
    },

    /**
     * Show validation error
     */
    showValidationError(message) {
        if (window.showToast) {
            window.showToast('Validation Error', message, 'error');
        } else {
            alert('Validation Error: ' + message);
        }
    },

    /**
     * Show processing message
     */
    showProcessingMessage() {
        if (window.showToast) {
            window.showToast('Processing Request', 'Submitting your time off request...', 'info', {
                duration: 2000
            });
        }
    },

    // ========================================================================
    // NEW: RECYCLEBIN DELETION FUNCTIONALITY
    // ========================================================================

    /**
     * Initialize recyclebin deletion handlers
     */
    initializeRecyclebinDeletion() {
        console.log('Initializing recyclebin deletion system...');

        // Get all deletable cells and attach direct event listeners
        this.attachCellEventListeners();

        // Handle delete button clicks with delegation (for dynamically created buttons)
        document.addEventListener('click', (e) => {
            console.log('🖱️ Click detected on:', e.target, e.target.className);

            // Check if click is on delete button or its icon
            if (e.target.classList.contains('timeoff-delete-btn') ||
            e.target.closest('.timeoff-delete-btn')) {

                console.log('🎯 Delete button click detected!');
                e.preventDefault();
                e.stopPropagation();

                const button = e.target.classList.contains('timeoff-delete-btn') ?
                e.target : e.target.closest('.timeoff-delete-btn');
                const cell = button.closest('.deletable-timeoff');

                if (cell) {
                    console.log('✅ Found cell for deletion:', cell);
                    this.handleDeleteButtonClick(cell);
                } else {
                    console.log('❌ Could not find deletable cell');
                }
            }
        });

        console.log('✅ Recyclebin deletion system initialized');
    },

    /**
     * Attach event listeners directly to cells (better performance)
     */
    attachCellEventListeners() {
        const deletableCells = document.querySelectorAll('.deletable-timeoff[data-deletable="true"]');

        deletableCells.forEach(cell => {
            if (this.isRemovableTimeOffType(cell)) {
                // Remove any existing listeners first
                cell.removeEventListener('mouseenter', cell._hoverHandler);
                cell.removeEventListener('mouseleave', cell._leaveHandler);

                // Create bound handlers
                cell._hoverHandler = () => this.showDeleteButton(cell);
                cell._leaveHandler = () => {
                    if (!this.isInConfirmationState(cell)) {
                        this.hideDeleteButton(cell);
                    }
                };

                // Attach listeners
                cell.addEventListener('mouseenter', cell._hoverHandler);
                cell.addEventListener('mouseleave', cell._leaveHandler);

                console.log('✅ Attached handlers to cell:', cell.getAttribute('data-timeoff-type'));
            }
        });

        console.log(`📎 Attached listeners to ${deletableCells.length} deletable cells`);
    },

    /**
     * Check if time off type is removable (CO/CM only)
     */
    isRemovableTimeOffType(cell) {
        const timeOffType = cell.getAttribute('data-timeoff-type');
        return timeOffType === 'CO' || timeOffType === 'CM';
    },

    /**
     * Check if cell is in confirmation state
     */
    isInConfirmationState(cell) {
        return cell.classList.contains('confirmation-state');
    },

    /**
     * Show red X delete button on hover
     */
    showDeleteButton(cell) {
        // Don't show if already in confirmation state
        if (this.isInConfirmationState(cell)) {
            console.log('Not showing delete button - cell in confirmation state');
            return;
        }

        // Don't create duplicate buttons
        if (cell.querySelector('.timeoff-delete-btn')) {
            console.log('Delete button already exists');
            return;
        }

        // Create red X button
        const deleteBtn = document.createElement('button');
        deleteBtn.className = 'timeoff-delete-btn';
        deleteBtn.innerHTML = '<i class="bi bi-x-lg"></i>';
        deleteBtn.title = 'Click to remove time off';

        // DEBUG: Add inline styles to ensure visibility
        deleteBtn.style.cssText = `
            position: absolute !important;
            top: 2px !important;
            right: 2px !important;
            background: #dc3545 !important;
            color: white !important;
            border: none !important;
            border-radius: 50% !important;
            width: 20px !important;
            height: 20px !important;
            font-size: 0.7rem !important;
            display: flex !important;
            align-items: center !important;
            justify-content: center !important;
            cursor: pointer !important;
            z-index: 1000 !important;
            box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2) !important;
            pointer-events: auto !important;
        `;

        // Position cell
        cell.style.position = 'relative';
        cell.appendChild(deleteBtn);

        console.log('✅ Delete button created and added to cell:', {
            cellType: cell.getAttribute('data-timeoff-type'),
            buttonExists: !!cell.querySelector('.timeoff-delete-btn')
        });
    },

    /**
     * Hide delete button
     */
    hideDeleteButton(cell) {
        this.removeDeleteButton(cell);
    },

    /**
     * Remove delete button from cell
     */
    removeDeleteButton(cell) {
        const existingBtn = cell.querySelector('.timeoff-delete-btn');
        if (existingBtn) {
            existingBtn.remove();
        }
    },

    /**
     * Handle delete button click - switch to recyclebin confirmation
     */
    handleDeleteButtonClick(cell) {
        const timeOffType = cell.getAttribute('data-timeoff-type');
        const date = cell.getAttribute('data-date');

        console.log('Delete button clicked for', timeOffType, 'on', date);

        // Validate removability
        if (!this.isRemovableTimeOffType(cell)) {
            console.warn('Cannot remove time off type:', timeOffType);
            this.showError(`${timeOffType} entries cannot be removed by users`);
            return;
        }

        // Switch to confirmation state
        this.showConfirmationState(cell);
    },

    /**
     * Show recyclebin confirmation state
     */
    showConfirmationState(cell) {
        const timeOffType = cell.getAttribute('data-timeoff-type');

        // Mark cell as in confirmation state
        cell.classList.add('confirmation-state');

        // Remove delete button
        this.removeDeleteButton(cell);

        // Create recyclebin button
        const recyclebinBtn = document.createElement('button');
        recyclebinBtn.className = 'timeoff-recyclebin-btn';
        recyclebinBtn.innerHTML = '<i class="bi bi-trash3"></i>';
        recyclebinBtn.title = 'Click to confirm removal';

        // Position button
        cell.appendChild(recyclebinBtn);

        // Change cell background to indicate confirmation state
        cell.style.backgroundColor = 'rgba(220, 53, 69, 0.15)';

        // Add click handler for recyclebin
        recyclebinBtn.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            this.executeRemoval(cell);
        });

        // Set timeout to revert to normal state
        const timeoutId = setTimeout(() => {
            this.revertToNormalState(cell);
        }, 3000);

        // Store timeout for cleanup
        const cellKey = `${cell.getAttribute('data-date')}-${timeOffType}`;
        this.state.confirmationTimeouts.set(cellKey, timeoutId);

        console.log('Showing recyclebin confirmation for', timeOffType, '(3 second timeout)');
    },

    /**
     * Revert cell to normal state (timeout or manual)
     */
    revertToNormalState(cell) {
        const timeOffType = cell.getAttribute('data-timeoff-type');
        const cellKey = `${cell.getAttribute('data-date')}-${timeOffType}`;

        // Clear timeout if exists
        if (this.state.confirmationTimeouts.has(cellKey)) {
            clearTimeout(this.state.confirmationTimeouts.get(cellKey));
            this.state.confirmationTimeouts.delete(cellKey);
        }

        // Remove confirmation state
        cell.classList.remove('confirmation-state');

        // Remove recyclebin button
        const recyclebinBtn = cell.querySelector('.timeoff-recyclebin-btn');
        if (recyclebinBtn) {
            recyclebinBtn.remove();
        }

        // Reset background
        cell.style.backgroundColor = '';

        console.log('Reverted to normal state for', timeOffType);
    },

    /**
     * Execute time off removal
     */
    async executeRemoval(cell) {
        const timeOffType = cell.getAttribute('data-timeoff-type');
        const date = cell.getAttribute('data-date');

        console.log('🗑️ Executing removal:', timeOffType, 'on', date);

        try {
            // Show loading state
            this.showLoadingState(cell);

            if (window.UtilitiesModule) {
                window.UtilitiesModule.showLoadingOverlay();
            }

            // Send removal request to server
            const result = await this.submitRemovalRequest(date);

            if (result.success) {
                this.handleRemovalSuccess(cell, timeOffType, result);
            } else {
                throw new Error(result.message || 'Removal failed');
            }

        } catch (error) {
            console.error('❌ Removal failed:', error);
            this.handleRemovalError(cell, error);
        } finally {
            if (window.UtilitiesModule) {
                window.UtilitiesModule.hideLoadingOverlay();
            }
        }
    },

    /**
     * Show loading state during removal
     */
    showLoadingState(cell) {
        const recyclebinBtn = cell.querySelector('.timeoff-recyclebin-btn');
        if (recyclebinBtn) {
            recyclebinBtn.innerHTML = '<div class="spinner-border spinner-border-sm"></div>';
            recyclebinBtn.disabled = true;
        }
    },

    /**
     * Submit removal request to server
     */
    /**
     * Submit removal request to server
     */
    async submitRemovalRequest(date) {
        const formData = new URLSearchParams();
        formData.append('date', date);
        formData.append('field', 'timeOff');
        formData.append('value', ''); // Empty value = remove time off

        console.log('📤 Sending removal request for date:', date);

        const response = await fetch('/user/time-management/update-field', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: formData
        });

        if (!response.ok) {
            let errorMessage = `Server returned ${response.status}`;
            try {
                // FIXED: Properly read JSON error response
                const errorResponse = await response.json();
                if (errorResponse.message) {
                    errorMessage = errorResponse.message;
                } else if (errorResponse.error) {
                    errorMessage = errorResponse.error;
                }
                console.log('📥 Error response:', errorResponse);
            } catch (parseError) {
                console.warn('Could not parse error response as JSON:', parseError);
                // Keep the fallback error message
            }
            throw new Error(errorMessage);
        }

        const result = await response.json();
        console.log('📥 Removal response:', result);

        return result;
    },

    /**
     * Handle successful removal
     */
    handleRemovalSuccess(cell, timeOffType, result) {
        console.log('✅ Removal successful for', timeOffType);

        // Clear confirmation state
        this.revertToNormalState(cell);

        // Update cell display to show removal
        this.updateCellAfterRemoval(cell, timeOffType);

        // Show success toast
        let successMessage = `${timeOffType} removed successfully`;
        if (result.holidayBalanceChange) {
            successMessage += `. ${result.holidayBalanceChange}`;
        }

        if (window.showToast) {
            window.showToast('Time Off Removed', successMessage, 'success', { duration: 3000 });
        }

        // Refresh page after short delay
        setTimeout(() => {
            console.log('🔄 Refreshing page after successful removal...');
            window.location.reload();
        }, 1500);
    },

    /**
     * Handle removal error
     */
    handleRemovalError(cell, error) {
        console.error('❌ Removal error:', error);

        // Revert to normal state
        this.revertToNormalState(cell);

        // Show error message
        const message = error.message || 'Failed to remove time off';
        if (window.showToast) {
            window.showToast('Removal Failed', message, 'error', { duration: 4000 });
        } else {
            alert('Removal Failed: ' + message);
        }
    },

    /**
     * Update cell display after successful removal
     */
    updateCellAfterRemoval(cell, originalTimeOffType) {
        const cellValue = cell.querySelector('.cell-value');
        if (!cellValue) return;

        // Check if there was work time with this time off
        const hasOvertimeWork = cell.getAttribute('data-has-overtime') === 'true';

        if (hasOvertimeWork) {
            // Show that work time is preserved but time off type is removed
            cellValue.innerHTML = '<span class="text-muted" title="Time off removed, work time preserved">Work Only</span>';
        } else {
            // No work time, just show empty
            cellValue.innerHTML = '<span class="text-muted">-</span>';
        }

        // Remove deletable styling
        cell.classList.remove('deletable-timeoff');
        cell.removeAttribute('data-timeoff-type');
        cell.removeAttribute('data-deletable');

        console.log(`✅ Updated cell display after ${originalTimeOffType} removal`);
    },

    /**
     * Show error message
     */
    showError(message) {
        if (window.showToast) {
            window.showToast('Error', message, 'error');
        } else {
            alert('Error: ' + message);
        }
    },

    // ========================================================================
    // PUBLIC API & UTILITIES
    // ========================================================================

    /**
     * Manually trigger time off form validation
     */
    validateCurrentForm() {
        const form = document.getElementById('timeoffForm');
        if (!form) return false;

        const formData = {
            startDate: form.querySelector('[name="startDate"]').value,
            endDate: form.querySelector('[name="endDate"]').value,
            timeOffType: form.querySelector('[name="timeOffType"]').value,
            singleDay: document.getElementById('singleDayRequest').checked
        };

        const error = this.validateTimeOffForm(formData);
        if (error) {
            this.showValidationError(error);
            return false;
        }

        return true;
    },

    /**
     * Reset time off form to initial state
     */
    resetTimeOffForm() {
        const form = document.getElementById('timeoffForm');
        if (form) {
            form.reset();

            // Reset single day checkbox
            const singleDayCheckbox = document.getElementById('singleDayRequest');
            const endDateContainer = document.getElementById('endDateContainer');

            if (singleDayCheckbox) {
                singleDayCheckbox.checked = false;
                if (endDateContainer) {
                    endDateContainer.style.display = 'block';
                }
            }
        }
    },

    /**
     * Check if there are any pending operations
     */
    hasPendingOperations() {
        return this.state.confirmationTimeouts.size > 0;
    },

    /**
     * Force cleanup all states (for debugging/testing)
     */
    forceCleanupAllStates() {
        // Clear all timeouts
        this.state.confirmationTimeouts.forEach(timeoutId => {
            clearTimeout(timeoutId);
        });
        this.state.confirmationTimeouts.clear();

        // Remove all confirmation states
        document.querySelectorAll('.confirmation-state').forEach(cell => {
            this.revertToNormalState(cell);
        });

        console.log('🧹 All recyclebin states forcibly cleaned up');
    },

    /**
     * Get current state info (for debugging)
     */
    getStateInfo() {
        return {
            isInitialized: this.state.isInitialized,
            activeConfirmations: this.state.confirmationTimeouts.size,
            confirmationCells: Array.from(document.querySelectorAll('.confirmation-state')).map(cell => ({
                date: cell.getAttribute('data-date'),
                type: cell.getAttribute('data-timeoff-type')
            }))
        };
    }
};

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = TimeOffManagementModule;
}

// Make available globally
window.TimeOffManagementModule = TimeOffManagementModule;