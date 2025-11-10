/**
 * ActionsUtility.js (ES6 Module)
 *
 * Modern actions utility for emergency operations and quick system actions.
 * Handles emergency cache resets, cache refreshes, validation, and health checks.
 *
 * @module features/utilities/common/ActionsUtility
 */

import { API } from '../../../core/api.js';
import { ToastNotification } from '../../../components/ToastNotification.js';

/**
 * ActionsUtility class
 * Manages action operations with modern ES6 patterns
 */
export class ActionsUtility {
    constructor() {
        this.elements = {
            // Button elements
            emergencyResetBtn: null,
            forceRefreshBtn: null,
            checkDataBtn: null,
            validateCacheBtn: null,
            healthCheckBtn: null,
            refreshAllBtn: null,
            clearBtn: null,

            // Display elements
            resultsContainer: null,
            content: null,
            controls: null,

            // Status elements
            lastAction: null,
            actionResult: null,
            actionTime: null
        };

        console.log('🔧 Actions utility created (ES6)');
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize the actions utility
     */
    initialize() {
        try {
            this.cacheElements();
            this.setupEventListeners();

            console.log('✅ Actions utility initialized (ES6)');
        } catch (error) {
            console.error('❌ Error initializing actions utility:', error);
            ToastNotification.error('Error', 'Failed to initialize actions utility');
        }
    }

    /**
     * Cache DOM elements for better performance
     */
    cacheElements() {
        // Button elements
        this.elements.emergencyResetBtn = document.getElementById('emergency-cache-reset-btn');
        this.elements.forceRefreshBtn = document.getElementById('force-cache-refresh-btn');
        this.elements.checkDataBtn = document.getElementById('check-cache-data-btn');
        this.elements.validateCacheBtn = document.getElementById('validate-cache-quick-btn');
        this.elements.healthCheckBtn = document.getElementById('quick-health-check-btn');
        this.elements.refreshAllBtn = document.getElementById('refresh-all-data-btn');
        this.elements.clearBtn = document.getElementById('clear-actions-results');

        // Display elements
        this.elements.resultsContainer = document.getElementById('actions-results');
        this.elements.content = document.getElementById('actions-content');
        this.elements.controls = document.getElementById('actions-controls');

        // Status elements
        this.elements.lastAction = document.getElementById('last-action');
        this.elements.actionResult = document.getElementById('action-result');
        this.elements.actionTime = document.getElementById('action-time');
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Emergency operations
        if (this.elements.emergencyResetBtn) {
            this.elements.emergencyResetBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.performEmergencyCacheReset();
            });
        }

