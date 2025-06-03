// actions-utility.js
$(document).ready(function() {
    console.log('🔧 Actions utility loaded');

    // Toggle icon animation
    $('#actions-controls').on('show.bs.collapse', function() {
        $('#actions-utility .toggle-icon').removeClass('bi-chevron-down').addClass('bi-chevron-up');
    }).on('hide.bs.collapse', function() {
        $('#actions-utility .toggle-icon').removeClass('bi-chevron-up').addClass('bi-chevron-down');
    });

    // Emergency Operations
    $('#emergency-cache-reset-btn').click(function(e) {
        e.preventDefault();
        performEmergencyCacheReset();
    });

    $('#force-cache-refresh-btn').click(function(e) {
        e.preventDefault();
        forceCacheRefresh();
    });

    // Quick Cache Operations
    $('#check-cache-data-btn').click(function(e) {
        e.preventDefault();
        checkCacheData();
    });

    $('#validate-cache-quick-btn').click(function(e) {
        e.preventDefault();
        validateCacheQuick();
    });

    // System Shortcuts
    $('#quick-health-check-btn').click(function(e) {
        e.preventDefault();
        quickHealthCheck();
    });

    $('#refresh-all-data-btn').click(function(e) {
        e.preventDefault();
        refreshAllData();
    });

    // Clear Results
    $('#clear-actions-results').click(function() {
        $('#actions-results').fadeOut();
        $('#actions-content').empty();
        updateActionStatus('None', 'Ready', '--:--:--');
    });

    // ========================================================================
    // EMERGENCY OPERATIONS
    // ========================================================================

    function performEmergencyCacheReset() {
        if (!confirm('⚠️ EMERGENCY CACHE RESET ⚠️\n\n' +
        'This will completely reset all cached data and session state.\n' +
        'This action should only be used if you are experiencing severe issues.\n\n' +
        'Are you absolutely sure you want to continue?')) {
            return;
        }

        const btn = $('#emergency-cache-reset-btn');
        setButtonLoading(btn, true);
        updateActionStatus('Emergency Cache Reset', 'In Progress', getCurrentTime());

        $.ajax({
            url: '/utility/cache/emergency-reset',
            method: 'POST',
            success: function(response) {
                if (response.success) {
                    const result = formatEmergencyResetResult(response);
                    displayActionResults('Emergency Cache Reset Completed', result);
                    updateActionStatus('Emergency Reset', 'Success', getCurrentTime());
                    showToast('Success', 'Emergency cache reset completed successfully', 'success');

                    // Auto-refresh all utilities after emergency reset
                    setTimeout(() => {
                        if (typeof window.refreshAllUtilities === 'function') {
                            window.refreshAllUtilities();
                        }
                    }, 2000);
                } else {
                    updateActionStatus('Emergency Reset', 'Failed', getCurrentTime());
                    showToast('Error', response.message || 'Emergency reset failed', 'error');
                }
            },
            error: function(xhr, status, error) {
                updateActionStatus('Emergency Reset', 'Error', getCurrentTime());
                handleAjaxError(xhr, status, error, 'perform emergency cache reset');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function forceCacheRefresh() {
        if (!confirm('Force refresh will reload all user data from files.\n' +
        'This may take a few moments. Continue?')) {
            return;
        }

        const btn = $('#force-cache-refresh-btn');
        setButtonLoading(btn, true);
        updateActionStatus('Force Cache Refresh', 'In Progress', getCurrentTime());

        $.ajax({
            url: '/utility/cache/refresh',
            method: 'POST',
            success: function(response) {
                if (response.success) {
                    const result = formatCacheRefreshResult(response);
                    displayActionResults('Cache Refresh Completed', result);
                    updateActionStatus('Cache Refresh', 'Success', getCurrentTime());
                    showToast('Success', 'Cache refreshed successfully', 'success');
                } else {
                    updateActionStatus('Cache Refresh', 'Failed', getCurrentTime());
                    showToast('Error', response.message || 'Cache refresh failed', 'error');
                }
            },
            error: function(xhr, status, error) {
                updateActionStatus('Cache Refresh', 'Error', getCurrentTime());
                handleAjaxError(xhr, status, error, 'force cache refresh');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    // ========================================================================
    // QUICK CACHE OPERATIONS
    // ========================================================================

    function checkCacheData() {
        const btn = $('#check-cache-data-btn');
        setButtonLoading(btn, true);
        updateActionStatus('Check Cache Data', 'In Progress', getCurrentTime());

        $.ajax({
            url: '/utility/cache/user-data-check',
            method: 'GET',
            success: function(response) {
                if (response.success) {
                    const result = formatCacheDataResult(response);
                    displayActionResults('Cache Data Check Results', result);
                    updateActionStatus('Data Check', 'Success', getCurrentTime());
                    showToast('Info', response.message || 'Cache data checked successfully', 'info');
                } else {
                    updateActionStatus('Data Check', 'Failed', getCurrentTime());
                    showToast('Error', response.message || 'Cache data check failed', 'error');
                }
            },
            error: function(xhr, status, error) {
                updateActionStatus('Data Check', 'Error', getCurrentTime());
                handleAjaxError(xhr, status, error, 'check cache data');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function validateCacheQuick() {
        const btn = $('#validate-cache-quick-btn');
        setButtonLoading(btn, true);
        updateActionStatus('Validate Cache', 'In Progress', getCurrentTime());

        $.ajax({
            url: '/utility/cache/validate',
            method: 'POST',
            success: function(response) {
                if (response.success) {
                    const result = formatCacheValidationResult(response);
                    displayActionResults('Cache Validation Results', result);
                    updateActionStatus('Cache Validation', 'Success', getCurrentTime());
                    showToast('Success', 'Cache validation completed successfully', 'success');
                } else {
                    updateActionStatus('Cache Validation', 'Failed', getCurrentTime());
                    showToast('Error', response.message || 'Cache validation failed', 'error');
                }
            },
            error: function(xhr, status, error) {
                updateActionStatus('Cache Validation', 'Error', getCurrentTime());
                handleAjaxError(xhr, status, error, 'validate cache');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    // ========================================================================
    // SYSTEM SHORTCUTS
    // ========================================================================

    function quickHealthCheck() {
        const btn = $('#quick-health-check-btn');
        setButtonLoading(btn, true);
        updateActionStatus('Health Check', 'In Progress', getCurrentTime());

        $.ajax({
            url: '/utility/health/overall',
            method: 'GET',
            success: function(response) {
                if (response.success) {
                    const result = formatHealthCheckResult(response);
                    displayActionResults('Quick Health Check Results', result);
                    updateActionStatus('Health Check', 'Success', getCurrentTime());

                    const status = response.overallHealthy ? 'success' : 'warning';
                    const message = response.overallHealthy ? 'System is healthy' : 'System issues detected';
                    showToast('Health Check', message, status);
                } else {
                    updateActionStatus('Health Check', 'Failed', getCurrentTime());
                    showToast('Error', response.message || 'Health check failed', 'error');
                }
            },
            error: function(xhr, status, error) {
                updateActionStatus('Health Check', 'Error', getCurrentTime());
                handleAjaxError(xhr, status, error, 'perform health check');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function refreshAllData() {
        const btn = $('#refresh-all-data-btn');
        setButtonLoading(btn, true);
        updateActionStatus('Refresh All Data', 'In Progress', getCurrentTime());

        // Use the main utility coordinator function if available
        if (typeof window.refreshAllUtilities === 'function') {
            try {
                window.refreshAllUtilities();
                updateActionStatus('Refresh All', 'Success', getCurrentTime());
                showToast('Success', 'All data refreshed successfully', 'success');

                displayActionResults('Refresh All Data Completed', `
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
            } catch (error) {
                updateActionStatus('Refresh All', 'Error', getCurrentTime());
                showToast('Error', 'Failed to refresh all data: ' + error.message, 'error');
            }
        } else {
            updateActionStatus('Refresh All', 'Error', getCurrentTime());
            showToast('Error', 'Refresh function not available', 'error');
        }

        setButtonLoading(btn, false);
    }

    // ========================================================================
    // RESULT FORMATTING FUNCTIONS
    // ========================================================================

    function formatEmergencyResetResult(data) {
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
                        <strong>Reset Time:</strong> ${data.timestamp || getCurrentTime()}
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

    function formatCacheRefreshResult(data) {
        return `
            <div class="cache-refresh-result">
                <div class="alert alert-success">
                    <i class="bi bi-arrow-repeat"></i>
                    <strong>Cache refresh completed successfully</strong>
                </div>

                <div class="refresh-stats">
                    <div class="stat-row">
                        <span class="stat-label">Users Before:</span>
                        <span class="stat-value">${data.beforeCount || 0}</span>
                    </div>
                    <div class="stat-row">
                        <span class="stat-label">Users After:</span>
                        <span class="stat-value">${data.afterCount || 0}</span>
                    </div>
                    <div class="stat-row">
                        <span class="stat-label">Change:</span>
                        <span class="stat-value ${(data.afterCount || 0) >= (data.beforeCount || 0) ? 'text-success' : 'text-warning'}">
                            ${((data.afterCount || 0) - (data.beforeCount || 0)) >= 0 ? '+' : ''}${(data.afterCount || 0) - (data.beforeCount || 0)}
                        </span>
                    </div>
                </div>

                <div class="refresh-info">
                    <p><strong>Refresh completed at:</strong> ${data.timestamp || getCurrentTime()}</p>
                    <p>All user data has been reloaded from the file system.</p>
                </div>
            </div>
        `;
    }

    function formatCacheDataResult(data) {
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
                        <span class="detail-value">${data.timestamp || getCurrentTime()}</span>
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

    function formatCacheValidationResult(data) {
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
                    <p><strong>Validation completed at:</strong> ${data.timestamp || getCurrentTime()}</p>
                    <p>Cache consistency and data integrity verified.</p>
                </div>
            </div>
        `;
    }

    function formatHealthCheckResult(data) {
        const isHealthy = data.overallHealthy;
        const healthyCount = Object.values(data.healthStatus || {}).filter(Boolean).length;
        const totalCount = Object.keys(data.healthStatus || {}).length;

        // Build task breakdown HTML separately to avoid template literal nesting issues
        let taskBreakdownHtml = '';
        if (data.healthStatus) {
            const taskEntries = Object.entries(data.healthStatus);
            taskBreakdownHtml = taskEntries.map(([taskId, healthy]) => {
                return `<div class="task-status">
                    <span class="task-name">${taskId}</span>
                    <span class="task-health ${healthy ? 'text-success' : 'text-danger'}">
                        <i class="bi ${healthy ? 'bi-check' : 'bi-x'}"></i>
                        ${healthy ? 'OK' : 'Issue'}
                    </span>
                </div>`;
            }).join('');
        }

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
                        <span class="text-muted">${data.timestamp || getCurrentTime()}</span>
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

    function displayActionResults(title, content) {
        $('#actions-content').html(`
            <div class="action-result">
                <h5>${title}</h5>
                <div class="result-content">
                    ${content}
                </div>
            </div>
        `);
        $('#actions-results').fadeIn();
    }

    function updateActionStatus(action, result, time) {
        $('#last-action').text(action);
        $('#action-time').text(time);

        // Update result with color coding
        const resultElement = $('#action-result');
        resultElement.text(result);

        if (result === 'Success') {
            resultElement.css('color', '#28a745');
        } else if (result === 'Failed' || result === 'Error') {
            resultElement.css('color', '#dc3545');
        } else if (result === 'In Progress') {
            resultElement.css('color', '#007bff');
        } else {
            resultElement.css('color', '#6c757d');
        }
    }

    function setButtonLoading(btn, loading) {
        if (loading) {
            btn.addClass('loading').prop('disabled', true);
            btn.find('i').addClass('spin');
        } else {
            btn.removeClass('loading').prop('disabled', false);
            btn.find('i').removeClass('spin');
        }
    }

    function getCurrentTime() {
        return new Date().toLocaleTimeString('en-US', {
            hour12: false,
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    }

    function showToast(title, message, type) {
        if (typeof window.showToast === 'function') {
            window.showToast(title, message, type);
        }
    }

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

    // ========================================================================
    // GLOBAL FUNCTIONS (for utility-main.js integration)
    // ========================================================================

    // Expose actions functions globally
    window.ActionsUtility = {
        emergencyReset: performEmergencyCacheReset,
        forceRefresh: forceCacheRefresh,
        checkData: checkCacheData,
        validate: validateCacheQuick,
        healthCheck: quickHealthCheck,
        refreshAll: refreshAllData
    };

    console.log('✅ Actions utility initialized');
});