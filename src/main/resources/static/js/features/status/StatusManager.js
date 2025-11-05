/**
 * StatusManager.js
 *
 * Manages user status page functionality including auto-refresh,
 * date formatting, and online user counting.
 *
 * @module features/status/StatusManager
 */

/**
 * StatusManager class
 * Handles status page updates and formatting
 */
export class StatusManager {
    constructor() {
        this.autoRefreshInterval = 60000; // 1 minute
        this.refreshTimer = null;

        console.log('StatusManager initialized');
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize status manager
     */
    initialize() {
        console.log('🚀 Initializing Status Manager...');

        // Initial setup
        this.updateOnlineCount();
        this.updateLastRefresh();
        this.formatDateDisplays();

        // Set up manual refresh button
        this.setupRefreshButton();

        // Setup auto-refresh every 60 seconds
        this.startAutoRefresh();

        console.log('✅ Status Manager initialized successfully');
    }

    // ========================================================================
    // AUTO-REFRESH
    // ========================================================================

    /**
     * Start auto-refresh timer
     */
    startAutoRefresh() {
        if (this.refreshTimer) {
            clearInterval(this.refreshTimer);
        }

        this.refreshTimer = setInterval(() => {
            this.autoRefreshStatus();
        }, this.autoRefreshInterval);

        console.log(`Auto-refresh started (every ${this.autoRefreshInterval / 1000}s)`);
    }

    /**
     * Stop auto-refresh timer
     */
    stopAutoRefresh() {
        if (this.refreshTimer) {
            clearInterval(this.refreshTimer);
            this.refreshTimer = null;
            console.log('Auto-refresh stopped');
        }
    }

    // ========================================================================
    // REFRESH BUTTON
    // ========================================================================

    /**
     * Setup event listener for refresh button
     */
    setupRefreshButton() {
        const refreshButton = document.querySelector('a[href*="/status/refresh"]');
        if (refreshButton) {
            refreshButton.addEventListener('click', (e) => {
                e.preventDefault();
                this.autoRefreshStatus();
                return false;
            });
        }
    }

    // ========================================================================
    // ONLINE COUNT
    // ========================================================================

    /**
     * Update online user count based on table data
     */
    updateOnlineCount() {
        const statusSpans = document.querySelectorAll('table tbody tr td span.badge span.ms-1');
        let onlineCount = 0;

        statusSpans.forEach(span => {
            if (span.textContent.trim() === 'Online') {
                onlineCount++;
            }
        });

        const onlineCountElement = document.getElementById('onlineCount');
        if (onlineCountElement) {
            onlineCountElement.textContent = onlineCount;
        }
    }

    // ========================================================================
    // TIMESTAMP FORMATTING
    // ========================================================================

    /**
     * Update last refresh timestamp
     */
    updateLastRefresh() {
        const now = new Date();
        const formattedDateTime = this.formatDateTime(now);

        const lastUpdateElement = document.getElementById('lastUpdate');
        if (lastUpdateElement) {
            lastUpdateElement.innerHTML = formattedDateTime;
        }
    }

    /**
     * Format date and time
     * @param {Date} date - Date to format
     * @returns {string} Formatted date string
     */
    formatDateTime(date) {
        // Get day of week
        const daysOfWeek = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
        const dayName = daysOfWeek[date.getDay()];

        // Format the date as DD/MM/YYYY
        const day = String(date.getDate()).padStart(2, '0');
        const month = String(date.getMonth() + 1).padStart(2, '0'); // January is 0
        const year = date.getFullYear();

        // Format the time
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');

        // Combine into Day :: DD/MM/YYYY :: HH:MM format with bold time
        return `${dayName} :: ${day}/${month}/${year} :: <strong>${hours}:${minutes}</strong>`;
    }

    /**
     * Format all date displays on the page
     */
    formatDateDisplays() {
        document.querySelectorAll('.date-display').forEach(element => {
            const timestamp = element.textContent.trim();

            // Skip if it's "Never" or other non-date value
            if (timestamp === "Never" || !timestamp.includes('-')) {
                return;
            }

            try {
                // Parse timestamp (expected format: yyyy-MM-dd HH:mm:ss)
                const parts = timestamp.split(' ');
                if (parts.length !== 2) return;

                const dateParts = parts[0].split('-');
                const timeParts = parts[1].split(':');

                if (dateParts.length !== 3 || timeParts.length < 2) return;

                const year = parseInt(dateParts[0]);
                const month = parseInt(dateParts[1]) - 1; // Months are 0-indexed in JS
                const day = parseInt(dateParts[2]);
                const hours = parseInt(timeParts[0]);
                const minutes = parseInt(timeParts[1]);

                const date = new Date(year, month, day, hours, minutes);

                // Format the date
                const formattedDate = this.formatDateTime(date);

                // Update the element
                element.innerHTML = formattedDate;

            } catch (error) {
                console.error("Error formatting date:", error);
            }
        });
    }

    // ========================================================================
    // AJAX REFRESH
    // ========================================================================

    /**
     * Refresh status data via AJAX
     */
    async autoRefreshStatus() {
        // Show loading indicator
        const refreshButton = document.querySelector('a[href*="/status/refresh"]');
        if (refreshButton) {
            refreshButton.innerHTML = '<i class="bi bi-arrow-clockwise me-1 spin"></i> Refreshing...';
            refreshButton.classList.add('disabled');
        }

        // Construct the absolute URL
        const ajaxUrl = window.location.origin + '/status/ajax-refresh';

        try {
            // Make AJAX request to get fresh status data
            const response = await fetch(ajaxUrl, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                credentials: 'same-origin'
            });

            if (!response.ok) {
                throw new Error(`Network response error: ${response.status} ${response.statusText}`);
            }

            const data = await response.json();

            // Update the online count
            const onlineCountElement = document.getElementById('onlineCount');
            if (onlineCountElement && data.onlineCount !== undefined) {
                onlineCountElement.textContent = data.onlineCount;
            }

            // Update the table body with new HTML
            if (data.tableHtml) {
                const tableBody = document.querySelector('table tbody');
                if (tableBody) {
                    tableBody.innerHTML = data.tableHtml;
                }
            }

            // Update last refresh time
            this.updateLastRefresh();

            // Format dates again (since we replaced the DOM elements)
            this.formatDateDisplays();

            // Restore refresh button
            if (refreshButton) {
                refreshButton.innerHTML = '<i class="bi bi-arrow-clockwise me-1"></i> Refresh';
                refreshButton.classList.remove('disabled');
            }

        } catch (error) {
            console.error('Error refreshing status data:', error);

            // Restore refresh button on error with visual feedback
            if (refreshButton) {
                refreshButton.innerHTML = '<i class="bi bi-arrow-clockwise me-1"></i> Refresh';
                refreshButton.classList.remove('disabled');
                refreshButton.classList.add('btn-danger');

                // Show error message
                const errorMessage = `Status refresh failed: ${error.message}`;
                console.error(errorMessage);

                // Reset button style after a delay
                setTimeout(() => {
                    refreshButton.classList.remove('btn-danger');
                    refreshButton.classList.add('btn-outline-secondary');
                }, 2000);
            }
        }
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Cleanup and destroy
     */
    destroy() {
        this.stopAutoRefresh();
        console.log('StatusManager destroyed');
    }
}
