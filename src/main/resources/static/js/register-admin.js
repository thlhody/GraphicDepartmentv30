// Initialize state management
const state = {
    bonusCalculationData: {
        totalEntries: window.serverData.totalEntries || 0,
        averageArticleNumbers: window.serverData.averageArticleNumbers || 0,
        averageGraphicComplexity: window.serverData.averageGraphicComplexity || 0,
        workedDays: window.serverData.workedDays || 0
    },
    currentUser: window.serverData.currentUser,
    currentYear: window.serverData.currentYear,
    currentMonth: window.serverData.currentMonth,
    entries: window.serverData.entries || [],
    filteredEntries: []
};

// Initialize the page
// Add this right after your state declaration to debug the initial state
document.addEventListener('DOMContentLoaded', function() {
    console.log("Initializing Admin Register Management");
    console.log("Initial state:", JSON.stringify(state, null, 2));
    // Initialize filtered entries
    state.filteredEntries = Array.isArray(state.entries) ? [...state.entries] : [];
    updateSummaryDisplay();

    // Add warning for no user selection
    if (!state.currentUser) {
        console.warn("No user selected - awaiting user selection");

        // Pre-select first user if available
        const userSelect = document.getElementById('userSelect');
        if (userSelect && userSelect.options.length > 1 && !userSelect.value) {
            // Show a suggestion to select a user
            const alertsContainer = document.querySelector('.alerts');
            if (alertsContainer) {
                alertsContainer.innerHTML = `
                    <div class="alert alert-info">
                        <i class="bi bi-info-circle me-2"></i>
                        Please select a user and period to view register entries.
                    </div>
                `;
            }
        }
    }

    // Initialize state
    state.filteredEntries = Array.isArray(state.entries) ? [...state.entries] : [];

    try {
        initializeFormHandling();
        initializeControllers();
        initializeEventListeners();
        initializeFormValidation();
        initializeEditableCG();

        if (state.currentUser?.userId) {
            console.log("Loading register summary for user:", state.currentUser);
            loadRegisterSummary();
            markFormFieldsAsFilled();
        } else {
            // Ensure form fields are properly initialized even without a user
            markDefaultFormFields();
        }
    } catch (error) {
        console.error("Initialization error:", error);
        showError("Failed to initialize page. Please refresh and try again.");
    }
    // If the summary data wasn't provided from the server, calculate it from entries
    if (state.entries && state.entries.length > 0 && !state.bonusCalculationData.totalEntries) {
        calculateSummaryFromEntries(state.entries);
    }
    syncFormWithUrlParams();
    updateTablePlaceholder();
    debugThymeleafData();
    debugFormStructure();
    setTimeout(() => {debugPrintPrepTypesDisplay();}, 1000);

});

// Add a function to calculate summary directly from entries
function calculateSummaryFromEntries(entries) {
    if (!entries || entries.length === 0) return;

    const validEntries = entries.filter(entry => entry.actionType !== 'IMPOSTARE');
    if (validEntries.length === 0) return;

    state.bonusCalculationData.totalEntries = validEntries.length;
    state.bonusCalculationData.averageArticleNumbers = validEntries.reduce((sum, entry) =>
    sum + (entry.articleNumbers || 0), 0) / validEntries.length;
    state.bonusCalculationData.averageGraphicComplexity = validEntries.reduce((sum, entry) =>
    sum + (entry.graphicComplexity || 0), 0) / validEntries.length;

    updateSummaryDisplay();
}

// Add this new function to ensure year and month are marked as filled
function markDefaultFormFields() {
    const yearSelect = document.getElementById('yearSelect');
    const monthSelect = document.getElementById('monthSelect');

    if (yearSelect && yearSelect.value) {
        yearSelect.classList.remove('field-empty');
        yearSelect.classList.add('field-filled');
    }

    if (monthSelect && monthSelect.value) {
        monthSelect.classList.remove('field-empty');
        monthSelect.classList.add('field-filled');
    }

    // Highlight the user select to encourage selection
    const userSelect = document.getElementById('userSelect');
    if (userSelect) {
        userSelect.classList.add('field-empty');
    }
}

