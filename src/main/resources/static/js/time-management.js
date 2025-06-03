/**
 * Refactored Unified Time Management JavaScript
 * Enhanced with toast notifications and improved editing controls
 */

document.addEventListener('DOMContentLoaded', function () {
    console.log('Time Management page loaded - compact layout');

    // Initialize all components
    initializeTimeOffForm();
    initializeInlineEditing();
    initializeTooltips();

    // Handle success messages with toast system
    handleServerMessages();
});

// ========================================================================
// COMPACT TIME OFF FORM FUNCTIONALITY
// ========================================================================

function initializeTimeOffForm() {
    const form = document.getElementById('timeoffForm');
    const startDateInput = form?.querySelector('input[name="startDate"]');
    const endDateInput = form?.querySelector('input[name="endDate"]');
    const singleDayCheckbox = document.getElementById('singleDayRequest');
    const endDateContainer = document.getElementById('endDateContainer');

    if (!form) return;

    console.log('Initializing compact time off form');

    // Single day request handling
    if (singleDayCheckbox) {
        singleDayCheckbox.addEventListener('change', function () {
            if (this.checked) {
                endDateContainer.style.display = 'none';
                endDateInput.value = startDateInput.value;
            } else {
                endDateContainer.style.display = 'block';
            }
        });
    }

    // Start date changes
    if (startDateInput) {
        startDateInput.addEventListener('change', function () {
            if (singleDayCheckbox.checked) {
                endDateInput.value = this.value;
            }
        });
    }

    // Form submission with toast feedback
    form.addEventListener('submit', function(e) {
        console.log('Submitting time off request...');
        showLoadingOverlay();

        // Show immediate feedback
        showToast('Processing Request', 'Submitting your time off request...', 'info', {
            duration: 2000
        });
    });
}

// ========================================================================
// ENHANCED INLINE EDITING WITH NON-EDITABLE FIELDS
// ========================================================================

let currentlyEditing = null;
let editingTimeout = null;

function initializeInlineEditing() {
    const table = document.getElementById('worktimeTable');
    if (!table) return;

    console.log('Initializing enhanced inline editing with restrictions');

    // Add click handlers to editable cells
    const editableCells = table.querySelectorAll('.editable-cell');
    editableCells.forEach(cell => {
        // Check if cell should be disabled or non-editable
        checkCellEditability(cell);

        // Add double-click handler
        cell.addEventListener('dblclick', function(e) {
            e.preventDefault();
            e.stopPropagation();
            handleCellDoubleClick(cell);
        });

        // Add hover effects for editable cells only
        cell.addEventListener('mouseenter', function() {
            if (!cell.classList.contains('disabled') && !cell.classList.contains('non-editable')) {
                const editIcon = cell.querySelector('.edit-icon');
                if (editIcon && !editIcon.classList.contains('d-none-force')) {
                    editIcon.classList.remove('d-none');
                }
            }
        });

        cell.addEventListener('mouseleave', function() {
            const editIcon = cell.querySelector('.edit-icon');
            if (editIcon) {
                editIcon.classList.add('d-none');
            }
        });
    });

    // Handle clicks outside to cancel editing
    document.addEventListener('click', function(e) {
        if (currentlyEditing && !currentlyEditing.contains(e.target)) {
            cancelEditing();
        }
    });

    // Handle escape key
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape' && currentlyEditing) {
            cancelEditing();
        }
    });
}

