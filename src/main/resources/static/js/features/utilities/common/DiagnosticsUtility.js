/**
 * DiagnosticsUtility.js (ES6 Module)
 *
 * Modern diagnostics utility for system analysis and troubleshooting.
 * Handles backup event diagnostics, system summary reports, and health checks.
 *
 * @module features/utilities/common/DiagnosticsUtility
 */

import { API } from '../../../core/api.js';
import { ToastNotification } from '../../../components/ToastNotification.js';

/**
 * DiagnosticsUtility class
 * Manages diagnostic operations with modern ES6 patterns
 */
export class DiagnosticsUtility {
    constructor() {
        this.elements = {
            // Control elements
            fileTypeSelect: null,
            yearInput: null,
            monthInput: null,
            systemTime: null,

            // Button elements
            backupEventsBtn: null,
            systemSummaryBtn: null,
            clearBtn: null,

            // Display elements
            resultsContainer: null,
            content: null,
            controls: null
        };

        this.systemTimeInterval = null;

        console.log('🔧 Diagnostics utility created (ES6)');
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize the diagnostics utility
     */
    initialize() {
        try {
            this.cacheElements();
            this.setupEventListeners();
            this.setDefaultValues();
            this.startSystemTime();

            console.log('✅ Diagnostics utility initialized (ES6)');
        } catch (error) {
            console.error('❌ Error initializing diagnostics utility:', error);
            ToastNotification.error('Error', 'Failed to initialize diagnostics utility');
        }
    }

    /**
     * Cache DOM elements for better performance
     */
    cacheElements() {
        // Control elements
        this.elements.fileTypeSelect = document.getElementById('diag-file-type');
        this.elements.yearInput = document.getElementById('diag-year');
        this.elements.monthInput = document.getElementById('diag-month');
        this.elements.systemTime = document.getElementById('diag-system-time');

        // Button elements
        this.elements.backupEventsBtn = document.getElementById('backup-events-diag-btn');
        this.elements.systemSummaryBtn = document.getElementById('system-summary-diag-btn');
        this.elements.clearBtn = document.getElementById('clear-diagnostics-results');

        // Display elements
        this.elements.resultsContainer = document.getElementById('diagnostics-results');
        this.elements.content = document.getElementById('diagnostics-content');
        this.elements.controls = document.getElementById('diagnostics-controls');
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Backup Events button
        if (this.elements.backupEventsBtn) {
            this.elements.backupEventsBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.getBackupEventDiagnostics();
            });
        }

