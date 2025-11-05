/**
 * WorktimeDataService.js
 *
 * Handles all data operations for admin worktime management.
 * Manages AJAX submissions, data fetching, and API communication.
 *
 * @module features/worktime/admin/WorktimeDataService
 */

import { API } from '../../../core/api.js';

/**
 * WorktimeDataService class
 * Manages data operations and API communication
 */
export class WorktimeDataService {
    /**
     * Submit worktime update to server
     * @param {HTMLElement} cell - Cell element with data attributes
     * @param {string} value - Worktime value
     * @returns {Promise<boolean>} True if successful
     */
    async submitWorktimeUpdate(cell, value) {
        const userId = cell.dataset.userId;
        const [year, month, day] = cell.dataset.date.split('-');

        if (!userId || !year || !month || !day) {
            console.error('Invalid cell data:', cell.dataset);
            alert('Error: Invalid cell data');
            return false;
        }

        // Get current view period BEFORE making the request
        const currentViewPeriod = this.getCurrentViewPeriod();

        console.log('Submitting worktime update:', {
            userId, year, month, day, value,
            currentView: currentViewPeriod
        });

        try {
            // Use postForm from API wrapper (handles CSRF automatically)
            const response = await API.postForm('/admin/worktime/update', {
                userId: userId,
                year: year,
                month: month,
                day: day,
                value: value
            });

            console.log('Update successful');

            // Maintain current view and reload data
            setTimeout(() => {
                console.log('Refreshing current view:', currentViewPeriod);
                // Redirect to the CURRENT view (not the entry's month)
                window.location.href = `/admin/worktime?year=${currentViewPeriod.year}&month=${currentViewPeriod.month}`;
            }, 1000);

            return true;

        } catch (error) {
            console.error('Update failed:', error);
            alert('Failed to update worktime: ' + (error.message || 'Unknown error'));
            return false;
        }
    }

    /**
     * Fetch entry data from server
     * @param {string} userId - User ID
     * @param {string} date - Date string (YYYY-MM-DD)
     * @returns {Promise<Object>} Entry data
     */
    async fetchEntryData(userId, date) {
        const [year, month, day] = date.split('-');

        try {
            const data = await API.get('/admin/worktime/entry-details', {
                userId,
                year,
                month,
                day
            });

            return data;
        } catch (error) {
            console.warn('API call failed:', error);
            throw error; // Let caller handle fallback
        }
    }

    /**
     * Get current view period from form selectors
     * @returns {Object} Current year and month
     */
    getCurrentViewPeriod() {
        const yearSelect = document.getElementById('yearSelect');
        const monthSelect = document.getElementById('monthSelect');

        // Get values from the actual form controls (not from URL or entry data)
        const currentYear = yearSelect ? yearSelect.value : new Date().getFullYear();
        const currentMonth = monthSelect ? monthSelect.value : new Date().getMonth() + 1;

        console.log('Current view period determined:', { year: currentYear, month: currentMonth });

        return {
            year: currentYear,
            month: currentMonth
        };
    }
}