// Form field validation and styling
function initializeFormValidation() {
    const selects = document.querySelectorAll('#yearSelect, #monthSelect, #userSelect');

    selects.forEach(select => {
        select.addEventListener('change', function() {
            if (this.value) {
                this.classList.remove('field-empty');
                this.classList.add('field-filled');
            } else {
                this.classList.remove('field-filled');
                this.classList.add('field-empty');
            }
        });

        // Initialize on page load
        if (select.value) {
            select.classList.remove('field-empty');
            select.classList.add('field-filled');
        }
    });
}

function markFormFieldsAsFilled() {
    const selects = document.querySelectorAll('#yearSelect, #monthSelect, #userSelect');
    selects.forEach(select => {
        if (select.value) {
            select.classList.remove('field-empty');
            select.classList.add('field-filled');
        }
    });
}

// Initialize CG editable fields
function initializeEditableCG() {
    const cgCells = document.querySelectorAll('.cg-editable');

    cgCells.forEach(cell => {
        cell.setAttribute('tabindex', '0');
        cell.style.cursor = 'pointer';

        // Click handler
        cell.addEventListener('click', function() {
            createCGEditor(this);
        });

        // Key handler for accessibility
        cell.addEventListener('keydown', function(e) {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                createCGEditor(this);
            }
        });
    });
}

function createCGEditor(cell) {
    const originalValue = parseFloat(cell.textContent.trim());
    const input = document.createElement('input');
    input.type = 'number';
    input.value = originalValue;
    input.min = '0.0';
    input.max = '9.0';
    input.step = '0.5';
    input.classList.add('form-control', 'form-control-sm');

    // Replace cell content with input
    cell.innerHTML = '';
    cell.appendChild(input);

    input.focus();
    // Select all text
    input.select();

    // Handle save on blur
    input.addEventListener('blur', function() {
        saveCGValue(cell, input, originalValue);
    });

    // Handle keyboard navigation
    input.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            saveCGValue(cell, input, originalValue);

            // Find next CG cell for tabbing
            const currentRow = cell.closest('tr');
            const nextRow = currentRow.nextElementSibling;

            if (nextRow) {
                const nextCG = nextRow.querySelector('.cg-editable');
                if (nextCG) {
                    nextCG.focus();
                    setTimeout(() => createCGEditor(nextCG), 50);
                }
            }

        } else if (e.key === 'Escape') {
            cell.textContent = originalValue.toFixed(1);
            cell.focus();
        } else if (e.key === 'Tab') {
            // Natural tab navigation will happen after blur
            saveCGValue(cell, input, originalValue);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            const newVal = Math.min(parseFloat(input.value) + 0.5, 9.0);
            input.value = newVal.toFixed(1);
        } else if (e.key === 'ArrowDown') {
            e.preventDefault();
            const newVal = Math.max(parseFloat(input.value) - 0.5, 0.0);
            input.value = newVal.toFixed(1);
        }
    });
}

function saveCGValue(cell, input, originalValue) {
    const newValue = parseFloat(input.value.trim());

    // Validate input
    if (isNaN(newValue) || newValue < 0 || newValue > 9) {
        cell.textContent = originalValue.toFixed(1);
        return;
    }

    // Only mark as changed if value actually changed
    if (newValue !== originalValue) {
        cell.textContent = newValue.toFixed(1);
        cell.classList.add('field-edited');

        // Mark the row as selected (check its checkbox)
        const row = cell.closest('tr');
        const checkbox = row.querySelector('.entry-select');

        if (checkbox) {
            checkbox.checked = true;
        }
    } else {
        cell.textContent = originalValue.toFixed(1);
    }
}

