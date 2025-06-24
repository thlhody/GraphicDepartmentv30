/**
 * FIXED: Enhanced worktime-admin.js with SN + work time support
 * Fixed the saveWorktime function to properly find the input element
 */

// Document ready initialization
document.addEventListener('DOMContentLoaded', function() {
    console.log('Worktime admin interface initialized with SN work time support');

    // Check structure of worktime cells
    const worktimeCells = document.querySelectorAll('.worktime-cell');
    console.log(`Found ${worktimeCells.length} worktime cells`);

    // Initialize click-outside handler
    initializeClickOutsideHandler();
});

/**
 * Show editor popup for worktime cell
 * Enhanced with dynamic entry information display
 */
function showEditor(cell) {
    // First, hide ALL other editors
    hideAllEditors();

    const editor = cell.querySelector('.worktime-editor');
    if (!editor) {
        console.error('No editor found in cell');
        return;
    }

    // ENHANCED: Populate dynamic entry information
    populateEntryInfo(editor, cell);

    // Position editor above the cell
    positionEditor(cell, editor);

    // Show the editor
    editor.style.display = 'block';
    editor.classList.add('show');

    // Focus on input field for better UX
    const input = editor.querySelector('input[type="text"]');
    if (input) {
        setTimeout(() => input.focus(), 100);
    }

    console.log('Editor shown for cell:', cell.dataset);
}

/**
 * Position editor relative to cell
 */
