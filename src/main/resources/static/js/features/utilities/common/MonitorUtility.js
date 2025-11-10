/**
 * MonitorUtility.js (ES6 Module)
 *
 * Modern cache and system monitoring utility for user self-service operations.
 * Handles cache status, validation, refresh, and data verification.
 *
 * @module features/utilities/common/MonitorUtility
 */

import { API } from '../../../core/api.js';
import { ToastNotification } from '../../../components/ToastNotification.js';

/**
 * MonitorUtility class
 * Manages cache monitoring and operations with modern ES6 patterns
 */
export class MonitorUtility {
    constructor() {
        this.elements = {
            // Button elements
            viewStatusBtn: null,
            validateBtn: null,
            refreshBtn: null,
            checkDataBtn: null,
            userCountBtn: null,
            clearBtn: null,

            // Display elements
            resultsContainer: null,
            content: null,
            controls: null,

            // Stats elements
            statusIndicator: null,
            cachedUsersCount: null,
            lastCheckTime: null,
            healthIcon: null,
            healthText: null,
            healthDetails: null
        };

        this.autoRefreshInterval = null;

        console.log('🔧 Monitor utility created (ES6)');
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize the monitor utility
     */
    initialize() {
        console.log('🚀 Initializing Monitor utility...');

        // Cache DOM elements
        this.cacheElements();

        // Setup event listeners
        this.setupEventListeners();

        // Load initial cache overview
        setTimeout(() => {
            this.loadCacheOverview();
        }, 1000);

        // Setup auto-refresh
        this.setupAutoRefresh();

        console.log('✅ Monitor utility initialized');
    }

    /**
     * Cache DOM elements for better performance
     */
    cacheElements() {
        // Button elements
        this.elements.viewStatusBtn = document.getElementById('view-cache-status-btn');
        this.elements.validateBtn = document.getElementById('validate-cache-btn');
        this.elements.refreshBtn = document.getElementById('refresh-cache-btn');
        this.elements.checkDataBtn = document.getElementById('check-user-data-btn');
        this.elements.userCountBtn = document.getElementById('user-count-btn');
        this.elements.clearBtn = document.getElementById('clear-monitor-results');

        // Display elements
        this.elements.resultsContainer = document.getElementById('monitor-results');
        this.elements.content = document.getElementById('monitor-content');
        this.elements.controls = document.getElementById('monitor-controls');

        // Stats elements
        this.elements.statusIndicator = document.getElementById('cache-status-indicator');
        this.elements.cachedUsersCount = document.getElementById('cached-users-count');
        this.elements.lastCheckTime = document.getElementById('last-check-time');
        this.elements.healthIcon = document.getElementById('cache-health-icon');
        this.elements.healthText = document.getElementById('cache-health-text');
        this.elements.healthDetails = document.getElementById('cache-health-details');
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Collapse toggle animation
        if (this.elements.controls) {
            const monitorUtility = document.querySelector('#monitor-utility');
            const toggleIcon = monitorUtility?.querySelector('.toggle-icon');

            $(this.elements.controls).on('show.bs.collapse', () => {
                toggleIcon?.classList.remove('bi-chevron-down');
                toggleIcon?.classList.add('bi-chevron-up');
            }).on('hide.bs.collapse', () => {
                toggleIcon?.classList.remove('bi-chevron-up');
                toggleIcon?.classList.add('bi-chevron-down');
            });
        }

        // Cache operations
        this.elements.viewStatusBtn?.addEventListener('click', (e) => {
            e.preventDefault();
            this.viewCacheStatus();
        });

        this.elements.validateBtn?.addEventListener('click', (e) => {
            e.preventDefault();
            this.validateCache();
        });

        this.elements.refreshBtn?.addEventListener('click', (e) => {
            e.preventDefault();
            this.refreshCache();
        });

        // Data verification
        this.elements.checkDataBtn?.addEventListener('click', (e) => {
            e.preventDefault();
            this.checkUserData();
        });

        this.elements.userCountBtn?.addEventListener('click', (e) => {
            e.preventDefault();
            this.getUserCount();
        });

        // Clear results
        this.elements.clearBtn?.addEventListener('click', () => {
            this.clearResults();
        });
    }

    /**
     * Setup auto-refresh interval (every 3 minutes)
     */
    setupAutoRefresh() {
        this.autoRefreshInterval = setInterval(() => {
            this.loadCacheOverview();
        }, 180000); // 3 minutes
    }

    /**
     * Cleanup auto-refresh interval
     */
    destroy() {
        if (this.autoRefreshInterval) {
            clearInterval(this.autoRefreshInterval);
        }
    }

    // ========================================================================
    // CACHE OVERVIEW
    // ========================================================================

    /**
     * Load cache overview (silent refresh of stats)
     */
    async loadCacheOverview() {
        try {
            const [userData, userCount] = await Promise.all([
                API.get('/utility/cache/user-data-check'),
                API.get('/utility/cache/user-count')
            ]);

            this.updateCacheStats(userData, userCount);
        } catch (error) {
            console.error('Error loading cache overview:', error);
            // Show fallback data
            this.updateCacheStats(
                { success: false, hasUserData: false, cachedUserCount: 0 },
                { success: false, cachedUserCount: 0 }
            );
        }
    }

    /**
     * Update cache statistics display
     * @param {Object} userData - User data check response
     * @param {Object} userCount - User count response
     */
    updateCacheStats(userData, userCount) {
        const hasData = userData.success && userData.hasUserData;
        const count = (userCount.success ? userCount.cachedUserCount : userData.cachedUserCount) || 0;

        // Update quick stats
        if (this.elements.statusIndicator) {
            this.elements.statusIndicator.textContent = hasData ? 'Healthy' : 'Issues';
            this.elements.statusIndicator.style.color = hasData ? '#28a745' : '#dc3545';
        }

        if (this.elements.cachedUsersCount) {
            this.elements.cachedUsersCount.textContent = count;
        }

        if (this.elements.lastCheckTime) {
            this.elements.lastCheckTime.textContent = this.getCurrentTime();
        }

        // Update cache health overview
        this.updateCacheHealthOverview(hasData, count);
    }

    /**
     * Update cache health overview display
     * @param {boolean} isHealthy - Cache health status
     * @param {number} userCount - Number of cached users
     */
    updateCacheHealthOverview(isHealthy, userCount) {
        if (!this.elements.healthIcon || !this.elements.healthText || !this.elements.healthDetails) {
            return;
        }

        if (isHealthy) {
            this.elements.healthIcon.style.color = '#28a745';
            this.elements.healthText.textContent = `Cache is healthy with ${userCount} user(s) loaded`;
            this.elements.healthDetails.innerHTML = `
                <small class="text-muted">
                    Last checked: ${this.getCurrentTime()} |
                    Status: Operational |
                    Data integrity: Verified
                </small>
            `;
            this.elements.healthDetails.style.display = 'block';
        } else {
            this.elements.healthIcon.style.color = '#dc3545';
            this.elements.healthText.textContent = 'Cache health issues detected';
            this.elements.healthDetails.innerHTML = `
                <small class="text-warning">
                    Issue detected: No user data found |
                    Recommendation: Perform cache refresh
                </small>
            `;
            this.elements.healthDetails.style.display = 'block';
        }
    }

    // ========================================================================
    // CACHE OPERATIONS
    // ========================================================================

    /**
     * View cache status
     */
    async viewCacheStatus() {
        const btn = this.elements.viewStatusBtn;
        this.setButtonLoading(btn, true);

        try {
            const data = await API.get('/utility/cache/status');

            if (data.success) {
                this.displayMonitorResults('Cache Status Report', this.formatCacheStatus(data));
                this.updateQuickStats('Cache Status', 'Loaded');
                this.updateLastCheck();
                ToastNotification.success('Success', 'Cache status retrieved successfully');
            } else {
                ToastNotification.error('Error', data.message || 'Failed to get cache status');
                this.updateQuickStats('Cache Status', 'Error');
            }
        } catch (error) {
            console.error('Cache status error:', error);
            ToastNotification.error('Error', `Failed to get cache status: ${error.message}`);
            this.updateQuickStats('Cache Status', 'Error');
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Validate cache
     */
    async validateCache() {
        const btn = this.elements.validateBtn;
        this.setButtonLoading(btn, true);

        try {
            const data = await API.post('/utility/cache/validate');

            if (data.success) {
                this.displayMonitorResults('Cache Validation Results', this.formatValidationResult(data));
                this.updateQuickStats('Cache Status', 'Valid');
                this.updateLastCheck();
                ToastNotification.success('Success', 'Cache validation completed successfully');

                // Update overview after validation
                setTimeout(() => {
                    this.loadCacheOverview();
                }, 1000);
            } else {
                ToastNotification.error('Error', data.message || 'Cache validation failed');
                this.updateQuickStats('Cache Status', 'Invalid');
            }
        } catch (error) {
            console.error('Cache validation error:', error);
            ToastNotification.error('Error', `Failed to validate cache: ${error.message}`);
            this.updateQuickStats('Cache Status', 'Error');
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Refresh cache
     */
    async refreshCache() {
        if (!confirm('Refresh cache will reload all user data from files. This may take a moment. Continue?')) {
            return;
        }

        const btn = this.elements.refreshBtn;
        this.setButtonLoading(btn, true);

        try {
            const data = await API.post('/utility/cache/refresh');

            if (data.success) {
                this.displayMonitorResults('Cache Refresh Results', this.formatRefreshResult(data));
                this.updateQuickStats('Cache Status', 'Refreshed');
                this.updateLastCheck();
                ToastNotification.success('Success', 'Cache refreshed successfully');

                // Update overview after refresh
                setTimeout(() => {
                    this.loadCacheOverview();
                }, 1000);
            } else {
                ToastNotification.error('Error', data.message || 'Cache refresh failed');
                this.updateQuickStats('Cache Status', 'Refresh Failed');
            }
        } catch (error) {
            console.error('Cache refresh error:', error);
            ToastNotification.error('Error', `Failed to refresh cache: ${error.message}`);
            this.updateQuickStats('Cache Status', 'Error');
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    // ========================================================================
    // DATA VERIFICATION OPERATIONS
    // ========================================================================

    /**
     * Check user data
     */
    async checkUserData() {
        const btn = this.elements.checkDataBtn;
        this.setButtonLoading(btn, true);

        try {
            const data = await API.get('/utility/cache/user-data-check');

            if (data.success) {
                this.displayMonitorResults('User Data Check Results', this.formatUserDataResult(data));
                this.updateQuickStats('User Data', data.hasUserData ? 'Present' : 'Missing');
                this.updateLastCheck();
                ToastNotification.info('Info', data.message || 'User data check completed');

                // Update overview after check
                setTimeout(() => {
                    this.loadCacheOverview();
                }, 500);
            } else {
                ToastNotification.error('Error', data.message || 'User data check failed');
                this.updateQuickStats('User Data', 'Error');
            }
        } catch (error) {
            console.error('User data check error:', error);
            ToastNotification.error('Error', `Failed to check user data: ${error.message}`);
            this.updateQuickStats('User Data', 'Error');
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Get user count
     */
    async getUserCount() {
        const btn = this.elements.userCountBtn;
        this.setButtonLoading(btn, true);

        try {
            const data = await API.get('/utility/cache/user-count');

            if (data.success) {
                this.displayMonitorResults('User Count Report', this.formatUserCountResult(data));
                this.updateQuickStats('User Count', data.cachedUserCount);
                this.updateLastCheck();
                ToastNotification.info('Info', data.message || 'User count retrieved');

                // Update cached users count
                if (this.elements.cachedUsersCount) {
                    this.elements.cachedUsersCount.textContent = data.cachedUserCount || 0;
                }
            } else {
                ToastNotification.error('Error', data.message || 'Failed to get user count');
                this.updateQuickStats('User Count', 'Error');
            }
        } catch (error) {
            console.error('User count error:', error);
            ToastNotification.error('Error', `Failed to get user count: ${error.message}`);
            this.updateQuickStats('User Count', 'Error');
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    // ========================================================================
    // DISPLAY FUNCTIONS
    // ========================================================================

    /**
     * Display monitor results
     * @param {string} title - Result title
     * @param {string} content - Result content HTML
     */
    displayMonitorResults(title, content) {
        if (!this.elements.content) return;

        this.elements.content.innerHTML = `
            <div class="monitor-result">
                <h5>${title}</h5>
                <div class="result-content">
                    ${content}
                </div>
                <div class="result-meta">
                    <small class="text-muted">Retrieved at: ${new Date().toLocaleString()}</small>
                </div>
            </div>
        `;

        if (this.elements.resultsContainer) {
            $(this.elements.resultsContainer).fadeIn();
        }
    }

    /**
     * Format cache status response
     * @param {Object} response - Server response
     * @returns {string} Formatted HTML
     */
    formatCacheStatus(response) {
        return `
            <div class="cache-status-report">
                <div class="status-header">
                    <h6>System Cache Status</h6>
                </div>
                <div class="status-content">
                    <pre class="status-text">${response.cacheStatus}</pre>
                </div>
                <div class="status-summary">
                    <div class="summary-item">
                        <strong>Report Generated:</strong> ${response.timestamp}
                    </div>
                    <div class="summary-item">
                        <strong>Status:</strong> <span class="text-success">Retrieved Successfully</span>
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Format validation result
     * @param {Object} response - Server response
     * @returns {string} Formatted HTML
     */
    formatValidationResult(response) {
        return `
            <div class="validation-result-detailed">
                <div class="alert alert-success">
                    <i class="bi bi-check-circle"></i>
                    <strong>${response.message}</strong>
                </div>

                <div class="validation-details">
                    <h6>Validation Results</h6>
                    <div class="validation-grid">
                        <div class="validation-item">
                            <strong>Has User Data:</strong>
                            <span class="${response.hasUserData ? 'text-success' : 'text-warning'}">
                                ${response.hasUserData ? 'Yes' : 'No'}
                            </span>
                        </div>
                        <div class="validation-item">
                            <strong>Cached Users:</strong>
                            <span class="text-info">${response.cachedUserCount || 0}</span>
                        </div>
                        <div class="validation-item">
                            <strong>Cache Status:</strong>
                            <span class="text-success">Valid</span>
                        </div>
                        <div class="validation-item">
                            <strong>Validation Time:</strong>
                            <span class="text-muted">${response.timestamp}</span>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Format refresh result
     * @param {Object} response - Server response
     * @returns {string} Formatted HTML
     */
    formatRefreshResult(response) {
        const change = (response.afterCount || 0) - (response.beforeCount || 0);
        return `
            <div class="refresh-result-detailed">
                <div class="alert alert-success">
                    <i class="bi bi-arrow-repeat"></i>
                    <strong>Cache refresh completed successfully</strong>
                </div>

                <div class="refresh-statistics">
                    <h6>Refresh Statistics</h6>
                    <div class="stats-grid">
                        <div class="stat-card">
                            <div class="stat-label">Before Refresh</div>
                            <div class="stat-value">${response.beforeCount || 0} users</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-label">After Refresh</div>
                            <div class="stat-value">${response.afterCount || 0} users</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-label">Change</div>
                            <div class="stat-value ${change >= 0 ? 'text-success' : 'text-warning'}">
                                ${change >= 0 ? '+' : ''}${change}
                            </div>
                        </div>
                    </div>
                </div>

                <div class="refresh-details">
                    <div class="detail-item">
                        <strong>Refresh Time:</strong> ${response.timestamp}
                    </div>
                    <div class="detail-item">
                        <strong>Status:</strong> <span class="text-success">Completed Successfully</span>
                    </div>
                    <div class="detail-item">
                        <strong>Data Source:</strong> User Data Service
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Format user data result
     * @param {Object} response - Server response
     * @returns {string} Formatted HTML
     */
    formatUserDataResult(response) {
        return `
            <div class="user-data-result">
                <div class="data-status ${response.hasUserData ? 'alert-success' : 'alert-warning'}">
                    <i class="bi ${response.hasUserData ? 'bi-check-circle' : 'bi-exclamation-triangle'}"></i>
                    <strong>${response.message}</strong>
                </div>

                <div class="data-analysis">
                    <h6>Data Analysis</h6>
                    <div class="analysis-grid">
                        <div class="analysis-item">
                            <strong>User Data Present:</strong>
                            <span class="${response.hasUserData ? 'text-success' : 'text-danger'}">
                                ${response.hasUserData ? 'Yes' : 'No'}
                            </span>
                        </div>
                        <div class="analysis-item">
                            <strong>Cached User Count:</strong>
                            <span class="${response.cachedUserCount > 0 ? 'text-success' : 'text-warning'}">
                                ${response.cachedUserCount || 0}
                            </span>
                        </div>
                        <div class="analysis-item">
                            <strong>Check Time:</strong>
                            <span class="text-muted">${response.timestamp}</span>
                        </div>
                    </div>
                </div>

                ${!response.hasUserData ? `
                    <div class="alert alert-info mt-3">
                        <i class="bi bi-info-circle"></i>
                        <strong>Recommendation:</strong> Consider refreshing the cache to reload user data.
                    </div>
                ` : ''}
            </div>
        `;
    }

    /**
     * Format user count result
     * @param {Object} response - Server response
     * @returns {string} Formatted HTML
     */
    formatUserCountResult(response) {
        return `
            <div class="user-count-result">
                <div class="count-display">
                    <div class="count-number">${response.cachedUserCount || 0}</div>
                    <div class="count-label">Cached Users</div>
                </div>

                <div class="count-details">
                    <div class="detail-row">
                        <span class="detail-label">Total Users:</span>
                        <span class="detail-value">${response.cachedUserCount || 0}</span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">Count Retrieved:</span>
                        <span class="detail-value">${response.timestamp}</span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">Status:</span>
                        <span class="detail-value text-success">Successfully Retrieved</span>
                    </div>
                </div>

                <div class="count-info">
                    <p><strong>Message:</strong> ${response.message}</p>
                </div>
            </div>
        `;
    }

    /**
     * Clear results display
     */
    clearResults() {
        if (this.elements.resultsContainer) {
            $(this.elements.resultsContainer).fadeOut();
        }
        if (this.elements.content) {
            this.elements.content.innerHTML = '';
        }
    }

    // ========================================================================
    // UTILITY FUNCTIONS
    // ========================================================================

    /**
     * Update quick stats
     * @param {string} label - Stat label
     * @param {string|number} value - Stat value
     */
    updateQuickStats(label, value) {
        if (label === 'Cache Status' && this.elements.statusIndicator) {
            this.elements.statusIndicator.textContent = value;
        }
    }

    /**
     * Update last check time
     */
    updateLastCheck() {
        if (!this.elements.lastCheckTime) return;

        const now = new Date();
        const timeString = now.toLocaleTimeString('en-US', {
            hour12: false,
            hour: '2-digit',
            minute: '2-digit'
        });
        this.elements.lastCheckTime.textContent = timeString;
    }

    /**
     * Get current time formatted
     * @returns {string} Current time
     */
    getCurrentTime() {
        return new Date().toLocaleTimeString('en-US', {
            hour12: false,
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    }

    /**
     * Set button loading state
     * @param {HTMLElement} btn - Button element
     * @param {boolean} loading - Loading state
     */
    setButtonLoading(btn, loading) {
        if (!btn) return;

        if (loading) {
            btn.classList.add('loading');
            btn.disabled = true;
            const icon = btn.querySelector('i');
            if (icon) icon.classList.add('spin');
        } else {
            btn.classList.remove('loading');
            btn.disabled = false;
            const icon = btn.querySelector('i');
            if (icon) icon.classList.remove('spin');
        }
    }

    // ========================================================================
    // PUBLIC API (for coordinator integration)
    // ========================================================================

    /**
     * Refresh status (for auto-refresh)
     */
    async refreshStatus() {
        await this.loadCacheOverview();
    }

    /**
     * Refresh overview (alias for coordinator compatibility)
     */
    async refreshOverview() {
        await this.refreshStatus();
    }
}

// Auto-initialize on DOM ready and expose globally for legacy compatibility
let instance = null;

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        instance = new MonitorUtility();
        instance.initialize();
        window.MonitorUtility = instance; // Legacy compatibility
    });
} else {
    instance = new MonitorUtility();
    instance.initialize();
    window.MonitorUtility = instance; // Legacy compatibility
}

export default MonitorUtility;