// Add separate event listener for export button
document.getElementById('exportExcel')?.addEventListener('click', function() {
    const userId = document.getElementById('userSelect').value;
    const year = document.getElementById('yearSelect').value;
    const month = document.getElementById('monthSelect').value;

    if (!userId) {
        showError("Please select a user");
        return;
    }

    // Create the export URL with query parameters
    const exportUrl = `/admin/register/export?userId=${userId}&year=${year}&month=${month}`;

    // Trigger the download
    window.location.href = exportUrl;
});

// Form handling
// Update initializeFormHandling function to use different selectors
function initializeFormHandling() {
    // Try different selectors to find the load button
    const loadDataBtn = document.querySelector('.period-selector button[type="submit"]') ||
    document.querySelector('.period-selector .btn-primary:first-child');

    if (loadDataBtn) {
        console.log("Load data button found:", loadDataBtn);

        loadDataBtn.addEventListener('click', function(e) {
            e.preventDefault();
            const userSelect = document.getElementById('userSelect');
            const yearSelect = document.getElementById('yearSelect');
            const monthSelect = document.getElementById('monthSelect');

            const userId = userSelect?.value;
            const year = yearSelect?.value;
            const month = monthSelect?.value;

            console.log("Load data clicked with:", {userId, year, month});

            if (!userId) {
                showError("Please select a user");
                if (userSelect) {
                    userSelect.classList.remove('field-filled');
                    userSelect.classList.add('field-empty');
                    // Add a subtle animation to draw attention
                    userSelect.animate([
                        { transform: 'translateX(0)' },
                        { transform: 'translateX(-5px)' },
                        { transform: 'translateX(5px)' },
                        { transform: 'translateX(0)' }
                    ], {
                        duration: 300,
                        iterations: 2
                    });
                }
                return;
            }

            // All fields look good, proceed with loading data
            console.log(`Navigating to /admin/register?userId=${userId}&year=${year}&month=${month}`);
            window.location.href = `/admin/register?userId=${userId}&year=${year}&month=${month}`;
        });
    } else {
        console.error("Load data button not found using primary selectors");

        // Try finding any button in the period selector area
        const firstButton = document.querySelector('.period-selector button');
        if (firstButton) {
            console.log("Using first button in period selector as fallback:", firstButton);

            firstButton.addEventListener('click', function(e) {
                e.preventDefault();
                const userId = document.getElementById('userSelect')?.value;
                const year = document.getElementById('yearSelect')?.value;
                const month = document.getElementById('monthSelect')?.value;

                if (!userId) {
                    showError("Please select a user");
                    return;
                }

                window.location.href = `/admin/register?userId=${userId}&year=${year}&month=${month}`;
            });
        } else {
            showError("UI initialization error: Unable to find Load button");
        }
    }
}

// Add this function to your script
function updateTablePlaceholder() {
    const table = document.getElementById('registerTable');
    if (!table) return;

    const tbody = table.querySelector('tbody');
    if (!tbody) return;

    // Check if the tbody is empty or only has placeholder content
    if (tbody.children.length === 0 || (tbody.children.length === 1 && tbody.children[0].classList.contains('placeholder-row'))) {
        tbody.innerHTML = `
            <tr class="placeholder-row">
                <td colspan="13" class="text-center p-5">
                    <div class="d-flex flex-column align-items-center">
                        <i class="bi bi-table text-muted mb-3" style="font-size: 3rem;"></i>
                        <h4 class="text-muted">No Register Entries</h4>
                        <p class="mb-3">Please select a user from the dropdown and click "Load Data" to view register entries.</p>
                        <div class="d-flex gap-2">
                            <i class="bi bi-arrow-up text-primary"></i>
                            <span class="text-primary">Select user above</span>
                        </div>
                    </div>
                </td>
            </tr>
        `;
    }
}

