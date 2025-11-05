/**
 * RegisterSearchManager.js
 *
 * Manages register search interface with advanced filtering.
 * Features: Select2 multi-select, advanced filter toggle, statistics calculation,
 * date range validation, and filter reset.
 *
 * @module features/register-search/RegisterSearchManager
 *
 * NOTE: This class uses jQuery for DOM manipulation and Select2 integration
 * as it's tightly integrated throughout the interface.
 */

/**
 * RegisterSearchManager class
 * Handles search form operations and statistics
 */
export class RegisterSearchManager {
    constructor() {
        this.searchForm = null;
        this.advancedOptions = null;
        this.toggleAdvancedBtn = null;
        this.resetFiltersBtn = null;

        console.log('RegisterSearchManager initialized');
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize register search manager
     */
    initialize() {
        console.log('🚀 Initializing Register Search Manager...');

        // Check if jQuery and Select2 are available
        if (typeof $ === 'undefined') {
            console.error('jQuery not loaded - Register Search requires jQuery');
            return;
        }

        if (typeof $.fn.select2 === 'undefined') {
            console.error('Select2 not loaded - Register Search requires Select2');
            return;
        }

        // Initialize Select2 dropdowns
        this.initializeSelect2();

        // Setup advanced options toggle
        this.setupAdvancedToggle();

        // Setup form handling
        this.setupFormHandling();

        // Setup filter reset
        this.setupFilterReset();

        // Calculate initial statistics
        this.calculateStats();

        console.log('✅ Register Search Manager initialized successfully');
    }

    // ========================================================================
    // SELECT2 INITIALIZATION
    // ========================================================================

    /**
     * Initialize Select2 dropdowns
     */
    initializeSelect2() {
        // Initialize all Select2 inputs with Bootstrap 5 theme
        $('.select2-input').select2({
            theme: 'bootstrap-5',
            width: '100%',
            placeholder: 'Select...',
            allowClear: true
        });

        console.log('Select2 dropdowns initialized');
    }

    // ========================================================================
    // ADVANCED OPTIONS TOGGLE
    // ========================================================================

    /**
     * Setup advanced options toggle
     */
    setupAdvancedToggle() {
        this.toggleAdvancedBtn = document.getElementById('toggleAdvanced');
        this.advancedOptions = document.getElementById('advancedOptions');

        if (this.toggleAdvancedBtn && this.advancedOptions) {
            this.toggleAdvancedBtn.addEventListener('click', () => {
                this.toggleAdvancedOptions();
            });

            console.log('Advanced options toggle setup');
        }
    }

    /**
     * Toggle advanced options visibility
     */
    toggleAdvancedOptions() {
        if (!this.advancedOptions) return;

        const isHidden = this.advancedOptions.style.display === 'none';

        if (isHidden) {
            this.advancedOptions.style.display = 'block';
            this.toggleAdvancedBtn.innerHTML = '<i class="bi bi-chevron-up me-2"></i>Hide Advanced Options';
        } else {
            this.advancedOptions.style.display = 'none';
            this.toggleAdvancedBtn.innerHTML = '<i class="bi bi-chevron-down me-2"></i>Show Advanced Options';
        }

        console.log(`Advanced options ${isHidden ? 'shown' : 'hidden'}`);
    }

    // ========================================================================
    // FORM HANDLING
    // ========================================================================

    /**
     * Setup form handling
     */
    setupFormHandling() {
        this.searchForm = document.getElementById('searchForm');

        if (this.searchForm) {
            this.searchForm.addEventListener('submit', (e) => {
                if (!this.validateDateRange()) {
                    e.preventDefault();
                    return false;
                }
            });

            console.log('Form handling setup');
        }
    }

    /**
     * Validate date range
     * @returns {boolean} True if valid
     */
    validateDateRange() {
        const startDateInput = document.getElementById('startDate');
        const endDateInput = document.getElementById('endDate');

        if (!startDateInput || !endDateInput) return true;

        const startDate = startDateInput.value;
        const endDate = endDateInput.value;

        // Both must be filled or both empty
        if ((startDate && !endDate) || (!startDate && endDate)) {
            alert('Please provide both start and end dates for date range filtering.');
            return false;
        }

        // Start date must be before or equal to end date
        if (startDate && endDate && startDate > endDate) {
            alert('Start date must be before or equal to end date.');
            return false;
        }

        return true;
    }

    // ========================================================================
    // FILTER RESET
    // ========================================================================

    /**
     * Setup filter reset button
     */
    setupFilterReset() {
        this.resetFiltersBtn = document.getElementById('resetFilters');

        if (this.resetFiltersBtn) {
            this.resetFiltersBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.resetFilters();
            });

            console.log('Filter reset setup');
        }
    }

    /**
     * Reset all filters
     */
    resetFilters() {
        // Get current URL parameters to preserve username and period
        const urlParams = new URLSearchParams(window.location.search);
        const username = urlParams.get('username');
        const year = urlParams.get('year');
        const month = urlParams.get('month');

        // Clear all form fields
        if (this.searchForm) {
            this.searchForm.reset();
        }

        // Reset Select2 fields
        $('.select2-input').val(null).trigger('change');

        // Build redirect URL preserving username and period
        let redirectUrl = '/status/register-search';
        const params = [];

        if (username) params.push(`username=${encodeURIComponent(username)}`);
        if (year) params.push(`year=${encodeURIComponent(year)}`);
        if (month) params.push(`month=${encodeURIComponent(month)}`);

        if (params.length > 0) {
            redirectUrl += '?' + params.join('&');
        }

        console.log('Resetting filters and redirecting to:', redirectUrl);
        window.location.href = redirectUrl;
    }

    /**
     * Check if any advanced filters are active
     * @returns {boolean} True if any advanced filters are set
     */
    hasAdvancedFilters() {
        if (!this.searchForm) return false;

        // Check Select2 fields
        const select2Values = $('.select2-input').map(function() {
            return $(this).val();
        }).get();

        const hasSelect2Values = select2Values.some(val => val !== null && val !== '');

        // Check date fields
        const startDate = document.getElementById('startDate')?.value || '';
        const endDate = document.getElementById('endDate')?.value || '';

        // Check text inputs
        const searchInputs = this.searchForm.querySelectorAll('input[type="text"]:not(.select2-search__field)');
        const hasTextValues = Array.from(searchInputs).some(input => input.value.trim() !== '');

        return hasSelect2Values || startDate !== '' || endDate !== '' || hasTextValues;
    }

    // ========================================================================
    // STATISTICS CALCULATION
    // ========================================================================

    /**
     * Calculate and display statistics for visible entries
     */
    calculateStats() {
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

        let totalArticles = 0;
        let totalComplexity = 0;

        // Get all table rows
        const tableRows = document.querySelectorAll('.table tbody tr');

        tableRows.forEach(row => {
            const cells = row.querySelectorAll('td');

            // Skip if not enough cells
            if (cells.length < 10) return;

            // Extract data (adjust indices based on actual table structure)
            const actionType = cells[5]?.textContent?.trim() || '';
            const articles = parseInt(cells[8]?.textContent || '0');
            const complexity = parseFloat(cells[9]?.textContent || '0');

            // Count action types
            switch (actionType) {
                case 'ORDIN':
                    actionCounts.ordin++;
                    break;
                case 'RE-ORDIN':
                    actionCounts.reordin++;
                    break;
                case 'CAMPION':
                    actionCounts.campion++;
                    break;
                case 'PROBA STAMPA':
                    actionCounts.probaStampa++;
                    break;
                case 'DESIGN':
                    actionCounts.design++;
                    break;
                case 'OTHERS':
                    actionCounts.others++;
                    break;
                case 'IMPOSTARE':
                    actionCounts.impostare++;
                    break;
                case 'ORDIN SPIZED':
                    actionCounts.ordinSpized++;
                    break;
                case 'CAMPION SPIZED':
                    actionCounts.campionSpized++;
                    break;
                case 'PROBA S. SPIZED':
                    actionCounts.probaSSpized++;
                    break;
            }

            // Accumulate totals
            totalArticles += articles;
            totalComplexity += complexity;
        });

        // Update DOM elements with counts
        this.updateStatElement('count-ordin', actionCounts.ordin);
        this.updateStatElement('count-reordin', actionCounts.reordin);
        this.updateStatElement('count-campion', actionCounts.campion);
        this.updateStatElement('count-proba-stampa', actionCounts.probaStampa);
        this.updateStatElement('count-design', actionCounts.design);
        this.updateStatElement('count-others', actionCounts.others);
        this.updateStatElement('count-impostare', actionCounts.impostare);
        this.updateStatElement('count-ordin-spized', actionCounts.ordinSpized);
        this.updateStatElement('count-campion-spized', actionCounts.campionSpized);
        this.updateStatElement('count-proba-s-spized', actionCounts.probaSSpized);

        // Update total counts
        this.updateStatElement('total-entries', tableRows.length);
        this.updateStatElement('total-articles', totalArticles);
        this.updateStatElement('total-complexity', totalComplexity.toFixed(2));

        console.log('Statistics calculated:', {
            totalEntries: tableRows.length,
            totalArticles,
            totalComplexity: totalComplexity.toFixed(2)
        });
    }

    /**
     * Update statistics element
     * @param {string} elementId - Element ID
     * @param {string|number} value - Value to display
     */
    updateStatElement(elementId, value) {
        const element = document.getElementById(elementId);
        if (element) {
            element.textContent = value;
        }
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Get current filter values
     * @returns {Object} Current filter values
     */
    getFilterValues() {
        if (!this.searchForm) return {};

        const formData = new FormData(this.searchForm);
        const filters = {};

        for (const [key, value] of formData.entries()) {
            if (value) {
                filters[key] = value;
            }
        }

        return filters;
    }

    /**
     * Cleanup and destroy
     */
    destroy() {
        // Destroy Select2 instances
        $('.select2-input').select2('destroy');

        console.log('RegisterSearchManager destroyed');
    }
}