async function checkCellEditability(cell) {
    const date = cell.getAttribute('data-date');
    const field = cell.getAttribute('data-field');
    const originalValue = cell.getAttribute('data-original');

    if (!date || !field) return;

    // Get the row to check for time off values
    const row = cell.closest('tr');
    const timeOffCell = row?.querySelector('.editable-cell[data-field="timeOff"]');
    const timeOffValue = timeOffCell?.getAttribute('data-original');

    // Logic: If time off exists, disable start/end time editing
    if ((field === 'startTime' || field === 'endTime') && timeOffValue && timeOffValue.trim() !== '') {
        cell.classList.add('non-editable');
        cell.setAttribute('title', `Cannot edit time fields when time off (${timeOffValue}) is set. Remove time off first.`);
        console.log(`Disabling ${field} because time off ${timeOffValue} is set for ${date}`);
        return;
    }

    // Time off field is always editable (can be added, changed, or removed)
    if (field === 'timeOff') {
        // Time off is always editable regardless of value
        console.log(`Time off field is editable for ${date}`);
    }

    try {
        const response = await fetch(`/user/time-management/can-edit?date=${date}&field=${field}`);
        const result = await response.json();

        if (!result.canEdit) {
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
}

function handleCellDoubleClick(cell) {
    // Check if cell is disabled or non-editable
    if (cell.classList.contains('disabled')) {
        showToast('Cannot Edit', cell.getAttribute('title') || 'This field cannot be edited', 'warning');
        return;
    }

    if (cell.classList.contains('non-editable')) {
        const field = cell.getAttribute('data-field');
        const row = cell.closest('tr');
        const timeOffCell = row?.querySelector('.editable-cell[data-field="timeOff"]');
        const timeOffValue = timeOffCell?.getAttribute('data-original');

        if ((field === 'startTime' || field === 'endTime') && timeOffValue) {
            showToast('Remove Time Off First',
                `Cannot edit ${field} while ${timeOffValue} is set. Remove time off to enable time editing.`,
                'warning', { duration: 4000 });
        } else {
            showToast('Read-Only Field', cell.getAttribute('title') || 'This field cannot be modified', 'warning');
        }
        return;
    }

    if (currentlyEditing) {
        cancelEditing();
    }

    startEditing(cell);
}

function startEditing(cell) {
    const field = cell.getAttribute('data-field');
    console.log('Starting edit for cell:', field);

    currentlyEditing = cell;
    cell.classList.add('editing');

    const currentValue = cell.getAttribute('data-original') || '';
    const cellValue = cell.querySelector('.cell-value');

    // Create appropriate editor based on field type
    let editor;

    if (field === 'timeOff') {
        editor = createTimeOffEditor(currentValue);
    } else if (field === 'startTime' || field === 'endTime') {
        editor = createEnhancedTimeEditor(currentValue);
    } else {
        console.error('Unknown field type:', field);
        return;
    }

    // Replace cell content with editor
    cellValue.style.display = 'none';
    cell.appendChild(editor);

    // Focus the editor
    editor.focus();

    // Set up editor event handlers
    setupEditorHandlers(editor, cell);

    // Add help text
    showEditingHelp(cell, field);
}

function createEnhancedTimeEditor(currentValue) {
    const editor = document.createElement('input');
    editor.className = 'inline-editor';
    editor.type = 'time';

    // Ensure 24-hour format
    if (currentValue) {
        // Convert to 24-hour format if needed
        const time24 = convertTo24Hour(currentValue);
        editor.value = time24;
    } else {
        editor.value = '';
    }

    editor.step = '60'; // 1 minute intervals

    // Force 24-hour display by setting attributes
    editor.setAttribute('pattern', '[0-9]{2}:[0-9]{2}');
    editor.setAttribute('placeholder', 'HH:MM');

    return editor;
}

function createTimeOffEditor(currentValue) {
    const editor = document.createElement('select');
    editor.className = 'inline-editor form-select form-select-sm';

    // Add options (excluding system-generated ones)
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
}

function setupEditorHandlers(editor, cell) {
    // Save on blur
    editor.addEventListener('blur', function() {
        saveEdit(cell, editor.value);
    });

    // Save on Enter, cancel on Escape
    editor.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            saveEdit(cell, editor.value);
        } else if (e.key === 'Escape') {
            e.preventDefault();
            cancelEditing();
        }
    });

    // Auto-save after pause in typing (for time inputs)
    if (editor.type === 'time') {
        editor.addEventListener('input', function() {
            clearTimeout(editingTimeout);
            editingTimeout = setTimeout(() => {
                if (currentlyEditing === cell) {
                    saveEdit(cell, editor.value);
                }
            }, 2000); // Save after 2 seconds of no changes
        });
    }
}

function showEditingHelp(cell, field) {
    const helpText = document.createElement('div');
    helpText.className = 'editing-help';

    const helpTexts = {
        'startTime': 'Enter time in 24-hour format (e.g., 09:00, 17:30)',
        'endTime': 'Enter time in 24-hour format (e.g., 09:00, 17:30)',
        'timeOff': 'Select CO for vacation, CM for medical leave'
    };

    helpText.textContent = helpTexts[field] || 'Press Enter to save, Escape to cancel';
    cell.appendChild(helpText);

    // Remove help text after 4 seconds
    setTimeout(() => {
        helpText.remove();
    }, 4000);
}