// Add this function to synchronize form values with URL parameters
function syncFormWithUrlParams() {
    // Get URL parameters
    const urlParams = new URLSearchParams(window.location.search);
    const urlUserId = urlParams.get('userId');
    const urlYear = urlParams.get('year');
    const urlMonth = urlParams.get('month');

    console.log("URL params:", {urlUserId, urlYear, urlMonth});

    // Set select values if parameters exist
    if (urlUserId) {
        const userSelect = document.getElementById('userSelect');
        if (userSelect) {
            userSelect.value = urlUserId;
            userSelect.classList.remove('field-empty');
            userSelect.classList.add('field-filled');
        }
    }

    if (urlYear) {
        const yearSelect = document.getElementById('yearSelect');
        if (yearSelect) {
            yearSelect.value = urlYear;
            yearSelect.classList.remove('field-empty');
            yearSelect.classList.add('field-filled');
        }
    }

    if (urlMonth) {
        const monthSelect = document.getElementById('monthSelect');
        if (monthSelect) {
            monthSelect.value = urlMonth;
            monthSelect.classList.remove('field-empty');
            monthSelect.classList.add('field-filled');
        }
    }
}

// UI Controllers
function initializeControllers() {
    // Initialize select all checkbox
    const selectAll = document.getElementById('selectAll');
    if (selectAll) {
        selectAll.addEventListener('change', function() {
            document.querySelectorAll('.entry-select').forEach(checkbox => {
                checkbox.checked = this.checked;
            });
        });
    }
}

// Event Listeners
function initializeEventListeners() {
    const listeners = {
        'calculateBonusBtn': calculateBonus,
        'saveChanges': saveChanges,
        'clearTable': clearTable
    };

    Object.entries(listeners).forEach(([id, handler]) => {
        document.getElementById(id)?.addEventListener('click', handler);
    });
}

// API Interactions
async function loadRegisterSummary() {
    if (!validateUserContext()) {
        showWarning('Missing user information. Please select a user and period.');
        return;
    }

    try {
        // Load register summary
        console.log('Loading summary for:', state.currentUser.username, state.currentUser.userId, state.currentYear, state.currentMonth);

        const summaryResponse = await fetch(
            `/admin/register/summary?username=${encodeURIComponent(state.currentUser.username)}` +
            `&userId=${state.currentUser.userId}&year=${state.currentYear}&month=${state.currentMonth}`
        );

        if (!summaryResponse.ok) {
            const errorText = await summaryResponse.text();
            console.error('Summary response:', summaryResponse.status, errorText);
            throw new Error(`HTTP error! status: ${summaryResponse.status} - ${errorText || summaryResponse.statusText}`);
        }

        const summary = await summaryResponse.json();
        console.log('Summary data:', summary);

        // Load worked days
        const workedDaysResponse = await fetch(
            `/admin/register/worked-days?userId=${state.currentUser.userId}` +
            `&year=${state.currentYear}&month=${state.currentMonth}`
        );

        if (!workedDaysResponse.ok) {
            const errorText = await workedDaysResponse.text();
            console.error('Worked days response:', workedDaysResponse.status, errorText);
            throw new Error(`Failed to load worked days: ${errorText || workedDaysResponse.statusText}`);
        }

        const workedDays = await workedDaysResponse.json();
        console.log('Worked days:', workedDays);

        // Update state with both summary and worked days
        state.bonusCalculationData = {
            ...summary,
            workedDays: workedDays
        };

        updateSummaryDisplay();

    } catch (error) {
        console.error('Summary loading error:', error);
        showError('Failed to load register summary: ' + error.message);
    }
}

