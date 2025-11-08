/**
 * TimeManagementAjaxHandler.js
 * Handles AJAX period navigation and fragment reloading without page reload
 *
 * Features:
 * - Intercept period navigation (year/month selection)
 * - Loading overlay during requests
 * - Fragment reloading after operations
 * - No page reloads - pure AJAX like register page
 *
 * @module features/time-management/AjaxHandler
 */

import { ToastNotification } from '../../components/ToastNotification.js';

/**
 * TimeManagementAjaxHandler - AJAX navigation and fragment reload handler
 */
export class TimeManagementAjaxHandler {

    /**
     * Create a TimeManagementAjaxHandler instance
     */
    constructor() {
        this.currentYear = this.getYearFromUrl();
        this.currentMonth = this.getMonthFromUrl();

        console.log('📄 TimeManagementAjaxHandler initialized');
        console.log(`Current period: ${this.currentYear}/${this.currentMonth}`);

        this.setupPeriodNavigation();
    }

    /**
     * Get year from URL parameters
     * @returns {number}
     * @private
     */
    getYearFromUrl() {
        const params = new URLSearchParams(window.location.search);
        const year = params.get('year');
        return year ? parseInt(year) : new Date().getFullYear();
    }

    /**
     * Get month from URL parameters
     * @returns {number}
     * @private
     */
    getMonthFromUrl() {
        const params = new URLSearchParams(window.location.search);
        const month = params.get('month');
        return month ? parseInt(month) : new Date().getMonth() + 1;
    }

    /**
     * Setup period navigation form to use AJAX
     * @private
     */
    setupPeriodNavigation() {
        // Find the period navigation form
        const periodForm = document.querySelector('form[action*="/user/time-management"]');

        if (!periodForm) {
            console.warn('⚠️ Period navigation form not found');
            return;
        }

        console.log('✅ Period navigation form found, attaching AJAX handler');

        // Override the form's submit handler
        periodForm.addEventListener('submit', (e) => {
            e.preventDefault();
            console.log('📅 Period navigation intercepted');

            const formData = new FormData(periodForm);
            const year = formData.get('year');
            const month = formData.get('month');

            if (year && month) {
                this.loadPeriod(parseInt(year), parseInt(month));
            }
        });
    }