async function saveEdit(cell, value) {
    const date = cell.getAttribute('data-date');
    const field = cell.getAttribute('data-field');
    const originalValue = cell.getAttribute('data-original') || '';

    console.log('Saving edit:', { date, field, value, originalValue });

    // Show saving indicator
    cell.classList.add('cell-saving');
    addFieldStatus(cell, 'saving');

    try {
        // Client-side validation
        const validationError = validateFieldValue(field, value);
        if (validationError) {
            throw new Error(validationError);
        }

        // Send to server
        const formData = new URLSearchParams();
        formData.append('date', date);
        formData.append('field', field);
        formData.append('value', value || '');

        const response = await fetch('/user/time-management/update-field', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: formData
        });

        const result = await response.json();

        if (result.success) {
            // Update cell with new value
            updateCellDisplay(cell, field, value);
            addFieldStatus(cell, 'success');

            // Special handling for time off changes - refresh editability of related fields
            if (field === 'timeOff') {
                const row = cell.closest('tr');
                const startTimeCell = row?.querySelector('.editable-cell[data-field="startTime"]');
                const endTimeCell = row?.querySelector('.editable-cell[data-field="endTime"]');

                // Refresh editability for start and end time fields
                if (startTimeCell) {
                    refreshCellEditability(startTimeCell);
                }
                if (endTimeCell) {
                    refreshCellEditability(endTimeCell);
                }

                if (value && value.trim() !== '') {
                    showToast('Time Off Added', `${field} set to ${value}. Start/End time editing disabled.`, 'success', {
                        duration: 3000
                    });
                } else {
                    showToast('Time Off Removed', 'Time off cleared. Start/End time editing enabled.', 'success', {
                        duration: 3000
                    });
                }
            } else {
                showToast('Field Updated', `${field} updated successfully`, 'success', {
                    duration: 2000
                });
            }

            // Schedule page refresh
            setTimeout(() => {
                console.log('Auto-refreshing page after field update...');
                window.location.reload();
            }, 1500);

        } else {
            throw new Error(result.error || 'Failed to update field');
        }

    } catch (error) {
        console.error('Error saving edit:', error);
        addFieldStatus(cell, 'error');

        // Use toast notification for errors
        showToast('Update Failed', error.message, 'error', {
            duration: 4000
        });

        // Clear field on error
        updateCellDisplay(cell, field, '');
    } finally {
        cell.classList.remove('cell-saving');
        finishEditing(cell);
    }
}

function cancelEditing() {
    if (!currentlyEditing) return;

    console.log('Cancelling edit');
    finishEditing(currentlyEditing);
}

function finishEditing(cell) {
    // Remove editor and show original content
    const editor = cell.querySelector('.inline-editor');
    const helpText = cell.querySelector('.editing-help');

    if (editor) editor.remove();
    if (helpText) helpText.remove();

    const cellValue = cell.querySelector('.cell-value');
    if (cellValue) cellValue.style.display = '';

    cell.classList.remove('editing');
    clearTimeout(editingTimeout);
    currentlyEditing = null;
}

function updateCellDisplay(cell, field, value) {
    const cellValue = cell.querySelector('.cell-value');

    if (field === 'timeOff') {
        // Update time off display
        const badge = cell.querySelector('.badge');
        if (value) {
            if (badge) {
                badge.textContent = value;
                // Update badge class based on type
                badge.className = 'badge rounded-pill small ' + (value === 'CO' ? 'bg-info' : 'bg-danger');
            } else {
                const newBadge = document.createElement('span');
                newBadge.className = 'badge rounded-pill small ' + (value === 'CO' ? 'bg-info' : 'bg-danger');
                newBadge.textContent = value;
                cellValue.innerHTML = '';
                cellValue.appendChild(newBadge);
            }
        } else {
            cellValue.textContent = '-';
        }
    } else if (field === 'startTime' || field === 'endTime') {
        // Ensure time is displayed in 24-hour format
        const displayTime = value ? convertTo24Hour(value) : '-';
        cellValue.textContent = displayTime;
    } else {
        // Default display
        cellValue.textContent = value || '-';
    }

    // Update data attribute
    cell.setAttribute('data-original', value || '');
}

function addFieldStatus(cell, status) {
    // Remove existing status
    const existingStatus = cell.querySelector('.field-status');
    if (existingStatus) existingStatus.remove();

    // Add new status indicator
    const statusIndicator = document.createElement('div');
    statusIndicator.className = `field-status ${status}`;
    cell.appendChild(statusIndicator);

    // Remove status after 3 seconds
    setTimeout(() => {
        statusIndicator.remove();
    }, 3000);
}

// ========================================================================
// CELL EDITABILITY REFRESH UTILITIES
// ========================================================================

function refreshCellEditability(cell) {
    // Reset classes
    cell.classList.remove('disabled', 'non-editable');
    cell.removeAttribute('title');

    // Re-check editability
    checkCellEditability(cell);
}

