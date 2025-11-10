/**
 * HealthUtility.js (ES6 Module)
 *
 * Modern system health monitoring utility for user self-service operations.
 * Handles overall health status, task health, and personal monitoring state.
 *
 * @module features/utilities/common/HealthUtility
 */

import { API } from '../../../core/api.js';
import { ToastNotification } from '../../../components/ToastNotification.js';

/**
 * HealthUtility class
 * Manages system health monitoring with modern ES6 patterns
 */
export class HealthUtility {
    constructor() {
        this.elements = {
            // Button elements
            overallHealthBtn: null,
            taskHealthBtn: null,
            monitoringStateBtn: null,
            healthSummaryBtn: null,
            clearBtn: null,

            // Display elements
            resultsContainer: null,
            content: null,
            controls: null,

            // Stats elements
            overallHealth: null,
            healthyTasksCount: null,
            lastHealthCheck: null,
            healthIndicator: null,
            healthStatus: null
        };

        this.autoRefreshInterval = null;

        console.log('🔧 Health utility created (ES6)');
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize the health utility
     */
    initialize() {
        console.log('🚀 Initializing Health utility...');

        // Cache DOM elements
        this.cacheElements();

        // Setup event listeners
        this.setupEventListeners();

        // Load initial health overview
        setTimeout(() => {
            this.loadHealthOverview();
        }, 1500);

        // Setup auto-refresh
        this.setupAutoRefresh();

        console.log('✅ Health utility initialized');
    }

    /**
     * Cache DOM elements for better performance
     */
    cacheElements() {
        // Button elements
        this.elements.overallHealthBtn = document.getElementById('overall-health-btn');
        this.elements.taskHealthBtn = document.getElementById('task-health-btn');
        this.elements.monitoringStateBtn = document.getElementById('monitoring-state-btn');
        this.elements.healthSummaryBtn = document.getElementById('health-summary-btn');
        this.elements.clearBtn = document.getElementById('clear-health-results');

        // Display elements
        this.elements.resultsContainer = document.getElementById('health-results');
        this.elements.content = document.getElementById('health-content');
        this.elements.controls = document.getElementById('health-controls');

        // Stats elements
        this.elements.overallHealth = document.getElementById('overall-health');
        this.elements.healthyTasksCount = document.getElementById('healthy-tasks-count');
        this.elements.lastHealthCheck = document.getElementById('last-health-check');
        this.elements.healthIndicator = document.getElementById('system-health-indicator');
        this.elements.healthStatus = document.getElementById('health-status');
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Collapse toggle animation
        if (this.elements.controls) {
            const healthUtility = document.querySelector('#health-utility');
            const toggleIcon = healthUtility?.querySelector('.toggle-icon');

            $(this.elements.controls).on('show.bs.collapse', () => {
                toggleIcon?.classList.remove('bi-chevron-down');
                toggleIcon?.classList.add('bi-chevron-up');
            }).on('hide.bs.collapse', () => {
                toggleIcon?.classList.remove('bi-chevron-up');
                toggleIcon?.classList.add('bi-chevron-down');
            });
        }

        // Health operations
        this.elements.overallHealthBtn?.addEventListener('click', (e) => {
            e.preventDefault();
            this.checkOverallHealth();
        });

        this.elements.taskHealthBtn?.addEventListener('click', (e) => {
            e.preventDefault();
            this.checkTaskHealth();
        });

        this.elements.monitoringStateBtn?.addEventListener('click', (e) => {
            e.preventDefault();
            this.checkMonitoringState();
        });

        this.elements.healthSummaryBtn?.addEventListener('click', (e) => {
            e.preventDefault();
            this.getHealthSummary();
        });

        // Clear results
        this.elements.clearBtn?.addEventListener('click', () => {
            this.clearResults();
        });
    }

    /**
     * Setup auto-refresh interval (every 5 minutes)
     */
    setupAutoRefresh() {
        this.autoRefreshInterval = setInterval(() => {
            this.loadHealthOverview();
        }, 300000); // 5 minutes
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
    // HEALTH OVERVIEW
    // ========================================================================

    /**
     * Load health overview (silent refresh of stats)
     */
    async loadHealthOverview() {
        try {
            const data = await API.get('/utility/health/overall');

            if (data.success) {
                this.updateHealthStats(data);
            }
        } catch (error) {
            console.error('Error loading health overview:', error);
            // Show fallback data
            this.updateHealthStats({
                overallHealthy: false,
                healthStatus: {}
            });
        }
    }

    /**
     * Update health statistics display
     * @param {Object} data - Health data response
     */
    updateHealthStats(data) {
        const isHealthy = data.overallHealthy || false;
        const healthStatus = data.healthStatus || {};
        const healthyTasksCount = Object.values(healthStatus).filter(Boolean).length;
        const totalTasks = Object.keys(healthStatus).length;

        // Update quick stats
        if (this.elements.overallHealth) {
            this.elements.overallHealth.textContent = isHealthy ? 'Healthy' : 'Issues Detected';
            this.elements.overallHealth.style.color = isHealthy ? '#28a745' : '#dc3545';
        }

        if (this.elements.healthyTasksCount) {
            this.elements.healthyTasksCount.textContent = `${healthyTasksCount}/${totalTasks}`;
        }

        if (this.elements.lastHealthCheck) {
            this.elements.lastHealthCheck.textContent = new Date().toLocaleTimeString();
        }

        // Update main health indicator
        this.updateMainHealthIndicator(isHealthy);
    }

    /**
     * Update main health indicator
     * @param {boolean} isHealthy - Health status
     */
    updateMainHealthIndicator(isHealthy) {
        if (!this.elements.healthIndicator || !this.elements.healthStatus) {
            return;
        }

        if (isHealthy) {
            this.elements.healthIndicator.classList.remove('unhealthy');
            this.elements.healthIndicator.classList.add('healthy');
            this.elements.healthStatus.textContent = 'Healthy';
        } else {
            this.elements.healthIndicator.classList.remove('healthy');
            this.elements.healthIndicator.classList.add('unhealthy');
            this.elements.healthStatus.textContent = 'Issues';
        }
    }

    // ========================================================================
    // HEALTH OPERATIONS
    // ========================================================================

    /**
     * Check overall system health
     */
    async checkOverallHealth() {
        const btn = this.elements.overallHealthBtn;
        this.setButtonLoading(btn, true);

        try {
            const data = await API.get('/utility/health/overall');

            if (data.success) {
                this.displayHealthResults('Overall System Health', this.formatOverallHealth(data));
                this.updateHealthStats(data);
                ToastNotification.success('Success', 'System health checked successfully');
            } else {
                ToastNotification.error('Error', data.message || 'Failed to check system health');
            }
        } catch (error) {
            console.error('Overall health check error:', error);
            ToastNotification.error('Error', `Failed to check system health: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Check task health details
     */
    async checkTaskHealth() {
        const btn = this.elements.taskHealthBtn;
        this.setButtonLoading(btn, true);

        try {
            const data = await API.get('/utility/health/tasks');

            if (data.success) {
                this.displayHealthResults('Task Health Details', this.formatTaskHealth(data));
                ToastNotification.info('Info', 'Task health details retrieved successfully');
            } else {
                ToastNotification.error('Error', data.message || 'Failed to get task health');
            }
        } catch (error) {
            console.error('Task health check error:', error);
            ToastNotification.error('Error', `Failed to get task health details: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Check personal monitoring state
     */
    async checkMonitoringState() {
        const btn = this.elements.monitoringStateBtn;
        this.setButtonLoading(btn, true);

        try {
            const data = await API.get('/utility/health/monitoring-state');

            if (data.success) {
                this.displayHealthResults('Personal Monitoring State', this.formatMonitoringState(data));
                ToastNotification.info('Info', 'Monitoring state retrieved successfully');
            } else {
                ToastNotification.error('Error', data.message || 'Failed to get monitoring state');
            }
        } catch (error) {
            console.error('Monitoring state check error:', error);
            ToastNotification.error('Error', `Failed to get monitoring state: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Get comprehensive health summary
     */
    async getHealthSummary() {
        const btn = this.elements.healthSummaryBtn;
        this.setButtonLoading(btn, true);

        try {
            const [overall, tasks, monitoring] = await Promise.all([
                API.get('/utility/health/overall'),
                API.get('/utility/health/tasks'),
                API.get('/utility/health/monitoring-state')
            ]);

            const summaryData = { overall, tasks, monitoring };
            this.displayHealthResults('Comprehensive Health Summary', this.formatHealthSummary(summaryData));
            ToastNotification.success('Success', 'Health summary generated successfully');
        } catch (error) {
            console.error('Health summary error:', error);
            ToastNotification.error('Error', `Failed to generate health summary: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    // ========================================================================
    // DISPLAY FUNCTIONS
    // ========================================================================

    /**
     * Display health results
     * @param {string} title - Result title
     * @param {string} content - Result content HTML
     */
    displayHealthResults(title, content) {
        if (!this.elements.content) return;

        this.elements.content.innerHTML = `
            <div class="health-result">
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
     * Format overall health response
     * @param {Object} data - Server response
     * @returns {string} Formatted HTML
     */
    formatOverallHealth(data) {
        const isHealthy = data.overallHealthy;
        const healthStatus = data.healthStatus || {};
        const healthyCount = Object.values(healthStatus).filter(Boolean).length;
        const totalCount = Object.keys(healthStatus).length;

        return `
            <div class="health-overview">
                <div class="health-status-banner ${isHealthy ? 'alert-success' : 'alert-danger'}">
                    <i class="bi ${isHealthy ? 'bi-check-circle' : 'bi-exclamation-triangle'}"></i>
                    <strong>System Status: ${isHealthy ? 'Healthy' : 'Issues Detected'}</strong>
                </div>

                <div class="health-summary">
                    <div class="status-item">
                        <strong>Overall Health:</strong>
                        <span class="${isHealthy ? 'text-success' : 'text-danger'}">
                            ${isHealthy ? 'All systems operational' : 'Some issues detected'}
                        </span>
                    </div>
                    <div class="status-item">
                        <strong>Healthy Tasks:</strong> ${healthyCount}/${totalCount}
                    </div>
                    <div class="status-item">
                        <strong>Last Check:</strong> ${data.timestamp}
                    </div>
                </div>

                <div class="task-summary">
                    <h6>Task Status Summary</h6>
                    ${Object.entries(healthStatus).map(([taskId, healthy]) => `
                        <div class="task-status-item">
                            <span class="task-name">${taskId}</span>
                            <span class="task-health ${healthy ? 'text-success' : 'text-danger'}">
                                <i class="bi ${healthy ? 'bi-check-circle' : 'bi-x-circle'}"></i>
                                ${healthy ? 'Healthy' : 'Issues'}
                            </span>
                        </div>
                    `).join('')}
                </div>
            </div>
        `;
    }

    /**
     * Format task health response
     * @param {Object} data - Server response
     * @returns {string} Formatted HTML
     */
    formatTaskHealth(data) {
        const taskDetails = data.taskDetails || {};

        return `
            <div class="task-health-details">
                <h6>Task Health Details</h6>
                ${Object.entries(taskDetails).map(([taskId, details]) => `
                    <div class="task-detail-card">
                        <div class="task-header">
                            <strong>${taskId}</strong>
                            <span class="task-status ${details.healthy ? 'text-success' : 'text-danger'}">
                                <i class="bi ${details.healthy ? 'bi-check-circle' : 'bi-x-circle'}"></i>
                                ${details.healthy ? 'Healthy' : 'Issues'}
                            </span>
                        </div>
                        <div class="task-details">
                            ${details.lastExecution ? `
                                <div class="detail-item">
                                    <strong>Last Execution:</strong> ${new Date(details.lastExecution).toLocaleString()}
                                </div>
                            ` : ''}
                            ${details.minutesSinceLastExecution !== undefined ? `
                                <div class="detail-item">
                                    <strong>Minutes Since Last:</strong> ${details.minutesSinceLastExecution}
                                </div>
                            ` : ''}
                            ${details.consecutiveFailures ? `
                                <div class="detail-item">
                                    <strong>Consecutive Failures:</strong>
                                    <span class="text-warning">${details.consecutiveFailures}</span>
                                </div>
                            ` : ''}
                            ${details.lastError ? `
                                <div class="detail-item">
                                    <strong>Last Error:</strong>
                                    <span class="text-danger">${details.lastError}</span>
                                </div>
                            ` : ''}
                        </div>
                    </div>
                `).join('')}
            </div>
        `;
    }

    /**
     * Format monitoring state response
     * @param {Object} data - Server response
     * @returns {string} Formatted HTML
     */
    formatMonitoringState(data) {
        return `
            <div class="monitoring-state">
                <h6>Personal Monitoring State</h6>
                <div class="monitoring-details">
                    <div class="status-item">
                        <strong>Username:</strong> ${data.username}
                    </div>
                    <div class="status-item">
                        <strong>Monitoring Mode:</strong>
                        <span class="badge badge-info">${data.monitoringMode}</span>
                    </div>
                    <div class="status-item">
                        <strong>Schedule Monitoring:</strong>
                        <span class="${data.scheduleMonitoring ? 'text-success' : 'text-secondary'}">
                            ${data.scheduleMonitoring ? 'Active' : 'Inactive'}
                        </span>
                    </div>
                    <div class="status-item">
                        <strong>Hourly Monitoring:</strong>
                        <span class="${data.hourlyMonitoring ? 'text-warning' : 'text-secondary'}">
                            ${data.hourlyMonitoring ? 'Active' : 'Inactive'}
                        </span>
                    </div>
                    <div class="status-item">
                        <strong>Temp Stop Monitoring:</strong>
                        <span class="${data.tempStopMonitoring ? 'text-info' : 'text-secondary'}">
                            ${data.tempStopMonitoring ? 'Active' : 'Inactive'}
                        </span>
                    </div>
                    <div class="status-item">
                        <strong>Continued After Schedule:</strong>
                        <span class="${data.continuedAfterSchedule ? 'text-warning' : 'text-success'}">
                            ${data.continuedAfterSchedule ? 'Yes' : 'No'}
                        </span>
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Format health summary response
     * @param {Object} data - Summary data with overall, tasks, and monitoring
     * @returns {string} Formatted HTML
     */
    formatHealthSummary(data) {
        const overall = data.overall;
        const tasks = data.tasks;
        const monitoring = data.monitoring;

        // Build task status HTML
        let taskStatusHtml = '';
        if (tasks.success && tasks.healthStatus) {
            taskStatusHtml = Object.entries(tasks.healthStatus).map(([taskId, healthy]) => `
                <div class="task-summary-item">
                    <span class="task-name">${taskId}</span>
                    <span class="${healthy ? 'text-success' : 'text-danger'}">
                        ${healthy ? '✓' : '✗'}
                    </span>
                </div>
            `).join('');
        }

        // Build monitoring summary
        let activeMonitoring = 'None';
        if (monitoring.success) {
            const activeItems = [];
            if (monitoring.scheduleMonitoring) activeItems.push('Schedule');
            if (monitoring.hourlyMonitoring) activeItems.push('Hourly');
            if (monitoring.tempStopMonitoring) activeItems.push('Temp Stop');
            activeMonitoring = activeItems.length > 0 ? activeItems.join(', ') : 'None';
        }

        return `
            <div class="comprehensive-health-summary">
                <div class="summary-section">
                    <h6>Overall System Status</h6>
                    ${overall.success ? `
                        <div class="summary-item ${overall.overallHealthy ? 'text-success' : 'text-danger'}">
                            <i class="bi ${overall.overallHealthy ? 'bi-check-circle' : 'bi-exclamation-triangle'}"></i>
                            ${overall.overallHealthy ? 'All systems operational' : 'Issues detected'}
                        </div>
                    ` : '<div class="text-danger">Failed to get overall health</div>'}
                </div>

                <div class="summary-section">
                    <h6>Task Status Summary</h6>
                    ${tasks.success ? `
                        <div class="task-summary-grid">
                            ${taskStatusHtml}
                        </div>
                    ` : '<div class="text-danger">Failed to get task health</div>'}
                </div>

                <div class="summary-section">
                    <h6>Personal Monitoring</h6>
                    ${monitoring.success ? `
                        <div class="monitoring-summary">
                            <div class="summary-item">
                                <strong>Mode:</strong> ${monitoring.monitoringMode || 'Unknown'}
                            </div>
                            <div class="summary-item">
                                <strong>Active Monitoring:</strong> ${activeMonitoring}
                            </div>
                        </div>
                    ` : '<div class="text-danger">Failed to get monitoring state</div>'}
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
        await this.loadHealthOverview();
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
        instance = new HealthUtility();
        instance.initialize();
        window.HealthUtility = instance; // Legacy compatibility
    });
} else {
    instance = new HealthUtility();
    instance.initialize();
    window.HealthUtility = instance; // Legacy compatibility
}

export default HealthUtility;