// Bonus Calculations
async function calculateBonus() {
    if (!validateUserContext()) {
        showWarning('Please select a user and period before calculating bonus.');
        return;
    }

    const config = getBonusConfig();
    if (!validateBonusConfig(config)) return;

    const workedDays = parseInt(document.getElementById('workedDays').value) || 0;
    if (workedDays <= 0) {
        showWarning('No worked days recorded for this period. The bonus calculation will show raw totals but bonus amounts will be zero.');
    }

    try {
        // Get visible entries from the table
        const visibleEntries = Array.from(document.querySelectorAll('#registerTable tbody tr'))
            .filter(row => row.style.display !== 'none')
            .map(row => {
            return {
                articleNumbers: parseInt(row.querySelector('td:nth-child(10)').textContent) || 0,
                graphicComplexity: parseFloat(row.querySelector('td:nth-child(11)').textContent) || 0,
                actionType: row.querySelector('td:nth-child(7)').textContent
            };
        })
        // Filter out only IMPOSTARE
            .filter(entry => entry.actionType !== 'IMPOSTARE');

        // Add error handling for empty entries
        if (visibleEntries.length === 0) {
            showWarning('No entries found to calculate bonus. Please load data first.');
            return;
        }

        // Log the payload for debugging
        console.log('Sending bonus calculation request with:', {
            entries: visibleEntries,
            userId: state.currentUser?.userId,
            year: state.currentYear,
            month: state.currentMonth,
            bonusConfig: config
        });

        const response = await fetch('/admin/register/calculate-bonus', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                entries: visibleEntries,
                userId: state.currentUser?.userId,
                year: state.currentYear,
                month: state.currentMonth,
                bonusConfig: config
            })
        });

        // More detailed error handling
        if (!response.ok) {
            const errorText = await response.text();
            console.error('Server response:', response.status, errorText);
            throw new Error(`Bonus calculation failed: ${errorText || response.statusText}`);
        }

        const result = await response.json();
        displayBonusResults(result);
    } catch (error) {
        console.error('Bonus calculation error:', error);

        // More specific error messaging
        if (error.message.includes('Worked days must be greater than 0')) {
            showError('No worked days recorded for this period. Please ensure worked days are properly set.');
        } else {
            showError(`Failed to calculate bonus: ${error.message}`);
        }
    }
}

// Utility Functions
function updateSummaryDisplay() {
    try {
        const totalEntries = document.getElementById('totalEntries');
        const averageArticles = document.getElementById('averageArticles');
        const averageComplexity = document.getElementById('averageComplexity');
        const workedDays = document.getElementById('workedDays');

        if (totalEntries) totalEntries.value = state.bonusCalculationData.totalEntries || 0;
        if (averageArticles) averageArticles.value = (state.bonusCalculationData.averageArticleNumbers || 0).toFixed(2);
        if (averageComplexity) averageComplexity.value = (state.bonusCalculationData.averageGraphicComplexity || 0).toFixed(2);
        if (workedDays) workedDays.value = state.bonusCalculationData.workedDays || 0;
    } catch (error) {
        console.error("Error updating summary display:", error);
        showError("Failed to update summary display");
    }
}

function updateSummaryFromEntries(entries) {
    const workedDays = state.bonusCalculationData.workedDays; // Preserve worked days

    state.bonusCalculationData = {
        totalEntries: entries.length,
        averageArticleNumbers: entries.reduce((sum, entry) => sum + entry.articleNumbers, 0) / entries.length || 0,
        averageGraphicComplexity: entries.reduce((sum, entry) => sum + entry.graphicComplexity, 0) / entries.length || 0,
        workedDays: workedDays // Keep the worked days value
    };

    updateSummaryDisplay();
}

