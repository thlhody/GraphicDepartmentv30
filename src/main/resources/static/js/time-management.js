/**
 * Refactored Unified Time Management JavaScript
 * Enhanced with toast notifications and improved editing controls
 */

// ========================================================================
// ENHANCED PAGE INITIALIZATION
// ========================================================================


document.addEventListener('DOMContentLoaded', function () {
    console.log('Time Management page loaded - compact layout');

    // Initialize all components
    initializeTimeOffForm();
    initializeInlineEditing();
    initializeTooltips();
    initializeSNOvertimeDisplay(); // NEW: Initialize SN display

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
// Global flag to prevent multiple simultaneous saves
let isSaving = false;

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

    // ADD THIS SECTION for tempStop field:
    if (field === 'tempStop') {
        // Temp stop follows same rules as start/end time
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

    // CORRECTED: SN special case handling
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
        // But still check date range for adding new time off
    }

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

    // FIX: Handle both cell-value and cell-content structures
    let cellValue = cell.querySelector('.cell-value');
    if (!cellValue) {
        cellValue = cell.querySelector('.cell-content');
    }

    if (!cellValue) {
        console.error('Could not find cell-value or cell-content in cell:', cell);
        return;
    }

    // Create appropriate editor based on field type
    let editor;

    if (field === 'timeOff') {
        editor = createTimeOffEditor(currentValue);
    } else if (field === 'startTime' || field === 'endTime') {
        editor = createEnhancedTimeEditor(currentValue);
    } else if (field === 'tempStop') {
        editor = createTempStopEditor(currentValue);
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

/**
 * NEW: Create temporary stop editor (add this new function)
 */
function createTempStopEditor(currentValue) {
    const editor = document.createElement('input');
    editor.className = 'inline-editor';
    editor.type = 'number';
    editor.min = '0';
    editor.max = '720';  // 12 hours = 720 minutes
    editor.step = '1';
    editor.value = currentValue || '0';
    editor.placeholder = 'Minutes';
    editor.style.width = '80px';
    editor.style.textAlign = 'center';

    return editor;
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

/**
 * ENHANCED: Setup editor handlers with better save control
 */
function setupEditorHandlers(editor, cell) {
    const field = cell.getAttribute('data-field');

    // Handle Enter key
    editor.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            console.log('📝 Manual save triggered by Enter key');
            saveEdit(cell, editor.value);
        } else if (e.key === 'Escape') {
            e.preventDefault();
            console.log('🚫 Edit cancelled by Escape key');
            cancelEditing();
        }
    });

    // Handle blur (when user clicks away)
    editor.addEventListener('blur', function() {
        console.log('📝 Manual save triggered by blur event');
        saveEdit(cell, editor.value);
    });

    // Auto-save after pause in typing (for time inputs only)
    if (editor.type === 'time') {
        let autoSaveTimeout;

        editor.addEventListener('input', function() {
            // Clear previous timeout
            clearTimeout(autoSaveTimeout);

            // Set new timeout
            autoSaveTimeout = setTimeout(() => {
                if (currentlyEditing === cell && !isSaving) {
                    console.log('⏱️ Auto-save triggered after 2 seconds of inactivity');
                    saveEdit(cell, editor.value);
                }
            }, 2000);
        });

        // Clear timeout when editor is removed
        editor.addEventListener('blur', function() {
            clearTimeout(autoSaveTimeout);
        });
    }
}

function showEditingHelp(cell, field) {
    const helpText = document.createElement('div');
    helpText.className = 'editing-help';

    const helpTexts = {
        'startTime': 'Enter time in 24-hour format (e.g., 09:00, 17:30)',
        'endTime': 'Enter time in 24-hour format (e.g., 09:00, 17:30)',
        'timeOff': 'Select CO for vacation, CM for medical leave',
        'tempStop': 'Enter temporary stop minutes (0-720, max 12 hours)'
    };

    const helpMessage = helpTexts[field] || 'Enter new value';
    helpText.textContent = helpMessage;
    helpText.innerHTML += '<br><small>Press Enter to save, Escape to cancel</small>';


    // Remove help text after 4 seconds
    setTimeout(() => {
        helpText.remove();
    }, 4000);
}

/**
 * ENHANCED: Save edit function with robust error handling and multiple-save protection
 */
async function saveEdit(cell, value) {
    // Prevent multiple simultaneous saves
    if (isSaving) {
        console.log('⏳ Save already in progress, skipping duplicate save attempt...');
        return;
    }

    isSaving = true;

    try {
        // 🔍 STEP 1: Extract data with robust error handling
        const row = cell.closest('tr');
        if (!row) {
            throw new Error('Cannot find parent row for this cell');
        }

        // Get date from row (not cell)
        let date = row.getAttribute('data-date');
        if (!date) {
            // Try alternative methods to get the date
            date = row.dataset.date;
            if (!date) {
                console.error('❌ No date found on row:', {
                    rowHTML: row.outerHTML.substring(0, 300),
                    allAttributes: Array.from(row.attributes).map(attr => `${attr.name}="${attr.value}"`),
                    classList: Array.from(row.classList)
                });
                throw new Error('Cannot determine date for this row. Row may be missing data-date attribute.');
            }
        }

        const field = cell.getAttribute('data-field');
        const originalValue = cell.getAttribute('data-original') || '';

        // 🔍 STEP 2: Validate required data
        if (!field) {
            throw new Error('Cell is missing data-field attribute');
        }

        console.log('🔍 DEBUG: Extracted data:', {
            date: date,
            dateType: typeof date,
            field: field,
            value: value,
            originalValue: originalValue,
            rowElement: row.tagName,
            cellElement: cell.tagName
        });

        // 🔍 STEP 3: Validate date format
        if (!date.match(/^\d{4}-\d{2}-\d{2}$/)) {
            throw new Error(`Invalid date format: "${date}". Expected YYYY-MM-DD format.`);
        }

        console.log('💾 Saving edit:', {
            date: date,
            field: field,
            value: value,
            originalValue: originalValue
        });

        // Show saving indicator
        cell.classList.add('cell-saving');
        addFieldStatus(cell, 'saving');

        // 🔍 STEP 4: Client-side validation
        const validationError = validateFieldValue(field, value);
        if (validationError) {
            throw new Error(validationError);
        }

        // 🔍 STEP 5: Send to server
        const formData = new URLSearchParams();
        formData.append('date', date);
        formData.append('field', field);
        formData.append('value', value || '');



        console.log(`📤 Sending data to server:`, {
            date: date,
            field: field,
            value: value || '',
            formDataString: formData.toString()
        });

        console.log('🔍 DEBUG: Extracted data:', {
            date: date,
            dateType: typeof date,
            field: field,           // ← ADD THIS LINE
            fieldLength: field.length,  // ← ADD THIS LINE
            fieldCharCodes: [...field].map(c => c.charCodeAt(0)), // ← ADD THIS LINE
            value: value,
            originalValue: originalValue
        });

        const response = await fetch('/user/time-management/update-field', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: formData
        });

        console.log(`📥 Response status: ${response.status}`);

        // 🔍 STEP 6: Handle response
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

        const result = await response.json();
        console.log(`✅ Update result:`, result);

        if (result.success) {
            // 🔍 STEP 7: Update UI on success
            updateCellDisplay(cell, field, value);
            addFieldStatus(cell, 'success');

            // Special handling for time off changes - refresh editability of related fields
            if (field === 'timeOff') {
                const startTimeCell = row.querySelector('.editable-cell[data-field="startTime"]');
                const endTimeCell = row.querySelector('.editable-cell[data-field="endTime"]');

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

            // Schedule page refresh after successful update
            setTimeout(() => {
                console.log('🔄 Auto-refreshing page after successful field update...');
                window.location.reload();
            }, 1500);

        } else {
            throw new Error(result.error || result.message || 'Update failed - unknown error');
        }

    } catch (error) {
        console.error('❌ Save error:', error);

        // Add error status to cell
        addFieldStatus(cell, 'error');

        // Show user-friendly error message
        let userMessage = 'Failed to update field';
        if (error.message.includes('date format')) {
            userMessage = 'Invalid date format. Please try refreshing the page.';
        } else if (error.message.includes('Server error')) {
            userMessage = 'Server error. Please try again.';
        } else if (error.message.includes('data-date')) {
            userMessage = 'Page data error. Please refresh the page.';
        } else {
            userMessage = error.message;
        }

        // Use toast notification for errors
        showToast('Update Failed', userMessage, 'error', {
            duration: 4000
        });

        // Clear field on error to show original value
        updateCellDisplay(cell, field, '');

    } finally {
        // 🔍 STEP 8: Cleanup
        cell.classList.remove('cell-saving');
        finishEditing(cell);
        isSaving = false; // Always reset the flag
    }
}

/**
 * ENHANCED: Cancel editing with proper cleanup
 */
function cancelEditing() {
    if (!currentlyEditing) return;

    console.log('🚫 Cancelling edit');
    finishEditing(currentlyEditing);
}

/**
 * ENHANCED: Finish editing with better cleanup
 */
function finishEditing(cell) {
    // Remove editor and show original content
    const editor = cell.querySelector('.inline-editor');
    const helpText = cell.querySelector('.editing-help');

    if (editor) {
        editor.remove();
    }
    if (helpText) {
        helpText.remove();
    }

    // FIX: Handle both cell-value and cell-content structures
    let cellValue = cell.querySelector('.cell-value');
    if (!cellValue) {
        cellValue = cell.querySelector('.cell-content');
    }

    if (cellValue) {
        cellValue.style.display = '';
    }

    cell.classList.remove('editing');

    // Clear any existing timeouts
    if (typeof editingTimeout !== 'undefined') {
        clearTimeout(editingTimeout);
    }

    currentlyEditing = null;
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

/**
 * ENHANCED: SN Overtime handling for time-management.js
 * Add these functions and modifications to handle SN days with overtime
 */

// ========================================================================
// SN OVERTIME DISPLAY ENHANCEMENTS
// ========================================================================

// ========================================================================
// MODULAR CELL DISPLAY UPDATE FUNCTIONS
// ========================================================================

/**
 * Main function to update cell display - now modular and easy to understand
 */
function updateCellDisplay(cell, field, value, rawData = null) {
    // Handle both cell-value and cell-content structures
    let cellValue = cell.querySelector('.cell-value');
    if (!cellValue) {
        cellValue = cell.querySelector('.cell-content');
    }
    if (!cellValue) {
        cellValue = cell; // Fallback to the cell itself
    }

    // Route to appropriate handler based on field type
    switch (field) {
        case 'tempStop':
            updateTempStopDisplay(cellValue, value);
            break;
        case 'timeOff':
            updateTimeOffDisplay(cellValue, value, rawData);
            break;
        case 'workTime':
            updateWorkTimeDisplay(cellValue, value, rawData);
            break;
        case 'overtime':
            updateOvertimeDisplay(cellValue, value, rawData);
            break;
        case 'startTime':
        case 'endTime':
            updateTimeDisplay(cellValue, value, rawData);
            break;
        default:
            updateDefaultDisplay(cellValue, value);
            break;
    }

    // Update data attribute
    cell.setAttribute('data-original', value || '');
}

/**
 * Handle temporary stop display
 */
function updateTempStopDisplay(cellValue, value) {
    const minutes = parseInt(value) || 0;

    if (minutes > 0) {
        cellValue.innerHTML = `<span class="text-info fw-medium temp-stop-display" title="Double-click to edit temporary stop time">${minutes}m</span>`;
    } else {
        cellValue.innerHTML = `<span class="text-muted temp-stop-display" title="Double-click to add temporary stop time">-</span>`;
    }
}

/**
 * Handle time off display with SN overtime logic
 */
function updateTimeOffDisplay(cellValue, value, rawData) {
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

            cellValue.innerHTML = `<span class="sn-work-display" title="National Holiday with ${formatMinutesToHours(rawData.totalOvertimeMinutes)} overtime work">${snDisplay}</span>`;

            // Mark the row as SN work entry
            const row = cellValue.closest('tr');
            if (row) {
                row.classList.add('sn-work-entry');
            }
        } else {
            // Regular time off display
            const timeOffClass = getTimeOffClass(value);
            const timeOffLabel = getTimeOffLabel(value);
            cellValue.innerHTML = `<span class="${timeOffClass}" title="${timeOffLabel}">${value}</span>`;
        }
    } else {
        cellValue.textContent = '-';
    }
}

