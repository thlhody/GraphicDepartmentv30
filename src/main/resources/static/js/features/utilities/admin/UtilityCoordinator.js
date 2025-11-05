/**
 * Utility Coordinator - Admin Utility Management
 *
 * Main coordinator for admin utility pages (Health, Monitor, Session, Backup, Diagnostics).
 * Handles system health monitoring, global refresh, auto-refresh, and cross-utility integration.
 *
 * NOTE: This module currently uses jQuery as it coordinates with legacy admin utility modules.
 * jQuery dependency will be removed when individual utility modules are refactored (Task 3.15).
 *
 * @module features/utilities/admin/UtilityCoordinator
 */

// ============================================================================
// STATE MANAGEMENT
// ============================================================================

/**
 * Global state object for utilities to share information
 */
const UtilityState = {
    systemHealth: {
        isHealthy: false,
        lastCheck: null,
        tasks: {}
    },
    cache: {
        hasUserData: false,
        userCount: 0,
        lastRefresh: null
    },
    session: {
        isHealthy: false,
        username: null,
        lastReset: null
    },
    backup: {
        lastOperation: null,
        availableBackups: 0
    }
};

/**
 * Event system for utilities to communicate
 */
const UtilityEvents = {
    trigger: function(eventName, data) {
        $(window).trigger('utility:' + eventName, data);
    },
    on: function(eventName, handler) {
        $(window).on('utility:' + eventName, handler);
    },
    off: function(eventName, handler) {
        $(window).off('utility:' + eventName, handler);
    }
};

// ============================================================================
// SYSTEM HEALTH MONITORING
// ============================================================================

/**
 * Initialize system health monitoring
 */
function initializeSystemHealth() {
    console.log('🔍 Initializing system health monitoring...');

    // Initial health check
    checkSystemHealthStatus();

    // Setup periodic health checks every 5 minutes
    setInterval(checkSystemHealthStatus, 300000);
}

/**
 * Check current system health status
 */
function checkSystemHealthStatus() {
    $.ajax({
        url: '/utility/health/overall',
        method: 'GET',
        success: function(response) {
            if (response.success) {
                updateMainHealthIndicator(response.overallHealthy);
            } else {
                updateMainHealthIndicator(false);
            }
        },
        error: function() {
            updateMainHealthIndicator(false);
        }
    });
}

/**
 * Update main health indicator in UI
 * @param {boolean} isHealthy - Whether system is healthy
 */
function updateMainHealthIndicator(isHealthy) {
    const indicator = $('#system-health-indicator');
    const statusText = $('#health-status');

    if (isHealthy) {
        indicator.removeClass('unhealthy').addClass('healthy');
        statusText.text('Healthy');
    } else {
        indicator.removeClass('healthy').addClass('unhealthy');
        statusText.text('Issues');
    }
}

// ============================================================================
// GLOBAL REFRESH MECHANISM
// ============================================================================

/**
 * Set up global refresh mechanism
 */
function setupGlobalRefresh() {
    console.log('🔄 Setting up global refresh mechanism...');
}

/**
 * Refresh all utilities
 */
function refreshAllUtilities() {
    console.log('🔄 Refreshing all utilities...');

    showToast('Info', 'Refreshing all utility data...', 'info');

    const refreshPromises = [];

    // Refresh each utility if available
    if (typeof window.MonitorUtility !== 'undefined') {
        refreshPromises.push(
            new Promise(resolve => {
                try {
                    window.MonitorUtility.refreshOverview();
                    setTimeout(resolve, 1000);
                } catch (e) {
                    console.warn('Monitor utility refresh failed:', e);
                    resolve();
                }
            })
        );
    }

    if (typeof window.SessionUtility !== 'undefined') {
        refreshPromises.push(
            new Promise(resolve => {
                try {
                    window.SessionUtility.refreshStatus();
                    setTimeout(resolve, 1000);
                } catch (e) {
                    console.warn('Session utility refresh failed:', e);
                    resolve();
                }
            })
        );
    }

    if (typeof window.HealthUtility !== 'undefined') {
        refreshPromises.push(
            new Promise(resolve => {
                try {
                    window.HealthUtility.refreshOverview();
                    setTimeout(resolve, 1000);
                } catch (e) {
                    console.warn('Health utility refresh failed:', e);
                    resolve();
                }
            })
        );
    }

    if (typeof window.BackupUtility !== 'undefined') {
        refreshPromises.push(
            new Promise(resolve => {
                try {
                    window.BackupUtility.refreshStatus();
                    setTimeout(resolve, 1000);
                } catch (e) {
                    console.warn('Backup utility refresh failed:', e);
                    resolve();
                }
            })
        );
    }

    // Refresh system health
    refreshPromises.push(
        new Promise(resolve => {
            checkSystemHealthStatus();
            setTimeout(resolve, 1000);
        })
    );

    Promise.all(refreshPromises).then(() => {
        showToast('Success', 'All utilities refreshed successfully', 'success');
    }).catch(() => {
        showToast('Warning', 'Some utilities may not have refreshed completely', 'warning');
    });
}

