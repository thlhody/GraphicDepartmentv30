/**
 * MergeUtility.js (ES6 Module)
 *
 * Modern merge management utility for monitoring and controlling merge operations.
 * Handles pending merge status, merge strategy monitoring, and data refresh operations.
 *
 * @module features/utilities/common/MergeUtility
 */

import { API } from '../../../core/api.js';
import { ToastNotification } from '../../../components/ToastNotification.js';

/**
 * MergeUtility class
 * Manages merge operations with modern ES6 patterns
 */
export class MergeUtility {
    constructor() {
        // State
        this.state = {
            pendingCount: 0,
            userHasPending: false,
            severity: 'none'
        };

        this.elements = {
            // Button elements
            checkStatusBtn: null,
            refreshCountBtn: null,
            clearPendingBtn: null,
            checkStrategyBtn: null,
            forceFullMergeBtn: null,
            clearBtn: null,

            // Display elements
            resultsContainer: null,
            content: null,
            controls: null,

            // Status elements
            pendingCount: null,
            severity: null,
            userStatus: null,
            warningBanner: null,
            warningText: null,

            // Strategy elements
            loginCount: null,
            nextType: null,
            performance: null,
            strategyBanner: null,
            strategyText: null
        };

        console.log('🔀 Merge utility created (ES6)');
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize the merge utility
     */
    initialize() {
        try {
            this.cacheElements();
            this.setupEventListeners();
            this.refreshOverview();

            console.log('✅ Merge utility initialized (ES6)');
        } catch (error) {
            console.error('❌ Error initializing merge utility:', error);
            ToastNotification.error('Error', 'Failed to initialize merge utility');
        }
    }

    /**
     * Cache DOM elements for better performance
     */
    cacheElements() {
        // Button elements
        this.elements.checkStatusBtn = document.getElementById('check-merge-status-btn');
        this.elements.refreshCountBtn = document.getElementById('refresh-merge-count-btn');
        this.elements.clearPendingBtn = document.getElementById('clear-pending-merges-btn');
        this.elements.checkStrategyBtn = document.getElementById('check-strategy-status-btn');
        this.elements.forceFullMergeBtn = document.getElementById('force-full-merge-btn');
        this.elements.clearBtn = document.getElementById('clear-merge-results');

        // Display elements
        this.elements.resultsContainer = document.getElementById('merge-results');
        this.elements.content = document.getElementById('merge-content');
        this.elements.controls = document.getElementById('merge-controls');

        // Status elements
        this.elements.pendingCount = document.getElementById('pending-merge-count');
        this.elements.severity = document.getElementById('merge-severity');
        this.elements.userStatus = document.getElementById('user-merge-status');
        this.elements.warningBanner = document.getElementById('merge-warning-banner');
        this.elements.warningText = document.getElementById('merge-warning-text');

        // Strategy elements
        this.elements.loginCount = document.getElementById('strategy-login-count');
        this.elements.nextType = document.getElementById('strategy-next-type');
        this.elements.performance = document.getElementById('strategy-performance');
        this.elements.strategyBanner = document.getElementById('strategy-info-banner');
        this.elements.strategyText = document.getElementById('strategy-info-text');
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Check merge status
        if (this.elements.checkStatusBtn) {
            this.elements.checkStatusBtn.addEventListener('click', () => {
                this.checkMergeStatus();
            });
        }

        // Refresh merge count
        if (this.elements.refreshCountBtn) {
            this.elements.refreshCountBtn.addEventListener('click', () => {
                this.refreshMergeCount();
            });
        }

        // Clear pending merges
        if (this.elements.clearPendingBtn) {
            this.elements.clearPendingBtn.addEventListener('click', () => {
                this.confirmClearPendingMerges();
            });
        }

        // Check strategy status
        if (this.elements.checkStrategyBtn) {
            this.elements.checkStrategyBtn.addEventListener('click', () => {
                this.checkStrategyStatus();
            });
        }

        // Force full merge
        if (this.elements.forceFullMergeBtn) {
            this.elements.forceFullMergeBtn.addEventListener('click', () => {
                this.confirmForceFullMerge();
            });
        }

        // Clear results
        if (this.elements.clearBtn) {
            this.elements.clearBtn.addEventListener('click', () => {
                this.clearResults();
            });
        }
    }

    /**
     * Refresh overview (auto-called on load)
     */
    refreshOverview() {
        console.log('🔄 Refreshing merge overview...');
        this.refreshMergeCount();
    }

    // ========================================================================
    // MERGE STATUS OPERATIONS
    // ========================================================================

    /**
     * Check merge status for current user
     */
    async checkMergeStatus() {
        console.log('🔍 Checking merge status...');

        try {
            const data = await API.get('/utility/merge/pending-status');

            if (data.success) {
                this.displayMergeStatus(data);
                this.updateStats(data);

                // Show toast notification
                const message = data.hasPendingMerges
                    ? `You have ${data.pendingCount} pending merge(s)`
                    : 'No pending merges';
                const type = data.hasPendingMerges ? 'warning' : 'success';
                ToastNotification[type]('Merge Status', message);
            } else {
                this.showError('Failed to check merge status: ' + data.message);
            }
        } catch (error) {
            console.error('Error checking merge status:', error);
            this.showError(`Failed to check merge status: ${error.message}`);
        }
    }

    /**
     * Refresh merge count (lighter operation)
     */
    async refreshMergeCount() {
        try {
            const data = await API.get('/utility/merge/pending-count');

            if (data.success) {
                this.state.pendingCount = data.pendingCount;
                this.state.severity = data.severity || 'none';

                // Update display
                if (this.elements.pendingCount) {
                    this.elements.pendingCount.textContent = data.pendingCount;
                }
                if (this.elements.severity) {
                    this.elements.severity.textContent = this.getSeverityText(data.severity);
                }

                // Update severity styling
                this.updateSeverityStyle(data.severity);

                // Show warning banner if needed
                if (data.pendingCount > 0 && data.recommendation) {
                    this.showWarningBanner(data.recommendation);
                } else {
                    this.hideWarningBanner();
                }
            }
        } catch (error) {
            console.error('Error refreshing merge count:', error);
            if (this.elements.pendingCount) {
                this.elements.pendingCount.textContent = 'Error';
            }
            if (this.elements.severity) {
                this.elements.severity.textContent = '-';
            }
        }
    }

    /**
     * Confirm and clear pending merges
     */
    confirmClearPendingMerges() {
        const confirmed = confirm(
            '⚠️ CLEAR PENDING MERGES ⚠️\n\n' +
            'This will clear ALL pending merge operations from the queue.\n\n' +
            'ONLY proceed if:\n' +
            '• Merge operations are confirmed stuck\n' +
            '• Network issues prevented completion\n' +
            '• Merges have not completed after long wait\n\n' +
            'Are you sure you want to continue?'
        );

        if (confirmed) {
            this.clearPendingMerges();
        }
    }

    /**
     * Clear all pending merges
     */
    async clearPendingMerges() {
        console.log('🗑️ Clearing pending merges...');

        try {
            const data = await API.post('/utility/merge/clear-pending');

            if (data.success) {
                this.displayClearResult(data);
                this.refreshMergeCount(); // Refresh count after clear

                // Show success notification
                ToastNotification.success('Success',
                    `Cleared ${data.clearedCount} pending merge operation(s)`);
            } else {
                this.showError('Failed to clear pending merges: ' + data.message);
            }
        } catch (error) {
            console.error('Error clearing pending merges:', error);
            this.showError(`Failed to clear pending merges: ${error.message}`);
        }
    }

    // ========================================================================
    // MERGE STRATEGY OPERATIONS
    // ========================================================================

    /**
     * Check merge strategy status
     */
    async checkStrategyStatus() {
        console.log('📊 Checking merge strategy status...');

        try {
            const data = await API.get('/utility/merge/strategy-status');

            if (data.success) {
                this.displayStrategyStatus(data);
                this.updateStrategyStats(data);

                // Show toast notification
                ToastNotification.info('Strategy Status', data.statusDescription);
            } else {
                this.showError('Failed to check strategy status: ' + data.message);
            }
        } catch (error) {
            console.error('Error checking strategy status:', error);
            this.showError(`Failed to check strategy status: ${error.message}`);
        }
    }

    /**
     * Confirm and force full merge
     */
    confirmForceFullMerge() {
        const confirmed = confirm(
            '🔄 FORCE FULL DATA REFRESH 🔄\n\n' +
            'This will force a complete data merge on your next login.\n\n' +
            'Use this when:\n' +
            '• Your data seems outdated or stale\n' +
            '• Changes from admin are not showing up\n' +
            '• You need to ensure you have the latest data\n\n' +
            'Next login will take ~7 seconds instead of ~0.1 seconds.\n\n' +
            'Continue?'
        );

        if (confirmed) {
            this.forceFullMerge();
        }
    }

    /**
     * Force full merge on next login
     */
    async forceFullMerge() {
        console.log('🔄 Forcing full merge on next login...');

        try {
            const data = await API.post('/utility/merge/force-full-merge');

            if (data.success) {
                this.displayForceResult(data);
                this.checkStrategyStatus(); // Refresh strategy status

                // Show success notification
                ToastNotification.success('Success',
                    'Full data refresh scheduled for next login');
            } else {
                this.showError('Failed to force full merge: ' + data.message);
            }
        } catch (error) {
            console.error('Error forcing full merge:', error);
            this.showError(`Failed to force full merge: ${error.message}`);
        }
    }

    // ========================================================================
    // DISPLAY FUNCTIONS
    // ========================================================================

    /**
     * Display merge status in results area
     */
    displayMergeStatus(data) {
        let html = '<div class="info-section">';
        html += '<h5>Merge Status for ' + data.username + '</h5>';
        html += '<div class="info-grid">';

        html += '<div class="info-item">';
        html += '<span class="info-label">Has Pending Merges:</span>';
        html += '<span class="info-value ' + (data.hasPendingMerges ? 'text-warning' : 'text-success') + '">';
        html += data.hasPendingMerges ? 'Yes' : 'No';
        html += '</span>';
        html += '</div>';

        html += '<div class="info-item">';
        html += '<span class="info-label">Total Pending Count:</span>';
        html += '<span class="info-value">' + data.pendingCount + '</span>';
        html += '</div>';

        html += '<div class="info-item">';
        html += '<span class="info-label">Timestamp:</span>';
        html += '<span class="info-value">' + data.timestamp + '</span>';
        html += '</div>';

        html += '</div>';

        if (data.warning) {
            html += '<div class="alert alert-warning mt-3">';
            html += '<strong>Warning:</strong> ' + data.warning;
            html += '</div>';
        }

        if (data.action) {
            html += '<div class="alert alert-info mt-2">';
            html += '<strong>Recommendation:</strong> ' + data.action;
            html += '</div>';
        }

        if (!data.hasPendingMerges) {
            html += '<div class="alert alert-success mt-3">';
            html += '<i class="bi bi-check-circle"></i> ' + (data.message || 'No pending merge operations');
            html += '</div>';
        }

        html += '</div>';

        if (this.elements.content) {
            this.elements.content.innerHTML = html;
        }
        this.showResults();
    }

    /**
     * Display clear result
     */
    displayClearResult(data) {
        let html = '<div class="success-section">';
        html += '<h5><i class="bi bi-check-circle text-success"></i> Pending Merges Cleared</h5>';
        html += '<div class="info-grid">';

        html += '<div class="info-item">';
        html += '<span class="info-label">Operations Cleared:</span>';
        html += '<span class="info-value text-success"><strong>' + data.clearedCount + '</strong></span>';
        html += '</div>';

        html += '<div class="info-item">';
        html += '<span class="info-label">Timestamp:</span>';
        html += '<span class="info-value">' + data.timestamp + '</span>';
        html += '</div>';

        html += '</div>';

        html += '<div class="alert alert-success mt-3">';
        html += '<strong>Success:</strong> ' + data.message;
        html += '</div>';

        html += '</div>';

        if (this.elements.content) {
            this.elements.content.innerHTML = html;
        }
        this.showResults();
    }

    /**
     * Display strategy status in results area
     */
    displayStrategyStatus(data) {
        let html = '<div class="info-section">';
        html += '<h5><i class="bi bi-speedometer2"></i> Login Merge Strategy Status</h5>';
        html += '<div class="info-grid">';

        html += '<div class="info-item">';
        html += '<span class="info-label">Current Login Count:</span>';
        html += '<span class="info-value"><strong>' + data.loginCount + '</strong></span>';
        html += '</div>';

        html += '<div class="info-item">';
        html += '<span class="info-label">Is First Login:</span>';
        html += '<span class="info-value ' + (data.isFirstLogin ? 'text-warning' : 'text-success') + '">';
        html += data.isFirstLogin ? 'Yes' : 'No';
        html += '</span>';
        html += '</div>';

        html += '<div class="info-item">';
        html += '<span class="info-label">Next Merge Type:</span>';
        html += '<span class="info-value ' + (data.shouldPerformFullMerge ? 'text-warning' : 'text-info') + '">';
        html += data.shouldPerformFullMerge ? 'Full Merge' : 'Fast Refresh';
        html += '</span>';
        html += '</div>';

        html += '<div class="info-item">';
        html += '<span class="info-label">Performance Benefit:</span>';
        html += '<span class="info-value">' + data.performanceBenefit + '</span>';
        html += '</div>';

        html += '<div class="info-item">';
        html += '<span class="info-label">Timestamp:</span>';
        html += '<span class="info-value">' + data.timestamp + '</span>';
        html += '</div>';

        html += '</div>';

        html += '<div class="alert alert-info mt-3">';
        html += '<strong>Status:</strong> ' + data.statusDescription;
        html += '</div>';

        if (data.isFirstLogin) {
            html += '<div class="alert alert-warning mt-2">';
            html += '<i class="bi bi-exclamation-triangle"></i> ';
            html += '<strong>First Login:</strong> Login will perform full data merge (~7 seconds)';
            html += '</div>';
        }

        html += '</div>';

        if (this.elements.content) {
            this.elements.content.innerHTML = html;
        }
        this.showResults();
    }

    /**
     * Display force merge result
     */
    displayForceResult(data) {
        let html = '<div class="success-section">';
        html += '<h5><i class="bi bi-check-circle text-success"></i> Full Data Refresh Scheduled</h5>';
        html += '<div class="info-grid">';

        html += '<div class="info-item">';
        html += '<span class="info-label">Previous Login Count:</span>';
        html += '<span class="info-value">' + data.previousLoginCount + '</span>';
        html += '</div>';

        html += '<div class="info-item">';
        html += '<span class="info-label">New Login Count:</span>';
        html += '<span class="info-value text-success"><strong>' + data.newLoginCount + '</strong></span>';
        html += '</div>';

        html += '<div class="info-item">';
        html += '<span class="info-label">Next Login Strategy:</span>';
        html += '<span class="info-value text-warning"><strong>' + data.nextStrategy + '</strong></span>';
        html += '</div>';

        html += '<div class="info-item">';
        html += '<span class="info-label">Timestamp:</span>';
        html += '<span class="info-value">' + data.timestamp + '</span>';
        html += '</div>';

        html += '</div>';

        html += '<div class="alert alert-success mt-3">';
        html += '<strong>Success:</strong> ' + data.message;
        html += '</div>';

        html += '<div class="alert alert-info mt-2">';
        html += '<i class="bi bi-info-circle"></i> ';
        html += '<strong>What\'s Next:</strong> Log out and log back in to perform full data refresh. ';
        html += 'This will ensure you have the latest data from all sources.';
        html += '</div>';

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
        console.error('Merge Utility Error:', message);

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
     * Update stats display
     */
    updateStats(data) {
        if (this.elements.pendingCount) {
            this.elements.pendingCount.textContent = data.pendingCount;
        }

        if (this.elements.userStatus) {
            this.elements.userStatus.textContent = data.hasPendingMerges ? 'Has Pending' : 'No Pending';

            // Update user status styling
            this.elements.userStatus.classList.remove('text-success', 'text-warning', 'text-danger');
            this.elements.userStatus.classList.add(data.hasPendingMerges ? 'text-warning' : 'text-success');
        }
    }

    /**
     * Update severity styling
     */
    updateSeverityStyle(severity) {
        if (!this.elements.severity) return;

        this.elements.severity.classList.remove('text-success', 'text-info', 'text-warning', 'text-danger');

        switch(severity) {
            case 'high':
                this.elements.severity.classList.add('text-danger');
                break;
            case 'medium':
                this.elements.severity.classList.add('text-warning');
                break;
            case 'low':
                this.elements.severity.classList.add('text-info');
                break;
            default:
                this.elements.severity.classList.add('text-success');
        }
    }

    /**
     * Update strategy stats display
     */
    updateStrategyStats(data) {
        if (this.elements.loginCount) {
            this.elements.loginCount.textContent = data.loginCount;
        }

        // Determine next strategy text
        const nextStrategy = data.shouldPerformFullMerge ? 'Full Merge' : 'Fast Refresh';
        if (this.elements.nextType) {
            this.elements.nextType.textContent = nextStrategy;

            // Update next strategy styling
            this.elements.nextType.classList.remove('text-success', 'text-warning', 'text-info');
            this.elements.nextType.classList.add(data.shouldPerformFullMerge ? 'text-warning' : 'text-success');
        }

        // Update performance display
        if (this.elements.performance) {
            this.elements.performance.textContent = data.performanceBenefit;
        }

        // Show info banner if first login
        if (data.isFirstLogin) {
            this.showStrategyInfoBanner('First login of the day - next login will perform full data merge');
        } else {
            this.hideStrategyInfoBanner();
        }
    }

    // ========================================================================
    // UTILITY FUNCTIONS
    // ========================================================================

    /**
     * Get severity text
     */
    getSeverityText(severity) {
        switch(severity) {
            case 'high': return 'High';
            case 'medium': return 'Medium';
            case 'low': return 'Low';
            case 'none': return 'None';
            default: return '-';
        }
    }

    /**
     * Show warning banner
     */
    showWarningBanner(message) {
        if (this.elements.warningText) {
            this.elements.warningText.textContent = message;
        }
        if (this.elements.warningBanner) {
            this.elements.warningBanner.style.display = 'block';
            setTimeout(() => {
                this.elements.warningBanner.style.opacity = '1';
            }, 10);
        }
    }

    /**
     * Hide warning banner
     */
    hideWarningBanner() {
        if (this.elements.warningBanner) {
            this.elements.warningBanner.style.opacity = '0';
            setTimeout(() => {
                this.elements.warningBanner.style.display = 'none';
            }, 300);
        }
    }

    /**
     * Show strategy info banner
     */
    showStrategyInfoBanner(message) {
        if (this.elements.strategyText) {
            this.elements.strategyText.textContent = message;
        }
        if (this.elements.strategyBanner) {
            this.elements.strategyBanner.style.display = 'block';
            setTimeout(() => {
                this.elements.strategyBanner.style.opacity = '1';
            }, 10);
        }
    }

    /**
     * Hide strategy info banner
     */
    hideStrategyInfoBanner() {
        if (this.elements.strategyBanner) {
            this.elements.strategyBanner.style.opacity = '0';
            setTimeout(() => {
                this.elements.strategyBanner.style.display = 'none';
            }, 300);
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
}

// ========================================================================
// AUTO-INITIALIZATION
// ========================================================================

// Auto-initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        initializeMergeUtility();
    });
} else {
    initializeMergeUtility();
}

function initializeMergeUtility() {
    const mergeUtility = new MergeUtility();
    mergeUtility.initialize();

    // Expose globally for backward compatibility
    window.MergeUtility = mergeUtility;

    console.log('📦 MergeUtility module loaded and initialized (ES6)');
}

// Export for ES6 module usage
export default MergeUtility;