function positionEditor(cell, editor) {
    const rect = cell.getBoundingClientRect();
    const viewportHeight = window.innerHeight;
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
function hideAllEditors() {
    document.querySelectorAll('.worktime-editor').forEach(editor => {
        editor.classList.remove('show');
        editor.style.display = 'none';
    });
}

/**
 * Set worktime using quick action buttons
 * Enhanced to handle all existing formats
 */
function setWorktime(btn, value) {
    event.stopPropagation(); // Prevent event bubbling

    console.log('Quick action selected:', value);

    const cell = btn.closest('.worktime-cell');
    if (!cell) {
        console.error('Cannot find worktime cell');
        return;
    }

    submitWorktimeUpdate(cell, value);
}

/**
 * FIXED: Save custom worktime from input field
 * Enhanced with proper input finding and SN:hours format support
 */
function saveWorktime(btn) {
    event.stopPropagation(); // Prevent event bubbling

    // FIXED: Properly find the input element within the same editor
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

    // Enhanced validation
    if (!validateWorktimeValue(value)) {
        return; // Validation failed, user already alerted
    }

    const cell = btn.closest('.worktime-cell');
    if (!cell) {
        console.error('Cannot find worktime cell');
        return;
    }

    submitWorktimeUpdate(cell, value);
}

/**
 * Enhanced validation for worktime values
 * Supports: numbers (1-24), SN:hours format, time off types (CO, CM, SN)
 */
function validateWorktimeValue(value) {
    // Handle SN + work time format (e.g., "SN:7.5")
    if (value.includes('SN:')) {
        return validateSNWorkTime(value);
    }

    // Handle time off types
    if (['CO', 'CM', 'SN'].includes(value.toUpperCase())) {
        return true; // Valid time off types
    }

    // Handle regular work hours
    if (/^\d+(\.\d+)?h?$/.test(value)) {
        const hours = parseFloat(value.replace('h', ''));
        if (hours < 1 || hours > 24) {
            alert('Work hours must be between 1 and 24');
            return false;
        }
        return true;
    }

    alert('Invalid format. Use:\n- Hours: 8 or 8h\n- SN work: SN:7.5\n- Time off: CO, CM, SN');
    return false;
}

/**
 * Validate SN + work time format
 * Enhanced with user-friendly feedback
 */
function validateSNWorkTime(value) {
    const parts = value.split(':');

    // Check format: must be exactly "SN:number"
    if (parts.length !== 2 || parts[0].toUpperCase() !== 'SN') {
        alert('Invalid SN format. Use SN:hours (e.g., SN:7.5)');
        return false;
    }

    // Parse and validate hours
    const hours = parseFloat(parts[1]);
    if (isNaN(hours) || hours < 1 || hours > 24) {
        alert('SN work hours must be between 1 and 24 (e.g., SN:7.5)');
        return false;
    }

    // Warn about partial hour discarding if applicable
    if (hours % 1 !== 0) {
        const fullHours = Math.floor(hours);
        const discarded = hours - fullHours;

        const confirmed = confirm(
            `SN Work Time Processing:\n\n` +
            `Input: ${hours} hours\n` +
            `Processed: ${fullHours} full hours\n` +
            `Discarded: ${discarded.toFixed(1)} hours\n\n` +
            `Only full hours count for holiday work. Continue?`
        );

        if (!confirmed) {
            return false;
        }
    }

    return true;
}

/**
 * Get current view period for redirect
 */
function getCurrentViewPeriod() {
    const yearSelect = document.getElementById('yearSelect');
    const monthSelect = document.getElementById('monthSelect');

    return {
        year: yearSelect ? yearSelect.value : new Date().getFullYear(),
        month: monthSelect ? monthSelect.value : new Date().getMonth() + 1
    };
}

/**
 * Submit worktime update to server
 * Enhanced with better error handling and user feedback
 */
function submitWorktimeUpdate(cell, value) {
    const userId = cell.dataset.userId;
    const [year, month, day] = cell.dataset.date.split('-');

    if (!userId || !year || !month || !day) {
        console.error('Invalid cell data:', cell.dataset);
        alert('Error: Invalid cell data');
        return;
    }

    // Get the current view period for redirect
    const currentViewPeriod = getCurrentViewPeriod();

    console.log('Submitting worktime update:', {
        userId, year, month, day, value,
        redirectYear: currentViewPeriod.year,
        redirectMonth: currentViewPeriod.month
    });

    // Show loading indicator
    showLoadingIndicator(cell);

    // Submit to server
    fetch('/admin/worktime/update', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: new URLSearchParams({
            userId: userId,
            year: year,
            month: month,
            day: day,
            value: value
        })
    })
        .then(response => {
        hideLoadingIndicator(cell);

        if (response.ok) {
            console.log('Update successful');
            showSuccessIndicator(cell);

            // Redirect to maintain current view
            setTimeout(() => {
                window.location.href = `/admin/worktime?year=${currentViewPeriod.year}&month=${currentViewPeriod.month}`;
            }, 1000);
        } else {
            console.error('Update failed with status:', response.status);
            response.text().then(text => {
                showErrorIndicator(cell);
                alert('Failed to update worktime: ' + text);
            });
        }
    })
        .catch(error => {
        hideLoadingIndicator(cell);
        showErrorIndicator(cell);
        console.error('Network error:', error);
        alert('Failed to update worktime: Network error');
    });
}

/**
 * Visual feedback functions
 */
function showLoadingIndicator(cell) {
    cell.style.backgroundColor = '#fff3cd';
    cell.style.opacity = '0.7';
}

function hideLoadingIndicator(cell) {
    cell.style.backgroundColor = '';
    cell.style.opacity = '';
}

function showSuccessIndicator(cell) {
    cell.style.backgroundColor = '#d4edda';
    setTimeout(() => {
        cell.style.backgroundColor = '';
    }, 2000);
}

function showErrorIndicator(cell) {
    cell.style.backgroundColor = '#f8d7da';
    setTimeout(() => {
        cell.style.backgroundColor = '';
    }, 3000);
}

/**
 * Initialize click-outside handler to close editors
 */
function initializeClickOutsideHandler() {
    document.addEventListener('click', function(event) {
        // Check if click is outside any worktime cell
        if (!event.target.closest('.worktime-cell')) {
            hideAllEditors();
        }
    });

    // Handle escape key
    document.addEventListener('keydown', function(event) {
        if (event.key === 'Escape') {
            hideAllEditors();
        }
    });
}

/**
 * Debug function to check cell structure (can be removed in production)
 */
