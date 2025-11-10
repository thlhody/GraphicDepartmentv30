/**
 * SessionUtility.js (ES6 Module)
 *
 * Modern session management utility for session diagnostics and operations.
 * Handles manual session resets, midnight reset status, and user context monitoring.
 *
 * @module features/utilities/common/SessionUtility
 */

import { API } from '../../../core/api.js';
import { ToastNotification } from '../../../components/ToastNotification.js';

/**
 * SessionUtility class
 * Manages session operations with modern ES6 patterns
 */
export class SessionUtility {
    constructor() {
        this.elements = {
            // Button elements
            manualResetBtn: null,
            resetStatusBtn: null,
            contextStatusBtn: null,
            clearBtn: null,

            // Display elements
            resultsContainer: null,
            content: null,
            controls: null,

            // Status elements
            lastOperation: null,
            operationTime: null,
            resetStatus: null,
            contextHealth: null
        };

        console.log('🔧 Session utility created (ES6)');
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize the session utility
     */
    initialize() {
        try {
            this.cacheElements();
            this.setupEventListeners();
            this.refreshOverview();

            console.log('✅ Session utility initialized (ES6)');
        } catch (error) {
            console.error('❌ Error initializing session utility:', error);
            ToastNotification.error('Error', 'Failed to initialize session utility');
        }
    }

    /**
     * Cache DOM elements for better performance
     */
    cacheElements() {
        // Button elements
        this.elements.manualResetBtn = document.getElementById('manual-session-reset-btn');
        this.elements.resetStatusBtn = document.getElementById('session-reset-status-btn');
        this.elements.contextStatusBtn = document.getElementById('session-context-status-btn');
        this.elements.clearBtn = document.getElementById('clear-session-results');

        // Display elements
        this.elements.resultsContainer = document.getElementById('session-results');
        this.elements.content = document.getElementById('session-content');
        this.elements.controls = document.getElementById('session-controls');

        // Status elements
        this.elements.lastOperation = document.getElementById('last-session-operation');
        this.elements.operationTime = document.getElementById('session-operation-time');
        this.elements.resetStatus = document.getElementById('session-reset-status');
        this.elements.contextHealth = document.getElementById('session-context-health');
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Manual reset button
        if (this.elements.manualResetBtn) {
            this.elements.manualResetBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.confirmManualReset();
            });
        }