/**
 * Refresh system status
 */
function refreshSystemStatus() {
    console.log('🔄 Refreshing system status...');
    checkSystemHealthStatus();

    if (typeof window.HealthUtility !== 'undefined') {
        window.HealthUtility.refreshOverview();
    }
}

// ============================================================================
// AUTO-REFRESH SETUP
// ============================================================================

/**
 * Set up auto-refresh timers
 */
function setupAutoRefresh() {
    console.log('⏰ Setting up auto-refresh timers...');

    // Update header timestamp every second
    setInterval(updateHeaderTimestamp, 1000);

    // Refresh system health every 5 minutes
    setInterval(checkSystemHealthStatus, 300000);

    // Refresh utility overviews every 3 minutes
    setInterval(refreshUtilityOverviews, 180000);
}

/**
 * Update header timestamp
 */
function updateHeaderTimestamp() {
    const now = new Date();
    const timeString = now.toLocaleTimeString('en-US', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });

    // Update the header timestamp if it exists
    const timestampElement = $('.stat-value').filter(function() {
        return $(this).closest('.stat-card').find('.stat-label').text() === 'Last Updated';
    });

    if (timestampElement.length) {
        timestampElement.text(timeString);
    }
}

/**
 * Refresh utility overviews (auto-refresh)
 */
function refreshUtilityOverviews() {
    console.log('🔄 Auto-refreshing utility overviews...');

    // Health overview
    if (typeof window.HealthUtility !== 'undefined' && window.HealthUtility.refreshOverview) {
        window.HealthUtility.refreshOverview();
    }

    // Monitor overview
    if (typeof window.MonitorUtility !== 'undefined' && window.MonitorUtility.refreshOverview) {
        window.MonitorUtility.refreshOverview();
    }

    // Session overview
    if (typeof window.SessionUtility !== 'undefined' && window.SessionUtility.refreshStatus) {
        window.SessionUtility.refreshStatus();
    }

    // Backup status
    if (typeof window.BackupUtility !== 'undefined' && window.BackupUtility.refreshStatus) {
        window.BackupUtility.refreshStatus();
    }
}

// ============================================================================
// CROSS-UTILITY INTEGRATION
// ============================================================================

/**
 * Initialize cross-utility integration
 */
function initializeCrossUtilityIntegration() {
    console.log('🔗 Initializing cross-utility integration...');

    // Setup utility state sharing
    setupUtilityStateSharing();

    // Setup coordinated error handling
    setupCoordinatedErrorHandling();

    // Setup utility communication
    setupUtilityCommunication();
}

/**
 * Set up utility state sharing
 */
function setupUtilityStateSharing() {
    // State is already defined at module level
    window.UtilityState = UtilityState;
}

/**
 * Set up coordinated error handling
 */
function setupCoordinatedErrorHandling() {
    // Global error handler for utility operations
    window.addEventListener('error', function(event) {
        console.error('Utility Error:', event.error);
        if (typeof window.showToast === 'function') {
            window.showToast('System Error', 'An unexpected error occurred. Check console for details.', 'error');
        }
    });

    // Setup AJAX error handler for all utility requests
    $(document).ajaxError(function(event, xhr, settings, thrownError) {
        if (settings.url && settings.url.includes('/utility/')) {
            console.error('Utility AJAX Error:', {
                url: settings.url,
                status: xhr.status,
                error: thrownError
            });

            // Don't show toast for every AJAX error to avoid spam
            // Individual utilities will handle their own error display
        }
    });
}