    /**
     * Load a specific period via AJAX
     * @param {number} year - Year to load
     * @param {number} month - Month to load (1-12)
     * @public
     */
    async loadPeriod(year, month) {
        console.log(`🔄 Loading period: ${year}/${month}`);

        try {
            this.showLoading();

            // Fetch the fragment for the new period
            const response = await fetch(`/user/time-management/fragment?year=${year}&month=${month}`);

            if (!response.ok) {
                throw new Error(`Server returned ${response.status}: ${response.statusText}`);
            }

            const html = await response.text();

            // Parse the HTML to check for errors
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, 'text/html');

            // Check for error message
            const errorAlert = doc.querySelector('.alert-danger');
            if (errorAlert) {
                const errorMessage = errorAlert.textContent.trim();
                ToastNotification.error('Error', errorMessage);
                return;
            }

            // Update the content
            await this.updateFragmentContent(html);

            // Update URL without page reload
            const newUrl = `/user/time-management?year=${year}&month=${month}`;
            window.history.pushState({ year, month }, '', newUrl);

            // Update current period
            this.currentYear = year;
            this.currentMonth = month;

            console.log(`✅ Period loaded successfully: ${year}/${month}`);

        } catch (error) {
            console.error('❌ Error loading period:', error);
            ToastNotification.error('Load Failed', error.message || 'Failed to load period data');
        } finally {
            this.hideLoading();
        }
    }

    /**
     * Update fragment content in the DOM
     * @param {string} html - HTML content from server
     * @private
     */
    async updateFragmentContent(html) {
        const contentContainer = document.getElementById('timeManagementContent');

        if (!contentContainer) {
            console.error('❌ Content container #timeManagementContent not found');
            return;
        }

        // Replace the content
        contentContainer.innerHTML = html;

        // Re-initialize all time management modules for the new content
        await this.reinitializeModules();

        console.log('✅ Fragment content updated');
    }

    /**
     * Reinitialize all time management modules after content update
     * @private
     */
    async reinitializeModules() {
        console.log('🔧 Reinitializing time management modules...');

        // Wait for next animation frame to ensure DOM is ready
        await new Promise(resolve => requestAnimationFrame(resolve));

        // Re-attach period navigation handler to the new form
        this.setupPeriodNavigation();

        // Reinitialize TimeOffManagement module
        if (window.TimeOffManagementModule) {
            // Reset state and reinitialize
            window.TimeOffManagementModule.state.isInitialized = false;
            window.TimeOffManagementModule.initialize();
            console.log('✅ TimeOffManagement reinitialized');
        }

        // Reinitialize InlineEditing module
        if (window.InlineEditingModule) {
            // Reset state and reinitialize
            window.InlineEditingModule.state.isInitialized = false;
            window.InlineEditingModule.initialize();
            console.log('✅ InlineEditing reinitialized');
        }

        // Reinitialize HolidayRequestModal
        if (window.HolidayRequestModalModule) {
            console.log('✅ HolidayRequestModal available');
        }

        // Reinitialize other modules as needed
        if (window.StatusDisplayModule) {
            if (window.StatusDisplayModule.state) {
                window.StatusDisplayModule.state.isInitialized = false;
            }
            window.StatusDisplayModule.initialize();
            console.log('✅ StatusDisplay reinitialized');
        }

        if (window.WorkTimeDisplayModule) {
            if (window.WorkTimeDisplayModule.state) {
                window.WorkTimeDisplayModule.state.isInitialized = false;
            }
            window.WorkTimeDisplayModule.initialize();
            console.log('✅ WorkTimeDisplay reinitialized');
        }

        if (window.PeriodNavigationModule) {
            if (window.PeriodNavigationModule.state) {
                window.PeriodNavigationModule.state.isInitialized = false;
            }
            window.PeriodNavigationModule.initialize();
            console.log('✅ PeriodNavigation reinitialized');
        }

        console.log('✅ All modules reinitialized');
    }

    /**
     * Reload current period (refresh data)
     * @public
     */
    async reloadCurrentPeriod() {
        console.log('🔄 Reloading current period...');
        await this.loadPeriod(this.currentYear, this.currentMonth);
    }

    /**
     * Show loading overlay
     * @private
     */
    showLoading() {
        // Remove existing overlay if any
        this.hideLoading();

        const overlay = document.createElement('div');
        overlay.id = 'tmLoadingOverlay';
        overlay.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.5);
            display: flex;
            justify-content: center;
            align-items: center;
            z-index: 9999;
        `;

        overlay.innerHTML = `
            <div class="text-center">
                <div class="spinner-border text-light" role="status" style="width: 3rem; height: 3rem;">
                    <span class="visually-hidden">Loading...</span>
                </div>
                <div class="text-light mt-3">Loading period data...</div>
            </div>
        `;

        document.body.appendChild(overlay);
    }

    /**
     * Hide loading overlay
     * @private
     */
    hideLoading() {
        const overlay = document.getElementById('tmLoadingOverlay');
        if (overlay) {
            overlay.style.opacity = '0';
            overlay.style.transition = 'opacity 0.2s';

            setTimeout(() => {
                overlay.remove();
            }, 200);
        }
    }

    /**
     * Navigate to next month
     * @public
     */
    nextMonth() {
        let year = this.currentYear;
        let month = this.currentMonth + 1;

        if (month > 12) {
            month = 1;
            year++;
        }

        this.loadPeriod(year, month);
    }

    /**
     * Navigate to previous month
     * @public
     */
    previousMonth() {
        let year = this.currentYear;
        let month = this.currentMonth - 1;

        if (month < 1) {
            month = 12;
            year--;
        }

        this.loadPeriod(year, month);
    }

    /**
     * Get current period info
     * @returns {{year: number, month: number}}
     * @public
     */
    getCurrentPeriod() {
        return {
            year: this.currentYear,
            month: this.currentMonth
        };
    }
}
