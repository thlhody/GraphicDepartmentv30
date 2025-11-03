// Initialize on DOM load
document.addEventListener('DOMContentLoaded', function() {
    // Initialize Select2 for searchable dropdowns
    if (typeof $ !== 'undefined' && $('.select2-input').length) {
        $('.select2-input').select2({
            theme: 'bootstrap-5',
            width: '100%',
            placeholder: 'Select...',
            allowClear: true
        });
    }

    // Toggle advanced options
    const toggleAdvancedBtn = document.getElementById('toggleAdvanced');
    if (toggleAdvancedBtn) {
        toggleAdvancedBtn.addEventListener('click', function() {
            const advancedOptions = document.getElementById('advancedOptions');
            if (advancedOptions.style.display === 'none') {
                advancedOptions.style.display = 'block';
                this.innerHTML = '<i class="bi bi-x me-1"></i>Hide';
            } else {
                advancedOptions.style.display = 'none';
                this.innerHTML = '<i class="bi bi-sliders me-1"></i>Options';
            }
        });
    }

    // Show advanced options if any of them are filled
    if (hasAdvancedFilters()) {
        const advancedOptions = document.getElementById('advancedOptions');
        const toggleAdvancedBtn = document.getElementById('toggleAdvanced');
        if (advancedOptions) advancedOptions.style.display = 'block';
        if (toggleAdvancedBtn) toggleAdvancedBtn.innerHTML = '<i class="bi bi-x me-1"></i>Hide';
    }

    // Set up form submission handler
    const searchForm = document.getElementById('searchForm');
    if (searchForm) {
        searchForm.addEventListener('submit', handleFormSubmit);
    }

    // Initialize summary statistics calculation
    calculateStats();
});

// Centralized form submission handler
function handleFormSubmit(event) {
    const usernameInput = document.getElementById('usernameInput');
    const yearSelect = document.getElementById('yearSelect');
    const monthSelect = document.getElementById('monthSelect');
    const currentYearInput = document.getElementById('currentYear');
    const currentMonthInput = document.getElementById('currentMonth');
    const yearInput = document.getElementById('yearInput');
    const monthInput = document.getElementById('monthInput');
    const startDateInput = document.getElementById('startDate');
    const endDateInput = document.getElementById('endDate');

    // Get username from hidden field or URL
    const username = (usernameInput ? usernameInput.value : '') || getCurrentUsername();

    // Set the username in the form's hidden input
    if (usernameInput) usernameInput.value = username;

    // Sync year and month values
    if (yearSelect && currentYearInput && yearInput) {
        currentYearInput.value = yearSelect.value;
        yearInput.value = yearSelect.value;
    }

    if (monthSelect && currentMonthInput && monthInput) {
        currentMonthInput.value = monthSelect.value;
        monthInput.value = monthSelect.value;
    }

    // Validate date range if provided
    const startDate = startDateInput ? startDateInput.value : '';
    const endDate = endDateInput ? endDateInput.value : '';

    if ((startDate && !endDate) || (!startDate && endDate)) {
        alert('Please provide both start and end dates for date range filtering');
        event.preventDefault();
        return false;
    }

    // Continue with form submission
    return true;
}

// Function to get the current username from URL parameters
function getCurrentUsername() {
    const urlParams = new URLSearchParams(window.location.search);
    return urlParams.get('username') || '';
}

// Function to reset search while preserving username and period
function resetSearch() {
    const yearInput = document.getElementById('currentYear');
    const monthInput = document.getElementById('currentMonth');
    const usernameInput = document.getElementById('usernameInput');

    if (!yearInput || !monthInput) {
        console.error('Year or month input not found');
        return;
    }

    const year = yearInput.value;
    const month = monthInput.value;
    const username = (usernameInput ? usernameInput.value : '') || getCurrentUsername();

    // Construct the redirect URL with preserved username and period
    let redirectUrl = `/status/register-search?year=${year}&month=${month}`;

    // Add username if present
    if (username) {
        redirectUrl += `&username=${username}`;
    }

    // Redirect with preserved username and period
    window.location.href = redirectUrl;
}

function hasAdvancedFilters() {
    // Check if any of the advanced search fields have values
    return (
    hasValue('startDate') ||
    hasValue('endDate') ||
    hasValue('actionType') ||
    hasValue('printPrepTypes') ||
    hasValue('clientName') ||
    hasValue('searchTerm')
    );
}

function hasValue(paramName) {
    const urlParams = new URLSearchParams(window.location.search);
    return urlParams.has(paramName) && urlParams.get(paramName) !== '';
}