function debugCellStructure() {
    const sampleCell = document.querySelector('.worktime-cell');
    if (sampleCell) {
        console.log("Sample cell found:", sampleCell);
        console.log("Cell data:", sampleCell.dataset);

        const editor = sampleCell.querySelector('.worktime-editor');
        if (editor) {
            console.log("Editor found:", editor);
        } else {
            console.warn("No editor found in sample cell");
        }
    } else {
        console.warn("No worktime cells found on page");
    }
}

/**
 * NEW: Populate dynamic entry information in the editor
 */
function populateEntryInfo(editor, cell) {
    const entryDetailsContainer = editor.querySelector('.entry-details');
    if (!entryDetailsContainer) return;

    // Get cell information
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

    // Fetch entry data from server
    fetchEntryData(userId, date)
        .then(entryData => {
        const entryInfoHTML = generateEntryInfoHTML(entryData, cell);
        entryDetailsContainer.innerHTML = entryInfoHTML;
    })
        .catch(error => {
        console.error('Error fetching entry data:', error);
        // Show fallback with current cell display
        const fallbackData = extractEntryFromCell(userId, date);
        const entryInfoHTML = generateEntryInfoHTML(fallbackData, cell);
        entryDetailsContainer.innerHTML = entryInfoHTML;
    });
}

/**
 * NEW: Fetch entry data from server
 */
