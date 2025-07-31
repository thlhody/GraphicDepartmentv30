/**
 * Refactored Unified Time Management JavaScript
 * Enhanced with toast notifications and improved editing controls
 */

// ========================================================================
// ENHANCED PAGE INITIALIZATION
// ========================================================================


document.addEventListener('DOMContentLoaded', function () {
    console.log('Time Management page loaded - enhanced with period selection');

    // FIXED: Prevent multiple initializations
    if (window.timeManagementInitialized) {
        console.warn('Time Management already initialized, skipping...');
        return;
    }

    // ADD THIS LINE HERE (after the initialization check):
    initializeTimeInputFeatures();

    // Initialize all components (existing code)
    initializeTimeOffForm();
    initializeInlineEditing();
    initializeTooltips();
    initializeSNOvertimeDisplay();

    // NEW: Initialize period selection and export
    initializePeriodSelection();
    initializeExportButton();

    // Handle success messages with toast system
    handleServerMessages();

    // Show keyboard shortcut help
    console.log('📅 Keyboard shortcuts: Ctrl+← (previous month), Ctrl+→ (next month)');

    // Mark as initialized
    window.timeManagementInitialized = true;
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

    if (!form) {
        console.error('❌ Time off form not found!');
        return;
    }

    console.log('✅ Time off form found:', form);
    console.log('✅ Form action:', form.action);
    console.log('✅ Form method:', form.method);


    console.log('Initializing compact time off form');

    if (singleDayCheckbox) {
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
        console.log('🚀 Form submit event triggered!');
        console.log('📝 Form data:', {
            startDate: startDateInput.value,
            endDate: endDateInput.value,
            timeOffType: form.querySelector('[name="timeOffType"]').value,
            singleDay: singleDayCheckbox.checked
        });

        // Validate form data
        if (!startDateInput.value) {
            console.error('❌ Start date is empty!');
            e.preventDefault();
            showToast('Validation Error', 'Start date is required', 'error');
            return;
        }

        if (!singleDayCheckbox.checked && !endDateInput.value) {
            console.error('❌ End date is empty!');
            e.preventDefault();
            showToast('Validation Error', 'End date is required for multi-day requests', 'error');
            return;
        }

        console.log('✅ Form validation passed, submitting...');
        showLoadingOverlay();

        // Show immediate feedback
        showToast('Processing Request', 'Submitting your time off request...', 'info', {
            duration: 2000
        });
    });
}

// ========================================================================
// ENHANCED EDITABILITY WITH STATUS INTEGRATION
// ========================================================================

let currentlyEditing = null;
let editingTimeout = null;
// Global flag to prevent multiple simultaneous saves
let isSaving = false;
let isInitialized = false;

/**
 * UPDATED: Create time editors using TimeInputModule
 */
function createEnhancedTimeEditor(currentValue) {
    // Use the TimeInputModule instead of creating editor manually
    return TimeInputModule.create24HourEditor(currentValue, {
        // Optional custom configuration for this specific use case
        helpText: 'Enter time in 24-hour format (e.g., 08:30, 13:45). You can type 0830 or 1345',
        width: '100px' // Slightly wider for better visibility
    });
}

/**
 * Initialize inline editing with status-based restrictions
 */
