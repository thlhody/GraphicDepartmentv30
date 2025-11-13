/**
 * SessionAutoRefresh.js
 *
 * Manages auto-refresh of session status data without full page reload.
 * Similar to StatusManager but specifically for session page.
 *
 * @module features/session/SessionAutoRefresh
 */

/**
 * SessionAutoRefresh class
 * Handles AJAX-based auto-refresh of session status
 */
export class SessionAutoRefresh {
    constructor() {
        this.autoRefreshInterval = 60000; // 60 seconds (1 minute)
        this.refreshTimer = null;

        console.log('SessionAutoRefresh initialized');
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize auto-refresh
     */
    initialize() {
        console.log('🔄 Initializing Session Auto-Refresh...');

        // Start auto-refresh timer
        this.startAutoRefresh();

        console.log('✅ Session Auto-Refresh initialized successfully');
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
            this.refreshSessionData();
        }, this.autoRefreshInterval);

        console.log(`📅 Session auto-refresh started (every ${this.autoRefreshInterval / 1000}s)`);
    }

    /**
     * Stop auto-refresh timer
     */
    stopAutoRefresh() {
        if (this.refreshTimer) {
            clearInterval(this.refreshTimer);
            this.refreshTimer = null;
            console.log('Session auto-refresh stopped');
        }
    }

    // ========================================================================
    // AJAX REFRESH
    // ========================================================================

    /**
     * Refresh session data via AJAX
     */
    async refreshSessionData() {
        const ajaxUrl = window.location.origin + '/user/session/ajax-status';

        try {
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

            // Update session status badge
            this.updateSessionStatus(data);

            // Update session duration if active
            if (data.sessionDuration) {
                this.updateSessionDuration(data.sessionDuration);
            }

            // Update completed session count
            if (data.completedSessionToday !== undefined) {
                this.updateCompletedSessionIndicator(data.completedSessionToday);
            }

            console.log('✅ Session data refreshed');

        } catch (error) {
            console.error('❌ Error refreshing session data:', error);
            // Don't show errors to user - just log them
            // The page is still functional, just not auto-updating
        }
    }

    // ========================================================================
    // UI UPDATES
    // ========================================================================

    /**
     * Update session status badge
     * @param {Object} data - Session data from server
     */
    updateSessionStatus(data) {
        const statusBadge = document.querySelector('.page-header .badge span:last-child');
        const statusIcon = document.querySelector('.page-header .badge i');

        if (!statusBadge || !data.sessionStatus) return;

        // Update status text
        statusBadge.textContent = data.sessionStatus;

        // Update badge colors based on status
        const badge = statusBadge.closest('.badge');
        if (!badge) return;

        // Remove all status classes
        badge.classList.remove('bg-success', 'bg-warning', 'bg-secondary', 'status-active');

        // Add appropriate class based on status
        switch (data.sessionStatus) {
            case 'Online':
                badge.classList.add('bg-success', 'status-active');
                if (statusIcon) {
                    statusIcon.classList.add('status-online');
                }
                break;
            case 'Temp Stop':
                badge.classList.add('bg-warning');
                if (statusIcon) {
                    statusIcon.classList.remove('status-online');
                }
                break;
            case 'Offline':
            default:
                badge.classList.add('bg-secondary');
                if (statusIcon) {
                    statusIcon.classList.remove('status-online');
                }
                break;
        }
    }

    /**
     * Update session duration display
     * @param {string} duration - Formatted duration string
     */
    updateSessionDuration(duration) {
        // Find duration display elements (could be in floating card or main section)
        const durationElements = document.querySelectorAll('[id*="duration"], [class*="duration"]');

        durationElements.forEach(element => {
            // Only update if it looks like a duration display
            if (element.textContent.includes(':') || element.textContent.includes('hour')) {
                element.textContent = duration;
            }
        });
    }

    /**
     * Update completed session indicator
     * @param {boolean} completed - Whether session was completed today
     */
    updateCompletedSessionIndicator(completed) {
        // Update button states if session was completed
        const startButton = document.querySelector('button[formaction*="start"]');
        if (startButton && completed) {
            startButton.disabled = true;
            startButton.title = 'You already completed a session today';
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
        console.log('SessionAutoRefresh destroyed');
    }
}
