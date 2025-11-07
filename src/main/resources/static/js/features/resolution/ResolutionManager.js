/**
 * ResolutionManager.js
 *
 * Manages work time resolution for unfinished sessions.
 * Features: backend time calculation, form validation, breakdown display,
 * and toast notification fallback system.
 *
 * @module features/resolution/ResolutionManager
 */

import { API } from '../../core/api.js';

/**
 * ResolutionManager class
 * Handles resolution form operations and time calculations
 */
export class ResolutionManager {
    constructor() {
        this.calculationForms = null;

        console.log('ResolutionManager initialized');
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize resolution manager
     */
    initialize() {
        console.log('🚀 Initializing Resolution Manager...');

        // Setup form listeners
        this.setupFormListeners();

        console.log('✅ Resolution Manager initialized successfully');
    }

    // ========================================================================
    // FORM HANDLING
    // ========================================================================

    /**
     * Setup event listeners for calculation forms
     */
    setupFormListeners() {
        this.calculationForms = document.querySelectorAll('.calculation-form');

        this.calculationForms.forEach((form) => {
            const hourSelect = form.querySelector('select[name="endHour"]');
            const minuteSelect = form.querySelector('select[name="endMinute"]');

            // Calculate time when hour or minute changes
            if (hourSelect) {
                hourSelect.addEventListener('change', () => {
                    this.calculateWorkTime(form);
                });
            }

            if (minuteSelect) {
                minuteSelect.addEventListener('change', () => {
                    this.calculateWorkTime(form);
                });
            }

            // Handle form submission
            form.addEventListener('submit', (e) => {
                e.preventDefault();
                this.handleResolutionSubmit(form);
            });
        });

        console.log(`Setup listeners for ${this.calculationForms.length} resolution forms`);
    }

    // ========================================================================
    // TIME CALCULATION
    // ========================================================================

    /**
     * Calculate work time via backend API
     * @param {HTMLFormElement} form - Calculation form element
     */
    async calculateWorkTime(form) {
        const hourSelect = form.querySelector('select[name="endHour"]');
        const minuteSelect = form.querySelector('select[name="endMinute"]');
        const entryDate = form.querySelector('input[name="entryDate"]').value;
        const resultDiv = form.querySelector('.calculation-result');

        if (!hourSelect || !minuteSelect || !resultDiv) {
            console.error('Required form elements not found');
            return;
        }

        // Show loading state
        resultDiv.innerHTML = '<div class="text-center"><div class="spinner-border spinner-border-sm" role="status"></div> Calculating...</div>';

        try {
            const response = await fetch('/user/session/calculate-resolution', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    [API.getCSRFHeader()]: API.getCSRFToken()
                },
                body: JSON.stringify({
                    entryDate: entryDate,
                    endHour: parseInt(hourSelect.value),
                    endMinute: parseInt(minuteSelect.value)
                })
            });

            if (!response.ok) {
                throw new Error(`Server error: ${response.status}`);
            }

            const data = await response.json();

            // Display calculation breakdown
            resultDiv.innerHTML = this.formatCalculationResult(data);

            console.log('Work time calculated successfully');

        } catch (error) {
            console.error('Error calculating work time:', error);
            resultDiv.innerHTML = `<div class="alert alert-danger">Error calculating time: ${error.message}</div>`;
            this.showToastSafe('Error', 'Failed to calculate work time', 'error');
        }
    }

    /**
     * Format calculation result for display
     * @param {Object} data - Calculation result from backend
     * @returns {string} Formatted HTML
     */
    formatCalculationResult(data) {
        return `
            <div class="calculation-breakdown">
                <h6 class="mb-3">Time Breakdown:</h6>
                <div class="row g-2">
                    <div class="col-md-6">
                        <div class="d-flex justify-content-between">
                            <strong>Total elapsed time:</strong>
                            <span>${data.formattedTotalElapsed || '0h 0m'}</span>
                        </div>
                    </div>
                    <div class="col-md-6">
                        <div class="d-flex justify-content-between">
                            <strong>Temporary stops:</strong>
                            <span>${data.formattedBreakTime || '0h 0m'}</span>
                        </div>
                    </div>
                    <div class="col-md-6">
                        <div class="d-flex justify-content-between">
                            <strong>Lunch break:</strong>
                            <span>${this.formatMinutes(data.lunchBreakMinutes || 0)}</span>
                        </div>
                    </div>
                    <div class="col-md-6">
                        <div class="d-flex justify-content-between">
                            <strong>Net work time:</strong>
                            <span class="text-primary fw-bold">${data.formattedNetWorkTime || '0h 0m'}</span>
                        </div>
                    </div>
                    <div class="col-12">
                        <div class="d-flex justify-content-between border-top pt-2 mt-2">
                            <strong>Overtime:</strong>
                            <span class="${data.overtimeMinutes > 0 ? 'text-success' : 'text-muted'} fw-bold">
                                ${data.formattedOvertimeMinutes || '0h 0m'}
                            </span>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    // ========================================================================
    // FORM SUBMISSION
    // ========================================================================

    /**
     * Handle resolution form submission
     * @param {HTMLFormElement} form - Form element
     */
    async handleResolutionSubmit(form) {
        const submitButton = form.querySelector('button[type="submit"]');
        const originalButtonText = submitButton ? submitButton.innerHTML : 'Submit';

        try {
            // Disable submit button
            if (submitButton) {
                submitButton.disabled = true;
                submitButton.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Resolving...';
            }

            // Get form data
            const formData = new FormData(form);
            const data = Object.fromEntries(formData.entries());

            // Submit resolution
            const response = await fetch(form.action, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    [API.getCSRFHeader()]: API.getCSRFToken()
                },
                body: new URLSearchParams(data)
            });

            if (!response.ok) {
                throw new Error(`Server error: ${response.status}`);
            }

            // Check if response is JSON or redirect
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                const result = await response.json();

                if (result.success) {
                    this.showToastSafe('Success', 'Session resolved successfully', 'success');

                    // Redirect after short delay
                    setTimeout(() => {
                        window.location.href = result.redirectUrl || '/user/session';
                    }, 1500);
                } else {
                    throw new Error(result.message || 'Resolution failed');
                }
            } else {
                // Direct redirect
                window.location.href = '/user/session';
            }

        } catch (error) {
            console.error('Error submitting resolution:', error);
            this.showToastSafe('Error', 'Failed to resolve session: ' + error.message, 'error');

            // Re-enable submit button
            if (submitButton) {
                submitButton.disabled = false;
                submitButton.innerHTML = originalButtonText;
            }
        }
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Format minutes to hours and minutes
     * @param {number} minutes - Minutes to format
     * @returns {string} Formatted string (e.g., "2h 30m")
     */
    formatMinutes(minutes) {
        if (!minutes || minutes === 0) return '0h 0m';

        const hours = Math.floor(minutes / 60);
        const mins = minutes % 60;
        return `${hours}h ${mins}m`;
    }

    /**
     * Show toast notification with fallback
     * @param {string} title - Toast title
     * @param {string} message - Toast message
     * @param {string} type - Toast type (success, error, warning, info)
     */
    showToastSafe(title, message, type) {
        // Try to use global toast function
        if (typeof window.showToast === 'function') {
            window.showToast(title, message, type);
        } else {
            // Fallback to Bootstrap alert
            this.createFallbackAlert(title, message, type);
        }
    }

    /**
     * Create fallback alert if toast is not available
     * @param {string} title - Alert title
     * @param {string} message - Alert message
     * @param {string} type - Alert type
     */
    createFallbackAlert(title, message, type) {
        const alertClass = {
            'success': 'alert-success',
            'error': 'alert-danger',
            'warning': 'alert-warning',
            'info': 'alert-info'
        }[type] || 'alert-info';

        const alertHtml = `
            <div class="alert ${alertClass} alert-dismissible fade show position-fixed top-0 start-50 translate-middle-x mt-3"
                 style="z-index: 9999; min-width: 300px;" role="alert">
                <strong>${title}:</strong> ${message}
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            </div>
        `;

        // Insert alert
        const alertContainer = document.createElement('div');
        alertContainer.innerHTML = alertHtml;
        document.body.appendChild(alertContainer.firstElementChild);

        // Auto-remove after 5 seconds
        setTimeout(() => {
            const alert = document.querySelector('.alert');
            if (alert) {
                alert.remove();
            }
        }, 5000);
    }
}
