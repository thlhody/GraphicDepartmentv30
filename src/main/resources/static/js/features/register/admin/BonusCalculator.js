/**
 * BonusCalculator.js
 * Bonus calculation logic for admin register
 *
 * Handles:
 * - Bonus configuration extraction and validation
 * - Bonus calculation API calls
 * - Results display with previous months comparison
 * - Metrics validation
 *
 * @module features/register/admin/BonusCalculator
 */

import { API } from '../../../core/api.js';

/**
 * BonusCalculator - Bonus calculation and display
 */
export class BonusCalculator {

    /**
     * Create a BonusCalculator instance
     * @param {AdminRegisterState} state - State management instance
     * @param {AdminRegisterView} view - View instance for UI feedback
     */
    constructor(state, view) {
        this.state = state;
        this.view = view;

        this.initializeElements();
    }

    /**
     * Initialize DOM element references
     * @private
     */
    initializeElements() {
        this.calculateButton = document.getElementById('calculateBonus');
        this.bonusResultsContainer = document.getElementById('bonusResults');

        // Bonus config form fields
        this.sumValueInput = document.getElementById('sumValue');
        this.entriesPercentageInput = document.getElementById('entriesPercentage');
        this.articlesPercentageInput = document.getElementById('articlesPercentage');
        this.complexityPercentageInput = document.getElementById('complexityPercentage');
        this.miscPercentageInput = document.getElementById('miscPercentage');
        this.normValueInput = document.getElementById('normValue');
        this.miscValueInput = document.getElementById('miscValue');
    }

    /**
     * Initialize bonus calculator
     * @public
     */
    initialize() {
        if (this.calculateButton) {
            this.calculateButton.addEventListener('click', () => this.handleCalculateBonus());
        }

        console.log('BonusCalculator initialized');
    }

    /**
     * Handle calculate bonus button click
     * @private
     */
    async handleCalculateBonus() {
        // Validate user context
        const userValidation = this.state.validateUserContext();
        if (!userValidation.valid) {
            this.view.showError(userValidation.error);
            return;
        }

        // Get bonus configuration
        const bonusConfig = this.getBonusConfig();
        if (!bonusConfig) {
            return; // Error already shown
        }

        // Validate configuration
        const configValidation = this.validateBonusConfig(bonusConfig);
        if (!configValidation.valid) {
            this.view.showError(configValidation.error);
            return;
        }

        try {
            // Collect visible entries (excluding IMPOSTARE)
            const entries = this.state.collectTableEntries()
                .filter(entry => entry.actionType !== 'IMPOSTARE');

            if (entries.length === 0) {
                this.view.showWarning('No valid entries to calculate bonus from.');
                return;
            }

            // Get worked days from input field (admin can edit this value)
            const workedDaysInput = document.getElementById('workedDays');
            const workedDays = workedDaysInput ? parseInt(workedDaysInput.value) || 0 : 0;

            // Prepare payload
            const payload = {
                userId: this.state.currentUser.userId,
                year: this.state.currentYear,
                month: this.state.currentMonth,
                entries: entries,
                bonusConfig: bonusConfig,
                workedDays: workedDays
            };

            console.log('Calculating bonus:', payload);

            // Call backend - API.post() returns parsed data directly, throws on error
            const result = await API.post('/admin/register/calculate-bonus', payload);

            // Display results
            this.displayBonusResults(result);

        } catch (error) {
            console.error('Bonus calculation error:', error);
            this.view.showError('Failed to calculate bonus. Please try again.');
        }
    }

    /**
     * Get bonus configuration from form
     * @returns {Object|null} Bonus config object or null if invalid
     * @private
     */
    getBonusConfig() {
        try {
            const config = {
                sumValue: parseFloat(this.sumValueInput?.value || 0),
                entriesPercentage: parseFloat(this.entriesPercentageInput?.value || 0),
                articlesPercentage: parseFloat(this.articlesPercentageInput?.value || 0),
                complexityPercentage: parseFloat(this.complexityPercentageInput?.value || 0),
                miscPercentage: parseFloat(this.miscPercentageInput?.value || 0),
                normValue: parseFloat(this.normValueInput?.value || 1.20),
                miscValue: parseFloat(this.miscValueInput?.value || 1.50)
            };

            // Check for NaN values
            for (const [key, value] of Object.entries(config)) {
                if (isNaN(value)) {
                    this.view.showError(`Invalid value for ${key}. Please enter a valid number.`);
                    return null;
                }
            }

            return config;

        } catch (error) {
            console.error('Error extracting bonus config:', error);
            this.view.showError('Error reading bonus configuration.');
            return null;
        }
    }

