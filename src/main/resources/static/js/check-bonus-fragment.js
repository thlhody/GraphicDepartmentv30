/**
 * Check Bonus Fragment JavaScript
 * Handles individual user bonus calculation in team-check-register.html
 */

(function() {
    'use strict';

    // CSRF Token handling
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;

    // DOM Elements
    let calculateBtn, saveBonusBtn, bonusResults, bonusErrorMessage, bonusSuccessMessage;
    let bonusSumInput, hoursOptionLive, hoursOptionStandard, hoursOptionManual, manualHoursInput;
    let liveHoursValue, standardHoursValue;

    // Cached hours values
    let cachedLiveHours = null;
    let cachedStandardHours = null;

    /**
     * Initialize the bonus fragment
     */
    function init() {
        // Get DOM elements
        calculateBtn = document.getElementById('calculateBonusBtn');
        saveBonusBtn = document.getElementById('saveBonusBtn');
        bonusResults = document.getElementById('bonusResults');
        bonusErrorMessage = document.getElementById('bonusErrorMessage');
        bonusSuccessMessage = document.getElementById('bonusSuccessMessage');
        bonusSumInput = document.getElementById('bonusSum');

        // Hours option elements
        hoursOptionLive = document.getElementById('hoursOptionLive');
        hoursOptionStandard = document.getElementById('hoursOptionStandard');
        hoursOptionManual = document.getElementById('hoursOptionManual');
        manualHoursInput = document.getElementById('manualHours');
        liveHoursValue = document.getElementById('liveHoursValue');
        standardHoursValue = document.getElementById('standardHoursValue');

        // Check if elements exist (fragment might not be loaded)
        if (!calculateBtn || !saveBonusBtn) {
            console.log('Check bonus fragment not loaded - skipping initialization');
            return;
        }

        // Attach event listeners
        calculateBtn.addEventListener('click', handleCalculateBonus);
        saveBonusBtn.addEventListener('click', handleSaveBonus);

        // Add listeners for hours option radio buttons
        if (hoursOptionManual) {
            hoursOptionManual.addEventListener('change', function() {
                manualHoursInput.disabled = !this.checked;
                if (this.checked) {
                    manualHoursInput.focus();
                }
            });
        }

        if (hoursOptionLive) {
            hoursOptionLive.addEventListener('change', function() {
                manualHoursInput.disabled = true;
            });
        }

        if (hoursOptionStandard) {
            hoursOptionStandard.addEventListener('change', function() {
                manualHoursInput.disabled = true;
            });
        }

        // Load hours values when fragment loads
        loadHoursValues();

        console.log('Check bonus fragment initialized');
    }

    /**
     * Load live and standard hours values from backend
     */
    async function loadHoursValues() {
        // Get selected user info from page context
        const username = SELECTED_USER;
        const userId = SELECTED_USER_ID;
        const year = CURRENT_YEAR;
        const month = CURRENT_MONTH;

        if (!username || !userId || !year || !month) {
            console.warn('Cannot load hours values - missing user or period information');
            return;
        }

        try {
            // Make API call to get hours values
            const response = await fetch(`/team/check-register/get-hours?username=${username}&userId=${userId}&year=${year}&month=${month}`, {
                method: 'GET',
                headers: {
                    [header]: token
                }
            });

            if (response.ok) {
                const data = await response.json();
                cachedLiveHours = data.liveHours || 0;
                cachedStandardHours = data.standardHours || 0;

                // Update badge displays (no minutes, whole numbers)
                if (liveHoursValue) {
                    liveHoursValue.textContent = Math.round(cachedLiveHours) + ' hrs';
                }
                if (standardHoursValue) {
                    standardHoursValue.textContent = Math.round(cachedStandardHours) + ' hrs';
                }
            }
        } catch (error) {
            console.error('Error loading hours values:', error);
            // Set default values on error
            cachedLiveHours = 0;
            cachedStandardHours = 0;
            if (liveHoursValue) liveHoursValue.textContent = '0 hrs';
            if (standardHoursValue) standardHoursValue.textContent = '0 hrs';
        }
    }

    /**
     * Handle calculate bonus button click
     */
    async function handleCalculateBonus() {
        hideMessages();

        // Validate inputs
        const bonusSum = parseFloat(bonusSumInput.value);

        if (isNaN(bonusSum) || bonusSum <= 0) {
            showError('Please enter a valid bonus sum greater than 0');
            return;
        }

        // Determine which hours option is selected
        let hoursOption = 'standard'; // default
        let manualHours = null;

        if (hoursOptionLive && hoursOptionLive.checked) {
            hoursOption = 'live';
        } else if (hoursOptionStandard && hoursOptionStandard.checked) {
            hoursOption = 'standard';
        } else if (hoursOptionManual && hoursOptionManual.checked) {
            hoursOption = 'manual';
            manualHours = parseFloat(manualHoursInput.value);

            if (isNaN(manualHours) || manualHours <= 0) {
                showError('Please enter valid manual hours greater than 0');
                return;
            }
        }

        // Get selected user info from page context
        const username = SELECTED_USER;
        const userId = SELECTED_USER_ID;
        const year = CURRENT_YEAR;
        const month = CURRENT_MONTH;

        if (!username || !userId || !year || !month) {
            showError('Missing user or period information. Please select a user and period.');
            return;
        }

        // Show loading state
        calculateBtn.disabled = true;
        calculateBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Calculating...';

        try {
            // Make API call to calculate bonus
            const response = await fetch('/team/check-register/calculate-bonus', {
                method: 'POST',
                headers: {
                    [header]: token,
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
            displayBonusResults(bonusData);
            showSuccess('Bonus calculated successfully!');

        } catch (error) {
            console.error('Error calculating bonus:', error);
            showError(error.message || 'Failed to calculate bonus. Please try again.');
        } finally {
            // Reset button state
            calculateBtn.disabled = false;
            calculateBtn.innerHTML = '<i class="bi bi-calculator me-1"></i>Calculate Bonus';
        }
    }

    /**
     * Handle save bonus button click
     */
    async function handleSaveBonus() {
        hideMessages();

        // Get current bonus data from displayed results
        const bonusData = extractDisplayedBonusData();

        if (!bonusData) {
            showError('No bonus data to save. Please calculate bonus first.');
            return;
        }

        // Show loading state
        saveBonusBtn.disabled = true;
        saveBonusBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Saving...';

        try {
            // Make API call to save bonus
            const response = await fetch('/team/check-register/save-bonus', {
                method: 'POST',
                headers: {
                    [header]: token,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(bonusData)
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({ message: 'Failed to save bonus' }));
                throw new Error(errorData.message || 'Failed to save bonus');
            }

            const result = await response.json();
            showSuccess('Bonus saved successfully to lead_check_bonus_' + bonusData.year + '_' + bonusData.month + '.json');

            // Show toast notification
            if (typeof window.showToast === 'function') {
                window.showToast('Success', 'Bonus saved successfully!', 'success');
            }

        } catch (error) {
            console.error('Error saving bonus:', error);
            showError(error.message || 'Failed to save bonus. Please try again.');
        } finally {
            // Reset button state
            saveBonusBtn.disabled = false;
            saveBonusBtn.innerHTML = '<i class="bi bi-save me-1"></i>Save Bonus';
        }
    }

    /**
     * Display bonus calculation results
     */
    function displayBonusResults(bonusData) {
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
        updateCell('result-totalWUM', formatNumber(bonusData.totalWUM));
        updateCell('result-workingHours', formatNumber(bonusData.workingHours));
        updateCell('result-targetWUHR', formatNumber(bonusData.targetWUHR));
        updateCell('result-totalWUHRM', formatNumber(bonusData.totalWUHRM));
        updateCell('result-efficiencyPercent', formatPercent(bonusData.efficiencyPercent));
        updateCell('result-bonusAmount', formatCurrency(bonusData.bonusAmount));

        // Show results table
        if (bonusResults) {
            bonusResults.style.display = 'block';
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
    function updateCell(cellId, value) {
        const cell = document.getElementById(cellId);
        if (cell) {
            cell.textContent = value;
        }
    }

    /**
     * Extract displayed bonus data for saving
     */
    function extractDisplayedBonusData() {
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

        if (hoursOptionLive && hoursOptionLive.checked) {
            hoursOption = 'live';
        } else if (hoursOptionStandard && hoursOptionStandard.checked) {
            hoursOption = 'standard';
        } else if (hoursOptionManual && hoursOptionManual.checked) {
            hoursOption = 'manual';
            manualHours = parseFloat(manualHoursInput.value) || 0;
        }

        return {
            username: SELECTED_USER,
            employeeId: SELECTED_USER_ID,
            name: document.getElementById('bonusUserName')?.textContent || SELECTED_USER,
            totalWUM: totalWUM,
            workingHours: workingHours,
            liveHours: cachedLiveHours,
            standardHours: cachedStandardHours,
            manualHours: manualHours,
            hoursOption: hoursOption,
            targetWUHR: targetWUHR,
            totalWUHRM: totalWUHRM,
            efficiencyPercent: efficiencyPercent,
            bonusAmount: bonusAmount,
            year: CURRENT_YEAR,
            month: CURRENT_MONTH,
            calculationDate: new Date().toLocaleDateString('en-GB')
        };
    }

    /**
     * Format number with 2 decimal places
     */
    function formatNumber(value) {
        if (value === null || value === undefined || isNaN(value)) {
            return '-';
        }
        return parseFloat(value).toFixed(2);
    }

    /**
     * Format percentage
     */
    function formatPercent(value) {
        if (value === null || value === undefined || isNaN(value)) {
            return '-';
        }
        return value + '%';
    }

    /**
     * Format currency
     */
    function formatCurrency(value) {
        if (value === null || value === undefined || isNaN(value)) {
            return '-';
        }
        return parseFloat(value).toFixed(2);
    }

    /**
     * Show error message
     */
    function showError(message) {
        if (bonusErrorMessage) {
            const errorText = document.getElementById('bonusErrorText');
            if (errorText) {
                errorText.textContent = message;
            }
            bonusErrorMessage.style.display = 'block';

            // Auto-hide after 5 seconds
            setTimeout(() => {
                bonusErrorMessage.style.display = 'none';
            }, 5000);
        }
    }

    /**
     * Show success message
     */
    function showSuccess(message) {
        if (bonusSuccessMessage) {
            const successText = document.getElementById('bonusSuccessText');
            if (successText) {
                successText.textContent = message;
            }
            bonusSuccessMessage.style.display = 'block';

            // Auto-hide after 5 seconds
            setTimeout(() => {
                bonusSuccessMessage.style.display = 'none';
            }, 5000);
        }
    }

    /**
     * Hide all messages
     */
    function hideMessages() {
        if (bonusErrorMessage) {
            bonusErrorMessage.style.display = 'none';
        }
        if (bonusSuccessMessage) {
            bonusSuccessMessage.style.display = 'none';
        }
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();