/**
 * Handle work time display for regular vs SN days
 */
function updateWorkTimeDisplay(cellValue, value, rawData) {
    if (rawData && rawData.timeOffType === 'SN') {
        // SN days show 0 work time (all time is overtime)
        cellValue.innerHTML = '<span class="text-muted small" title="No regular work time on holidays - all time is overtime">0:00</span>';
    } else if (value && value !== '-') {
        cellValue.innerHTML = `<span class="text-primary fw-medium">${value}</span>`;
    } else {
        cellValue.textContent = '-';
    }
}

/**
 * Handle overtime display with special SN styling
 */
function updateOvertimeDisplay(cellValue, value, rawData) {
    if (value && value !== '-') {
        const isSnOvertime = rawData && rawData.timeOffType === 'SN';
        const badgeClass = isSnOvertime ?
        'badge bg-warning text-dark rounded-pill overtime-display small sn-overtime' :
        'badge bg-success rounded-pill overtime-display small';
        const title = isSnOvertime ?
        `Holiday overtime work: ${value}` :
        `Overtime work: ${value}`;

        cellValue.innerHTML = `<span class="${badgeClass}" title="${title}">${value}</span>`;
    } else {
        cellValue.textContent = '-';
    }
}

/**
 * Handle start time and end time display with SN warnings
 */