        if (this.elements.forceRefreshBtn) {
            this.elements.forceRefreshBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.forceCacheRefresh();
            });
        }

        // Quick cache operations
        if (this.elements.checkDataBtn) {
            this.elements.checkDataBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.checkCacheData();
            });
        }

        if (this.elements.validateCacheBtn) {
            this.elements.validateCacheBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.validateCacheQuick();
            });
        }

        // System shortcuts
        if (this.elements.healthCheckBtn) {
            this.elements.healthCheckBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.quickHealthCheck();
            });
        }

        if (this.elements.refreshAllBtn) {
            this.elements.refreshAllBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.refreshAllData();
            });
        }

        // Clear button
        if (this.elements.clearBtn) {
            this.elements.clearBtn.addEventListener('click', () => {
                this.clearResults();
            });
        }

        // Toggle icon animation for collapsible controls
        if (this.elements.controls) {
            this.elements.controls.addEventListener('show.bs.collapse', () => {
                const toggleIcon = document.querySelector('#actions-utility .toggle-icon');
                if (toggleIcon) {
                    toggleIcon.classList.remove('bi-chevron-down');
                    toggleIcon.classList.add('bi-chevron-up');
                }
            });

            this.elements.controls.addEventListener('hide.bs.collapse', () => {
                const toggleIcon = document.querySelector('#actions-utility .toggle-icon');
                if (toggleIcon) {
                    toggleIcon.classList.remove('bi-chevron-up');
                    toggleIcon.classList.add('bi-chevron-down');
                }
            });
        }
    }

    // ========================================================================
    // EMERGENCY OPERATIONS
    // ========================================================================

    /**
     * Perform emergency cache reset
     */
    async performEmergencyCacheReset() {
        const confirmed = confirm(
            '⚠️ EMERGENCY CACHE RESET ⚠️\n\n' +
            'This will completely reset all cached data and session state.\n' +
            'This action should only be used if you are experiencing severe issues.\n\n' +
            'Are you absolutely sure you want to continue?'
        );

        if (!confirmed) return;

        const btn = this.elements.emergencyResetBtn;
        this.setButtonLoading(btn, true);
        this.updateActionStatus('Emergency Cache Reset', 'In Progress', this.getCurrentTime());

        try {
            const data = await API.post('/utility/cache/emergency-reset');

            if (data.success) {
                const result = this.formatEmergencyResetResult(data);
                this.displayActionResults('Emergency Cache Reset Completed', result);
                this.updateActionStatus('Emergency Reset', 'Success', this.getCurrentTime());
                ToastNotification.success('Success', 'Emergency cache reset completed successfully');

                // Auto-refresh all utilities after emergency reset
                setTimeout(() => {
                    if (typeof window.refreshAllUtilities === 'function') {
                        window.refreshAllUtilities();
                    }
                }, 2000);
            } else {
                this.updateActionStatus('Emergency Reset', 'Failed', this.getCurrentTime());
                ToastNotification.error('Error', data.message || 'Emergency reset failed');
            }
        } catch (error) {
            console.error('Error performing emergency cache reset:', error);
            this.updateActionStatus('Emergency Reset', 'Error', this.getCurrentTime());
            ToastNotification.error('Error', `Failed to perform emergency cache reset: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Force cache refresh
     */
    async forceCacheRefresh() {
        const confirmed = confirm(
            'Force refresh will reload all user data from files.\n' +
            'This may take a few moments. Continue?'
        );

        if (!confirmed) return;

        const btn = this.elements.forceRefreshBtn;
        this.setButtonLoading(btn, true);
        this.updateActionStatus('Force Cache Refresh', 'In Progress', this.getCurrentTime());

        try {
            const data = await API.post('/utility/cache/refresh');

            if (data.success) {
                const result = this.formatCacheRefreshResult(data);
                this.displayActionResults('Cache Refresh Completed', result);
                this.updateActionStatus('Cache Refresh', 'Success', this.getCurrentTime());
                ToastNotification.success('Success', 'Cache refreshed successfully');
            } else {
                this.updateActionStatus('Cache Refresh', 'Failed', this.getCurrentTime());
                ToastNotification.error('Error', data.message || 'Cache refresh failed');
            }
        } catch (error) {
            console.error('Error forcing cache refresh:', error);
            this.updateActionStatus('Cache Refresh', 'Error', this.getCurrentTime());
            ToastNotification.error('Error', `Failed to force cache refresh: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    // ========================================================================
    // QUICK CACHE OPERATIONS
    // ========================================================================

    /**
     * Check cache data
     */
    async checkCacheData() {
        const btn = this.elements.checkDataBtn;
        this.setButtonLoading(btn, true);
        this.updateActionStatus('Check Cache Data', 'In Progress', this.getCurrentTime());

        try {
            const data = await API.get('/utility/cache/user-data-check');

            if (data.success) {
                const result = this.formatCacheDataResult(data);
                this.displayActionResults('Cache Data Check Results', result);
                this.updateActionStatus('Data Check', 'Success', this.getCurrentTime());
                ToastNotification.info('Info', data.message || 'Cache data checked successfully');
            } else {
                this.updateActionStatus('Data Check', 'Failed', this.getCurrentTime());
                ToastNotification.error('Error', data.message || 'Cache data check failed');
            }
        } catch (error) {
            console.error('Error checking cache data:', error);
            this.updateActionStatus('Data Check', 'Error', this.getCurrentTime());
            ToastNotification.error('Error', `Failed to check cache data: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Validate cache quick
     */
    async validateCacheQuick() {
        const btn = this.elements.validateCacheBtn;
        this.setButtonLoading(btn, true);
        this.updateActionStatus('Validate Cache', 'In Progress', this.getCurrentTime());

        try {
            const data = await API.post('/utility/cache/validate');

            if (data.success) {
                const result = this.formatCacheValidationResult(data);
                this.displayActionResults('Cache Validation Results', result);
                this.updateActionStatus('Cache Validation', 'Success', this.getCurrentTime());
                ToastNotification.success('Success', 'Cache validation completed successfully');
            } else {
                this.updateActionStatus('Cache Validation', 'Failed', this.getCurrentTime());
                ToastNotification.error('Error', data.message || 'Cache validation failed');
            }
        } catch (error) {
            console.error('Error validating cache:', error);
            this.updateActionStatus('Cache Validation', 'Error', this.getCurrentTime());
            ToastNotification.error('Error', `Failed to validate cache: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    // ========================================================================
    // SYSTEM SHORTCUTS
    // ========================================================================

    /**
     * Quick health check
     */
    async quickHealthCheck() {
        const btn = this.elements.healthCheckBtn;
        this.setButtonLoading(btn, true);
        this.updateActionStatus('Health Check', 'In Progress', this.getCurrentTime());

        try {
            const data = await API.get('/utility/health/overall');

            if (data.success) {
                const result = this.formatHealthCheckResult(data);
                this.displayActionResults('Quick Health Check Results', result);
                this.updateActionStatus('Health Check', 'Success', this.getCurrentTime());

                const status = data.overallHealthy ? 'success' : 'warning';
                const message = data.overallHealthy ? 'System is healthy' : 'System issues detected';
                ToastNotification[status]('Health Check', message);
            } else {
                this.updateActionStatus('Health Check', 'Failed', this.getCurrentTime());
                ToastNotification.error('Error', data.message || 'Health check failed');
            }
        } catch (error) {
            console.error('Error performing health check:', error);
            this.updateActionStatus('Health Check', 'Error', this.getCurrentTime());
            ToastNotification.error('Error', `Failed to perform health check: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Refresh all data
     */
    refreshAllData() {
        const btn = this.elements.refreshAllBtn;
        this.setButtonLoading(btn, true);
        this.updateActionStatus('Refresh All Data', 'In Progress', this.getCurrentTime());

        try {
            // Use the main utility coordinator function if available
            if (typeof window.refreshAllUtilities === 'function') {
                window.refreshAllUtilities();
                this.updateActionStatus('Refresh All', 'Success', this.getCurrentTime());
                ToastNotification.success('Success', 'All data refreshed successfully');

                this.displayActionResults('Refresh All Data Completed', `
                    <div class="refresh-result">
                        <div class="alert alert-success">
                            <i class="bi bi-check-circle"></i>
                            All utility data has been refreshed successfully.
                        </div>
                        <div class="refresh-details">
                            <h6>Refreshed Components:</h6>
                            <ul class="refresh-list">
                                <li><i class="bi bi-heart-pulse"></i> System Health Status</li>
                                <li><i class="bi bi-person-workspace"></i> Session Information</li>
                                <li><i class="bi bi-activity"></i> Cache Monitoring</li>
                                <li><i class="bi bi-archive"></i> Backup Status</li>
                            </ul>
                        </div>
                    </div>
                `);
            } else {
                this.updateActionStatus('Refresh All', 'Error', this.getCurrentTime());
                ToastNotification.error('Error', 'Refresh function not available');
            }
        } catch (error) {
            console.error('Error refreshing all data:', error);
            this.updateActionStatus('Refresh All', 'Error', this.getCurrentTime());
            ToastNotification.error('Error', `Failed to refresh all data: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    // ========================================================================
    // RESULT FORMATTING FUNCTIONS
    // ========================================================================

    /**
     * Format emergency reset result
     */
    formatEmergencyResetResult(data) {
        return `
            <div class="emergency-reset-result">
                <div class="alert alert-success">
                    <i class="bi bi-check-circle"></i>
                    <strong>Emergency cache reset completed successfully</strong>
                </div>

                <div class="reset-details">
                    <h6>Reset Actions Performed:</h6>
                    <ul class="reset-actions">
                        <li><i class="bi bi-trash"></i> All cached data cleared</li>
                        <li><i class="bi bi-arrow-clockwise"></i> Session state reset</li>
                        <li><i class="bi bi-database"></i> User context refreshed</li>
                        <li><i class="bi bi-shield-check"></i> System integrity verified</li>
                    </ul>
                </div>

                <div class="reset-info">
                    <div class="info-item">
                        <strong>Reset Time:</strong> ${data.timestamp || this.getCurrentTime()}
                    </div>
                    <div class="info-item">
                        <strong>Next Steps:</strong> All utilities will auto-refresh in a few seconds
                    </div>
                </div>

                <div class="alert alert-info mt-3">
                    <i class="bi bi-info-circle"></i>
                    You may need to refresh the page if issues persist.
                </div>
            </div>
        `;
    }

    /**
     * Format cache refresh result
     */
    formatCacheRefreshResult(data) {
        const beforeCount = data.beforeCount || 0;
        const afterCount = data.afterCount || 0;
        const change = afterCount - beforeCount;

        return `
            <div class="cache-refresh-result">
                <div class="alert alert-success">
                    <i class="bi bi-arrow-repeat"></i>
                    <strong>Cache refresh completed successfully</strong>
                </div>

                <div class="refresh-stats">
                    <div class="stat-row">
                        <span class="stat-label">Users Before:</span>
                        <span class="stat-value">${beforeCount}</span>
                    </div>
                    <div class="stat-row">
                        <span class="stat-label">Users After:</span>
                        <span class="stat-value">${afterCount}</span>
                    </div>
                    <div class="stat-row">
                        <span class="stat-label">Change:</span>
                        <span class="stat-value ${change >= 0 ? 'text-success' : 'text-warning'}">
                            ${change >= 0 ? '+' : ''}${change}
                        </span>
                    </div>
                </div>

                <div class="refresh-info">
                    <p><strong>Refresh completed at:</strong> ${data.timestamp || this.getCurrentTime()}</p>
                    <p>All user data has been reloaded from the file system.</p>
                </div>
            </div>
        `;
    }

    /**
     * Format cache data result
     */
    formatCacheDataResult(data) {
        return `
            <div class="cache-data-result">
                <div class="data-status ${data.hasUserData ? 'alert-success' : 'alert-warning'}">
                    <i class="bi ${data.hasUserData ? 'bi-check-circle' : 'bi-exclamation-triangle'}"></i>
                    <strong>${data.message || 'Cache data check completed'}</strong>
                </div>

                <div class="data-details">
                    <div class="detail-row">
                        <span class="detail-label">Has User Data:</span>
                        <span class="detail-value ${data.hasUserData ? 'text-success' : 'text-danger'}">
                            ${data.hasUserData ? 'Yes' : 'No'}
                        </span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">Cached Users:</span>
                        <span class="detail-value">${data.cachedUserCount || 0}</span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">Check Time:</span>
                        <span class="detail-value">${data.timestamp || this.getCurrentTime()}</span>
                    </div>
                </div>

                ${!data.hasUserData ? `
                    <div class="alert alert-warning mt-3">
                        <i class="bi bi-exclamation-triangle"></i>
                        No user data found in cache. Consider performing a cache refresh.
                    </div>
                ` : ''}
            </div>
        `;
    }

    /**
     * Format cache validation result
     */
    formatCacheValidationResult(data) {
        return `
            <div class="cache-validation-result">
                <div class="alert alert-success">
                    <i class="bi bi-check-circle"></i>
                    <strong>${data.message || 'Cache validation completed successfully'}</strong>
                </div>

                <div class="validation-details">
                    <div class="validation-item">
                        <strong>Has User Data:</strong>
                        <span class="${data.hasUserData ? 'text-success' : 'text-warning'}">
                            ${data.hasUserData ? 'Valid' : 'Missing'}
                        </span>
                    </div>
                    <div class="validation-item">
                        <strong>User Count:</strong>
                        <span class="text-info">${data.cachedUserCount || 0}</span>
                    </div>
                    <div class="validation-item">
                        <strong>Cache Status:</strong>
                        <span class="text-success">Validated</span>
                    </div>
                </div>

                <div class="validation-summary">
                    <p><strong>Validation completed at:</strong> ${data.timestamp || this.getCurrentTime()}</p>
                    <p>Cache consistency and data integrity verified.</p>
                </div>
            </div>
        `;
    }

    /**
     * Format health check result
     */
    formatHealthCheckResult(data) {
        const isHealthy = data.overallHealthy;
        const healthStatus = data.healthStatus || {};
        const healthyCount = Object.values(healthStatus).filter(Boolean).length;
        const totalCount = Object.keys(healthStatus).length;

        // Build task breakdown HTML
        const taskBreakdownHtml = Object.entries(healthStatus)
            .map(([taskId, healthy]) => `
                <div class="task-status">
                    <span class="task-name">${taskId}</span>
                    <span class="task-health ${healthy ? 'text-success' : 'text-danger'}">
                        <i class="bi ${healthy ? 'bi-check' : 'bi-x'}"></i>
                        ${healthy ? 'OK' : 'Issue'}
                    </span>
                </div>
            `).join('');

        return `
            <div class="health-check-result">
                <div class="alert ${isHealthy ? 'alert-success' : 'alert-warning'}">
                    <i class="bi ${isHealthy ? 'bi-heart-pulse' : 'bi-exclamation-triangle'}"></i>
                    <strong>System Health: ${isHealthy ? 'All Systems Operational' : 'Issues Detected'}</strong>
                </div>

                <div class="health-summary">
                    <div class="health-stat">
                        <strong>Overall Status:</strong>
                        <span class="${isHealthy ? 'text-success' : 'text-warning'}">
                            ${isHealthy ? 'Healthy' : 'Attention Needed'}
                        </span>
                    </div>
                    <div class="health-stat">
                        <strong>Healthy Tasks:</strong>
                        <span class="text-info">${healthyCount}/${totalCount}</span>
                    </div>
                    <div class="health-stat">
                        <strong>Check Time:</strong>
                        <span class="text-muted">${data.timestamp || this.getCurrentTime()}</span>
                    </div>
                </div>

                <div class="task-breakdown">
                    <h6>Task Status Breakdown:</h6>
                    ${taskBreakdownHtml}
                </div>

                ${!isHealthy ? `
                    <div class="alert alert-info mt-3">
                        <i class="bi bi-info-circle"></i>
                        For detailed analysis, use the Health utility section.
                    </div>
                ` : ''}
            </div>
        `;
    }

    // ========================================================================
    // DISPLAY AND UTILITY FUNCTIONS
    // ========================================================================

    /**
     * Display action results
     */
    displayActionResults(title, content) {
        if (!this.elements.content || !this.elements.resultsContainer) return;

        this.elements.content.innerHTML = `
            <div class="action-result">
                <h5>${title}</h5>
                <div class="result-content">
                    ${content}
                </div>
            </div>
        `;

        // Fade in results
        this.elements.resultsContainer.style.display = 'block';
        setTimeout(() => {
            this.elements.resultsContainer.style.opacity = '1';
        }, 10);
    }

    /**
     * Update action status
     */
    updateActionStatus(action, result, time) {
        if (this.elements.lastAction) {
            this.elements.lastAction.textContent = action;
        }

        if (this.elements.actionTime) {
            this.elements.actionTime.textContent = time;
        }

        if (this.elements.actionResult) {
            this.elements.actionResult.textContent = result;

            // Update result with color coding
            let color = '#6c757d'; // default gray
            if (result === 'Success') {
                color = '#28a745'; // green
            } else if (result === 'Failed' || result === 'Error') {
                color = '#dc3545'; // red
            } else if (result === 'In Progress') {
                color = '#007bff'; // blue
            }
            this.elements.actionResult.style.color = color;
        }
    }

    /**
     * Set button loading state
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

    /**
     * Clear results display
     */
    clearResults() {
        if (this.elements.content) {
            this.elements.content.innerHTML = '';
        }
        if (this.elements.resultsContainer) {
            this.elements.resultsContainer.style.opacity = '0';
            setTimeout(() => {
                this.elements.resultsContainer.style.display = 'none';
            }, 300);
        }
        this.updateActionStatus('None', 'Ready', '--:--:--');
    }

    /**
     * Get current time formatted
     */
    getCurrentTime() {
        return new Date().toLocaleTimeString('en-US', {
            hour12: false,
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    }

    // ========================================================================
    // PUBLIC ACTION METHODS (backward compatibility)
    // ========================================================================

    /**
     * Emergency reset (public alias)
     */
    emergencyReset() {
        this.performEmergencyCacheReset();
    }

    /**
     * Force refresh (public alias)
     */
    forceRefresh() {
        this.forceCacheRefresh();
    }

    /**
     * Check data (public alias)
     */
    checkData() {
        this.checkCacheData();
    }

    /**
     * Validate (public alias)
     */
    validate() {
        this.validateCacheQuick();
    }

    /**
     * Health check (public alias)
     */
    healthCheck() {
        this.quickHealthCheck();
    }

    /**
     * Refresh all (public alias)
     */
    refreshAll() {
        this.refreshAllData();
    }
}

// ========================================================================
// AUTO-INITIALIZATION
// ========================================================================

// Auto-initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        initializeActionsUtility();
    });
} else {
    initializeActionsUtility();
}

function initializeActionsUtility() {
    const actionsUtility = new ActionsUtility();
    actionsUtility.initialize();

    // Expose globally for backward compatibility
    window.ActionsUtility = actionsUtility;

    console.log('📦 ActionsUtility module loaded and initialized (ES6)');
}

// Export for ES6 module usage
export default ActionsUtility;
