/**
 * FIXED: Enhanced worktime-admin.js with SN + work time support
 * Fixed the saveWorktime function to properly find the input element
 *
 * ⚠️ IMPORTANT: Time-off type patterns in this file MUST match TimeOffTypeRegistry.java
 * Backend source of truth: src/main/java/com/ctgraphdep/config/TimeOffTypeRegistry.java
 *
 * Special Day Types (support work hours TYPE:X):
 * - SN (National Holiday), CO (Vacation), CM (Medical), W (Weekend), CE (Special Event)
 *
 * Plain Time-Off Types (no work hours):
 * - D (Delegation), CN (Unpaid Leave), CR (Recovery Leave)
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

function validateWorktimeValue(value) {
    // Handle special day work time format (SN:5, CO:6, CM:4, W:8, CE:6)
    if (value.includes(':')) {
        return validateSpecialDayWorktime(value);
    }

    // Handle ZS format (ZS-5 means missing 5 hours)
    if (value.toUpperCase().startsWith('ZS-')) {
        return validateZSFormat(value);
    }

    // Handle time off types (expanded with CR, CN, D, CE)
    if (['CO', 'CM', 'SN', 'W', 'CR', 'CN', 'D', 'CE'].includes(value.toUpperCase())) {
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

    alert('Invalid format. Use:\n- Hours: 8 or 8h\n- Special work: SN:7.5, CO:6, CM:4, W:8, CE:6\n- Time off: CO, CM, SN, W, CR, CN, D, CE\n- Short day: ZS-5 (missing 5 hours)');
    return false;
}

/**
 * Validate ZS format (short day)
 * Format: ZS-5 means user is missing 5 hours (worked less than schedule)
 */
function validateZSFormat(value) {
    const parts = value.toUpperCase().split('-');

    if (parts.length !== 2 || parts[0] !== 'ZS') {
        alert('Invalid ZS format. Use: ZS-X where X is missing hours (e.g., ZS-5)');
        return false;
    }

    const missingHours = parseInt(parts[1]);
    if (isNaN(missingHours) || missingHours < 1 || missingHours > 12) {
        alert('Missing hours must be between 1 and 12 (e.g., ZS-5)');
        return false;
    }

    return true;
}

/**
 * Validate special day work time format for all types
 * Supports: SN:5, CO:6, CM:4, W:8, CE:6
 */