async function saveChanges() {
    if (!state.currentUser) {
        showError('No user selected');
        return;
    }

    try {
        // Get ALL entries from the table, not just the edited ones
        const allEntries = Array.from(document.querySelectorAll('#registerTable tbody tr'))
            .filter(row => row.style.display !== 'none') // Only include visible rows
            .map(row => {
            // Convert date from dd/MM/yyyy to yyyy-MM-dd format
            const dateText = row.querySelector('td:nth-child(2)').textContent;
            const [day, month, year] = dateText.split('/');
            const isoDate = `${year}-${month}-${day}`;

            // Get print prep types text
            const printPrepTypesCell = row.querySelector('td:nth-child(8)');
            const printPrepTypesText = printPrepTypesCell ? printPrepTypesCell.textContent.trim() : '';

            // Improved print prep types handling
            let printPrepTypes;
            if (!printPrepTypesText || printPrepTypesText.trim() === '' || printPrepTypesText.toLowerCase() === 'null') {
                // Default to DIGITAL if empty or null
                printPrepTypes = ['DIGITAL'];
            } else {
                // Handle case where text might contain 'null' string
                printPrepTypes = printPrepTypesText
                    .split(',')
                    .map(type => type.trim())
                    .filter(type => type && type.toLowerCase() !== 'null');

                // If we filtered everything out, use default
                if (printPrepTypes.length === 0) {
                    printPrepTypes = ['DIGITAL'];
                }
            }

            // Important: Get the current syncStatus from the badge
            const statusBadge = row.querySelector('td:nth-child(13) .badge');
            let adminSync = statusBadge ? statusBadge.textContent.trim() : "USER_DONE";

            // Check if this row was just edited
            const checkbox = row.querySelector('.entry-select');
            const editedCG = row.querySelector('.cg-editable.field-edited');

            // If currently edited, mark as ADMIN_EDITED
            if ((checkbox && checkbox.checked) || editedCG) {
                adminSync = "ADMIN_EDITED";
            }

            return {
                entryId: parseInt(row.querySelector('.entry-select').value),
                userId: state.currentUser.userId,
                date: isoDate,
                orderId: row.querySelector('td:nth-child(3)').textContent,
                productionId: row.querySelector('td:nth-child(4)').textContent,
                omsId: row.querySelector('td:nth-child(5)').textContent,
                clientName: row.querySelector('td:nth-child(6)').textContent,
                actionType: row.querySelector('td:nth-child(7)').textContent,
                printPrepTypes: printPrepTypes,
                colorsProfile: row.querySelector('td:nth-child(9)').textContent,
                articleNumbers: parseInt(row.querySelector('td:nth-child(10)').textContent) || 0,
                graphicComplexity: parseFloat(row.querySelector('td:nth-child(11)').textContent) || 0,
                observations: row.querySelector('td:nth-child(12)').textContent || "",
                adminSync: adminSync // Use the current or updated status
            };
        });

        // Check if anything has been edited in this session
        const hasEdits = allEntries.some(entry => entry.adminSync === "ADMIN_EDITED");

        if (!hasEdits) {
            showWarning('No changes detected. Nothing to save.');
            return;
        }

        // Log the payload for debugging
        console.log('Saving all entries with their current status:', allEntries);

        const response = await fetch('/admin/register/save', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                username: state.currentUser.username,
                userId: state.currentUser.userId,
                year: state.currentYear,
                month: state.currentMonth,
                entries: allEntries
            })
        });

        if (!response.ok) {
            const errorText = await response.text();
            console.error('Server response:', response.status, errorText);
            throw new Error(`Save failed: ${errorText || response.statusText}`);
        }

        // Show success message
        const alertsContainer = document.querySelector('.alerts');
        if (alertsContainer) {
            alertsContainer.innerHTML = `
                <div class="alert alert-success">
                    <i class="bi bi-check-circle me-2"></i>
                    Changes saved successfully. Reloading page...
                </div>
            `;
        }

        // Clear all "field-edited" classes to reset edit state
        document.querySelectorAll('.field-edited').forEach(element => {
            element.classList.remove('field-edited');
        });

        // Uncheck all checkboxes
        document.querySelectorAll('.entry-select').forEach(checkbox => {
            checkbox.checked = false;
        });

        // Reload after successful save
        setTimeout(() => {
            window.location.reload();
        }, 1500);

    } catch (error) {
        console.error('Save error:', error);
        showError('Failed to save changes: ' + error.message);
    }
}

function clearTable() {
    if (confirm('Are you sure you want to clear all entries?')) {
        document.querySelectorAll('#registerTable tbody tr').forEach(row => {
            row.style.display = 'none';
        });

        // Reset filtered entries and update summary
        state.filteredEntries = [];
        state.bonusCalculationData.totalEntries = 0;
        state.bonusCalculationData.averageArticleNumbers = 0;
        state.bonusCalculationData.averageGraphicComplexity = 0;
        updateSummaryDisplay();

        // Hide bonus results if visible
        const bonusResults = document.getElementById('bonusResults');
        if (bonusResults) {
            bonusResults.style.display = 'none';
        }
    }
}

