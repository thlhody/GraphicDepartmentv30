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

            console.log('WorkedDays from field:', {
                fieldValue: workedDaysInput?.value,
                parsedValue: workedDays,
                stateValue: this.state.bonusCalculationData.workedDays
            });

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

        // Get bonus results container
        const bonusContainer = document.getElementById('bonusResults');
        if (!bonusContainer) {
            console.error('Bonus results container not found');
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

        // Build detailed vertical metrics table
        const tableHTML = `
            <div class="card-header bg-success text-white">
                <h5 class="card-title mb-0">
                    <i class="bi bi-calculator me-2"></i>
                    Bonus Calculation Results
                </h5>
            </div>
            <div class="card-body">
                <div class="table-responsive">
                    <table class="table table-bordered table-sm">
                        <thead class="table-light">
                            <tr>
                                <th style="width: 25%;">Metric</th>
                                <th style="width: 18.75%;">Current</th>
                                <th style="width: 18.75%;">${formatMonth(1)}</th>
                                <th style="width: 18.75%;">${formatMonth(2)}</th>
                                <th style="width: 18.75%;">${formatMonth(3)}</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td><strong>Entries</strong></td>
                                <td>${this.formatNumber(result.entries)}</td>
                                <td>${this.formatNumber(result.previousMonthsDetails?.month1?.entries || '-')}</td>
                                <td>${this.formatNumber(result.previousMonthsDetails?.month2?.entries || '-')}</td>
                                <td>${this.formatNumber(result.previousMonthsDetails?.month3?.entries || '-')}</td>
                            </tr>
                            <tr>
                                <td><strong>Article Numbers</strong></td>
                                <td>${this.formatNumber(result.articleNumbers, 2)}</td>
                                <td>${this.formatNumber(result.previousMonthsDetails?.month1?.articleNumbers || '-', 2)}</td>
                                <td>${this.formatNumber(result.previousMonthsDetails?.month2?.articleNumbers || '-', 2)}</td>
                                <td>${this.formatNumber(result.previousMonthsDetails?.month3?.articleNumbers || '-', 2)}</td>
                            </tr>
                            <tr>
                                <td><strong>Graphic Complexity</strong></td>
                                <td>${this.formatNumber(result.graphicComplexity, 2)}</td>
                                <td>${this.formatNumber(result.previousMonthsDetails?.month1?.graphicComplexity || '-', 2)}</td>
                                <td>${this.formatNumber(result.previousMonthsDetails?.month2?.graphicComplexity || '-', 2)}</td>
                                <td>${this.formatNumber(result.previousMonthsDetails?.month3?.graphicComplexity || '-', 2)}</td>
                            </tr>
                            <tr>
                                <td><strong>Miscellaneous</strong></td>
                                <td>${this.formatNumber(result.misc, 2)}</td>
                                <td>${this.formatNumber(result.previousMonthsDetails?.month1?.misc || '-', 2)}</td>
                                <td>${this.formatNumber(result.previousMonthsDetails?.month2?.misc || '-', 2)}</td>
                                <td>${this.formatNumber(result.previousMonthsDetails?.month3?.misc || '-', 2)}</td>
                            </tr>
                            <tr class="table-info">
                                <td><strong>Worked Days</strong></td>
                                <td>${this.formatNumber(result.workedDays)}</td>
                                <td>${this.formatNumber(result.previousMonthsDetails?.month1?.workedDays || '-')}</td>
                                <td>${this.formatNumber(result.previousMonthsDetails?.month2?.workedDays || '-')}</td>
                                <td>${this.formatNumber(result.previousMonthsDetails?.month3?.workedDays || '-')}</td>
                            </tr>
                            <tr class="table-info">
                                <td><strong>Worked %</strong></td>
                                <td>${this.formatNumber(result.workedPercentage, 2)}%</td>
                                <td>${result.previousMonthsDetails?.month1 ? this.formatNumber(result.previousMonthsDetails.month1.workedPercentage, 2) + '%' : '-'}</td>
                                <td>${result.previousMonthsDetails?.month2 ? this.formatNumber(result.previousMonthsDetails.month2.workedPercentage, 2) + '%' : '-'}</td>
                                <td>${result.previousMonthsDetails?.month3 ? this.formatNumber(result.previousMonthsDetails.month3.workedPercentage, 2) + '%' : '-'}</td>
                            </tr>
                            <tr class="table-warning">
                                <td><strong>Bonus %</strong></td>
                                <td><strong>${this.formatNumber(result.bonusPercentage, 2)}%</strong></td>
                                <td>${result.previousMonthsDetails?.month1 ? this.formatNumber(result.previousMonthsDetails.month1.bonusPercentage, 2) + '%' : '-'}</td>
                                <td>${result.previousMonthsDetails?.month2 ? this.formatNumber(result.previousMonthsDetails.month2.bonusPercentage, 2) + '%' : '-'}</td>
                                <td>${result.previousMonthsDetails?.month3 ? this.formatNumber(result.previousMonthsDetails.month3.bonusPercentage, 2) + '%' : '-'}</td>
                            </tr>
                            <tr class="table-success">
                                <td><strong>Bonus Amount</strong></td>
                                <td><strong>${this.formatNumber(result.bonusAmount, 2)} RON</strong></td>
                                <td><strong>${this.formatNumber(result.previousMonths?.month1 || 0, 2)} RON</strong></td>
                                <td><strong>${this.formatNumber(result.previousMonths?.month2 || 0, 2)} RON</strong></td>
                                <td><strong>${this.formatNumber(result.previousMonths?.month3 || 0, 2)} RON</strong></td>
                            </tr>
                        </tbody>
                    </table>
                </div>
                ${result.workedDays <= 0 ? `
                <div class="alert alert-warning mt-2 mb-0">
                    <i class="bi bi-exclamation-triangle me-2"></i>
                    <small>No worked days recorded - showing raw totals with zero bonus amounts</small>
                </div>
                ` : ''}
            </div>
        `;

        // Replace existing content with new detailed table
        bonusContainer.innerHTML = tableHTML;
        bonusContainer.style.display = 'block';

        // Scroll to results
        bonusContainer.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

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