function updateTimeDisplay(cellValue, value, rawData) {
    if (rawData && rawData.timeOffType === 'SN') {
        const displayTime = value ? convertTo24Hour(value) : '-';
        cellValue.innerHTML = `<span class="text-warning" title="Working on national holiday - all time counts as overtime">${displayTime}</span>`;
    } else {
        const displayTime = value ? convertTo24Hour(value) : '-';
        cellValue.textContent = displayTime;
    }
}

/**
 * Default display for unhandled field types
 */
function updateDefaultDisplay(cellValue, value) {
    cellValue.textContent = value || '-';
}

// ========================================================================
// HELPER FUNCTIONS (Keep existing ones)
// ========================================================================

/**
 * Get appropriate CSS class for time off types
 */
function getTimeOffClass(timeOffType) {
    switch (timeOffType) {
        case 'SN': return 'holiday';
        case 'CO': return 'vacation';
        case 'CM': return 'medical';
        default: return 'time-off-display';
    }
}

/**
 * Get descriptive label for time off types
 */
function getTimeOffLabel(timeOffType) {
    switch (timeOffType) {
        case 'SN': return 'National Holiday';
        case 'CO': return 'Vacation Day';
        case 'CM': return 'Medical Leave';
        default: return timeOffType;
    }
}

/**
 * Format minutes to hours display
 */