        // System Summary button
        if (this.elements.systemSummaryBtn) {
            this.elements.systemSummaryBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.getSystemSummary();
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
                const toggleIcon = document.querySelector('#diagnostics-utility .toggle-icon');
                if (toggleIcon) {
                    toggleIcon.classList.remove('bi-chevron-down');
                    toggleIcon.classList.add('bi-chevron-up');
                }
            });

            this.elements.controls.addEventListener('hide.bs.collapse', () => {
                const toggleIcon = document.querySelector('#diagnostics-utility .toggle-icon');
                if (toggleIcon) {
                    toggleIcon.classList.remove('bi-chevron-up');
                    toggleIcon.classList.add('bi-chevron-down');
                }
            });
        }
    }

    /**
     * Set default values for inputs
     */
    setDefaultValues() {
        if (this.elements.monthInput) {
            const currentMonth = new Date().getMonth() + 1;
            this.elements.monthInput.value = currentMonth;
        }
    }

    /**
     * Start system time display
     */
    startSystemTime() {
        this.updateSystemTime();
        this.systemTimeInterval = setInterval(() => {
            this.updateSystemTime();
        }, 1000);
    }

    /**
     * Update system time display
     */
    updateSystemTime() {
        if (this.elements.systemTime) {
            const now = new Date();
            this.elements.systemTime.textContent = now.toLocaleString();
        }
    }

    /**
     * Cleanup resources
     */
    destroy() {
        if (this.systemTimeInterval) {
            clearInterval(this.systemTimeInterval);
            this.systemTimeInterval = null;
        }
        console.log('🔧 Diagnostics utility destroyed');
    }

    // ========================================================================
    // DIAGNOSTIC OPERATIONS
    // ========================================================================

    /**
     * Get backup event diagnostics
     */
    async getBackupEventDiagnostics() {
        const fileType = this.elements.fileTypeSelect?.value;
        const year = this.elements.yearInput?.value;
        const month = this.elements.monthInput?.value;
        const btn = this.elements.backupEventsBtn;

        this.setButtonLoading(btn, true);

        try {
            const params = {};
            if (fileType) params.fileType = fileType;
            if (year) params.year = year;
            if (month) params.month = month;

            const data = await API.get('/utility/diagnostics/backup-events', params);

            if (data.success) {
                this.displayDiagnosticsResults(
                    'Backup Event Diagnostics',
                    this.formatBackupEventDiagnostics(data)
                );
                ToastNotification.success('Success', 'Backup event diagnostics retrieved successfully');
            } else {
                ToastNotification.error('Error', data.message || 'Failed to get backup event diagnostics');
            }
        } catch (error) {
            console.error('Error getting backup event diagnostics:', error);
            ToastNotification.error('Error', `Failed to get backup event diagnostics: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Get system summary
     */
    async getSystemSummary() {
        const btn = this.elements.systemSummaryBtn;
        this.setButtonLoading(btn, true);

        try {
            const data = await API.get('/utility/diagnostics/system-summary');

            if (data.success) {
                this.displayDiagnosticsResults(
                    'System Summary Report',
                    this.formatSystemSummary(data)
                );
                ToastNotification.success('Success', 'System summary generated successfully');
            } else {
                ToastNotification.error('Error', data.message || 'Failed to generate system summary');
            }
        } catch (error) {
            console.error('Error getting system summary:', error);
            ToastNotification.error('Error', `Failed to generate system summary: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    // ========================================================================
    // DISPLAY FUNCTIONS
    // ========================================================================

    /**
     * Display diagnostics results
     */
    displayDiagnosticsResults(title, content) {
        if (!this.elements.content || !this.elements.resultsContainer) return;

        this.elements.content.innerHTML = `
            <div class="diagnostics-result">
                <h5>${title}</h5>
                <div class="result-content">
                    ${content}
                </div>
                <div class="result-meta">
                    <small class="text-muted">Generated at: ${new Date().toLocaleString()}</small>
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
     * Format backup event diagnostics
     */
    formatBackupEventDiagnostics(data) {
        return `
            <div class="backup-event-diagnostics">
                <div class="diagnostics-header">
                    <h6>Backup Event System Analysis</h6>
                    ${data.fileType ? `<p><strong>File Type:</strong> ${data.fileType}</p>` : ''}
                </div>

                <div class="diagnostics-content">
                    <div class="diagnostics-section">
                        <h6>Event System Status</h6>
                        <pre class="diagnostics-text">${data.diagnostics || 'No diagnostic data available'}</pre>
                    </div>

                    ${data.fileSpecific ? `
                        <div class="diagnostics-section">
                            <h6>File-Specific Events</h6>
                            <pre class="diagnostics-text">${data.fileSpecific}</pre>
                        </div>
                    ` : ''}

                    <div class="diagnostics-summary">
                        <div class="summary-item">
                            <strong>Analysis Time:</strong> ${data.timestamp || new Date().toLocaleString()}
                        </div>
                        <div class="summary-item">
                            <strong>Scope:</strong> ${data.fileType ? 'File-Specific' : 'System-Wide'}
                        </div>
                    </div>
                </div>

                <div class="diagnostics-actions">
                    <button class="btn btn-sm btn-outline-primary" onclick="window.DiagnosticsUtility.refreshBackupEventDiagnostics()">
                        <i class="bi bi-arrow-repeat"></i> Refresh
                    </button>
                    <button class="btn btn-sm btn-outline-secondary" onclick="window.DiagnosticsUtility.exportDiagnostics('backup-events')">
                        <i class="bi bi-download"></i> Export
                    </button>
                </div>
            </div>
        `;
    }

    /**
     * Format system summary
     */
    formatSystemSummary(data) {
        const summary = data.summary || {};

        return `
            <div class="system-summary-report">
                <div class="summary-overview">
                    <h6>System Overview</h6>
                    <div class="summary-grid">
                        <div class="summary-card ${summary.systemHealthy ? 'status-healthy' : 'status-unhealthy'}">
                            <div class="card-icon">
                                <i class="bi ${summary.systemHealthy ? 'bi-check-circle' : 'bi-exclamation-triangle'}"></i>
                            </div>
                            <div class="card-content">
                                <h6>System Health</h6>
                                <p>${summary.systemHealthy ? 'All Systems Operational' : 'Issues Detected'}</p>
                            </div>
                        </div>

                        <div class="summary-card">
                            <div class="card-icon">
                                <i class="bi bi-person-circle"></i>
                            </div>
                            <div class="card-content">
                                <h6>Current User</h6>
                                <p>${summary.currentUser || 'Unknown'}</p>
                                <small>Role: ${summary.userRole || 'N/A'}</small>
                            </div>
                        </div>

                        <div class="summary-card ${summary.cacheHealthy ? 'status-healthy' : 'status-warning'}">
                            <div class="card-icon">
                                <i class="bi bi-hdd"></i>
                            </div>
                            <div class="card-content">
                                <h6>Cache Status</h6>
                                <p>${summary.cacheHealthy ? 'Healthy' : 'Issues'}</p>
                                <small>Users: ${summary.cachedUserCount || 0}</small>
                            </div>
                        </div>

                        <div class="summary-card">
                            <div class="card-icon">
                                <i class="bi bi-eye"></i>
                            </div>
                            <div class="card-content">
                                <h6>Monitoring</h6>
                                <p>${summary.monitoringMode || 'Unknown'}</p>
                                <small>Tasks: ${summary.healthyTasks || 0}</small>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="detailed-analysis">
                    <h6>Detailed Analysis</h6>

                    <div class="analysis-section">
                        <h6>User Information</h6>
                        <div class="info-table">
                            <div class="info-row">
                                <span class="info-label">Username:</span>
                                <span class="info-value">${summary.currentUser || 'Unknown'}</span>
                            </div>
                            <div class="info-row">
                                <span class="info-label">User ID:</span>
                                <span class="info-value">${summary.userId || 'N/A'}</span>
                            </div>
                            <div class="info-row">
                                <span class="info-label">Role:</span>
                                <span class="info-value">${summary.userRole || 'Unknown'}</span>
                            </div>
                        </div>
                    </div>

                    <div class="analysis-section">
                        <h6>System Performance</h6>
                        <div class="info-table">
                            <div class="info-row">
                                <span class="info-label">System Health:</span>
                                <span class="info-value ${summary.systemHealthy ? 'text-success' : 'text-danger'}">
                                    ${summary.systemHealthy ? 'Optimal' : 'Needs Attention'}
                                </span>
                            </div>
                            <div class="info-row">
                                <span class="info-label">Cache Performance:</span>
                                <span class="info-value ${summary.cacheHealthy ? 'text-success' : 'text-warning'}">
                                    ${summary.cacheHealthy ? 'Good' : 'Degraded'}
                                </span>
                            </div>
                            <div class="info-row">
                                <span class="info-label">Data Integrity:</span>
                                <span class="info-value ${summary.hasUserData ? 'text-success' : 'text-warning'}">
                                    ${summary.hasUserData ? 'Verified' : 'Check Required'}
                                </span>
                            </div>
                        </div>
                    </div>

                    <div class="analysis-section">
                        <h6>Monitoring Status</h6>
                        <div class="info-table">
                            <div class="info-row">
                                <span class="info-label">Mode:</span>
                                <span class="info-value">${summary.monitoringMode || 'Unknown'}</span>
                            </div>
                            <div class="info-row">
                                <span class="info-label">Active Tasks:</span>
                                <span class="info-value">${summary.healthyTasks || 0}</span>
                            </div>
                            <div class="info-row">
                                <span class="info-label">Session Status:</span>
                                <span class="info-value">${summary.sessionResetStatus || 'Unknown'}</span>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="recommendations">
                    <h6>Recommendations</h6>
                    <div class="recommendations-list">
                        ${!summary.systemHealthy ? `
                            <div class="recommendation warning">
                                <i class="bi bi-exclamation-triangle"></i>
                                <span>System health issues detected. Consider running health diagnostics.</span>
                            </div>
                        ` : ''}

                        ${!summary.cacheHealthy ? `
                            <div class="recommendation warning">
                                <i class="bi bi-hdd"></i>
                                <span>Cache performance issues. Consider cache refresh or reset.</span>
                            </div>
                        ` : ''}

                        ${!summary.hasUserData ? `
                            <div class="recommendation info">
                                <i class="bi bi-info-circle"></i>
                                <span>User data verification recommended.</span>
                            </div>
                        ` : ''}

                        ${summary.systemHealthy && summary.cacheHealthy && summary.hasUserData ? `
                            <div class="recommendation success">
                                <i class="bi bi-check-circle"></i>
                                <span>All systems operating normally. No immediate action required.</span>
                            </div>
                        ` : ''}
                    </div>
                </div>

                <div class="report-actions">
                    <button class="btn btn-sm btn-outline-success" onclick="window.DiagnosticsUtility.refreshSystemSummary()">
                        <i class="bi bi-arrow-repeat"></i> Refresh Report
                    </button>
                    <button class="btn btn-sm btn-outline-primary" onclick="window.DiagnosticsUtility.exportDiagnostics('system-summary')">
                        <i class="bi bi-download"></i> Export Report
                    </button>
                    <button class="btn btn-sm btn-outline-info" onclick="window.DiagnosticsUtility.scheduleHealthCheck()">
                        <i class="bi bi-calendar-plus"></i> Schedule Check
                    </button>
                </div>
            </div>
        `;
    }

    // ========================================================================
    // UTILITY FUNCTIONS
    // ========================================================================

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
    }

    // ========================================================================
    // PUBLIC ACTION METHODS (called from HTML buttons)
    // ========================================================================

    /**
     * Refresh backup event diagnostics
     */
    refreshBackupEventDiagnostics() {
        this.getBackupEventDiagnostics();
    }

    /**
     * Refresh system summary
     */
    refreshSystemSummary() {
        this.getSystemSummary();
    }

    /**
     * Export diagnostics to file
     */
    exportDiagnostics(type) {
        try {
            const content = this.elements.content?.textContent || '';
            const blob = new Blob([content], { type: 'text/plain' });
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `${type}-diagnostics-${new Date().toISOString().slice(0, 10)}.txt`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);
            ToastNotification.success('Success', 'Diagnostics exported successfully');
        } catch (error) {
            console.error('Error exporting diagnostics:', error);
            ToastNotification.error('Error', 'Failed to export diagnostics');
        }
    }

    /**
     * Schedule health check (placeholder)
     */
    scheduleHealthCheck() {
        ToastNotification.info('Info', 'Health check scheduling is not yet implemented');
    }
}

// ========================================================================
// AUTO-INITIALIZATION
// ========================================================================

// Auto-initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        initializeDiagnosticsUtility();
    });
} else {
    initializeDiagnosticsUtility();
}

function initializeDiagnosticsUtility() {
    const diagnosticsUtility = new DiagnosticsUtility();
    diagnosticsUtility.initialize();

    // Expose globally for backward compatibility and HTML onclick handlers
    window.DiagnosticsUtility = diagnosticsUtility;

    console.log('📦 DiagnosticsUtility module loaded and initialized (ES6)');
}

// Export for ES6 module usage
export default DiagnosticsUtility;