/**
 * Set up utility communication
 */
function setupUtilityCommunication() {
    // Events system is already defined at module level
    window.UtilityEvents = UtilityEvents;

    // Setup standard utility events
    UtilityEvents.on('cacheUpdated', function(event, data) {
        console.log('Cache updated:', data);
        // Notify other utilities that cache has been updated
        if (typeof window.SessionUtility !== 'undefined') {
            window.SessionUtility.refreshStatus();
        }
    });

    UtilityEvents.on('sessionReset', function(event, data) {
        console.log('Session reset:', data);
        // Refresh all utilities after session reset
        setTimeout(refreshAllUtilities, 2000);
    });

    UtilityEvents.on('healthStatusChanged', function(event, data) {
        console.log('Health status changed:', data);
        updateMainHealthIndicator(data.isHealthy);
    });
}

// ============================================================================
// EMERGENCY OPERATIONS
// ============================================================================

/**
 * Perform emergency cache reset
 */
function performEmergencyReset() {
    console.log('🚨 Performing emergency reset...');

    if (!confirm('This will perform an emergency cache reset. This action will:\n\n' +
    '• Clear all cached data\n' +
    '• Reset session state\n' +
    '• Reload user information\n\n' +
    'Are you sure you want to continue?')) {
        return;
    }

    showToast('Warning', 'Performing emergency reset...', 'warning');

    $.ajax({
        url: '/utility/cache/emergency-reset',
        method: 'POST',
        success: function(response) {
            if (response.success) {
                showToast('Success', 'Emergency reset completed successfully', 'success');

                // Trigger utility event
                UtilityEvents.trigger('emergencyReset', response);

                // Refresh all utilities after reset
                setTimeout(() => {
                    refreshAllUtilities();
                }, 2000);
            } else {
                showToast('Error', response.message || 'Emergency reset failed', 'error');
            }
        },
        error: function(xhr, status, error) {
            handleAjaxError(xhr, status, error, 'perform emergency reset');
        }
    });
}

/**
 * Perform session reset
 */
function performSessionReset() {
    console.log('🔄 Performing session reset...');

    if (!confirm('This will reset your current session. This action will:\n\n' +
    '• Clear session data\n' +
    '• Reset monitoring state\n' +
    '• Refresh user context\n\n' +
    'Are you sure you want to continue?')) {
        return;
    }

    showToast('Info', 'Performing session reset...', 'info');

    $.ajax({
        url: '/utility/session/manual-reset',
        method: 'POST',
        success: function(response) {
            if (response.success) {
                showToast('Success', 'Session reset completed successfully', 'success');

                // Trigger utility event
                UtilityEvents.trigger('sessionReset', response);

                // Refresh session utility after reset
                setTimeout(() => {
                    if (typeof window.SessionUtility !== 'undefined') {
                        window.SessionUtility.refreshStatus();
                    }
                }, 2000);
            } else {
                showToast('Error', response.message || 'Session reset failed', 'error');
            }
        },
        error: function(xhr, status, error) {
            handleAjaxError(xhr, status, error, 'perform session reset');
        }
    });
}

/**
 * Show system summary
 */
function showSystemSummary() {
    console.log('📊 Showing system summary...');

    $.ajax({
        url: '/utility/diagnostics/system-summary',
        method: 'GET',
        success: function(response) {
            if (response.success) {
                displaySystemSummary(response);
                showToast('Info', 'System summary generated', 'info');
            } else {
                showToast('Error', response.message || 'Failed to generate system summary', 'error');
            }
        },
        error: function(xhr, status, error) {
            handleAjaxError(xhr, status, error, 'generate system summary');
        }
    });
}

/**
 * Display system summary
 * @param {Object} data - Summary data from server
 */