    /**
     * Validate bonus configuration
     * @param {Object} config - Bonus configuration
     * @returns {Object} Validation result {valid: boolean, error: string}
     * @private
     */
    validateBonusConfig(config) {
        // Check that percentages sum to 1.0 (100%)
        const percentageSum = config.entriesPercentage +
                              config.articlesPercentage +
                              config.complexityPercentage +
                              config.miscPercentage;

        const tolerance = 0.01; // Allow small floating point errors
        if (Math.abs(percentageSum - 1.0) > tolerance) {
            return {
                valid: false,
                error: `Percentages must sum to 1.0 (100%). Current sum: ${percentageSum.toFixed(2)}`
            };
        }

        // Check misc value is non-negative
        if (config.miscValue < 0) {
            return {
                valid: false,
                error: 'Misc value cannot be negative.'
            };
        }

        // Check sum value is positive
        if (config.sumValue <= 0) {
            return {
                valid: false,
                error: 'Sum value must be greater than 0.'
            };
        }

        return { valid: true };
    }

    /**
     * Display bonus calculation results
     * @param {Object} result - Calculation result from backend
     * @public
     */
    displayBonusResults(result) {
        console.log('Displaying bonus results:', result);

        // Use the existing bonus results table structure (compact layout)
        const bonusTable = document.getElementById('bonusResults');
        const bonusTableBody = document.getElementById('bonusResultsBody');

        if (!bonusTable || !bonusTableBody) {
            console.error('Bonus results table not found');
            return;
        }

        // Calculate month names for headers
        const currentDate = new Date(this.state.currentYear, this.state.currentMonth - 1);
        const formatMonth = (monthsAgo) => {
            const date = new Date(currentDate);
            date.setMonth(currentDate.getMonth() - monthsAgo);
            const monthName = date.toLocaleString('en-US', { month: 'short' });
            const yearShort = date.getFullYear().toString().slice(2);
            return `${monthName}/${yearShort}`;
        };

        // Update table headers with dynamic month names
        const thead = bonusTable.querySelector('thead tr');
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
                <th>${formatMonth(1)}</th>
                <th>${formatMonth(2)}</th>
                <th>${formatMonth(3)}</th>
            `;
        }

        // Build compact table row (matches legacy layout)
        const rowClass = result.workedDays <= 0 ? 'table-warning' : '';
        bonusTableBody.innerHTML = `
            <tr class="${rowClass}">
                <td>${this.state.currentUser?.name || ''}</td>
                <td>${result.entries || 0}</td>
                <td>${this.formatNumber(result.articleNumbers, 2)}</td>
                <td>${this.formatNumber(result.graphicComplexity, 2)}</td>
                <td>${this.formatNumber(result.misc, 2)}</td>
                <td>${result.workedDays || 0}</td>
                <td>${this.formatNumber(result.workedPercentage, 2)}</td>
                <td>${this.formatNumber(result.bonusPercentage, 2)}</td>
                <td><strong>${this.formatNumber(result.bonusAmount, 2)}</strong></td>
                <td>${this.formatNumber(result.previousMonths?.month1 || 0, 2)}</td>
                <td>${this.formatNumber(result.previousMonths?.month2 || 0, 2)}</td>
                <td>${this.formatNumber(result.previousMonths?.month3 || 0, 2)}</td>
            </tr>
        `;

        // Add warning if worked days is 0
        if (result.workedDays <= 0) {
            bonusTableBody.innerHTML += `
                <tr>
                    <td colspan="12" class="text-center text-warning">
                        <small><i>* No worked days recorded - showing raw totals with zero bonus amounts</i></small>
                    </td>
                </tr>
            `;
        }

        // Show the bonus results table (compact display)
        bonusTable.style.display = 'block';

        // Scroll to results
        bonusTable.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

        this.view.showSuccess('Bonus calculated successfully!');
    }

    /**
     * Format number with optional decimals
     * @param {number|string} value - Value to format
     * @param {number} decimals - Number of decimal places (default 0)
     * @returns {string} Formatted number
     * @private
     */
    formatNumber(value, decimals = 0) {
        if (value === null || value === undefined || value === '-') {
            return '-';
        }

        const num = parseFloat(value);
        if (isNaN(num)) {
            return '-';
        }

        return num.toFixed(decimals);
    }

    /**
     * Format percentage
     * @param {number} value - Percentage value (0-1)
     * @returns {string} Formatted percentage
     * @private
     */
    formatPercentage(value) {
        if (value === null || value === undefined) {
            return '-';
        }

        const percent = parseFloat(value) * 100;
        if (isNaN(percent)) {
            return '-';
        }

        return `${percent.toFixed(2)}%`;
    }

    /**
     * Format currency (RON)
     * @param {number|string} value - Currency value
     * @returns {string} Formatted currency
     * @private
     */
    formatCurrency(value) {
        if (value === null || value === undefined || value === '-') {
            return '-';
        }

        const num = parseFloat(value);
        if (isNaN(num)) {
            return '-';
        }

        return `${num.toFixed(2)} RON`;
    }

    /**
     * Hide bonus results
     * @public
     */
    hideBonusResults() {
        if (this.bonusResultsContainer) {
            this.bonusResultsContainer.style.display = 'none';
            this.bonusResultsContainer.innerHTML = '';
        }
    }
}