// Bonus calculation functions
function validateBonusConfig(config) {
    const totalPercentage = config.entriesPercentage +
    config.articlesPercentage +
    config.complexityPercentage +
    config.miscPercentage;

    if (Math.abs(totalPercentage - 1.0) > 0.0001) {
        showError('Percentages must sum to 1.0');
        return false;
    }

    if (config.miscValue < 0) {
        showError('Misc value must be non-negative');
        return false;
    }

    return true;
}

function getBonusConfig() {
    return {
        sumValue: parseFloat(document.getElementById('bonusSum').value) || 0.0,
        entriesPercentage: parseFloat(document.getElementById('entriesPercentage').value) || 0.0,
        articlesPercentage: parseFloat(document.getElementById('articlesPercentage').value) || 0.0,
        complexityPercentage: parseFloat(document.getElementById('complexityPercentage').value) || 0.0,
        miscPercentage: parseFloat(document.getElementById('miscPercentage').value) || 0.0,
        normValue: parseFloat(document.getElementById('normValue').value) || 1.20,
        miscValue: parseFloat(document.getElementById('miscValue').value) || 1.50
    };
}

// Modify your displayBonusResults function to handle null or missing data safely
function displayBonusResults(result) {
    const bonusResults = document.getElementById('bonusResults');
    const tbody = document.getElementById('bonusResultsBody');

    if (!bonusResults || !tbody) {
        console.error('Bonus results elements not found');
        return;
    }

    // Safely handle result in case it's null or undefined
    if (!result) {
        console.error('No result data received');
        showWarning('No bonus calculation result received from server.');
        return;
    }

    console.log('Received bonus result:', result);

    // Get previous months
    const currentDate = new Date(state.currentYear, state.currentMonth - 1);
    const prevMonths = [];

    for (let i = 1; i <= 3; i++) {
        const prevDate = new Date(currentDate);
        prevDate.setMonth(currentDate.getMonth() - i);
        const monthName = prevDate.toLocaleString('en-US', { month: 'short' });
        const yearStr = prevDate.getFullYear().toString().slice(2);
        prevMonths.push(`${monthName}/${yearStr}`);
    }

    // Add warning class if worked days is 0
    const rowClass = result.workedDays <= 0 ? 'table-warning' : '';

    // Update the table headers dynamically
    const thead = bonusResults.querySelector('thead tr');
    if (thead) {
        thead.innerHTML = `
            <th>Name</th>
            <th>Entries</th>
            <th>Art Nr.</th>
            <th>CG</th>
            <th>Misc</th>
            <th>Worked D</th>
            <th>Worked%</th>
            <th>Bonus%</th>
            <th>Bonus$</th>
            <th>${prevMonths[0]}</th>
            <th>${prevMonths[1]}</th>
            <th>${prevMonths[2]}</th>
        `;
    }

    // Safely access properties with optional chaining and defaults
    tbody.innerHTML = `
        <tr class="${rowClass}">
            <td>${state.currentUser?.name || ''}</td>
            <td>${result.entries || 0}</td>
            <td>${(result.articleNumbers || 0).toFixed(2)}</td>
            <td>${(result.graphicComplexity || 0).toFixed(2)}</td>
            <td>${(result.misc || 0).toFixed(2)}</td>
            <td>${result.workedDays || 0}</td>
            <td>${(result.workedPercentage || 0).toFixed(2)}</td>
            <td>${(result.bonusPercentage || 0).toFixed(2)}</td>
            <td>${(result.bonusAmount || 0).toFixed(2)}</td>
            <td>${(result.previousMonths?.month1 || 0).toFixed(2)}</td>
            <td>${(result.previousMonths?.month2 || 0).toFixed(2)}</td>
            <td>${(result.previousMonths?.month3 || 0).toFixed(2)}</td>
        </tr>
    `;

    // Add explanatory message if worked days is 0
    if (result.workedDays <= 0) {
        tbody.innerHTML += `
            <tr>
                <td colspan="12" class="text-center text-warning">
                    <small><i>* No worked days recorded - showing raw totals with zero bonus amounts</i></small>
                </td>
            </tr>
        `;
    }

    bonusResults.style.display = 'block';
}

