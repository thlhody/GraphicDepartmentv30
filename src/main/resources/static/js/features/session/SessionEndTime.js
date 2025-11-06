/**
 * SessionEndTime.js
 *
 * Manages end time scheduling and work time calculations for active sessions.
 * Handles recommended end time, work time calculation preview, and automatic page refresh.
 *
 * @module features/session/SessionEndTime
 */

import { API } from '../../core/api.js';
import { formatMinutesToHours } from '../../core/utils.js';

/**
 * SessionEndTime class
 * Handles end time scheduling and calculations
 */
export class SessionEndTime {
    constructor() {
        this.endHourInput = document.getElementById('endHour');
        this.endMinuteInput = document.getElementById('endMinute');
        this.useRecommendedBtn = document.getElementById('useRecommendedTime');

        if (this.endHourInput && this.endMinuteInput) {
            this.initEndTimeScheduler();
            this.initEndTimeChecker();
        }
    }

    /**
     * Initialize the end time scheduler functionality
     */
    initEndTimeScheduler() {
        const schedulerForm = this.endHourInput.closest('form');
        if (!schedulerForm) return;

        // Get the parent container of the scheduler
        const schedulerContainer = schedulerForm.closest('.stat-container');
        if (!schedulerContainer) return;

        // Create calculation preview container
        this.createCalculationContainer(schedulerContainer);

        // Fetch recommended end time for default values
        this.fetchRecommendedEndTime();

        // Add event listeners for hour/minute changes
        this.endHourInput.addEventListener('input', () => this.calculateEndTimeWorkTime());
        this.endMinuteInput.addEventListener('input', () => this.calculateEndTimeWorkTime());

        // Handle recommendation button click
        if (this.useRecommendedBtn) {
            this.useRecommendedBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.useRecommendedTime();
            });
        }

        // Initial calculation
        this.calculateEndTimeWorkTime();
    }

    /**
     * Create calculation preview container
     * @param {HTMLElement} schedulerContainer - Container element
     */
    createCalculationContainer(schedulerContainer) {
        let calculationContainer = schedulerContainer.querySelector('.end-time-calculation-result');
        if (!calculationContainer) {
            calculationContainer = document.createElement('div');
            calculationContainer.className = 'end-time-calculation-result mt-2 mb-2';
            calculationContainer.style.display = 'none';

            // Find the title to insert after
            const titleElement = schedulerContainer.querySelector('.text-muted');
            if (titleElement && titleElement.nextSibling) {
                schedulerContainer.insertBefore(calculationContainer, titleElement.nextSibling);
            } else {
                schedulerContainer.prepend(calculationContainer);
            }
        }

        this.calculationContainer = calculationContainer;
    }

    /**
     * Fetch recommended end time from server
     */
    async fetchRecommendedEndTime() {
        try {
            const data = await API.get('/user/session/recommended-end-time');

            if (data.success && data.recommendedEndTime) {
                const [hours, minutes] = data.recommendedEndTime.split(':');
                this.endHourInput.value = hours;
                this.endMinuteInput.value = minutes;

                // Calculate work time with these values
                this.calculateEndTimeWorkTime();
            } else {
                // Fallback to 5 PM if recommendation fails
                this.endHourInput.value = '17';
                this.endMinuteInput.value = '00';
            }
        } catch (error) {
            console.error('Error fetching recommended end time:', error);
            // Fallback to 5 PM
            this.endHourInput.value = '17';
            this.endMinuteInput.value = '00';
        }
    }

    /**
     * Use recommended time button handler
     */
    async useRecommendedTime() {
        try {
            const data = await API.get('/user/session/recommended-end-time');

            if (data.success && data.recommendedEndTime) {
                const [hours, minutes] = data.recommendedEndTime.split(':');
                this.endHourInput.value = hours;
                this.endMinuteInput.value = minutes;

                // Calculate with the new values
                this.calculateEndTimeWorkTime();

                // Show toast with details
                let messageText = 'Recommended end time loaded: ' + data.recommendedEndTime;
                if (data.expectedEndTime) {
                    messageText += ' (Schedule end: ' + data.expectedEndTime + ')';
                }

                window.showToast('Recommended Time', messageText, 'info');
            } else {
                window.showToast('Warning', data.message || 'Could not get recommended time', 'warning');
            }
        } catch (error) {
            console.error('Error fetching recommended end time:', error);
            window.showToast('Error', 'Error fetching recommended end time', 'error');
        }
    }

    /**
     * Calculate work time based on the end time inputs
     */
    async calculateEndTimeWorkTime() {
        const calculationPreview = document.getElementById('endTimeCalculationPreview');
        if (!calculationPreview) return;

        // Clear previous calculation
        calculationPreview.innerHTML = '<div class="text-muted small">Calculating...</div>';

        // Get input values
        const endHour = parseInt(this.endHourInput.value);
        const endMinute = parseInt(this.endMinuteInput.value);

        // Validate inputs
        if (isNaN(endHour) || isNaN(endMinute)) {
            calculationPreview.innerHTML = '<div class="text-muted small">Select end time to see calculation</div>';
            if (this.calculationContainer) {
                this.calculationContainer.style.display = 'none';
            }
            return;
        }

        try {
            // Call endpoint to calculate end time
            const data = await API.post('/user/session/calculate-end-time', {
                endHour: endHour,
                endMinute: endMinute
            });

            if (data.success) {
                // Generate calculation HTML
                const previewHTML = this.generateCalculationPreview(data);
                calculationPreview.innerHTML = previewHTML;

                // Show the container
                if (this.calculationContainer) {
                    this.calculationContainer.style.display = 'block';
                }
            } else {
                calculationPreview.innerHTML = `<div class="text-danger small">${data.message || 'Calculation failed'}</div>`;
            }
        } catch (error) {
            console.error('Error calculating end time:', error);
            calculationPreview.innerHTML = '<div class="text-danger small">Error calculating work time</div>';
        }
    }

    /**
     * Generate calculation preview HTML
     * @param {Object} data - Calculation data from server
     * @returns {string} HTML string
     */
    generateCalculationPreview(data) {
        let html = '<div class="calculation-details small">';

        // Total elapsed time
        html += `<div class="mb-1">
            <i class="bi bi-stopwatch me-1 text-muted"></i>
            <span>Total: ${data.formattedTotalElapsed || formatMinutesToHours(data.totalElapsedMinutes)}</span>
        </div>`;

        // Temporary stops/breaks if any
        if (data.breakMinutes > 0) {
            html += `<div class="mb-1">
                <i class="bi bi-dash-circle me-1 text-warning"></i>
                <span>Breaks: ${data.formattedBreakTime || formatMinutesToHours(data.breakMinutes)}</span>
            </div>`;
        }

        // Lunch break deduction if applicable
        if (data.lunchDeducted) {
            html += `<div class="mb-1">
                <i class="bi bi-cup-hot me-1 text-warning"></i>
                <span>Lunch: ${formatMinutesToHours(data.lunchBreakMinutes)}</span>
            </div>`;
        }

        // Net work time
        html += `<div class="mb-1">
            <i class="bi bi-check-circle me-1 text-success"></i>
            <strong>Net: ${data.formattedNetWorkTime || formatMinutesToHours(data.netWorkMinutes)}</strong>
        </div>`;

        // Overtime information if applicable
        if (data.overtimeMinutes > 0) {
            html += `<div class="mb-1 text-primary">
                <i class="bi bi-clock-history me-1"></i>
                <span>Overtime: ${data.formattedOvertimeMinutes || formatMinutesToHours(data.overtimeMinutes)}</span>
            </div>`;
        }

        // Warning if work time is less than expected
        if (data.warningMessage) {
            html += `<div class="mt-2 alert alert-warning py-1 px-2 mb-0 small">
                <i class="bi bi-exclamation-triangle me-1"></i>
                ${data.warningMessage}
            </div>`;
        }

        html += '</div>';
        return html;
    }

    /**
     * Initialize end time checker for automatic page refresh
     */
    initEndTimeChecker() {
        const scheduledTimeElement = document.getElementById('scheduled-end-time');
        if (!scheduledTimeElement) return;

        const scheduledTimeText = scheduledTimeElement.textContent.trim();
        const timeMatch = scheduledTimeText.match(/(\d{1,2}):(\d{2})/);

        if (!timeMatch) return;

        const hours = parseInt(timeMatch[1], 10);
        const minutes = parseInt(timeMatch[2], 10);

        // Create date object for the scheduled end time
        const scheduledDate = new Date();
        scheduledDate.setHours(hours, minutes, 0, 0);

        // If time is in the past, ignore
        const now = new Date();
        if (scheduledDate <= now) return;

        // Calculate milliseconds until scheduled time
        const timeUntilEnd = scheduledDate - now;

        console.log(`Page refresh scheduled for ${scheduledDate.toLocaleTimeString()}, in ${Math.round(timeUntilEnd/1000)} seconds`);

        // Set a timeout to refresh the page at the exact time
        setTimeout(() => {
            console.log('Scheduled end time reached, refreshing page...');
            // Add a small delay to ensure the backend has time to process
            setTimeout(() => {
                window.location.reload();
            }, 3000);
        }, timeUntilEnd + 500); // Add 500ms to ensure we're past the time
    }
}