function displaySystemSummary(data) {
    const summary = data.summary || {};

    const summaryHtml = `
        <div class="system-summary-display">
            <div class="summary-section">
                <h6>User Information</h6>
                <div class="summary-grid">
                    <div class="summary-item">
                        <strong>User:</strong> ${summary.currentUser || 'Unknown'}
                    </div>
                    <div class="summary-item">
                        <strong>Role:</strong> ${summary.userRole || 'Unknown'}
                    </div>
                    <div class="summary-item">
                        <strong>User ID:</strong> ${summary.userId || 'N/A'}
                    </div>
                </div>
            </div>

            <div class="summary-section">
                <h6>System Health</h6>
                <div class="summary-grid">
                    <div class="summary-item">
                        <strong>Overall Status:</strong>
                        <span class="${summary.systemHealthy ? 'text-success' : 'text-danger'}">
                            ${summary.systemHealthy ? 'Healthy' : 'Issues Detected'}
                        </span>
                    </div>
                    <div class="summary-item">
                        <strong>Cache Health:</strong>
                        <span class="${summary.cacheHealthy ? 'text-success' : 'text-danger'}">
                            ${summary.cacheHealthy ? 'Healthy' : 'Issues'}
                        </span>
                    </div>
                    <div class="summary-item">
                        <strong>Monitoring:</strong> ${summary.monitoringMode || 'Unknown'}
                    </div>
                </div>
            </div>

            <div class="summary-section">
                <h6>Cache Information</h6>
                <div class="summary-grid">
                    <div class="summary-item">
                        <strong>Cached Users:</strong> ${summary.cachedUserCount || 0}
                    </div>
                    <div class="summary-item">
                        <strong>Has Data:</strong>
                        <span class="${summary.hasUserData ? 'text-success' : 'text-warning'}">
                            ${summary.hasUserData ? 'Yes' : 'No'}
                        </span>
                    </div>
                    <div class="summary-item">
                        <strong>Tasks:</strong> ${summary.healthyTasks || 0}
                    </div>
                </div>
            </div>

            <div class="summary-section">
                <h6>Quick Actions</h6>
                <div class="quick-actions">
                    <button class="btn btn-sm btn-outline-primary" onclick="window.refreshAllUtilities()">
                        <i class="bi bi-arrow-repeat"></i> Refresh All
                    </button>
                    ${!summary.cacheHealthy ? `
                        <button class="btn btn-sm btn-outline-warning" onclick="window.performEmergencyReset()">
                            <i class="bi bi-exclamation-triangle"></i> Emergency Reset
                        </button>
                    ` : ''}
                    ${!summary.systemHealthy ? `
                        <button class="btn btn-sm btn-outline-info" onclick="window.HealthUtility.checkTasks()">
                            <i class="bi bi-list-task"></i> Check Tasks
                        </button>
                    ` : ''}
                </div>
            </div>

            <div class="summary-meta">
                <small class="text-muted">Generated at: ${data.timestamp}</small>
            </div>
        </div>
    `;

    $('#system-summary-content').html(summaryHtml);
    $('#system-status-summary').fadeIn();
}

// ============================================================================
// UTILITY STATUS MONITORING
// ============================================================================

/**
 * Get utility load status
 * @returns {Object} Utility load status
 */
function getUtilityLoadStatus() {
    const utilities = {
        'Backup Management': typeof window.BackupUtility !== 'undefined',
        'Cache Monitoring': typeof window.MonitorUtility !== 'undefined',
        'Session Diagnostics': typeof window.SessionUtility !== 'undefined',
        'System Health': typeof window.HealthUtility !== 'undefined',
        'Diagnostics': typeof window.DiagnosticsUtility !== 'undefined',
        'Quick Actions': typeof window.ActionsUtility !== 'undefined',
        'Main Coordinator': typeof window.UtilityMain !== 'undefined'
    };

    const allLoaded = Object.values(utilities).every(Boolean);
    const loadedCount = Object.values(utilities).filter(Boolean).length;
    const totalCount = Object.keys(utilities).length;

    return {
        utilities: utilities,
        allLoaded: allLoaded,
        loadedCount: loadedCount,
        totalCount: totalCount,
        loadPercentage: Math.round((loadedCount / totalCount) * 100)
    };
}

/**
 * Check and display utility status
 * @returns {Object} Utility load status
 */