function validateUserContext() {
    // Detailed validation
    if (!state.currentUser) {
        console.warn("No user selected");
        return false;
    }

    if (!state.currentUser.userId) {
        console.warn("Selected user has no ID");
        return false;
    }

    if (!state.currentYear) {
        console.warn("No year selected");
        return false;
    }

    if (!state.currentMonth) {
        console.warn("No month selected");
        return false;
    }

    console.log('User context validation successful:', {
        userId: state.currentUser.userId,
        name: state.currentUser.name,
        year: state.currentYear,
        month: state.currentMonth
    });

    return true;
}

function showError(message) {
    const alertsContainer = document.querySelector('.alerts');
    if (!alertsContainer) return;

    const alertDiv = document.createElement('div');
    alertDiv.className = 'alert alert-danger';
    alertDiv.textContent = message;

    // Clear existing alerts
    alertsContainer.innerHTML = '';
    alertsContainer.appendChild(alertDiv);
}

function showWarning(message) {
    const alertsContainer = document.querySelector('.alerts');
    if (!alertsContainer) return;

    const alertDiv = document.createElement('div');
    alertDiv.className = 'alert alert-warning'; // Use warning style
    alertDiv.textContent = message;

    // Clear existing alerts
    alertsContainer.innerHTML = '';
    alertsContainer.appendChild(alertDiv);
}

function debugThymeleafData() {
    console.log("Thymeleaf data check:");
    console.log("currentYear:", /*[[${currentYear}]]*/ null);
    console.log("currentMonth:", /*[[${currentMonth}]]*/ null);
    console.log("selectedUser:", /*[[${selectedUser}]]*/ null);
    console.log("entries:", /*[[${entries}]]*/ []);
    console.log("bonusConfig:", /*[[${bonusConfig}]]*/ null);
}

function debugFormStructure() {
    console.log("Debugging form structure:");

    // Find all important elements
    const periodSelector = document.querySelector('.period-selector');
    console.log("Period selector found:", !!periodSelector);

    const buttonRow = document.querySelector('.button-row');
    console.log("Button row found:", !!buttonRow);

    if (buttonRow) {
        const buttons = buttonRow.querySelectorAll('button');
        console.log("Buttons in button row:", buttons.length);
        buttons.forEach((btn, i) => {
            console.log(`Button ${i+1}:`, btn.textContent.trim(), "Class:", btn.className);
        });
    }

    // Try various selectors for the load button
    const loadBtnSelectors = [
        'button[type="submit"]',
        '.button-row .btn-primary',
        '.btn-primary',
        'button:contains("Load Data")'
    ];

    loadBtnSelectors.forEach(selector => {
        try {
            const elements = document.querySelectorAll(selector);
            console.log(`Selector "${selector}" found ${elements.length} elements`);
        } catch (e) {
            console.log(`Selector "${selector}" error:`, e.message);
        }
    });
}

function debugPrintPrepTypesDisplay() {
    console.log("Debugging Print Prep Types Display:");

    const rows = document.querySelectorAll('#registerTable tbody tr');
    rows.forEach((row, index) => {
        const printPrepCell = row.querySelector('td:nth-child(8)');
        const entryIdCell = row.querySelector('.entry-select');
        const entryId = entryIdCell ? entryIdCell.value : 'unknown';

        console.log(`Row ${index} (Entry ID: ${entryId}):`, {
            rawContent: printPrepCell ? printPrepCell.innerHTML : 'cell not found',
            trimmedContent: printPrepCell ? printPrepCell.textContent.trim() : 'cell not found',
            isEmpty: printPrepCell ? (printPrepCell.textContent.trim() === '') : 'cell not found',
            parsedArray: printPrepCell ?
            printPrepCell.textContent.split(',')
                .map(type => type.trim())
                .filter(type => type.length > 0) :
            'cell not found'
        });
    });
}