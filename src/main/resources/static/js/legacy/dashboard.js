// REPLACE your entire dashboard.js content with this enhanced version:

/**
 * Enhanced Dashboard Auto-Refresh System
 * Provides progressive loading: immediate display → local data → fresh network data
 */
class DashboardAutoRefresh {
    constructor() {
        this.isMonitoring = false;
        this.refreshInterval = null;
        this.checkInterval = 5000; // Check every 5 seconds
        this.maxChecks = 24; // Stop after 2 minutes
        this.checkCount = 0;

        console.log('Enhanced Dashboard Auto-Refresh System initialized');
        this.init();
    }

    init() {
        // Start monitoring for cache refresh completion
        this.startMonitoring();

        // Add manual refresh button
        this.addRefreshButton();

        // Initial status check
        this.checkCacheStatus();
    }

    /**
     * Start monitoring cache refresh status
     */
    startMonitoring() {
        if (this.isMonitoring) return;

        this.isMonitoring = true;
        this.checkCount = 0;

        console.log('Monitoring cache refresh status...');
        this.showRefreshIndicator();

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

    /**
     * Show refresh indicator
     */
    showRefreshIndicator() {
        const indicator = this.getOrCreateIndicator();
        indicator.innerHTML = `
            <div class="alert alert-info alert-dismissible fade show" role="alert">
                <i class="bi bi-arrow-clockwise spinning me-2"></i>
                <strong>Updating...</strong> Loading fresh user data in background.
                <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
            </div>
        `;
        indicator.style.display = 'block';
    }

    /**
     * Hide refresh indicator
     */
    hideRefreshIndicator() {
        const indicator = document.getElementById('refresh-indicator');
        if (indicator) {
            indicator.style.display = 'none';
        }
    }

    /**
     * Show success message
     */
    showSuccessMessage() {
        const indicator = this.getOrCreateIndicator();
        indicator.innerHTML = `
            <div class="alert alert-success alert-dismissible fade show" role="alert">
                <i class="bi bi-check-circle me-2"></i>
                <strong>Updated!</strong> Dashboard metrics refreshed with latest data.
                <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
            </div>
        `;

        // Auto-hide after 5 seconds
        setTimeout(() => {
            this.hideRefreshIndicator();
        }, 5000);
    }

    /**
     * Show timeout message
     */
    showTimeoutMessage() {
        const indicator = this.getOrCreateIndicator();
        indicator.innerHTML = `
            <div class="alert alert-warning alert-dismissible fade show" role="alert">
                <i class="bi bi-exclamation-triangle me-2"></i>
                <strong>Timeout</strong> Refresh is taking longer than expected.
                <button type="button" class="btn btn-sm btn-outline-primary ms-2" onclick="dashboardRefresh.startMonitoring()">
                    Try Again
                </button>
                <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
            </div>
        `;
    }

    /**
     * Show error message
     */
    showErrorMessage() {
        const indicator = this.getOrCreateIndicator();
        indicator.innerHTML = `
            <div class="alert alert-danger alert-dismissible fade show" role="alert">
                <i class="bi bi-exclamation-circle me-2"></i>
                <strong>Error</strong> Unable to check refresh status.
                <button type="button" class="btn btn-sm btn-outline-primary ms-2" onclick="location.reload()">
                    Reload Page
                </button>
                <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
            </div>
        `;
    }

    /**
     * Get or create refresh indicator element
     */
    getOrCreateIndicator() {
        let indicator = document.getElementById('refresh-indicator');
        if (!indicator) {
            indicator = document.createElement('div');
            indicator.id = 'refresh-indicator';
            indicator.style.position = 'fixed';
            indicator.style.top = '20px';
            indicator.style.right = '20px';
            indicator.style.zIndex = '9999';
            indicator.style.maxWidth = '400px';
            document.body.appendChild(indicator);
        }
        return indicator;
    }

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
}

// CSS for spinning animation
const style = document.createElement('style');
style.textContent = `
    .spinning {
        animation: spin 1s linear infinite;
    }
    @keyframes spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
    }
    #refresh-indicator .alert {
        margin-bottom: 0;
        box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
    }
`;
document.head.appendChild(style);

// Initialize auto-refresh when page loads
let dashboardRefresh;
document.addEventListener('DOMContentLoaded', function() {
    dashboardRefresh = new DashboardAutoRefresh();

    // Legacy support: keep the 5-minute reload for pages with data-refresh="true"
    if (document.querySelector('[data-refresh="true"]')) {
        console.log('Legacy 5-minute auto-reload enabled');
        setInterval(function() {
            location.reload();
        }, 300000); // 5 minutes
    }
});