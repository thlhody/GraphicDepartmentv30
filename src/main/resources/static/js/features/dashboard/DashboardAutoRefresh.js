/**
 * DashboardAutoRefresh.js
 *
 * Enhanced Dashboard Auto-Refresh System
 * Provides progressive loading: immediate display → local data → fresh network data
 * Monitors cache refresh completion and updates dashboard metrics automatically.
 *
 * @module features/dashboard/DashboardAutoRefresh
 */

/**
 * DashboardAutoRefresh class
 * Manages automatic dashboard metric refresh and cache monitoring
 */
export class DashboardAutoRefresh {
    constructor() {
        this.isMonitoring = false;
        this.refreshInterval = null;
        this.checkInterval = 5000; // Check every 5 seconds
        this.maxChecks = 24; // Stop after 2 minutes
        this.checkCount = 0;
        this.currentToastId = null; // Track current toast ID for dismissal
        this.hasShownInitialNotification = false; // Track if we've shown notification on first load

        console.log('Enhanced Dashboard Auto-Refresh System initialized');
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize dashboard auto-refresh
     */
    init() {
        // Start monitoring for cache refresh completion
        this.startMonitoring();

        // Add manual refresh button
        this.addRefreshButton();

        // Initial status check
        this.checkCacheStatus();
    }

    // ========================================================================
    // MONITORING
    // ========================================================================

    /**
     * Start monitoring cache refresh status
     */
    startMonitoring() {
        if (this.isMonitoring) return;

        this.isMonitoring = true;
        this.checkCount = 0;

        console.log('Monitoring cache refresh status...');

        // Only show notification on first load (after login)
        if (!this.hasShownInitialNotification) {
            this.showRefreshIndicator();
            this.hasShownInitialNotification = true;
        }

        this.refreshInterval = setInterval(() => {
            this.checkCacheStatus();
        }, this.checkInterval);
    }

    /**
     * Stop monitoring
     */
    stopMonitoring() {
        if (!this.isMonitoring) return;

        this.isMonitoring = false;

        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
            this.refreshInterval = null;
        }

        this.hideRefreshIndicator();
        console.log('Cache refresh monitoring stopped');
    }

    /**
     * Check cache refresh status
     */
    async checkCacheStatus() {
        try {
            this.checkCount++;

            const response = await fetch('/api/cache/status');
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            const status = await response.json();

            console.log(`Cache check ${this.checkCount}: ${status.status} (${status.userCount} users, refreshing: ${status.isRefreshing})`);

            if (!status.isRefreshing && status.status === 'READY') {
                // Cache refresh completed!
                console.log('Cache refresh completed - updating dashboard metrics');
                this.stopMonitoring();
                await this.refreshDashboardMetrics();
                this.showSuccessMessage();
            } else if (this.checkCount >= this.maxChecks) {
                // Stop after max checks
                console.log('Max refresh checks reached - stopping monitoring');
                this.stopMonitoring();
                this.showTimeoutMessage();
            }

        } catch (error) {
            console.error('Error checking cache status:', error);

            // Stop monitoring on repeated errors
            if (this.checkCount >= 5) {
                console.log('Too many errors, stopping monitoring');
                this.stopMonitoring();
                this.showErrorMessage();
            }
        }
    }

    // ========================================================================
    // METRICS REFRESH
    // ========================================================================

    /**
     * Refresh dashboard metrics with fresh data
     */
    async refreshDashboardMetrics() {
        try {
            const response = await fetch('/api/cache/metrics');
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            const metrics = await response.json();
            console.log('Fresh metrics received:', metrics);

            // Update metrics in the UI
            this.updateMetricsInDOM(metrics);

        } catch (error) {
            console.error('Error refreshing dashboard metrics:', error);
        }
    }

    /**
     * Update metrics in the DOM
     */
    updateMetricsInDOM(metrics) {
        try {
            // Update online users - look for the metric container
            const onlineElement = this.findMetricElement('Online Users');
            if (onlineElement) {
                onlineElement.textContent = metrics.onlineUsers;
                console.log('Updated online users to:', metrics.onlineUsers);
            }

            // Update active users
            const activeElement = this.findMetricElement('Active Users');
            if (activeElement) {
                activeElement.textContent = metrics.activeUsers;
                console.log('Updated active users to:', metrics.activeUsers);
            }

            // Update last update time
            const lastUpdateElement = this.findMetricElement('Last Update');
            if (lastUpdateElement) {
                const now = new Date();
                lastUpdateElement.textContent = now.toLocaleTimeString();
                console.log('Updated last update time');
            }

        } catch (error) {
            console.error('Error updating metrics in DOM:', error);
        }
    }