async function fetchEntryData(userId, date) {
    const [year, month, day] = date.split('-');

    try {
        const response = await fetch(`/admin/worktime/entry-details?userId=${userId}&year=${year}&month=${month}&day=${day}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const data = await response.json();
        return data;
    } catch (error) {
        // Fallback: try to extract info from the cell display
        console.warn('API call failed, using fallback method:', error);
        return extractEntryFromCell(userId, date);
    }
}

/**
 * NEW: Fallback method to extract entry info from cell display
 */
function extractEntryFromCell(userId, date) {
    // Find the cell using the data attributes
    const cell = document.querySelector(`[data-user-id="${userId}"][data-date="${date}"]`);
    if (!cell) {
        return { hasEntry: false };
    }

    const cellContent = cell.textContent.trim();
    const entryData = {
        hasEntry: cellContent !== '-',
        displayValue: cellContent
    };

    // Try to parse different formats
    if (cellContent === 'SN') {
        entryData.timeOffType = 'SN';
        entryData.timeOffLabel = 'National Holiday';
    } else if (cellContent === 'CO') {
        entryData.timeOffType = 'CO';
        entryData.timeOffLabel = 'Vacation';
    } else if (cellContent === 'CM') {
        entryData.timeOffType = 'CM';
        entryData.timeOffLabel = 'Medical Leave';
    } else if (cellContent.startsWith('SN') && cellContent.length > 2) {
        // Handle SN4 format
        const hours = cellContent.substring(2);
        entryData.timeOffType = 'SN';
        entryData.timeOffLabel = 'National Holiday';
        entryData.overtimeHours = hours + 'h';
    } else if (cellContent.includes('h') || /^\d+$/.test(cellContent)) {
        // Regular work hours
        entryData.workHours = cellContent.includes('h') ? cellContent : cellContent + 'h';
    }

    return entryData;
}

/**
 * NEW: Generate HTML for entry information display
 */
function generateEntryInfoHTML(entryData, cell) {
    if (!entryData || !entryData.hasEntry) {
        return `<small class="text-muted no-entry">
            <i class="bi bi-calendar-x entry-icon"></i>No entry for this date
        </small>`;
    }

    let html = '';
    let hasData = false;

    // Handle different data sources (API vs fallback)
    if (entryData.dayStartTime || entryData.startTime) {
        const startTime = entryData.dayStartTime || entryData.startTime;
        html += `<small class="text-success">
            <i class="bi bi-play-circle entry-icon"></i><strong>Start:</strong> ${startTime}
        </small>`;
        hasData = true;
    }

    if (entryData.dayEndTime || entryData.endTime) {
        const endTime = entryData.dayEndTime || entryData.endTime;
        html += `<small class="text-danger">
            <i class="bi bi-stop-circle entry-icon"></i><strong>End:</strong> ${endTime}
        </small>`;
        hasData = true;
    }

    if (entryData.totalTemporaryStopMinutes && parseInt(entryData.totalTemporaryStopMinutes) > 0) {
        html += `<small class="text-warning">
            <i class="bi bi-pause-circle entry-icon"></i><strong>Temp stops:</strong> ${entryData.totalTemporaryStopMinutes} minutes
        </small>`;
        hasData = true;
    }

    if (entryData.lunchBreakDeducted === true || entryData.lunchBreakDeducted === 'true') {
        html += `<small class="text-info">
            <i class="bi bi-cup-hot entry-icon"></i><strong>Lunch:</strong> Deducted
        </small>`;
        hasData = true;
    }

    // Handle time off
    if (entryData.timeOffType && entryData.timeOffType !== 'null') {
        const timeOffLabel = entryData.timeOffLabel || getTimeOffLabel(entryData.timeOffType);
        const iconClass = getTimeOffIcon(entryData.timeOffType);
        html += `<small class="text-primary">
            <i class="${iconClass} entry-icon"></i><strong>Type:</strong> ${timeOffLabel}
        </small>`;
        hasData = true;
    }

    // Handle work hours (from API or fallback)
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

    // Handle overtime (API or fallback)
    if (entryData.totalOvertimeMinutes && parseInt(entryData.totalOvertimeMinutes) > 0) {
        const hours = formatMinutesToHours(parseInt(entryData.totalOvertimeMinutes));
        html += `<small class="text-danger">
            <i class="bi bi-clock-history entry-icon"></i><strong>Holiday overtime:</strong> ${hours}
        </small>`;
        hasData = true;
    } else if (entryData.overtimeHours) {
        html += `<small class="text-danger">
            <i class="bi bi-clock-history entry-icon"></i><strong>Holiday overtime:</strong> ${entryData.overtimeHours}
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

    // Show date info
    const cellDate = cell.dataset.date;
    if (cellDate) {
        html += `<small class="text-muted">
            <i class="bi bi-calendar entry-icon"></i><strong>Date:</strong> ${cellDate}
        </small>`;
        hasData = true;
    }

    if (!hasData) {
        return `<small class="text-muted no-entry">
            <i class="bi bi-info-circle entry-icon"></i>Entry exists but no details available
        </small>`;
    }

    // Handle status
    if (entryData.adminSync && entryData.adminSync !== 'null') {
        const statusLabel = getStatusLabel(entryData.adminSync);
        const statusClass = getStatusClass(entryData.adminSync);
        html += `<small class="${statusClass}">
            <i class="bi bi-info-circle entry-icon"></i><strong>Status:</strong> ${statusLabel}
        </small>`;
        hasData = true;
    }

    return html;
}

/**
 * NEW: Helper functions for formatting
 */
function getTimeOffLabel(timeOffType) {
    switch (timeOffType) {
        case 'SN': return 'National Holiday';
        case 'CO': return 'Vacation';
        case 'CM': return 'Medical Leave';
        default: return timeOffType;
    }
}

function getTimeOffIcon(timeOffType) {
    switch (timeOffType) {
        case 'SN': return 'bi bi-calendar-event text-success';
        case 'CO': return 'bi bi-airplane text-info';
        case 'CM': return 'bi bi-heart-pulse text-warning';
        default: return 'bi bi-calendar-x';
    }
}

function getStatusLabel(adminSync) {
    switch (adminSync) {
        case 'USER_DONE': return 'User Completed';
        case 'ADMIN_EDITED': return 'Admin Modified';
        case 'USER_IN_PROCESS': return 'In Progress';
        case 'ADMIN_BLANK': return 'Admin Blank';
        default: return adminSync;
    }
}

function getStatusClass(adminSync) {
    switch (adminSync) {
        case 'USER_DONE': return 'text-success';
        case 'ADMIN_EDITED': return 'text-warning';
        case 'USER_IN_PROCESS': return 'text-info';
        case 'ADMIN_BLANK': return 'text-secondary';
        default: return 'text-muted';
    }
}

function formatMinutesToHours(minutes) {
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    if (mins === 0) {
        return `${hours}h`;
    }
    return `${hours}h ${mins}m`;
}