function checkUtilityStatus() {
    const status = getUtilityLoadStatus();

    console.log(`📊 Utility Status: ${status.loadedCount}/${status.totalCount} loaded (${status.loadPercentage}%)`);
    console.table(status.utilities);

    if (typeof window.showToast === 'function') {
        const message = status.allLoaded ?
        'All utilities are loaded and ready' :
        `${status.loadedCount}/${status.totalCount} utilities loaded (${status.loadPercentage}%)`;
        const type = status.allLoaded ? 'success' : 'warning';
        window.showToast('Utility Status', message, type);
    }

    return status;
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

/**
 * Show toast notification
 * @param {string} title - Toast title
 * @param {string} message - Toast message
 * @param {string} type - Toast type (success, error, warning, info)
 */
function showToast(title, message, type) {
    if (typeof window.showToast === 'function') {
        window.showToast(title, message, type);
    } else {
        console.log(`${type.toUpperCase()}: ${title} - ${message}`);
    }
}

/**
 * Handle AJAX error
 * @param {Object} xhr - XMLHttpRequest object
 * @param {string} status - Status text
 * @param {string} error - Error message
 * @param {string} operation - Operation description
 */
function handleAjaxError(xhr, status, error, operation) {
    let message = `Failed to ${operation}: ${error}`;
    try {
        if (xhr.responseJSON && xhr.responseJSON.message) {
            message = xhr.responseJSON.message;
        }
    } catch (e) {
        // Use default message
    }
    showToast('Error', message, 'error');
}

// ============================================================================
// INITIALIZATION
// ============================================================================

/**
 * Initialize the utility coordinator
 */
function init() {
    console.log('🚀 Main Utility Coordinator initializing...');

    // Initialize system health monitoring
    initializeSystemHealth();

    // Setup global refresh mechanism
    setupGlobalRefresh();

    // Initialize auto-refresh intervals
    setupAutoRefresh();

    // Initialize cross-utility communication
    initializeCrossUtilityIntegration();

    // Wait for all utilities to load, then perform final initialization
    setTimeout(function() {
        const status = getUtilityLoadStatus();

        console.log('✅ Main Utility Coordinator initialized');
        console.log(`🎯 Utility Load Status: ${status.loadedCount}/${status.totalCount} (${status.loadPercentage}%)`);
        console.log('🔗 Available utilities:', status.utilities);

        if (status.allLoaded) {
            console.log('🎉 All utilities loaded successfully!');
            // Trigger utility event
            UtilityEvents.trigger('allUtilitiesLoaded', status);
        } else {
            console.warn('⚠️ Some utilities failed to load:',
                Object.keys(status.utilities).filter(key => !status.utilities[key])
            );
        }

        // Update utility state
        UtilityState.systemHealth.lastCheck = new Date().toISOString();
    }, 2000);
}

// ============================================================================
// DOM READY INITIALIZATION
// ============================================================================

// Auto-initialize on DOM ready
$(document).ready(function() {
    init();
});

// ============================================================================
// EXPORTS
// ============================================================================

// Export coordinator API
export {
    // Initialization
    init,

    // Health monitoring
    initializeSystemHealth,
    checkSystemHealthStatus,
    updateMainHealthIndicator,

    // Refresh operations
    refreshAllUtilities,
    refreshSystemStatus,

    // Emergency operations
    performEmergencyReset,
    performSessionReset,
    showSystemSummary,

    // Status monitoring
    getUtilityLoadStatus,
    checkUtilityStatus,

    // State and events
    UtilityState,
    UtilityEvents
};

// ============================================================================
// GLOBAL COMPATIBILITY LAYER
// ============================================================================

// Expose main functions globally for HTML onclick handlers
window.initializeSystemHealth = initializeSystemHealth;
window.refreshAllUtilities = refreshAllUtilities;
window.refreshSystemStatus = refreshSystemStatus;
window.performEmergencyReset = performEmergencyReset;
window.performSessionReset = performSessionReset;
window.showSystemSummary = showSystemSummary;
window.checkUtilityStatus = checkUtilityStatus;

// Main utility coordinator object
window.UtilityMain = {
    initializeHealth: initializeSystemHealth,
    refreshAll: refreshAllUtilities,
    refreshStatus: refreshSystemStatus,
    emergencyReset: performEmergencyReset,
    sessionReset: performSessionReset,
    systemSummary: showSystemSummary,
    updateHealth: checkSystemHealthStatus,
    getStatus: getUtilityLoadStatus,
    checkStatus: checkUtilityStatus,
    state: UtilityState,
    events: UtilityEvents
};