    /**
     * Find metric element by label text
     */
    findMetricElement(labelText) {
        const labels = document.querySelectorAll('.text-muted');
        for (const label of labels) {
            if (label.textContent.trim() === labelText) {
                const valueElement = label.nextElementSibling;
                if (valueElement && valueElement.classList.contains('h5')) {
                    return valueElement;
                }
            }
        }
        return null;
    }

    // ========================================================================
    // UI INDICATORS
    // ========================================================================

    /**
     * Show refresh indicator using ToastNotification
     */
    showRefreshIndicator() {
        // Check if ToastNotification is available
        if (!window.ToastNotification) {
            console.warn('ToastNotification not available yet, skipping indicator');
            return;
        }

        // Hide any existing toast first
        if (this.currentToastId) {
            window.ToastNotification.hide(this.currentToastId);
        }

        // Show new persistent toast
        this.currentToastId = window.ToastNotification.info(
            'Loading Dashboard',
            'Fetching latest user data...',
            {
                persistent: true,
                icon: 'bi-arrow-clockwise'
            }
        );
    }

    /**
     * Hide refresh indicator
     */
    hideRefreshIndicator() {
        if (this.currentToastId && window.ToastNotification) {
            window.ToastNotification.hide(this.currentToastId);
            this.currentToastId = null;
        }
    }

    /**
     * Show success message using ToastNotification
     */
    showSuccessMessage() {
        // Dismiss the updating toast
        this.hideRefreshIndicator();

        // Show success toast (auto-dismisses after 5 seconds by default)
        if (window.ToastNotification) {
            window.ToastNotification.success(
                'Dashboard Ready',
                'All metrics loaded successfully'
            );
        }
    }

    /**
     * Show timeout message using ToastNotification
     */
    showTimeoutMessage() {
        // Dismiss the updating toast
        this.hideRefreshIndicator();

        // Show warning toast
        if (window.ToastNotification) {
            window.ToastNotification.warning(
                'Still Loading',
                'Dashboard refresh is taking longer than expected',
                { duration: 8000 }
            );
        }
    }

    /**
     * Show error message using ToastNotification
     */
    showErrorMessage() {
        // Dismiss the updating toast
        this.hideRefreshIndicator();

        // Show error toast
        if (window.ToastNotification) {
            window.ToastNotification.error(
                'Load Failed',
                'Unable to fetch dashboard data. Please refresh the page.',
                { duration: 8000 }
            );
        }
    }

    // ========================================================================
    // MANUAL REFRESH
    // ========================================================================

    /**
     * Add manual refresh button to dashboard
     */
    addRefreshButton() {
        try {
            const headerTitle = document.querySelector('h1.h2, h2.h3');
            if (headerTitle) {
                const refreshBtn = document.createElement('button');
                refreshBtn.className = 'btn btn-outline-primary btn-sm ms-3';
                refreshBtn.innerHTML = '<i class="bi bi-arrow-clockwise me-1"></i> Refresh';
                refreshBtn.onclick = () => this.manualRefresh();
                refreshBtn.title = 'Refresh dashboard metrics';

                headerTitle.parentNode.appendChild(refreshBtn);
                console.log('Manual refresh button added');
            }
        } catch (error) {
            console.error('Error adding refresh button:', error);
        }
    }

    /**
     * Manual refresh trigger
     */
    async manualRefresh() {
        console.log('Manual refresh triggered');
        try {
            await this.refreshDashboardMetrics();
            this.showSuccessMessage();
        } catch (error) {
            console.error('Manual refresh failed:', error);
            this.showErrorMessage();
        }
    }

    // ========================================================================
    // LEGACY SUPPORT
    // ========================================================================

    /**
     * Enable legacy 5-minute auto-reload
     */
    static enableLegacyAutoReload() {
        if (document.querySelector('[data-refresh="true"]')) {
            console.log('Legacy 5-minute auto-reload enabled');
            setInterval(function() {
                location.reload();
            }, 300000); // 5 minutes
        }
    }
}