function resetFilters() {
    const startDateInput = document.getElementById('startDate');
    const endDateInput = document.getElementById('endDate');
    const actionTypeInput = document.getElementById('actionType');
    const printPrepTypesInput = document.getElementById('printPrepTypes');
    const clientNameInput = document.getElementById('clientName');
    const yearInput = document.getElementById('currentYear');
    const monthInput = document.getElementById('currentMonth');
    const usernameInput = document.getElementById('usernameInput');

    // Clear all form fields
    if (startDateInput) startDateInput.value = '';
    if (endDateInput) endDateInput.value = '';
    if (actionTypeInput) actionTypeInput.value = '';
    if (printPrepTypesInput) printPrepTypesInput.value = '';
    if (clientNameInput) clientNameInput.value = '';

    // Get the current period values
    const year = yearInput ? yearInput.value : '';
    const month = monthInput ? monthInput.value : '';

    // Get username from hidden field or URL
    const username = (usernameInput ? usernameInput.value : '') || getCurrentUsername();

    // Reset Select2 fields
    if (typeof $ !== 'undefined' && $('.select2-input').length) {
        $('.select2-input').val(null).trigger('change');
    }

    // Construct the redirect URL preserving username and period
    let redirectUrl = `/status/register-search?year=${year}&month=${month}`;

    // Add username if present
    if (username) {
        redirectUrl += `&username=${username}`;
    }

    // Redirect with preserved username and period
    window.location.href = redirectUrl;
}

function calculateStats() {
    try {
        // Action counts object
        const actionCounts = {
            ordin: 0,
            reordin: 0,
            campion: 0,
            probaStampa: 0,
            design: 0,
            others: 0,
            impostare: 0,
            ordinSpized: 0,
            campionSpized: 0,
            probaSSpized: 0
        };

        // Metrics object
        const metrics = {
            totalEntries: 0,
            totalNoImpostare: 0,
            totalArticles: 0,
            totalComplexity: 0
        };

        // Get all valid entries from the table
        const entries = Array.from(document.querySelectorAll('.table tbody tr'));
        if (!entries.length) return;

        // Process each entry
        entries.forEach(row => {
            const cells = row.cells;
            if (!cells || cells.length < 9) return;

            const actionType = cells[5]?.textContent?.trim() || '';
            const articles = parseInt(cells[8]?.textContent || '0');
            const complexity = parseFloat(cells[9]?.textContent || '0');

            // Count action types
            if (actionType) {
                if (actionType === 'ORDIN') actionCounts.ordin++;
                else if (actionType === 'REORDIN') actionCounts.reordin++;
                else if (actionType === 'CAMPION') actionCounts.campion++;
                else if (actionType === 'PROBA STAMPA') actionCounts.probaStampa++;
                else if (actionType === 'DESIGN') actionCounts.design++;
                else if (actionType === AppConstants.IMPOSTARE) actionCounts.impostare++;
                else if (actionType === 'ORDIN SPIZED') actionCounts.ordinSpized++;
                else if (actionType === 'CAMPION SPIZED') actionCounts.campionSpized++;
                else if (actionType === 'PROBA S SPIZED') actionCounts.probaSSpized++;
                else actionCounts.others++;
            }

            // Calculate metrics
            metrics.totalEntries++;

            if (!actionType.includes(AppConstants.IMPOSTARE)) {
                metrics.totalNoImpostare++;
                metrics.totalArticles += articles;
                metrics.totalComplexity += complexity;
            }
        });

        // Calculate averages
        const avgArticles = metrics.totalNoImpostare ?
        (metrics.totalArticles / metrics.totalNoImpostare).toFixed(2) : '0.00';
        const avgComplexity = metrics.totalNoImpostare ?
        (metrics.totalComplexity / metrics.totalNoImpostare).toFixed(2) : '0.00';

        // Define count elements with null-safe access
        const countElements = {
            ordin: document.getElementById('count-ordin'),
            reordin: document.getElementById('count-reordin'),
            campion: document.getElementById('count-campion'),
            probaStampa: document.getElementById('count-proba-stampa'),
            design: document.getElementById('count-design'),
            others: document.getElementById('count-others'),
            impostare: document.getElementById('count-impostare'),
            ordinSpized: document.getElementById('count-ordin-spized'),
            campionSpized: document.getElementById('count-campion-spized'),
            probaSSpized: document.getElementById('count-proba-s-spized')
        };

        const metricsElements = {
            totalEntries: document.getElementById('total-entries'),
            totalNoImpostare: document.getElementById('total-entries-no-impostare'),
            avgArticles: document.getElementById('avg-articles'),
            avgComplexity: document.getElementById('avg-complexity')
        };

        // Safely update elements
        Object.entries(countElements).forEach(([key, element]) => {
            if (element) element.textContent = actionCounts[key];
        });

        // Update metrics elements
        if (metricsElements.totalEntries) metricsElements.totalEntries.textContent = metrics.totalEntries;
        if (metricsElements.totalNoImpostare) metricsElements.totalNoImpostare.textContent = metrics.totalNoImpostare;
        if (metricsElements.avgArticles) metricsElements.avgArticles.textContent = avgArticles;
        if (metricsElements.avgComplexity) metricsElements.avgComplexity.textContent = avgComplexity;

    } catch (error) {
        console.error('Error calculating stats:', error);
    }
}