function formatMinutesToHours(minutes) {
    if (!minutes || minutes === 0) return '0h';

    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;

    if (mins === 0) {
        return `${hours}h`;
    }
    return `${hours}h ${mins}m`;
}
/**
 * ENHANCED: Check if a row represents an SN day with work
 */
function isSNWorkDay(rowData) {
    return rowData &&
    rowData.timeOffType === 'SN' &&
    rowData.totalOvertimeMinutes &&
    parseInt(rowData.totalOvertimeMinutes) > 0;
}

/**
 * ENHANCED: Get appropriate CSS class for time off types
 */
function getTimeOffClass(timeOffType) {
    switch (timeOffType) {
        case 'SN': return 'holiday';
        case 'CO': return 'vacation';
        case 'CM': return 'medical';
        default: return 'time-off-display';
    }
}

/**
 * ENHANCED: Get descriptive label for time off types
 */
function getTimeOffLabel(timeOffType) {
    switch (timeOffType) {
        case 'SN': return 'National Holiday';
        case 'CO': return 'Vacation Day';
        case 'CM': return 'Medical Leave';
        default: return timeOffType;
    }
}

/**
 * ENHANCED: Format minutes to hours display
 */
function formatMinutesToHours(minutes) {
    if (!minutes || minutes === 0) return '0h';

    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;

    if (mins === 0) {
        return `${hours}h`;
    }
    return `${hours}h ${mins}m`;
}

/**
 * ENHANCED: Refresh row display after updates
 */
function refreshRowAfterUpdate(row, updatedData) {
    // Check if this became or stopped being an SN work day
    if (isSNWorkDay(updatedData)) {
        row.classList.add('sn-work-entry', 'updated');

        // Add special indicator for SN work
        const timeOffCell = row.querySelector('.timeoff-cell .cell-value');
        if (timeOffCell && updatedData.timeOffType === 'SN') {
            updateCellDisplay(timeOffCell.closest('td'), 'timeOff', 'SN', updatedData);
        }

        // Update overtime display
        const overtimeCell = row.querySelector('.overtime-display');
        if (overtimeCell && updatedData.totalOvertimeMinutes) {
            const formattedOvertime = formatMinutesToHours(updatedData.totalOvertimeMinutes);
            updateCellDisplay(overtimeCell.closest('td'), 'overtime', formattedOvertime, updatedData);
        }

        // Remove the updated animation after 2 seconds
        setTimeout(() => {
            row.classList.remove('updated');
        }, 2000);
    } else {
        row.classList.remove('sn-work-entry');
    }
}

/**
 * ENHANCED: Validate field editing for SN days
 */