function refreshRowEditability(row) {
    // Refresh all editable cells in the row
    const editableCells = row.querySelectorAll('.editable-cell');
    editableCells.forEach(cell => {
        refreshCellEditability(cell);
    });
}

// ========================================================================
// TIME FORMAT UTILITIES
// ========================================================================

function convertTo24Hour(timeString) {
    if (!timeString) return '';

    // If already in 24-hour format (HH:MM), return as is
    if (/^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/.test(timeString)) {
        return timeString;
    }

    // If in 12-hour format, convert to 24-hour
    const time12Match = timeString.match(/^(\d{1,2}):(\d{2})\s*(AM|PM)$/i);
    if (time12Match) {
        let hours = parseInt(time12Match[1]);
        const minutes = time12Match[2];
        const period = time12Match[3].toUpperCase();

        if (period === 'AM' && hours === 12) {
            hours = 0;
        } else if (period === 'PM' && hours !== 12) {
            hours += 12;
        }

        return `${hours.toString().padStart(2, '0')}:${minutes}`;
    }

    // Return original if no conversion needed
    return timeString;
}

function convertTo12Hour(timeString) {
    if (!timeString) return '';

    const timeParts = timeString.split(':');
    if (timeParts.length !== 2) return timeString;

    let hours = parseInt(timeParts[0]);
    const minutes = timeParts[1];

    const period = hours >= 12 ? 'PM' : 'AM';
    hours = hours % 12 || 12;

    return `${hours}:${minutes} ${period}`;
}

// ========================================================================
// VALIDATION FUNCTIONS
// ========================================================================

function validateFieldValue(field, value) {
    if (!value || value.trim() === '') {
        return null; // Empty values are allowed (clears the field)
    }

    switch (field) {
        case 'startTime':
        case 'endTime':
            // Validate 24-hour time format (HH:MM)
            if (!/^([01]?[0-9]|2[0-3]):[0-5][0-9]$/.test(value)) {
                return 'Invalid time format. Use 24-hour format (e.g., 09:00, 17:30)';
            }
            break;

        case 'timeOff':
            if (value !== 'CO' && value !== 'CM') {
                return 'Invalid time off type. Use CO for vacation or CM for medical';
            }
            break;

        default:
            return 'Unknown field type';
    }

    return null;
}

// ========================================================================
// SERVER MESSAGE HANDLING WITH TOAST SYSTEM
// ========================================================================

function handleServerMessages() {
    // The toast system will automatically process server-side messages
    // This function is called to ensure any additional client-side processing

    // Check for success messages and provide additional feedback
    const successAlert = document.querySelector('.alert-success');
    if (successAlert) {
        // Hide the original alert since toast system handles it
        successAlert.style.display = 'none';

        setTimeout(function() {
            console.log('Auto-refreshing page after successful operation...');
            window.location.href = window.location.pathname;
        }, 3000); // 3 second delay
    }

    // Check for error messages
    const errorAlert = document.querySelector('.alert-danger');
    if (errorAlert) {
        errorAlert.style.display = 'none';
    }
}

// ========================================================================
// UTILITY FUNCTIONS
// ========================================================================

function initializeTooltips() {
    // Initialize Bootstrap tooltips if available
    if (typeof bootstrap !== 'undefined' && bootstrap.Tooltip) {
        const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
        tooltipTriggerList.map(function (tooltipTriggerEl) {
            return new bootstrap.Tooltip(tooltipTriggerEl);
        });
    }
}

function showLoadingOverlay() {
    const overlay = document.getElementById('loadingOverlay');
    if (overlay) {
        overlay.classList.remove('d-none');
    }
}

function hideLoadingOverlay() {
    const overlay = document.getElementById('loadingOverlay');
    if (overlay) {
        overlay.classList.add('d-none');
    }
}

// ========================================================================
// ERROR HANDLING
// ========================================================================

window.addEventListener('error', function(e) {
    console.error('JavaScript error:', e.error);
    hideLoadingOverlay();

    // Use toast system for error reporting
    showToast('System Error', 'An unexpected error occurred. Please refresh the page.', 'error', {
        persistent: true
    });
});

window.addEventListener('unhandledrejection', function(e) {
    console.error('Unhandled promise rejection:', e.reason);
    hideLoadingOverlay();

    showToast('Network Error', 'A network error occurred. Please check your connection.', 'error', {
        persistent: true
    });
});

// ========================================================================
// PERFORMANCE MONITORING
// ========================================================================

const perfStart = performance.now();
window.addEventListener('load', function() {
    const loadTime = performance.now() - perfStart;
    console.log(`Compact time management page loaded in ${loadTime.toFixed(2)}ms`);
});