        // Reset status button
        if (this.elements.resetStatusBtn) {
            this.elements.resetStatusBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.getResetStatus();
            });
        }

        // Context status button
        if (this.elements.contextStatusBtn) {
            this.elements.contextStatusBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.getUserContextStatus();
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
                const toggleIcon = document.querySelector('#session-utility .toggle-icon');
                if (toggleIcon) {
                    toggleIcon.classList.remove('bi-chevron-down');
                    toggleIcon.classList.add('bi-chevron-up');
                }
            });

            this.elements.controls.addEventListener('hide.bs.collapse', () => {
                const toggleIcon = document.querySelector('#session-utility .toggle-icon');
                if (toggleIcon) {
                    toggleIcon.classList.remove('bi-chevron-up');
                    toggleIcon.classList.add('bi-chevron-down');
                }
            });
        }
    }

    /**
     * Refresh overview (auto-called on load)
     */
    async refreshOverview() {
        console.log('🔄 Refreshing session overview...');

        try {
            // Load both reset status and context status in parallel
            await Promise.all([
                this.loadResetStatusSilent(),
                this.loadContextStatusSilent()
            ]);
        } catch (error) {
            console.error('Error refreshing session overview:', error);
        }
    }

    // ========================================================================
    // SESSION OPERATIONS
    // ========================================================================

    /**
     * Confirm and perform manual session reset
     */
    confirmManualReset() {
        const confirmed = confirm(
            '🔄 MANUAL SESSION RESET 🔄\n\n' +
            'This will manually trigger a session midnight reset.\n\n' +
            'Use this when:\n' +
            '• Session data needs to be refreshed\n' +
            '• Day transition issues occur\n' +
            '• Manual reset is required for testing\n\n' +
            'Continue with manual session reset?'
        );

        if (confirmed) {
            this.performManualReset();
        }
    }

    /**
     * Perform manual session reset
     */
    async performManualReset() {
        const btn = this.elements.manualResetBtn;
        this.setButtonLoading(btn, true);
        this.updateStatus('Manual Reset', 'In Progress', this.getCurrentTime());

        console.log('🔄 Performing manual session reset...');

        try {
            const data = await API.post('/utility/session/manual-reset');

            if (data.success) {
                this.displayManualResetResult(data);
                this.updateStatus('Manual Reset', 'Success', this.getCurrentTime());
                ToastNotification.success('Success', 'Manual session reset completed successfully');

                // Refresh overview after reset
                setTimeout(() => {
                    this.refreshOverview();
                }, 1000);
            } else {
                this.updateStatus('Manual Reset', 'Failed', this.getCurrentTime());
                this.showError('Failed to perform manual reset: ' + data.message);
            }
        } catch (error) {
            console.error('Error performing manual reset:', error);
            this.updateStatus('Manual Reset', 'Error', this.getCurrentTime());
            this.showError(`Failed to perform manual reset: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Get midnight reset status
     */
    async getResetStatus() {
        const btn = this.elements.resetStatusBtn;
        this.setButtonLoading(btn, true);
        this.updateStatus('Reset Status', 'Loading', this.getCurrentTime());

        console.log('📊 Getting session reset status...');

        try {
            const data = await API.get('/utility/session/reset-status');

            if (data.success) {
                this.displayResetStatusResult(data);
                this.updateStatusDisplay(data.resetStatus);
                this.updateStatus('Reset Status', 'Success', this.getCurrentTime());
                ToastNotification.info('Info', `Reset Status: ${data.resetStatus}`);
            } else {
                this.updateStatus('Reset Status', 'Failed', this.getCurrentTime());
                this.showError('Failed to get reset status: ' + data.message);
            }
        } catch (error) {
            console.error('Error getting reset status:', error);
            this.updateStatus('Reset Status', 'Error', this.getCurrentTime());
            this.showError(`Failed to get reset status: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Load reset status silently (for overview)
     */
    async loadResetStatusSilent() {
        try {
            const data = await API.get('/utility/session/reset-status');
            if (data.success) {
                this.updateStatusDisplay(data.resetStatus);
            }
        } catch (error) {
            console.error('Error loading reset status silently:', error);
        }
    }

    /**
     * Get user context status
     */
    async getUserContextStatus() {
        const btn = this.elements.contextStatusBtn;
        this.setButtonLoading(btn, true);
        this.updateStatus('Context Status', 'Loading', this.getCurrentTime());

        console.log('📊 Getting user context status...');

        try {
            const data = await API.get('/utility/session/context-status');

            if (data.success) {
                this.displayContextStatusResult(data);
                this.updateContextHealthDisplay(data.isHealthy);
                this.updateStatus('Context Status', 'Success', this.getCurrentTime());

                const healthMsg = data.isHealthy ? 'User context is healthy' : 'User context has issues';
                const type = data.isHealthy ? 'success' : 'warning';
                ToastNotification[type]('Context Status', healthMsg);
            } else {
                this.updateStatus('Context Status', 'Failed', this.getCurrentTime());
                this.showError('Failed to get user context status: ' + data.message);
            }
        } catch (error) {
            console.error('Error getting user context status:', error);
            this.updateStatus('Context Status', 'Error', this.getCurrentTime());
            this.showError(`Failed to get user context status: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Load context status silently (for overview)
     */
    async loadContextStatusSilent() {
        try {
            const data = await API.get('/utility/session/context-status');
            if (data.success) {
                this.updateContextHealthDisplay(data.isHealthy);
            }
        } catch (error) {
            console.error('Error loading context status silently:', error);
        }
    }

    // ========================================================================
    // DISPLAY FUNCTIONS
    // ========================================================================

    /**
     * Display manual reset result
     */
    displayManualResetResult(data) {
        let html = '<div class="success-section">';
        html += '<h5><i class="bi bi-check-circle text-success"></i> Manual Session Reset Completed</h5>';
        html += '<div class="info-grid">';

        html += '<div class="info-item">';
        html += '<span class="info-label">Username:</span>';
        html += '<span class="info-value"><strong>' + data.username + '</strong></span>';
        html += '</div>';

        html += '<div class="info-item">';
        html += '<span class="info-label">Reset Time:</span>';
        html += '<span class="info-value">' + data.timestamp + '</span>';
        html += '</div>';

        html += '</div>';

        html += '<div class="alert alert-success mt-3">';
        html += '<strong>Success:</strong> ' + data.message;
        html += '</div>';

        html += '<div class="alert alert-info mt-2">';
        html += '<i class="bi bi-info-circle"></i> ';
        html += '<strong>Note:</strong> Session has been manually reset. All session data has been refreshed.';
        html += '</div>';

        html += '</div>';

        if (this.elements.content) {
            this.elements.content.innerHTML = html;
        }
        this.showResults();
    }

    /**
     * Display reset status result
     */
    displayResetStatusResult(data) {
        const status = data.resetStatus || 'Unknown';
        const isHealthy = status.includes('Enabled') || status.includes('Active') || status.includes('Running');

        let html = '<div class="info-section">';
        html += '<h5><i class="bi bi-clock-history"></i> Session Reset Status</h5>';
        html += '<div class="info-grid">';

        html += '<div class="info-item">';
        html += '<span class="info-label">Current Status:</span>';
        html += '<span class="info-value ' + (isHealthy ? 'text-success' : 'text-warning') + '"><strong>' + status + '</strong></span>';
        html += '</div>';

        html += '<div class="info-item">';
        html += '<span class="info-label">Check Time:</span>';
        html += '<span class="info-value">' + data.timestamp + '</span>';
        html += '</div>';

        html += '</div>';

        html += '<div class="alert ' + (isHealthy ? 'alert-success' : 'alert-warning') + ' mt-3">';
        html += '<i class="bi ' + (isHealthy ? 'bi-check-circle' : 'bi-exclamation-triangle') + '"></i> ';
        html += '<strong>Status:</strong> ' + (isHealthy ? 'Midnight reset is active and running normally' : 'Check midnight reset configuration');
        html += '</div>';

        html += '</div>';

        if (this.elements.content) {
            this.elements.content.innerHTML = html;
        }
        this.showResults();
    }

    /**
     * Display user context status result
     */
    displayContextStatusResult(data) {
        const isHealthy = data.isHealthy && data.hasRealUser && data.isInitialized;

        let html = '<div class="info-section">';
        html += '<h5><i class="bi bi-person-circle"></i> User Context Status</h5>';
        html += '<div class="info-grid">';

        html += '<div class="info-item">';
        html += '<span class="info-label">Current Username:</span>';
        html += '<span class="info-value"><strong>' + (data.currentUsername || 'N/A') + '</strong></span>';
        html += '</div>';

        html += '<div class="info-item">';
        html += '<span class="info-label">User ID:</span>';
        html += '<span class="info-value">' + (data.currentUserId || 'N/A') + '</span>';
        html += '</div>';

        html += '<div class="info-item">';
        html += '<span class="info-label">Is Healthy:</span>';
        html += '<span class="info-value ' + (data.isHealthy ? 'text-success' : 'text-danger') + '">';
        html += data.isHealthy ? 'Yes' : 'No';
        html += '</span>';
        html += '</div>';

        html += '<div class="info-item">';
        html += '<span class="info-label">Has Real User:</span>';
        html += '<span class="info-value ' + (data.hasRealUser ? 'text-success' : 'text-warning') + '">';
        html += data.hasRealUser ? 'Yes' : 'No';
        html += '</span>';
        html += '</div>';

        html += '<div class="info-item">';
        html += '<span class="info-label">Is Initialized:</span>';
        html += '<span class="info-value ' + (data.isInitialized ? 'text-success' : 'text-warning') + '">';
        html += data.isInitialized ? 'Yes' : 'No';
        html += '</span>';
        html += '</div>';

        html += '<div class="info-item">';
        html += '<span class="info-label">Check Time:</span>';
        html += '<span class="info-value">' + data.timestamp + '</span>';
        html += '</div>';

        html += '</div>';

        html += '<div class="alert ' + (isHealthy ? 'alert-success' : 'alert-warning') + ' mt-3">';
        html += '<i class="bi ' + (isHealthy ? 'bi-check-circle' : 'bi-exclamation-triangle') + '"></i> ';
        html += '<strong>Overall Status:</strong> ';
        html += isHealthy ? 'User context is healthy and fully initialized' : 'User context may have issues - check details above';
        html += '</div>';

        if (!data.hasRealUser || !data.isInitialized) {
            html += '<div class="alert alert-info mt-2">';
            html += '<i class="bi bi-info-circle"></i> ';
            html += '<strong>Recommendation:</strong> ';
            if (!data.hasRealUser) {
                html += 'User context does not have a real user loaded. ';
            }
            if (!data.isInitialized) {
                html += 'Cache is not initialized. ';
            }
            html += 'Consider refreshing cache or checking system health.';
            html += '</div>';
        }

        html += '</div>';

        if (this.elements.content) {
            this.elements.content.innerHTML = html;
        }
        this.showResults();
    }

    /**
     * Show error message
     */
    showError(message) {
        console.error('Session Utility Error:', message);

        const html = '<div class="alert alert-danger">' +
                    '<i class="bi bi-exclamation-triangle"></i> ' + message +
                    '</div>';

        if (this.elements.content) {
            this.elements.content.innerHTML = html;
        }
        this.showResults();

        ToastNotification.error('Error', message);
    }

    // ========================================================================
    // UPDATE FUNCTIONS
    // ========================================================================

    /**
     * Update status display
     */
    updateStatus(operation, result, time) {
        if (this.elements.lastOperation) {
            this.elements.lastOperation.textContent = operation;
        }

        if (this.elements.operationTime) {
            this.elements.operationTime.textContent = time;
        }
    }

    /**
     * Update reset status display
     */
    updateStatusDisplay(status) {
        if (this.elements.resetStatus) {
            this.elements.resetStatus.textContent = status || 'Unknown';

            const isHealthy = status && (status.includes('Enabled') || status.includes('Active') || status.includes('Running'));

            this.elements.resetStatus.classList.remove('text-success', 'text-warning', 'text-danger');
            this.elements.resetStatus.classList.add(isHealthy ? 'text-success' : 'text-warning');
        }
    }

    /**
     * Update context health display
     */
    updateContextHealthDisplay(isHealthy) {
        if (this.elements.contextHealth) {
            this.elements.contextHealth.textContent = isHealthy ? 'Healthy' : 'Issues';

            this.elements.contextHealth.classList.remove('text-success', 'text-warning', 'text-danger');
            this.elements.contextHealth.classList.add(isHealthy ? 'text-success' : 'text-warning');
        }
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
     * Show results container
     */
    showResults() {
        if (this.elements.resultsContainer) {
            this.elements.resultsContainer.style.display = 'block';
            setTimeout(() => {
                this.elements.resultsContainer.style.opacity = '1';
            }, 10);
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
    // PUBLIC METHODS (backward compatibility)
    // ========================================================================

    /**
     * Manual reset (public alias)
     */
    manualReset() {
        this.confirmManualReset();
    }

    /**
     * Get status (public alias)
     */
    getStatus() {
        this.getResetStatus();
    }

    /**
     * Get context (public alias)
     */
    getContext() {
        this.getUserContextStatus();
    }

    /**
     * Refresh (public alias)
     */
    refresh() {
        this.refreshOverview();
    }
}

// ========================================================================
// AUTO-INITIALIZATION
// ========================================================================

// Auto-initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        initializeSessionUtility();
    });
} else {
    initializeSessionUtility();
}

function initializeSessionUtility() {
    const sessionUtility = new SessionUtility();
    sessionUtility.initialize();

    // Expose globally for backward compatibility
    window.SessionUtility = sessionUtility;

    console.log('📦 SessionUtility module loaded and initialized (ES6)');
}

// Export for ES6 module usage
export default SessionUtility;