function validateFieldForSNDay(field, value, rowData) {
    if (!rowData || rowData.timeOffType !== 'SN') {
        return null; // Not an SN day, normal validation applies
    }

    switch (field) {
        case 'timeOff':
            return 'Cannot modify time off type for national holidays. Contact admin if changes needed.';
        case 'startTime':
        case 'endTime':
            // Allow time editing on SN days but warn user
            showToast('Holiday Work Notice',
                `Editing work time on national holiday. All work time will be counted as overtime.`,
                'warning', 5000);
            return null;
        default:
            return null;
    }
}

/**
 * ENHANCED: Show special notification for SN day work
 */
function showSNWorkNotification(actionType) {
    const messages = {
        'timeUpdated': 'Work time updated for national holiday. All time counts as overtime.',
        'workStarted': 'Started work on national holiday. All time will be overtime.',
        'workEnded': 'Completed work on national holiday. Time recorded as overtime.'
    };

    const message = messages[actionType] || 'National holiday work time updated.';
    showToast('Holiday Work Updated', message, 'warning', 4000);
}

// ========================================================================
// ENHANCED INLINE EDITING WITH SN SUPPORT
// ========================================================================

/**
 * ENHANCED: Start inline editing with SN day validation
 */
function startInlineEdit(cell) {
    const field = cell.getAttribute('data-field');
    const rowData = getRowData(cell.closest('tr'));

    // Validate if this field can be edited on SN days
    const snValidation = validateFieldForSNDay(field, null, rowData);
    if (snValidation) {
        showToast('Edit Restricted', snValidation, 'error');
        return;
    }

    // Continue with normal editing...
    const currentValue = cell.getAttribute('data-original') || '';
    const input = createInlineInput(field, currentValue);

    // Add special styling for SN day editing
    if (rowData && rowData.timeOffType === 'SN') {
        input.classList.add('sn-day-edit');
        input.style.borderColor = '#ffc107';
        input.style.backgroundColor = 'rgba(255, 193, 7, 0.1)';
    }

    const cellValue = cell.querySelector('.cell-value') || cell;
    cellValue.innerHTML = '';
    cellValue.appendChild(input);

    input.focus();
    input.select();

    // Save on blur or Enter
    input.addEventListener('blur', () => saveInlineEdit(cell, input, field));
    input.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            saveInlineEdit(cell, input, field);
        }
    });
}

/**
 * ENHANCED: Save inline edit with SN day handling
 */
function saveInlineEdit(cell, input, field) {
    const newValue = input.value.trim();
    const row = cell.closest('tr');
    const rowData = getRowData(row);

    // Special handling for SN days
    if (rowData && rowData.timeOffType === 'SN' && (field === 'startTime' || field === 'endTime')) {
        // Show confirmation for SN day work time changes
        if (newValue && newValue !== cell.getAttribute('data-original')) {
            showSNWorkNotification('timeUpdated');
        }
    }

    // Continue with normal save process...
    updateCellDisplay(cell, field, newValue, rowData);

    // Submit the change
    submitFieldUpdate(cell, field, newValue);
}

// ========================================================================
// UTILITY FUNCTIONS FOR SN DISPLAY
// ========================================================================

/**
 * Get row data including SN overtime information
 */
function getRowData(row) {
    if (!row) return null;

    return {
        date: row.getAttribute('data-date'),
        timeOffType: row.querySelector('.timeoff-cell [class*="holiday"], .timeoff-cell [class*="vacation"], .timeoff-cell [class*="medical"]')?.textContent?.trim(),
        totalOvertimeMinutes: row.getAttribute('data-overtime-minutes'),
        // Add other fields as needed
    };
}

/**
 * Initialize SN overtime display on page load
 */
function initializeSNOvertimeDisplay() {
    // Find all rows with SN + overtime and apply special styling
    document.querySelectorAll('.worktime-entry').forEach(row => {
        const rowData = getRowData(row);
        if (isSNWorkDay(rowData)) {
            row.classList.add('sn-work-entry');
        }
    });

    console.log('SN overtime display initialized');
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

        // ADD THIS CASE:
        case 'tempStop':
            const minutes = parseInt(value);
            if (isNaN(minutes) || minutes < 0) {
                return 'Temporary stop must be a positive number';
            }
            if (minutes > 720) {
                return 'Temporary stop cannot exceed 12 hours (720 minutes)';
            }
            break;

        default:
            return null; // CHANGE: Don't throw error for unknown fields, just return null
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