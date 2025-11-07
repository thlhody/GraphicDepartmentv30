/**
 * CheckBonusFragment.js
 *
 * Handles individual user bonus calculation in team-check-register.html.
 * Used by team leaders to calculate and save bonus for a selected user.
 *
 * @module features/bonus/CheckBonusFragment
 */

import { API } from '../../core/api.js';

/**
 * CheckBonusFragment class
 * Manages individual user bonus calculation interface
 */
export class CheckBonusFragment {
    constructor() {
        // Cached hours values
        this.cachedLiveHours = null;
        this.cachedStandardHours = null;

        // DOM element references
        this.elements = {};
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize check bonus fragment
     */
    initialize() {
        console.log('Initializing Check Bonus Fragment...');

        this.cacheElements();

        // Check if elements exist (fragment might not be loaded)
        if (!this.validateElements()) {
            console.log('Check bonus fragment not loaded - skipping initialization');
            return;
        }

        this.setupEventListeners();
        this.initializeBonusSumEdit();
        this.loadHoursValues();

        console.log('✅ Check Bonus Fragment initialized');
    }

    /**
     * Cache DOM element references
     */
    cacheElements() {
        this.elements = {
            calculateBtn: document.getElementById('calculateBonusBtn'),
            saveBonusBtn: document.getElementById('saveBonusBtn'),
            bonusResults: document.getElementById('bonusResults'),
            bonusErrorMessage: document.getElementById('bonusErrorMessage'),
            bonusSuccessMessage: document.getElementById('bonusSuccessMessage'),
            bonusSumInput: document.getElementById('bonusSum'),
            hoursOptionLive: document.getElementById('hoursOptionLive'),
            hoursOptionStandard: document.getElementById('hoursOptionStandard'),
            hoursOptionManual: document.getElementById('hoursOptionManual'),
            manualHoursInput: document.getElementById('manualHours'),
            liveHoursValue: document.getElementById('liveHoursValue'),
            standardHoursValue: document.getElementById('standardHoursValue'),
            bonusSumDisplay: document.getElementById('bonusSumDisplay'),
            bonusSumText: document.getElementById('bonusSumText')
        };
    }

    /**
     * Validate required elements exist
     */
    validateElements() {
        return this.elements.calculateBtn && this.elements.saveBonusBtn;
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Main action buttons
        this.elements.calculateBtn.addEventListener('click', () => this.handleCalculateBonus());
        this.elements.saveBonusBtn.addEventListener('click', () => this.handleSaveBonus());

        // Hours option radio buttons
        if (this.elements.hoursOptionManual) {
            this.elements.hoursOptionManual.addEventListener('change', (e) => {
                this.elements.manualHoursInput.disabled = !e.target.checked;
                if (e.target.checked) {
                    this.elements.manualHoursInput.focus();
                }
            });
        }

        if (this.elements.hoursOptionLive) {
            this.elements.hoursOptionLive.addEventListener('change', () => {
                this.elements.manualHoursInput.disabled = true;
            });
        }

        if (this.elements.hoursOptionStandard) {
            this.elements.hoursOptionStandard.addEventListener('change', () => {
                this.elements.manualHoursInput.disabled = true;
            });
        }
    }

    // ========================================================================
    // BONUS SUM EDITING
    // ========================================================================

    /**
     * Initialize bonus sum double-click edit functionality
     */
    initializeBonusSumEdit() {
        const { bonusSumDisplay, bonusSumInput, bonusSumText } = this.elements;

        if (!bonusSumDisplay || !bonusSumInput || !bonusSumText) {
            return;
        }

        // Double-click to edit
        bonusSumDisplay.addEventListener('dblclick', () => {
            bonusSumDisplay.style.display = 'none';
            bonusSumInput.style.display = 'block';
            bonusSumInput.focus();
            bonusSumInput.select();
        });

        // When input loses focus, save
        bonusSumInput.addEventListener('blur', () => this.saveBonusSum());

        // Keyboard shortcuts
        bonusSumInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                this.saveBonusSum();
            } else if (e.key === 'Escape') {
                // Cancel edit - restore original value
                bonusSumInput.value = bonusSumText.textContent;
                bonusSumInput.style.display = 'none';
                bonusSumDisplay.style.display = 'block';
            }
        });
    }

    /**
     * Save bonus sum value
     */
    saveBonusSum() {
        const { bonusSumInput, bonusSumText, bonusSumDisplay } = this.elements;

        const value = parseFloat(bonusSumInput.value);
        if (!isNaN(value) && value > 0) {
            // Update display text
            bonusSumText.textContent = Math.round(value);
            bonusSumInput.value = Math.round(value);
        } else {
            // Invalid value - restore previous
            bonusSumInput.value = bonusSumText.textContent;
        }

        // Switch back to display mode
        bonusSumInput.style.display = 'none';
        bonusSumDisplay.style.display = 'block';
    }

    // ========================================================================
    // HOURS VALUES
    // ========================================================================

    /**
     * Load live and standard hours values from backend
     */
    async loadHoursValues() {
        // Get selected user info from page context (global variables)
        const username = window.SELECTED_USER;
        const userId = window.SELECTED_USER_ID;
        const year = window.CURRENT_YEAR;
        const month = window.CURRENT_MONTH;

        if (!username || !userId || !year || !month) {
            console.warn('Cannot load hours values - missing user or period information');
            return;
        }

        try {
            // Make API call to get hours values
            const response = await fetch(
                `/team/check-register/get-hours?username=${username}&userId=${userId}&year=${year}&month=${month}`,
                {
                    method: 'GET',
                    headers: {
                        [API.getCSRFHeader()]: API.getCSRFToken()
                    }
                }
            );

            if (response.ok) {
                const data = await response.json();
                this.cachedLiveHours = data.liveHours || 0;
                this.cachedStandardHours = data.standardHours || 0;

                // Update badge displays (whole numbers)
                if (this.elements.liveHoursValue) {
                    this.elements.liveHoursValue.textContent = Math.round(this.cachedLiveHours) + ' hrs';
                }
                if (this.elements.standardHoursValue) {
                    this.elements.standardHoursValue.textContent = Math.round(this.cachedStandardHours) + ' hrs';
                }
            }
        } catch (error) {
            console.error('Error loading hours values:', error);
            // Set default values on error
            this.cachedLiveHours = 0;
            this.cachedStandardHours = 0;
            if (this.elements.liveHoursValue) this.elements.liveHoursValue.textContent = '0 hrs';
            if (this.elements.standardHoursValue) this.elements.standardHoursValue.textContent = '0 hrs';
        }
    }

    // ========================================================================
    // BONUS CALCULATION
    // ========================================================================

    /**
     * Handle calculate bonus button click
     */
    async handleCalculateBonus() {
        this.hideMessages();

        // Validate inputs
        const bonusSum = parseFloat(this.elements.bonusSumInput.value);

        if (isNaN(bonusSum) || bonusSum <= 0) {
            this.showError('Please enter a valid bonus sum greater than 0');
            return;
        }

        // Determine which hours option is selected
        let hoursOption = 'standard'; // default
        let manualHours = null;

        if (this.elements.hoursOptionLive?.checked) {
            hoursOption = 'live';
        } else if (this.elements.hoursOptionStandard?.checked) {
            hoursOption = 'standard';
        } else if (this.elements.hoursOptionManual?.checked) {
            hoursOption = 'manual';
            manualHours = parseFloat(this.elements.manualHoursInput.value);

            if (isNaN(manualHours) || manualHours <= 0) {
                this.showError('Please enter valid manual hours greater than 0');
                return;
            }
        }

        // Get selected user info from page context
        const username = window.SELECTED_USER;
        const userId = window.SELECTED_USER_ID;
        const year = window.CURRENT_YEAR;
        const month = window.CURRENT_MONTH;

        if (!username || !userId || !year || !month) {
            this.showError('Missing user or period information. Please select a user and period.');
            return;
        }

        // Show loading state
        this.setButtonLoading(this.elements.calculateBtn, true, 'Calculating...');

        try {
            // Make API call to calculate bonus
            const response = await fetch('/team/check-register/calculate-bonus', {
                method: 'POST',
                headers: {
                    [API.getCSRFHeader()]: API.getCSRFToken(),
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    username: username,
                    userId: userId,
                    year: year,
                    month: month,
                    bonusSum: bonusSum,
                    hoursOption: hoursOption,
                    manualHours: manualHours
                })
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({ message: 'Failed to calculate bonus' }));
                throw new Error(errorData.message || 'Failed to calculate bonus');
            }

            const bonusData = await response.json();

            // Display results
            this.displayBonusResults(bonusData);
            this.showSuccess('Bonus calculated successfully!');

        } catch (error) {
            console.error('Error calculating bonus:', error);
            this.showError(error.message || 'Failed to calculate bonus. Please try again.');
        } finally {
            // Reset button state
            this.setButtonLoading(this.elements.calculateBtn, false,
                '<i class="bi bi-calculator me-1"></i>Calculate Bonus');
        }
    }

    // ========================================================================
    // BONUS SAVING
    // ========================================================================

    /**
     * Handle save bonus button click
     */
    async handleSaveBonus() {
        this.hideMessages();

        // Get current bonus data from displayed results
        const bonusData = this.extractDisplayedBonusData();

        if (!bonusData) {
            this.showError('No bonus data to save. Please calculate bonus first.');
            return;
        }

        // Show loading state
        this.setButtonLoading(this.elements.saveBonusBtn, true, 'Saving...');

        try {
            // Make API call to save bonus
            const response = await fetch('/team/check-register/save-bonus', {
                method: 'POST',
                headers: {
                    [API.getCSRFHeader()]: API.getCSRFToken(),
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(bonusData)
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({ message: 'Failed to save bonus' }));
                throw new Error(errorData.message || 'Failed to save bonus');
            }

            const result = await response.json();
            this.showSuccess(`Bonus saved successfully to lead_check_bonus_${bonusData.year}_${bonusData.month}.json`);

            // Show toast notification
            if (typeof window.showToast === 'function') {
                window.showToast('Success', 'Bonus saved successfully!', 'success');
            }

        } catch (error) {
            console.error('Error saving bonus:', error);
            this.showError(error.message || 'Failed to save bonus. Please try again.');
        } finally {
            // Reset button state
            this.setButtonLoading(this.elements.saveBonusBtn, false,
                '<i class="bi bi-save me-1"></i>Save Bonus');
        }
    }

    // ========================================================================
    // RESULTS DISPLAY
    // ========================================================================

    /**
     * Display bonus calculation results
     */
    displayBonusResults(bonusData) {
        if (!bonusData) {
            console.error('No bonus data to display');
            return;
        }

        // Update user name
        const bonusUserName = document.getElementById('bonusUserName');
        if (bonusUserName) {
            bonusUserName.textContent = bonusData.name || bonusData.username || 'Unknown User';
        }

        // Update result cells with null-safe handling
        this.updateCell('result-totalWUM', this.formatNumber(bonusData.totalWUM));
        this.updateCell('result-workingHours', this.formatNumber(bonusData.workingHours));
        this.updateCell('result-targetWUHR', this.formatNumber(bonusData.targetWUHR));
        this.updateCell('result-totalWUHRM', this.formatNumber(bonusData.totalWUHRM));
        this.updateCell('result-efficiencyPercent', this.formatPercent(bonusData.efficiencyPercent));
        this.updateCell('result-bonusAmount', this.formatCurrency(bonusData.bonusAmount));

        // Show results table
        if (this.elements.bonusResults) {
            this.elements.bonusResults.style.display = 'block';
        }

        // Hide empty message
        const noBonusMessage = document.getElementById('noBonusMessage');
        if (noBonusMessage) {
            noBonusMessage.style.display = 'none';
        }
    }

    /**
     * Update cell content
     */
    updateCell(cellId, value) {
        const cell = document.getElementById(cellId);
        if (cell) {
            cell.textContent = value;
        }
    }

    /**
     * Extract displayed bonus data for saving
     */
    extractDisplayedBonusData() {
        const totalWUM = parseFloat(document.getElementById('result-totalWUM')?.textContent) || 0;
        const workingHours = parseFloat(document.getElementById('result-workingHours')?.textContent) || 0;
        const targetWUHR = parseFloat(document.getElementById('result-targetWUHR')?.textContent) || 0;
        const totalWUHRM = parseFloat(document.getElementById('result-totalWUHRM')?.textContent) || 0;
        const efficiencyPercent = parseInt(document.getElementById('result-efficiencyPercent')?.textContent) || 0;
        const bonusAmount = parseFloat(document.getElementById('result-bonusAmount')?.textContent.replace(/[^0-9.-]+/g, '')) || 0;

        // Check if we have valid data
        if (totalWUM === 0 && workingHours === 0) {
            return null;
        }

        // Determine which hours option was selected
        let hoursOption = 'standard';
        let manualHours = null;

        if (this.elements.hoursOptionLive?.checked) {
            hoursOption = 'live';
        } else if (this.elements.hoursOptionStandard?.checked) {
            hoursOption = 'standard';
        } else if (this.elements.hoursOptionManual?.checked) {
            hoursOption = 'manual';
            manualHours = parseFloat(this.elements.manualHoursInput.value) || 0;
        }

        return {
            username: window.SELECTED_USER,
            employeeId: window.SELECTED_USER_ID,
            name: document.getElementById('bonusUserName')?.textContent || window.SELECTED_USER,
            totalWUM: totalWUM,
            workingHours: workingHours,
            liveHours: this.cachedLiveHours,
            standardHours: this.cachedStandardHours,
            manualHours: manualHours,
            hoursOption: hoursOption,
            targetWUHR: targetWUHR,
            totalWUHRM: totalWUHRM,
            efficiencyPercent: efficiencyPercent,
            bonusAmount: bonusAmount,
            year: window.CURRENT_YEAR,
            month: window.CURRENT_MONTH,
            calculationDate: new Date().toLocaleDateString('en-GB')
        };
    }

    // ========================================================================
    // FORMATTING HELPERS
    // ========================================================================

    /**
     * Format number with 2 decimal places
     */
    formatNumber(value) {
        if (value === null || value === undefined || isNaN(value)) {
            return '-';
        }
        return parseFloat(value).toFixed(2);
    }

    /**
     * Format percentage
     */
    formatPercent(value) {
        if (value === null || value === undefined || isNaN(value)) {
            return '-';
        }
        return value + '%';
    }

    /**
     * Format currency
     */
    formatCurrency(value) {
        if (value === null || value === undefined || isNaN(value)) {
            return '-';
        }
        return parseFloat(value).toFixed(2);
    }

    // ========================================================================
    // UI HELPERS
    // ========================================================================

    /**
     * Set button loading state
     */
    setButtonLoading(button, loading, text) {
        button.disabled = loading;
        button.innerHTML = loading ?
            '<span class="spinner-border spinner-border-sm me-2"></span>' + text :
            text;
    }

    /**
     * Show error message
     */
    showError(message) {
        if (this.elements.bonusErrorMessage) {
            const errorText = document.getElementById('bonusErrorText');
            if (errorText) {
                errorText.textContent = message;
            }
            this.elements.bonusErrorMessage.style.display = 'block';

            // Auto-hide after 5 seconds
            setTimeout(() => {
                this.elements.bonusErrorMessage.style.display = 'none';
            }, 5000);
        }
    }

    /**
     * Show success message
     */
    showSuccess(message) {
        if (this.elements.bonusSuccessMessage) {
            const successText = document.getElementById('bonusSuccessText');
            if (successText) {
                successText.textContent = message;
            }
            this.elements.bonusSuccessMessage.style.display = 'block';

            // Auto-hide after 5 seconds
            setTimeout(() => {
                this.elements.bonusSuccessMessage.style.display = 'none';
            }, 5000);
        }
    }

    /**
     * Hide all messages
     */
    hideMessages() {
        if (this.elements.bonusErrorMessage) {
            this.elements.bonusErrorMessage.style.display = 'none';
        }
        if (this.elements.bonusSuccessMessage) {
            this.elements.bonusSuccessMessage.style.display = 'none';
        }
    }
}