function initializeInlineEditing() {
    // Prevent multiple initializations
    if (isInitialized) {
        console.warn('Inline editing already initialized, skipping...');
        return;
    }

    const table = document.querySelector('.table');
    if (!table) return;

    console.log('Initializing enhanced inline editing with status-based restrictions');

    // Add click handlers to editable cells
    const editableCells = table.querySelectorAll('.editable-cell');
    editableCells.forEach(cell => {
        // Check status-based editability
        checkStatusBasedEditability(cell);

        // FIXED: Remove any existing event listeners to prevent duplicates
        cell.removeEventListener('dblclick', handleCellDoubleClick);

        // Add double-click handler
        cell.addEventListener('dblclick', function(e) {
            e.preventDefault();
            e.stopPropagation();
            handleCellDoubleClick(cell);
        });

        // Add hover effects for editable cells only
        cell.addEventListener('mouseenter', function() {
            if (!cell.classList.contains('status-locked')) {
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

    // Initialize status tooltips
    initializeStatusTooltips();

    // FIXED: Handle clicks outside to cancel editing with better detection
    document.addEventListener('click', function(e) {
        if (currentlyEditing) {
            // Check if click is inside the editing cell or its editor
            const isInsideEditor = currentlyEditing.contains(e.target) ||
            e.target.closest('.inline-editor') ||
            e.target.classList.contains('inline-editor');

            if (!isInsideEditor) {
                console.log('👆 Click outside detected, canceling edit');
                // Add small delay to prevent conflicts
                setTimeout(() => {
                    if (currentlyEditing && !isSaving) {
                        cancelEditing();
                    }
                }, 50);
            }
        }
    });

    // Handle escape key
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape' && currentlyEditing) {
            e.preventDefault();
            cancelEditing();
        }
    });

    // Mark as initialized
    isInitialized = true;
}

/**
 * Check status-based editability using backend status info
 */
function checkStatusBasedEditability(cell) {
    const row = cell.closest('tr');
    if (!row) return;

    // Get status information from the row's data or status cell
    const statusInfo = getRowStatusInfo(row);

    if (!statusInfo) {
        console.warn('No status info found for row, allowing edit');
        return;
    }

    console.log(`Checking editability for row: isModifiable=${statusInfo.isModifiable}, status=${statusInfo.rawStatus}`);

    if (!statusInfo.isModifiable) {
        // Mark cell as locked
        cell.classList.add('status-locked');
        cell.setAttribute('title', statusInfo.tooltipText || 'Cannot edit this field');

        // Add row-level styling
        if (statusInfo.isFinal) {
            row.classList.add('status-final');
        } else if (statusInfo.isUserInProcess) {
            row.classList.add('status-in-process');
        }

        console.log(`Cell locked: ${statusInfo.fullDescription}`);
    } else {
        // Remove any previous locks
        cell.classList.remove('status-locked');
        console.log(`Cell editable: ${statusInfo.fullDescription}`);
    }
}

/**
 * Extract status information from row
 */
function getRowStatusInfo(row) {
    // Try to get status from hidden data attributes (if added by backend)
    const statusData = row.dataset.statusInfo;
    if (statusData) {
        try {
            return JSON.parse(statusData);
        } catch (e) {
            console.warn('Failed to parse status data:', statusData);
        }
    }

    // Fallback: Extract from status cell
    const statusCell = row.querySelector('.status-cell .status-indicator');
    if (!statusCell) return null;

    // Extract basic info from DOM
    const isModifiable = statusCell.querySelector('.modifiable-indicator') !== null;
    const isLocked = statusCell.querySelector('.locked-indicator') !== null;
    const badgeText = statusCell.querySelector('.status-badge')?.textContent?.trim();
    const tooltipText = statusCell.getAttribute('title');

    return {
        isModifiable: isModifiable && !isLocked,
        isFinal: badgeText?.includes('F'),
        isUserInProcess: badgeText?.includes('Active'),
        fullDescription: tooltipText || 'Unknown status',
        tooltipText: tooltipText
    };
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
    // Prevent event bubbling that causes immediate closure
    event.preventDefault();
    event.stopPropagation();

    // FIXED: Prevent multiple simultaneous editing attempts
    if (isSaving) {
        console.log('⏳ Save in progress, ignoring double-click...');
        return;
    }

    // FIXED: If already editing this exact cell, ignore
    if (currentlyEditing === cell) {
        console.log('🔄 Already editing this cell, ignoring duplicate double-click...');
        return;
    }

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

    // Check if cell is locked by status
    if (cell.classList.contains('status-locked')) {
        const row = cell.closest('tr');
        const statusInfo = getRowStatusInfo(row);

        showToast('Cannot Edit',
            statusInfo?.fullDescription || 'This field cannot be edited due to its current status',
            'warning',
            { duration: 4000 });
        return;
    }

    // FIXED: Cancel any existing editing before starting new one
    if (currentlyEditing && currentlyEditing !== cell) {
        console.log('🔄 Canceling previous edit to start new one...');
        cancelEditing();
    }

    // FIXED: Add small delay to ensure clean start
    setTimeout(() => {
        startEditing(cell);
    }, 50);
}

function startEditing(cell) {
    const field = cell.getAttribute('data-field');
    console.log('Starting edit for cell:', field);

    // FIXED: Force cleanup of any existing editors in this cell
    const existingEditors = cell.querySelectorAll('.inline-editor');
    existingEditors.forEach(editor => {
        console.log('🧹 Removing existing editor before creating new one');
        editor.remove();
    });

    // FIXED: Remove any existing help text
    const existingHelp = cell.querySelectorAll('.editing-help');
    existingHelp.forEach(help => {
        help.remove();
    });

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

    // FIXED: Ensure unique editor identification
    editor.setAttribute('data-field-editor', field);
    editor.setAttribute('data-editor-id', Date.now()); // Unique ID

    // Replace cell content with editor
    cellValue.style.display = 'none';
    cell.appendChild(editor);

    // FIXED: Focus with delay to prevent immediate blur
    setTimeout(() => {
        if (editor.parentNode) { // Ensure editor still exists
            editor.focus();
            if (editor.select) {
                editor.select();
            }
        }
    }, 100);

    // Set up editor event handlers with fixes
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
    // REPLACE THE ENTIRE EXISTING FUNCTION WITH THIS:
    return TimeInputModule.create24HourEditor(currentValue, {
        helpText: 'Enter time in 24-hour format (e.g., 08:30, 13:45). You can type 0830 or 1345',
        width: '100px'
    });
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

    // FIXED: Prevent immediate event firing
    let isInitializing = true;
    setTimeout(() => {
        isInitializing = false;
    }, 200);

    // Handle Enter key
    editor.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            e.stopPropagation();
            console.log('📝 Manual save triggered by Enter key');
            saveEdit(cell, editor.value);
        } else if (e.key === 'Escape') {
            e.preventDefault();
            e.stopPropagation();
            console.log('🚫 Edit cancelled by Escape key');
            cancelEditing();
        }
    });

    // FIXED: Handle blur with initialization check
    editor.addEventListener('blur', function(e) {
        // Don't save immediately if we're still initializing
        if (isInitializing) {
            console.log('⏭️ Skipping blur save during initialization');
            return;
        }

        // FIXED: Add delay to prevent conflicts with other events
        setTimeout(() => {
            if (currentlyEditing === cell && !isSaving) {
                console.log('📝 Manual save triggered by blur event');
                saveEdit(cell, editor.value);
            }
        }, 100);
    });

    // Auto-save after pause in typing (for time inputs only)
    if (editor.type === 'time') {
        let autoSaveTimeout;

        editor.addEventListener('input', function() {
            // Don't auto-save during initialization
            if (isInitializing) return;

            // Clear previous timeout
            clearTimeout(autoSaveTimeout);

            // Set new timeout
            autoSaveTimeout = setTimeout(() => {
                if (currentlyEditing === cell && !isSaving && !isInitializing) {
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

    // FIXED: Prevent click events on editor from bubbling up
    editor.addEventListener('click', function(e) {
        e.stopPropagation();
    });

    editor.addEventListener('mousedown', function(e) {
        e.stopPropagation();
    });

    editor.addEventListener('mouseup', function(e) {
        e.stopPropagation();
    });
}

/**
 * UPDATED: Enhanced help text using TimeInputModule
 */
function showEditingHelp(cell, field) {
    // REPLACE THE ENTIRE EXISTING FUNCTION WITH THIS:

    // Remove any existing help text
    const existingHelp = cell.querySelectorAll('.editing-help');
    existingHelp.forEach(help => help.remove());

    let helpElement;

    if (field === 'startTime' || field === 'endTime') {
        // Use TimeInputModule for time fields
        helpElement = TimeInputModule.createHelpText(field);
    } else {
        // Create standard help for other fields
        helpElement = document.createElement('div');
        helpElement.className = 'editing-help';

        const helpTexts = {
            'timeOff': 'Select CO for vacation, CM for medical leave',
            'tempStop': 'Enter temporary stop minutes (0-720, max 12 hours)'
        };

        const helpMessage = helpTexts[field] || 'Enter new value';
        helpElement.innerHTML = `${helpMessage}<br><small>Press Enter to save, Escape to cancel</small>`;

        // Standard help text styling
        Object.assign(helpElement.style, {
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
}

/**
 * Create standard help text for non-time fields
 */
function createStandardHelpText(field) {
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
    if (!cell) return;

    console.log('🏁 Finishing edit for cell');

    // FIXED: Remove ALL editors and help text (in case of duplicates)
    const editors = cell.querySelectorAll('.inline-editor');
    const helpTexts = cell.querySelectorAll('.editing-help');

    editors.forEach(editor => {
        console.log('🗑️ Removing editor:', editor.getAttribute('data-editor-id'));
        editor.remove();
    });

    helpTexts.forEach(help => {
        help.remove();
    });

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

    // FIXED: Clear currentlyEditing reference
    if (currentlyEditing === cell) {
        currentlyEditing = null;
    }
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
// PERIOD SELECTION FUNCTIONALITY
// ========================================================================

function initializePeriodSelection() {
    console.log('Initializing period selection controls');

    const periodForm = document.querySelector('.card-header form[action*="/user/time-management"]');
    const yearSelect = document.getElementById('yearSelect');
    const monthSelect = document.getElementById('monthSelect');

    if (!periodForm || !yearSelect || !monthSelect) {
        console.warn('Period selection elements not found');
        return;
    }

    // Set current values from URL parameters or page data
    const urlParams = new URLSearchParams(window.location.search);
    const currentYear = urlParams.get('year');
    const currentMonth = urlParams.get('month');

    if (currentYear) {
        yearSelect.value = currentYear;
    }
    if (currentMonth) {
        monthSelect.value = currentMonth;
    }

    // Handle form submission with loading indication
    periodForm.addEventListener('submit', function(e) {
        console.log('Loading new period:', {
            year: yearSelect.value,
            month: monthSelect.value
        });

        // Show loading indication
        showLoadingOverlay();

        // Show toast notification
        const monthName = monthSelect.options[monthSelect.selectedIndex].text;
        showToast('Loading Period',
            `Loading ${monthName} ${yearSelect.value}...`,
            'info',
            { duration: 2000 });
    });

    // Quick period navigation
    addQuickPeriodNavigation();
}

function addQuickPeriodNavigation() {
    const yearSelect = document.getElementById('yearSelect');
    const monthSelect = document.getElementById('monthSelect');

    if (!yearSelect || !monthSelect) return;

    // Add keyboard shortcuts for period navigation
    document.addEventListener('keydown', function(e) {
        // Only if not editing a cell
        if (currentlyEditing) return;

        // Ctrl + Left Arrow: Previous month
        if (e.ctrlKey && e.key === 'ArrowLeft') {
            e.preventDefault();
            navigateToPreviousMonth();
        }

        // Ctrl + Right Arrow: Next month
        if (e.ctrlKey && e.key === 'ArrowRight') {
            e.preventDefault();
            navigateToNextMonth();
        }
    });
}

function navigateToPreviousMonth() {
    const yearSelect = document.getElementById('yearSelect');
    const monthSelect = document.getElementById('monthSelect');

    let year = parseInt(yearSelect.value);
    let month = parseInt(monthSelect.value);

    month--;
    if (month < 1) {
        month = 12;
        year--;
    }

    navigateToPeriod(year, month);
}

function navigateToNextMonth() {
    const yearSelect = document.getElementById('yearSelect');
    const monthSelect = document.getElementById('monthSelect');

    let year = parseInt(yearSelect.value);
    let month = parseInt(monthSelect.value);

    month++;
    if (month > 12) {
        month = 1;
        year++;
    }

    navigateToPeriod(year, month);
}

function navigateToPeriod(year, month) {
    const currentUrl = new URL(window.location);
    currentUrl.searchParams.set('year', year);
    currentUrl.searchParams.set('month', month);

    showToast('Navigating',
        `Loading ${getMonthName(month)} ${year}...`,
        'info',
        { duration: 1500 });

    showLoadingOverlay();
    window.location.href = currentUrl.toString();
}

function getMonthName(monthNumber) {
    const months = [
        'January', 'February', 'March', 'April', 'May', 'June',
        'July', 'August', 'September', 'October', 'November', 'December'
    ];
    return months[monthNumber - 1] || 'Unknown';
}

// ========================================================================
// EXPORT FUNCTIONALITY
// ========================================================================

function initializeExportButton() {
    const exportButton = document.querySelector('.btn-outline-success[href*="/export"]');

    if (!exportButton) {
        console.warn('Export button not found');
        return;
    }

    exportButton.addEventListener('click', function(e) {
        console.log('Initiating Excel export...');

        // Show toast notification
        showToast('Exporting Data',
            'Generating Excel file for download...',
            'info',
            { duration: 3000 });

        // Don't prevent default - let the download proceed
        // The link will handle the actual export
    });
}

/**
 * ENHANCED: SN Overtime handling for time-management.js
 * Add these functions and modifications to handle SN days with overtime
 */

// ========================================================================
// SN OVERTIME DISPLAY ENHANCEMENTS
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
    // Format the time properly for display
    let displayTime = '-';

    if (value) {
        // Ensure consistent 24-hour format for display
        displayTime = TimeInputModule.formatTime(value, '24hour');
    }

    if (rawData && rawData.timeOffType === 'SN') {
        cellValue.innerHTML = `<span class="text-warning" title="Working on national holiday - all time counts as overtime">${displayTime}</span>`;
    } else {
        cellValue.textContent = displayTime;
    }
}

/**
 * ENHANCED: Add validation indicator for time inputs
 */
function addTimeInputValidation() {
    // Add real-time validation styling for all time inputs
    document.addEventListener('input', function(e) {
        if (e.target.hasAttribute('data-time-input')) {
            const value = e.target.value;
            const isValid = TimeInputModule.validateTime(value);

            // Update validation classes
            if (value.length === 5) {
                e.target.classList.toggle('is-valid', isValid);
                e.target.classList.toggle('is-invalid', !isValid);
            } else {
                e.target.classList.remove('is-valid', 'is-invalid');
            }
        }
    });
}

/**
 * ENHANCED: Initialize time input module features
 */
function initializeTimeInputFeatures() {
    // Add time input validation listeners
    document.addEventListener('input', function(e) {
        if (e.target.hasAttribute('data-time-input')) {
            const value = e.target.value;
            const isValid = TimeInputModule.validateTime(value);

            // Update validation classes
            if (value.length === 5) {
                e.target.classList.toggle('is-valid', isValid);
                e.target.classList.toggle('is-invalid', !isValid);
            } else {
                e.target.classList.remove('is-valid', 'is-invalid');
            }
        }
    });

    // Log module availability
    if (window.TimeInputModule) {
        console.log('✅ TimeInputModule loaded successfully');
    } else {
        console.warn('⚠️ TimeInputModule not found - time inputs may not work properly');
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
 * ENHANCED: Check if a row represents an SpecialDay day with work
 */

function isSpecialDayWithWork(rowData) {
    return rowData &&
    rowData.isSpecialDay &&
    rowData.hasWork &&
    rowData.totalOvertimeMinutes > 0;
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
    if (isSpecialDayWithWork(updatedData)) {
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
    (row.querySelector('.overtime-cell') ? extractOvertimeMinutes(row.querySelector('.overtime-cell').textContent) : 0);

    return {
        date: row.getAttribute('data-date'),
        timeOffType: timeOffType,
        totalOvertimeMinutes: parseInt(overtimeMinutes) || 0,
        hasWork: overtimeMinutes > 0,
        isSpecialDay: ['SN', 'CO', 'CM', 'W'].includes(timeOffType)
    };
}

/**
 * Initialize SN overtime display on page load
 */
function initializeSNOvertimeDisplay() {
    console.log('Initializing enhanced special day work display...');

    // Find all rows with special day work and apply appropriate styling
    document.querySelectorAll('.worktime-entry').forEach(row => {
        const rowData = getRowData(row);
        if (isSpecialDayWithWork(rowData)) {
            applySpecialDayWorkStyling(row, rowData);
        }
    });

    // Initialize tooltips for special day work
    initializeSpecialDayTooltips();

    console.log('Enhanced special day work display initialized');
}

/**
 * Apply special day work styling based on type
 */
function applySpecialDayWorkStyling(row, rowData) {
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
}

/**
 * Initialize tooltips for all special day work types
 */
function initializeSpecialDayTooltips() {
    // Initialize tooltips for special day work displays
    const specialDaySelectors = [
        '.sn-work-display',
        '.co-work-display',
        '.cm-work-display',
        '.w-work-display'
    ];

    specialDaySelectors.forEach(selector => {
        document.querySelectorAll(selector).forEach(element => {
            // Set tooltip based on type and hours
            const type = getSpecialDayTypeFromClass(element.className);
            const hours = extractHoursFromDisplay(element.textContent);
            const tooltip = generateSpecialDayTooltip(type, hours);

            element.setAttribute('title', tooltip);
            element.setAttribute('data-bs-toggle', 'tooltip');
            element.setAttribute('data-bs-placement', 'top');
        });
    });
}

/**
 * Get special day type from CSS class
 */
function getSpecialDayTypeFromClass(className) {
    if (className.includes('sn-work-display')) return 'SN';
    if (className.includes('co-work-display')) return 'CO';
    if (className.includes('cm-work-display')) return 'CM';
    if (className.includes('w-work-display')) return 'W';
    return 'Special Day';
}

/**
 * Extract hours from display text (e.g., "SN4" → 4)
 */
function extractHoursFromDisplay(displayText) {
    const match = displayText.match(/(\d+)$/);
    return match ? parseInt(match[1]) : 0;
}

/**
 * Generate tooltip text for special day work
 */
function generateSpecialDayTooltip(type, hours) {
    const typeNames = {
        'SN': 'National Holiday',
        'CO': 'Time Off Day',
        'CM': 'Medical Leave',
        'W': 'Weekend'
    };

    const typeName = typeNames[type] || 'Special Day';
    return hours > 0 ?
    `${typeName} with ${hours} hour${hours !== 1 ? 's' : ''} overtime work` :
    `${typeName}`;
}

/**
 * Extract overtime minutes from text content
 */
function extractOvertimeMinutes(text) {
    if (!text) return 0;

    // Match patterns like "2:30", "02:30", etc.
    const timeMatch = text.match(/(\d{1,2}):(\d{2})/);
    if (timeMatch) {
        const hours = parseInt(timeMatch[1]);
        const minutes = parseInt(timeMatch[2]);
        return (hours * 60) + minutes;
    }

    return 0;
}

// ========================================================================
// STATUS DISPLAY FUNCTIONALITY
// ========================================================================

/**
 * Initialize status tooltips
 */
function initializeStatusTooltips() {
    // Initialize Bootstrap tooltips for status indicators
    const statusIndicators = document.querySelectorAll('.status-indicator[data-bs-toggle="tooltip"]');
    statusIndicators.forEach(indicator => {
        new bootstrap.Tooltip(indicator, {
            html: true,
            placement: 'top',
            trigger: 'hover'
        });
    });
}

/**
 * Show detailed status information in modal
 */
function showStatusDetails(statusElement, event) {
    event.stopPropagation();

    const modal = new bootstrap.Modal(document.getElementById('statusDetailsModal'));
    const contentDiv = document.getElementById('statusDetailsContent');

    // Extract status information
    const row = statusElement.closest('tr');
    const statusInfo = getRowStatusInfo(row);
    const date = row.querySelector('.date-cell')?.textContent?.trim();

    if (!statusInfo) {
        contentDiv.innerHTML = '<p class="text-muted">No status information available</p>';
        modal.show();
        return;
    }

    // Build detailed status content
    let content = `
        <div class="status-details-section">
            <div class="status-details-label">
                <i class="${statusInfo.iconClass || 'bi-info-circle'} status-icon-large"></i>
                Entry Status
            </div>
            <div class="status-details-value">
                ${statusInfo.fullDescription || 'Unknown status'}
            </div>
        </div>
    `;

    if (date) {
        content += `
            <div class="status-details-section">
                <div class="status-details-label">Date</div>
                <div class="status-details-value">${date}</div>
            </div>
        `;
    }

    content += `
        <div class="status-details-section">
            <div class="status-details-label">Editability</div>
            <div class="status-details-value">
                ${statusInfo.isModifiable ?
                    '<i class="bi bi-check-circle text-success me-1"></i>Can be modified' :
                    '<i class="bi bi-lock-fill text-danger me-1"></i>Cannot be modified'}
            </div>
        </div>
    `;

    if (statusInfo.editedTimeAgo) {
        content += `
            <div class="status-details-section">
                <div class="status-details-label">Last Modified</div>
                <div class="status-details-value">${statusInfo.editedTimeAgo}</div>
            </div>
        `;
    }

    if (statusInfo.isOwnedByCurrentUser !== undefined) {
        content += `
            <div class="status-details-section">
                <div class="status-details-label">Ownership</div>
                <div class="status-details-value">
                    ${statusInfo.isOwnedByCurrentUser ?
                        '<i class="bi bi-person-check text-success me-1"></i>Your entry' :
                        '<i class="bi bi-person-x text-warning me-1"></i>Admin/Team entry'}
                </div>
            </div>
        `;
    }

    contentDiv.innerHTML = content;
    modal.show();
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

/**
 * UPDATED: Time conversion using TimeInputModule
 */
function convertTo24Hour(timeString) {
    return TimeInputModule.convertTo24Hour(timeString);
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
            // Use TimeInputModule validation
            return TimeInputModule.getValidationError(value);

        case 'timeOff':
            if (value !== 'CO' && value !== 'CM') {
                return 'Invalid time off type. Use CO for vacation or CM for medical';
            }
            break;

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
            return null;
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