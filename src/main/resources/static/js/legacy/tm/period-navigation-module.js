/**
 * Period Navigation Module - Handles month/year selection and navigation
 * Manages period selection controls, keyboard shortcuts, and URL navigation
 */

const PeriodNavigationModule = {

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize period navigation functionality
     */
    initialize() {
        console.log('Initializing Period Navigation Module...');

        this.initializePeriodSelection();
        this.initializeExportButton();
        this.addQuickPeriodNavigation();

        console.log('✅ Period Navigation Module initialized');
    },

    // ========================================================================
    // PERIOD SELECTION CONTROLS
    // ========================================================================

    /**
     * Initialize period selection form
     */
    initializePeriodSelection() {
        console.log('Initializing period selection controls');

        const periodForm = document.querySelector('.card-header form[action*="/user/time-management"]');
        const yearSelect = document.getElementById('yearSelect');
        const monthSelect = document.getElementById('monthSelect');

        if (!periodForm || !yearSelect || !monthSelect) {
            console.warn('Period selection elements not found');
            return;
        }

        // Set current values from URL parameters or page data
        this.setCurrentPeriodValues(yearSelect, monthSelect);

        // Handle form submission with loading indication
        this.setupFormSubmission(periodForm, yearSelect, monthSelect);
    },

    /**
     * Set current period values from URL or defaults
     */
    setCurrentPeriodValues(yearSelect, monthSelect) {
        const urlParams = new URLSearchParams(window.location.search);
        const currentYear = urlParams.get('year');
        const currentMonth = urlParams.get('month');

        if (currentYear && yearSelect.querySelector(`option[value="${currentYear}"]`)) {
            yearSelect.value = currentYear;
        }
        if (currentMonth && monthSelect.querySelector(`option[value="${currentMonth}"]`)) {
            monthSelect.value = currentMonth;
        }

        console.log('Set period values:', { year: yearSelect.value, month: monthSelect.value });
    },

    /**
     * Set up period form submission
     */
    setupFormSubmission(periodForm, yearSelect, monthSelect) {
        periodForm.addEventListener('submit', (e) => {
            const selectedPeriod = {
                year: yearSelect.value,
                month: monthSelect.value
            };

            console.log('Loading new period:', selectedPeriod);

            // Show loading indication
            if (window.UtilitiesModule) {
                window.UtilitiesModule.showLoadingOverlay();
            }

            // Show toast notification
            const monthName = monthSelect.options[monthSelect.selectedIndex].text;
            this.showNavigationMessage(`Loading ${monthName} ${yearSelect.value}...`);
        });
    },

    // ========================================================================
    // KEYBOARD NAVIGATION
    // ========================================================================

    /**
     * Add keyboard shortcuts for quick period navigation
     */
    addQuickPeriodNavigation() {
        const yearSelect = document.getElementById('yearSelect');
        const monthSelect = document.getElementById('monthSelect');

        if (!yearSelect || !monthSelect) return;

        // Add keyboard shortcuts for period navigation
        document.addEventListener('keydown', (e) => {
            // Only if not editing a cell
            if (this.isEditingInProgress()) return;

            // Ctrl + Left Arrow: Previous month
            if (e.ctrlKey && e.key === 'ArrowLeft') {
                e.preventDefault();
                this.navigateToPreviousMonth();
            }

            // Ctrl + Right Arrow: Next month
            if (e.ctrlKey && e.key === 'ArrowRight') {
                e.preventDefault();
                this.navigateToNextMonth();
            }
        });

        console.log('📅 Keyboard shortcuts initialized: Ctrl+← (previous month), Ctrl+→ (next month)');
    },

    /**
     * Check if inline editing is currently in progress
     */
    isEditingInProgress() {
        // Check if InlineEditingModule is available and editing
        if (window.InlineEditingModule && typeof window.InlineEditingModule.getCurrentState === 'function') {
            return window.InlineEditingModule.getCurrentState().isEditing;
        }

        // Fallback: check for editing class
        return document.querySelector('.editing') !== null;
    },

    /**
     * Navigate to previous month
     */
    navigateToPreviousMonth() {
        const yearSelect = document.getElementById('yearSelect');
        const monthSelect = document.getElementById('monthSelect');

        let year = parseInt(yearSelect.value);
        let month = parseInt(monthSelect.value);

        month--;
        if (month < 1) {
            month = 12;
            year--;
        }

        this.navigateToPeriod(year, month);
    },

    /**
     * Navigate to next month
     */
    navigateToNextMonth() {
        const yearSelect = document.getElementById('yearSelect');
        const monthSelect = document.getElementById('monthSelect');

        let year = parseInt(yearSelect.value);
        let month = parseInt(monthSelect.value);

        month++;
        if (month > 12) {
            month = 1;
            year++;
        }

        this.navigateToPeriod(year, month);
    },

    /**
     * Navigate to specific period
     * @param {number} year - Target year
     * @param {number} month - Target month
     */
    navigateToPeriod(year, month) {
        // Validate year and month
        if (!this.isValidPeriod(year, month)) {
            console.warn('Invalid period:', { year, month });
            return;
        }

        const currentUrl = new URL(window.location);
        currentUrl.searchParams.set('year', year);
        currentUrl.searchParams.set('month', month);

        const monthName = window.UtilitiesModule?.getMonthName(month) || `Month ${month}`;

        this.showNavigationMessage(`Loading ${monthName} ${year}...`);

        if (window.UtilitiesModule) {
            window.UtilitiesModule.showLoadingOverlay();
        }

        window.location.href = currentUrl.toString();
    },

    /**
     * Validate period values
     */
    isValidPeriod(year, month) {
        return year >= 2020 && year <= 2030 && month >= 1 && month <= 12;
    },

    /**
     * Show navigation message
     */
    showNavigationMessage(message) {
        if (window.showToast) {
            window.showToast('Navigating', message, 'info', { duration: 1500 });
        }
    },

    // ========================================================================
    // EXPORT FUNCTIONALITY
    // ========================================================================

    /**
     * Initialize export button
     */
    initializeExportButton() {
        const exportButton = document.querySelector('.btn-outline-success[href*="/export"]');

        if (!exportButton) {
            console.warn('Export button not found');
            return;
        }

        exportButton.addEventListener('click', (e) => {
            console.log('Initiating Excel export...');

            // Show toast notification
            if (window.showToast) {
                window.showToast('Exporting Data',
                    'Generating Excel file for download...',
                    'info',
                    { duration: 3000 });
            }

            // Don't prevent default - let the download proceed
        });
    },

    // ========================================================================
    // PERIOD UTILITIES
    // ========================================================================

    /**
     * Get current period from URL or form
     * @returns {Object} Current period {year, month}
     */
    getCurrentPeriod() {
        const urlParams = new URLSearchParams(window.location.search);
        const yearSelect = document.getElementById('yearSelect');
        const monthSelect = document.getElementById('monthSelect');

        return {
            year: parseInt(urlParams.get('year') || yearSelect?.value || new Date().getFullYear()),
            month: parseInt(urlParams.get('month') || monthSelect?.value || (new Date().getMonth() + 1))
        };
    },

    /**
     * Set period in form controls
     * @param {number} year - Year to set
     * @param {number} month - Month to set
     */
    setPeriod(year, month) {
        const yearSelect = document.getElementById('yearSelect');
        const monthSelect = document.getElementById('monthSelect');

        if (yearSelect && yearSelect.querySelector(`option[value="${year}"]`)) {
            yearSelect.value = year;
        }
        if (monthSelect && monthSelect.querySelector(`option[value="${month}"]`)) {
            monthSelect.value = month;
        }
    },

    /**
     * Get period display string
     * @param {number} year - Year
     * @param {number} month - Month
     * @returns {string} Formatted period string
     */
    getPeriodDisplayString(year, month) {
        const monthName = window.UtilitiesModule?.getMonthName(month) || `Month ${month}`;
        return `${monthName} ${year}`;
    },

    /**
     * Check if period is current month
     * @param {number} year - Year to check
     * @param {number} month - Month to check
     * @returns {boolean} True if current month
     */
    isCurrentMonth(year, month) {
        const now = new Date();
        return year === now.getFullYear() && month === (now.getMonth() + 1);
    },

    /**
     * Check if period is in the future
     * @param {number} year - Year to check
     * @param {number} month - Month to check
     * @returns {boolean} True if future month
     */
    isFutureMonth(year, month) {
        const now = new Date();
        const currentYear = now.getFullYear();
        const currentMonth = now.getMonth() + 1;

        return year > currentYear || (year === currentYear && month > currentMonth);
    },

    /**
     * Get next period
     * @param {number} year - Current year
     * @param {number} month - Current month
     * @returns {Object} Next period {year, month}
     */
    getNextPeriod(year, month) {
        let nextYear = year;
        let nextMonth = month + 1;

        if (nextMonth > 12) {
            nextMonth = 1;
            nextYear++;
        }

        return { year: nextYear, month: nextMonth };
    },

    /**
     * Get previous period
     * @param {number} year - Current year
     * @param {number} month - Current month
     * @returns {Object} Previous period {year, month}
     */
    getPreviousPeriod(year, month) {
        let prevYear = year;
        let prevMonth = month - 1;

        if (prevMonth < 1) {
            prevMonth = 12;
            prevYear--;
        }

        return { year: prevYear, month: prevMonth };
    },

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Programmatically navigate to a specific period
     * @param {number} year - Target year
     * @param {number} month - Target month
     */
    goToPeriod(year, month) {
        this.navigateToPeriod(year, month);
    },

    /**
     * Go to current month
     */
    goToCurrentMonth() {
        const now = new Date();
        this.navigateToPeriod(now.getFullYear(), now.getMonth() + 1);
    },

    /**
     * Go to next month
     */
    goToNextMonth() {
        this.navigateToNextMonth();
    },

    /**
     * Go to previous month
     */
    goToPreviousMonth() {
        this.navigateToPreviousMonth();
    },

    /**
     * Refresh current period (reload page)
     */
    refreshCurrentPeriod() {
        window.location.reload();
    }
};

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = PeriodNavigationModule;
}

// Make available globally
window.PeriodNavigationModule = PeriodNavigationModule;