function validateSpecialDayWorktime(value) {
    const parts = value.split(':');

    // Check format: must be exactly "TYPE:number"
    if (parts.length !== 2) {
        alert('Invalid format. Use TYPE:hours (e.g., SN:7.5, CO:6, CM:4, W:8, CE:6)');
        return false;
    }

    const type = parts[0].toUpperCase();
    const hoursStr = parts[1];

    // Validate type (added CE)
    if (!['SN', 'CO', 'CM', 'W', 'CE'].includes(type)) {
        alert('Invalid type. Use SN, CO, CM, W, or CE (e.g., SN:7.5, CE:6)');
        return false;
    }

    // Parse and validate hours
    const hours = parseFloat(hoursStr);
    if (isNaN(hours) || hours < 1 || hours > 24) {
        alert(`${type} work hours must be between 1 and 24 (e.g., ${type}:7.5)`);
        return false;
    }

    // Warn about partial hour discarding if applicable
    if (hours % 1 !== 0) {
        const fullHours = Math.floor(hours);
        const discarded = hours - fullHours;

        const typeLabels = {
            'SN': 'National Holiday',
            'CO': 'Time Off',
            'CM': 'Medical Leave',
            'W': 'Weekend'
        };

        const confirmed = confirm(
            `${typeLabels[type]} Work Time Processing:\n\n` +
            `Input: ${hours} hours\n` +
            `Processed: ${fullHours} full hours\n` +
            `Discarded: ${discarded.toFixed(1)} hours\n\n` +
            `Only full hours count for special day work.\n\n` +
            `Continue with ${fullHours} hours?`
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
 * PROPERLY FIXED: Maintain current year/month view after update
 */
function submitWorktimeUpdate(cell, value) {
    const userId = cell.dataset.userId;
    const [year, month, day] = cell.dataset.date.split('-');

    if (!userId || !year || !month || !day) {
        console.error('Invalid cell data:', cell.dataset);
        alert('Error: Invalid cell data');
        return;
    }

    // Get current view period BEFORE making the request
    const currentViewPeriod = getCurrentViewPeriod();

    console.log('Submitting worktime update:', {
        userId, year, month, day, value,
        currentView: currentViewPeriod
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

            // PROPERLY FIXED: Maintain current view and reload data
            setTimeout(() => {
                console.log('Refreshing current view:', currentViewPeriod);
                // Redirect to the CURRENT view (not the entry's month)
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
 * FIXED: Get current view period from form selectors
 */
function getCurrentViewPeriod() {
    const yearSelect = document.getElementById('yearSelect');
    const monthSelect = document.getElementById('monthSelect');

    // Get values from the actual form controls (not from URL or entry data)
    const currentYear = yearSelect ? yearSelect.value : new Date().getFullYear();
    const currentMonth = monthSelect ? monthSelect.value : new Date().getMonth() + 1;

    console.log('Current view period determined:', { year: currentYear, month: currentMonth });

    return {
        year: currentYear,
        month: currentMonth
    };
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
    } else if (cellContent === 'CR') {
        entryData.timeOffType = 'CR';
        entryData.timeOffLabel = 'Recovery Leave (CR)';
    } else if (cellContent === 'CN') {
        entryData.timeOffType = 'CN';
        entryData.timeOffLabel = 'Unpaid Leave (CN)';
    } else if (cellContent === 'D') {
        entryData.timeOffType = 'D';
        entryData.timeOffLabel = 'Delegation (D)';
    } else if (cellContent === 'CE') {
        entryData.timeOffType = 'CE';
        entryData.timeOffLabel = 'Event Leave (CE)';
    } else if (cellContent.startsWith('ZS-')) {
        // Handle ZS-5 format
        entryData.timeOffType = cellContent;
        entryData.timeOffLabel = getTimeOffLabel(cellContent);
    } else if (cellContent.startsWith('SN') && cellContent.length > 2) {
        // Handle SN4 format
        const hours = cellContent.substring(2);
        entryData.timeOffType = 'SN';
        entryData.timeOffLabel = 'National Holiday';
        entryData.overtimeHours = hours + 'h';
    } else if (cellContent.startsWith('CO') && cellContent.length > 2 && /^\d+$/.test(cellContent.substring(2))) {
        // Handle CO6 format
        const hours = cellContent.substring(2);
        entryData.timeOffType = 'CO';
        entryData.timeOffLabel = 'Vacation';
        entryData.overtimeHours = hours + 'h';
    } else if (cellContent.startsWith('CM') && cellContent.length > 2 && /^\d+$/.test(cellContent.substring(2))) {
        // Handle CM4 format
        const hours = cellContent.substring(2);
        entryData.timeOffType = 'CM';
        entryData.timeOffLabel = 'Medical Leave';
        entryData.overtimeHours = hours + 'h';
    } else if (cellContent.startsWith('CE') && cellContent.length > 2 && /^\d+$/.test(cellContent.substring(2))) {
        // Handle CE6 format
        const hours = cellContent.substring(2);
        entryData.timeOffType = 'CE';
        entryData.timeOffLabel = 'Event Leave (CE)';
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

    // NEW: Show temporary stops details if available
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
        // Fallback if we only have the total
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
        const description = getTimeOffDescription(entryData.timeOffType);

        html += `<small class="text-primary">
            <i class="${iconClass} entry-icon"></i><strong>Type:</strong> ${timeOffLabel}
        </small>`;

        // Add helpful description for special types (CR, ZS, CN, D, CE)
        if (description) {
            html += `<small class="text-muted d-block ms-3" style="font-size: 0.85em; font-style: italic; line-height: 1.3;">
                <i class="bi bi-info-circle-fill entry-icon text-info"></i>${description}
            </small>`;
        }

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

    // Handle overtime (API or fallback) - with dynamic label based on day/type
    if (entryData.totalOvertimeMinutes && parseInt(entryData.totalOvertimeMinutes) > 0) {
        const hours = formatMinutesToHours(parseInt(entryData.totalOvertimeMinutes));
        const cellDate = cell.dataset.date;
        const overtimeLabel = getOvertimeTypeLabel(cellDate, entryData.timeOffType);
        html += `<small class="text-danger">
            <i class="bi bi-clock-history entry-icon"></i><strong>${overtimeLabel}:</strong> ${hours}
        </small>`;
        hasData = true;
    } else if (entryData.overtimeHours) {
        const cellDate = cell.dataset.date;
        const overtimeLabel = getOvertimeTypeLabel(cellDate, entryData.timeOffType);
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
    if (!timeOffType) return timeOffType;

    // Handle ZS format (ZS-5 means missing 5 hours)
    if (timeOffType.startsWith('ZS-')) {
        const missingHours = timeOffType.split('-')[1];
        return `Short Day (missing ${missingHours}h)`;
    }

    switch (timeOffType.toUpperCase()) {
        case 'SN': return 'National Holiday';
        case 'CO': return 'Vacation';
        case 'CM': return 'Medical Leave';
        case 'W': return 'Weekend Work';
        case 'CR': return 'Recovery Leave (CR)';
        case 'CN': return 'Unpaid Leave (CN)';
        case 'D': return 'Delegation (D)';
        case 'CE': return 'Event Leave (CE)';
        default: return timeOffType;
    }
}

function getTimeOffIcon(timeOffType) {
    if (!timeOffType) return 'bi bi-calendar-x';

    // Handle ZS format
    if (timeOffType.startsWith('ZS-')) {
        return 'bi bi-hourglass-split text-warning';
    }

    switch (timeOffType.toUpperCase()) {
        case 'SN': return 'bi bi-calendar-event text-success';
        case 'CO': return 'bi bi-airplane text-info';
        case 'CM': return 'bi bi-heart-pulse text-warning';
        case 'W': return 'bi bi-calendar-week text-secondary';
        case 'CR': return 'bi bi-battery-charging text-success';
        case 'CN': return 'bi bi-dash-circle text-secondary';
        case 'D': return 'bi bi-briefcase text-primary';
        case 'CE': return 'bi bi-gift text-danger';
        default: return 'bi bi-calendar-x';
    }
}

/**
 * Get helpful description for each time-off type
 */
function getTimeOffDescription(timeOffType) {
    if (!timeOffType) return '';

    // Handle ZS format
    if (timeOffType.startsWith('ZS-')) {
        const missingHours = timeOffType.split('-')[1];
        return `User worked less than schedule. Missing ${missingHours} hours will be deducted from overtime.`;
    }

    switch (timeOffType.toUpperCase()) {
        case 'CR':
            return 'Recovery Leave - Paid day off using overtime balance. Deducts full schedule hours (8h) from overtime → regular time.';
        case 'CN':
            return 'Unpaid Leave - Day off without payment. Does not count as work day or deduct from balances.';
        case 'D':
            return 'Delegation / Business Trip - Normal work day with special documentation. Counts as regular work day.';
        case 'CE':
            return 'Event Leave - Special event (marriage, birth, death). Free days per company policy. Field 2 required in form.';
        case 'SN':
            return 'National Holiday - Company holiday. If worked, all time counts as overtime.';
        case 'CO':
            return 'Vacation - Paid time off using vacation balance. Deducts from annual vacation days.';
        case 'CM':
            return 'Medical Leave - Sick day. Does not deduct from vacation balance.';
        case 'W':
            return 'Weekend Work - Work on weekend day. All time counts as overtime.';
        default:
            return '';
    }
}

/**
 * Determine the correct overtime type label based on date and time-off type
 * - Monday-Friday normal work: "Overtime"
 * - Saturday-Sunday (W): "Weekend Overtime"
 * - SN/CO/CE/CM with work: "Holiday Overtime"
 */
function getOvertimeTypeLabel(dateString, timeOffType) {
    // Parse the date to determine day of week
    const date = new Date(dateString);
    const dayOfWeek = date.getDay(); // 0 = Sunday, 6 = Saturday

    // Check if it's a holiday/vacation/event with work (SN:5, CO:5, CE:5, CM:5)
    if (timeOffType) {
        const upperType = timeOffType.toUpperCase();
        if (upperType === 'SN' || upperType.startsWith('SN:') ||
            upperType === 'CO' || upperType.startsWith('CO:') ||
            upperType === 'CE' || upperType.startsWith('CE:') ||
            upperType === 'CM' || upperType.startsWith('CM:')) {
            return 'Holiday Overtime';
        }

        // Weekend work (W, W:5)
        if (upperType === 'W' || upperType.startsWith('W:')) {
            return 'Weekend Overtime';
        }
    }

    // Check if it's a weekend day (Saturday or Sunday)
    if (dayOfWeek === 0 || dayOfWeek === 6) {
        return 'Weekend Overtime';
    }

    // Normal weekday overtime
    return 'Overtime';
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

/**
 * Format datetime string to HH:mm format
 * @param {string} dateTimeStr - DateTime string (e.g., "2025-10-07 20:31:41" or ISO format)
 * @returns {string} Formatted time string (e.g., "20:31")
 */
function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return '--:--';

    try {
        // Handle both "YYYY-MM-DD HH:mm:ss" and ISO format
        let date;
        if (dateTimeStr.includes('T')) {
            // ISO format
            date = new Date(dateTimeStr);
        } else {
            // "YYYY-MM-DD HH:mm:ss" format
            const parts = dateTimeStr.split(' ');
            if (parts.length === 2) {
                date = new Date(parts[0] + 'T' + parts[1]);
            } else {
                return '--:--';
            }
        }

        if (isNaN(date.getTime())) {
            return '--:--';
        }

        // Format as HH:mm
        const hours = date.getHours().toString().padStart(2, '0');
        const minutes = date.getMinutes().toString().padStart(2, '0');
        return `${hours}:${minutes}`;
    } catch (error) {
        console.warn('Error formatting datetime:', dateTimeStr, error);
        return '--:--';
    }
}

// ========================================================================
// FINALIZATION FUNCTIONALITY
// ========================================================================

let currentFinalizationData = null;

/**
 * Show finalization confirmation dialog
 * @param {number|null} userId - User ID to finalize, or null for all users
 */
function showFinalizeConfirmation(userId) {
    const modal = new bootstrap.Modal(document.getElementById('finalizeConfirmationModal'));
    const modalText = document.getElementById('finalizeModalText');
    const detailsDiv = document.getElementById('finalizeDetails');

    // Store finalization data
    currentFinalizationData = {
        userId: userId,
        year: getCurrentYear(),
        month: getCurrentMonth()
    };

    // Update modal content based on scope
    if (userId === null) {
        modalText.innerHTML = `
            <strong>Finalize ALL users</strong> for ${getMonthName(currentFinalizationData.month)} ${currentFinalizationData.year}?
            <br><br>
            This will mark all worktime entries as <strong>ADMIN_FINAL</strong> and prevent any further modifications.
        `;

        detailsDiv.innerHTML = `
            <strong>Scope:</strong> All users in this period<br>
            <strong>Period:</strong> ${getMonthName(currentFinalizationData.month)} ${currentFinalizationData.year}<br>
            <strong>Estimated entries:</strong> ~${estimateEntryCount()} entries<br>
            <strong>Status change:</strong> → ADMIN_FINAL
        `;
    } else {
        const userName = getUserName(userId);
        modalText.innerHTML = `
            <strong>Finalize user "${userName}"</strong> for ${getMonthName(currentFinalizationData.month)} ${currentFinalizationData.year}?
            <br><br>
            This will mark all worktime entries for this user as <strong>ADMIN_FINAL</strong> and prevent any further modifications.
        `;

        detailsDiv.innerHTML = `
            <strong>Scope:</strong> ${userName} only<br>
            <strong>Period:</strong> ${getMonthName(currentFinalizationData.month)} ${currentFinalizationData.year}<br>
            <strong>Estimated entries:</strong> ~${getDaysInMonth(currentFinalizationData.year, currentFinalizationData.month)} entries<br>
            <strong>Status change:</strong> → ADMIN_FINAL
        `;
    }

    modal.show();
}

/**
 * Finalize specific user (called from button)
 */
function finalizeSpecificUser() {
    const userSelect = document.getElementById('finalizeUserSelect');
    const userId = userSelect.value;

    if (!userId) {
        alert('Please select a user to finalize');
        return;
    }

    showFinalizeConfirmation(parseInt(userId));
}

/**
 * Execute the finalization after confirmation
 */
async function executeFinalization() {
    if (!currentFinalizationData) {
        console.error('No finalization data available');
        return;
    }

    // Hide confirmation modal
    const confirmModal = bootstrap.Modal.getInstance(document.getElementById('finalizeConfirmationModal'));
    confirmModal.hide();

    // Show progress modal
    const progressModal = new bootstrap.Modal(document.getElementById('finalizationProgressModal'));
    progressModal.show();

    try {
        console.log('Executing finalization:', currentFinalizationData);

        // Build form data
        const formData = new URLSearchParams();
        formData.append('year', currentFinalizationData.year);
        formData.append('month', currentFinalizationData.month);
        if (currentFinalizationData.userId) {
            formData.append('userId', currentFinalizationData.userId);
        }

        // Submit finalization request
        const response = await fetch('/admin/worktime/finalize', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: formData
        });

        // Hide progress modal
        progressModal.hide();

        if (response.ok) {
            // Success - redirect to show results
            console.log('Finalization successful, redirecting...');
            window.location.reload(); // Reload to show updated status and success message
        } else {
            // Error handling
            const errorText = await response.text();
            console.error('Finalization failed:', response.status, errorText);

            alert('Finalization failed: ' + (errorText || 'Unknown error'));
        }

    } catch (error) {
        // Hide progress modal
        progressModal.hide();

        console.error('Finalization error:', error);
        alert('Finalization failed: Network error');
    } finally {
        currentFinalizationData = null;
    }
}

// ========================================================================
// HELPER FUNCTIONS FOR FINALIZATION
// ========================================================================

/**
 * Get current year from form
 */
function getCurrentYear() {
    const yearSelect = document.getElementById('yearSelect');
    return yearSelect ? parseInt(yearSelect.value) : new Date().getFullYear();
}

/**
 * Get current month from form
 */
function getCurrentMonth() {
    const monthSelect = document.getElementById('monthSelect');
    return monthSelect ? parseInt(monthSelect.value) : new Date().getMonth() + 1;
}

/**
 * Get month name from number
 */
function getMonthName(monthNumber) {
    const months = [
        'January', 'February', 'March', 'April', 'May', 'June',
        'July', 'August', 'September', 'October', 'November', 'December'
    ];
    return months[monthNumber - 1] || 'Unknown';
}

/**
 * Get user name from userId
 */
function getUserName(userId) {
    const userSelect = document.getElementById('finalizeUserSelect');
    const option = userSelect.querySelector(`option[value="${userId}"]`);
    return option ? option.textContent : `User ${userId}`;
}

/**
 * Estimate total entry count for all users
 */
function estimateEntryCount() {
    const userSelect = document.getElementById('finalizeUserSelect');
    const userCount = userSelect.options.length - 1; // Exclude "Select user..." option
    const daysInMonth = getDaysInMonth(getCurrentYear(), getCurrentMonth());
    return userCount * daysInMonth;
}

/**
 * Get days in month
 */
function getDaysInMonth(year, month) {
    return new Date(year, month, 0).getDate();
}
