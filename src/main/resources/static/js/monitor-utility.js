// monitor-utility.js - Enhanced Version
$(document).ready(function() {
    console.log('🔧 Monitor utility loaded (enhanced)');

    // Auto-load cache overview on page load
    setTimeout(loadCacheOverview, 1000);

    // Toggle icon animation
    $('#monitor-controls').on('show.bs.collapse', function() {
        $('#monitor-utility .toggle-icon').removeClass('bi-chevron-down').addClass('bi-chevron-up');
    }).on('hide.bs.collapse', function() {
        $('#monitor-utility .toggle-icon').removeClass('bi-chevron-up').addClass('bi-chevron-down');
    });

    // Cache Operations
    $('#view-cache-status-btn').click(function(e) {
        e.preventDefault();
        viewCacheStatus();
    });

    $('#validate-cache-btn').click(function(e) {
        e.preventDefault();
        validateCache();
    });

    $('#refresh-cache-btn').click(function(e) {
        e.preventDefault();
        refreshCache();
    });

    // Data Verification
    $('#check-user-data-btn').click(function(e) {
        e.preventDefault();
        checkUserData();
    });

    $('#user-count-btn').click(function(e) {
        e.preventDefault();
        getUserCount();
    });

    // Clear Results
    $('#clear-monitor-results').click(function() {
        $('#monitor-results').fadeOut();
        $('#monitor-content').empty();
    });

    // Auto-refresh cache status every 3 minutes
    setInterval(loadCacheOverview, 180000);

    // ========================================================================
    // CACHE OVERVIEW FUNCTIONS
    // ========================================================================

    function loadCacheOverview() {
        // Load basic cache info without showing full results
        Promise.all([
            $.get('/utility/cache/user-data-check'),
            $.get('/utility/cache/user-count')
        ]).then(function(responses) {
            const [userData, userCount] = responses;
            updateCacheStats(userData, userCount);
        }).catch(function() {
            updateCacheStats(
                { success: false, hasUserData: false, cachedUserCount: 0 },
                { success: false, cachedUserCount: 0 }
            );
        });
    }

    function updateCacheStats(userData, userCount) {
        const hasData = userData.success && userData.hasUserData;
        const count = (userCount.success ? userCount.cachedUserCount : userData.cachedUserCount) || 0;

        // Update quick stats
        $('#cache-status-indicator').text(hasData ? 'Healthy' : 'Issues');
        $('#cached-users-count').text(count);
        $('#last-check-time').text(getCurrentTime());

        // Update cache health overview
        updateCacheHealthOverview(hasData, count);

        // Update stats colors
        const statusElement = $('#cache-status-indicator');
        if (hasData) {
            statusElement.css('color', '#28a745');
        } else {
            statusElement.css('color', '#dc3545');
        }
    }

    function updateCacheHealthOverview(isHealthy, userCount) {
        const healthIcon = $('#cache-health-icon');
        const healthText = $('#cache-health-text');
        const healthDetails = $('#cache-health-details');

        if (isHealthy) {
            healthIcon.css('color', '#28a745');
            healthText.text(`Cache is healthy with ${userCount} user(s) loaded`);
            healthDetails.html(`
                <small class="text-muted">
                    Last checked: ${getCurrentTime()} |
                    Status: Operational |
                    Data integrity: Verified
                </small>
            `).show();
        } else {
            healthIcon.css('color', '#dc3545');
            healthText.text('Cache health issues detected');
            healthDetails.html(`
                <small class="text-warning">
                    Issue detected: No user data found |
                    Recommendation: Perform cache refresh
                </small>
            `).show();
        }
    }

    // ========================================================================
    // CACHE OPERATIONS
    // ========================================================================

    function viewCacheStatus() {
        const btn = $('#view-cache-status-btn');
        setButtonLoading(btn, true);

        $.ajax({
            url: '/utility/cache/status',
            method: 'GET',
            success: function(response) {
                if (response.success) {
                    displayMonitorResults('Cache Status Report', formatCacheStatus(response));
                    updateQuickStats('Cache Status', 'Loaded');
                    updateLastCheck();
                    showToast('Success', 'Cache status retrieved successfully', 'success');
                } else {
                    showToast('Error', response.message || 'Failed to get cache status', 'error');
                    updateQuickStats('Cache Status', 'Error');
                }
            },
            error: function(xhr, status, error) {
                handleAjaxError(xhr, status, error, 'get cache status');
                updateQuickStats('Cache Status', 'Error');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function validateCache() {
        const btn = $('#validate-cache-btn');
        setButtonLoading(btn, true);

        $.ajax({
            url: '/utility/cache/validate',
            method: 'POST',
            success: function(response) {
                if (response.success) {
                    displayMonitorResults('Cache Validation Results', formatValidationResult(response));
                    updateQuickStats('Cache Status', 'Valid');
                    updateLastCheck();
                    showToast('Success', 'Cache validation completed successfully', 'success');

                    // Update overview after validation
                    setTimeout(loadCacheOverview, 1000);
                } else {
                    showToast('Error', response.message || 'Cache validation failed', 'error');
                    updateQuickStats('Cache Status', 'Invalid');
                }
            },
            error: function(xhr, status, error) {
                handleAjaxError(xhr, status, error, 'validate cache');
                updateQuickStats('Cache Status', 'Error');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function refreshCache() {
        if (!confirm('Refresh cache will reload all user data from files. This may take a moment. Continue?')) {
            return;
        }

        const btn = $('#refresh-cache-btn');
        setButtonLoading(btn, true);

        $.ajax({
            url: '/utility/cache/refresh',
            method: 'POST',
            success: function(response) {
                if (response.success) {
                    displayMonitorResults('Cache Refresh Results', formatRefreshResult(response));
                    updateQuickStats('Cache Status', 'Refreshed');
                    updateLastCheck();
                    showToast('Success', 'Cache refreshed successfully', 'success');

                    // Update overview after refresh
                    setTimeout(loadCacheOverview, 1000);
                } else {
                    showToast('Error', response.message || 'Cache refresh failed', 'error');
                    updateQuickStats('Cache Status', 'Refresh Failed');
                }
            },
            error: function(xhr, status, error) {
                handleAjaxError(xhr, status, error, 'refresh cache');
                updateQuickStats('Cache Status', 'Error');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    // ========================================================================
    // DATA VERIFICATION OPERATIONS
    // ========================================================================

    function checkUserData() {
        const btn = $('#check-user-data-btn');
        setButtonLoading(btn, true);

        $.ajax({
            url: '/utility/cache/user-data-check',
            method: 'GET',
            success: function(response) {
                if (response.success) {
                    displayMonitorResults('User Data Check Results', formatUserDataResult(response));
                    updateQuickStats('User Data', response.hasUserData ? 'Present' : 'Missing');
                    updateLastCheck();
                    showToast('Info', response.message || 'User data check completed', 'info');

                    // Update overview after check
                    setTimeout(loadCacheOverview, 500);
                } else {
                    showToast('Error', response.message || 'User data check failed', 'error');
                    updateQuickStats('User Data', 'Error');
                }
            },
            error: function(xhr, status, error) {
                handleAjaxError(xhr, status, error, 'check user data');
                updateQuickStats('User Data', 'Error');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function getUserCount() {
        const btn = $('#user-count-btn');
        setButtonLoading(btn, true);

        $.ajax({
            url: '/utility/cache/user-count',
            method: 'GET',
            success: function(response) {
                if (response.success) {
                    displayMonitorResults('User Count Report', formatUserCountResult(response));
                    updateQuickStats('User Count', response.cachedUserCount);
                    updateLastCheck();
                    showToast('Info', response.message || 'User count retrieved', 'info');

                    // Update cached users count
                    $('#cached-users-count').text(response.cachedUserCount || 0);
                } else {
                    showToast('Error', response.message || 'Failed to get user count', 'error');
                    updateQuickStats('User Count', 'Error');
                }
            },
            error: function(xhr, status, error) {
                handleAjaxError(xhr, status, error, 'get user count');
                updateQuickStats('User Count', 'Error');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    // ========================================================================
    // DISPLAY FUNCTIONS
    // ========================================================================

    function displayMonitorResults(title, content) {
        $('#monitor-content').html(`
            <div class="monitor-result">
                <h5>${title}</h5>
                <div class="result-content">
                    ${content}
                </div>
                <div class="result-meta">
                    <small class="text-muted">Retrieved at: ${new Date().toLocaleString()}</small>
                </div>
            </div>
        `);
        $('#monitor-results').fadeIn();
    }

    function formatCacheStatus(response) {
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

    function formatValidationResult(response) {
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

                <div class="validation-actions">
                    <button class="btn btn-sm btn-outline-primary" onclick="refreshCacheValidation()">
                        <i class="bi bi-arrow-repeat"></i> Re-validate
                    </button>
                </div>
            </div>
        `;
    }

    function formatRefreshResult(response) {
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
                            <div class="stat-value ${(response.afterCount || 0) >= (response.beforeCount || 0) ? 'text-success' : 'text-warning'}">
                                ${((response.afterCount || 0) - (response.beforeCount || 0)) >= 0 ? '+' : ''}${(response.afterCount || 0) - (response.beforeCount || 0)}
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

    function formatUserDataResult(response) {
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

    function formatUserCountResult(response) {
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

    // ========================================================================
    // UTILITY FUNCTIONS
    // ========================================================================

    function updateQuickStats(label, value) {
        if (label === 'Cache Status') {
            $('#cache-status-indicator').text(value);
        }
        // Add more stat updates as needed
    }

    function updateLastCheck() {
        const now = new Date();
        const timeString = now.toLocaleTimeString('en-US', {
            hour12: false,
            hour: '2-digit',
            minute: '2-digit'
        });
        $('#last-check-time').text(timeString);
    }

    function getCurrentTime() {
        return new Date().toLocaleTimeString('en-US', {
            hour12: false,
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
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
    // ACTION FUNCTIONS (called from HTML buttons)
    // ========================================================================

    window.refreshCacheValidation = function() {
        $('#validate-cache-btn').click();
    };

    // ========================================================================
    // GLOBAL FUNCTIONS (for utility-main.js integration)
    // ========================================================================

    // Expose monitor functions globally
    window.MonitorUtility = {
        viewStatus: viewCacheStatus,
        validate: validateCache,
        refresh: refreshCache,
        checkData: checkUserData,
        getUserCount: getUserCount,
        refreshOverview: loadCacheOverview
    };

    console.log('✅ Monitor utility initialized (enhanced)');
});