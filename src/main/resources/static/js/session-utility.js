// session-utility.js
$(document).ready(function() {
    console.log('🔧 Session utility loaded');

    // Auto-load session status on page load
    setTimeout(loadSessionStatus, 1000);

    // Toggle icon animation
    $('#session-controls').on('show.bs.collapse', function() {
        $('#session-utility .toggle-icon').removeClass('bi-chevron-down').addClass('bi-chevron-up');
    }).on('hide.bs.collapse', function() {
        $('#session-utility .toggle-icon').removeClass('bi-chevron-up').addClass('bi-chevron-down');
    });

    // Check Session Status
    $('#check-session-btn').click(function(e) {
        e.preventDefault();
        checkSessionStatus();
    });

    // Manual Session Reset
    $('#manual-reset-btn').click(function(e) {
        e.preventDefault();
        performManualSessionReset();
    });

    // Context Status
    $('#context-status-btn').click(function(e) {
        e.preventDefault();
        checkContextStatus();
    });

    // Reset Status/History
    $('#reset-status-btn').click(function(e) {
        e.preventDefault();
        getResetStatus();
    });

    // Clear Results
    $('#clear-session-results').click(function() {
        $('#session-results').fadeOut();
        $('#session-content').empty();
    });

    // Auto-refresh session status every 2 minutes
    setInterval(loadSessionStatus, 120000);

    // ========================================================================
    // SESSION STATUS FUNCTIONS
    // ========================================================================

    function loadSessionStatus() {
        // Load basic session info without showing results
        $.ajax({
            url: '/utility/session/context-status',
            method: 'GET',
            success: function(response) {
                if (response.success) {
                    updateSessionStats(response);
                }
            },
            error: function() {
                updateSessionStats({
                    currentUsername: 'Error',
                    isHealthy: false,
                    hasRealUser: false
                });
            }
        });
    }

    function updateSessionStats(data) {
        $('#current-username').text(data.currentUsername || 'Unknown');
        $('#session-health').text(data.isHealthy ? 'Healthy' : 'Issues Detected');
        $('#cache-health').text(data.hasRealUser ? 'Valid User' : 'No User Data');

        // Update colors based on health
        const healthElement = $('#session-health');
        const cacheElement = $('#cache-health');

        if (data.isHealthy) {
            healthElement.css('color', '#28a745');
        } else {
            healthElement.css('color', '#dc3545');
        }

        if (data.hasRealUser) {
            cacheElement.css('color', '#28a745');
        } else {
            cacheElement.css('color', '#dc3545');
        }
    }

    function checkSessionStatus() {
        const btn = $('#check-session-btn');
        setButtonLoading(btn, true);

        $.ajax({
            url: '/utility/session/context-status',
            method: 'GET',
            success: function(response) {
                if (response.success) {
                    displaySessionResults('Session Status Check', formatSessionStatus(response));
                    updateSessionStats(response);
                    showToast('Success', 'Session status checked successfully', 'success');
                } else {
                    showToast('Error', response.message || 'Failed to check session status', 'error');
                }
            },
            error: function(xhr, status, error) {
                handleAjaxError(xhr, status, error, 'check session status');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function performManualSessionReset() {
        if (!confirm('Are you sure you want to perform a manual session reset? This will clear your current session state.')) {
            return;
        }

        const btn = $('#manual-reset-btn');
        setButtonLoading(btn, true);

        $.ajax({
            url: '/utility/session/manual-reset',
            method: 'POST',
            success: function(response) {
                if (response.success) {
                    displaySessionResults('Manual Session Reset', formatResetResult(response));
                    showToast('Success', 'Manual session reset completed successfully', 'success');

                    // Refresh session status after reset
                    setTimeout(loadSessionStatus, 2000);
                } else {
                    showToast('Error', response.message || 'Failed to perform manual reset', 'error');
                }
            },
            error: function(xhr, status, error) {
                handleAjaxError(xhr, status, error, 'perform manual session reset');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function checkContextStatus() {
        const btn = $('#context-status-btn');
        setButtonLoading(btn, true);

        $.ajax({
            url: '/utility/session/context-status',
            method: 'GET',
            success: function(response) {
                if (response.success) {
                    displaySessionResults('User Context Status', formatContextStatus(response));
                    showToast('Info', 'Context status retrieved successfully', 'info');
                } else {
                    showToast('Error', response.message || 'Failed to get context status', 'error');
                }
            },
            error: function(xhr, status, error) {
                handleAjaxError(xhr, status, error, 'get context status');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function getResetStatus() {
        const btn = $('#reset-status-btn');
        setButtonLoading(btn, true);

        $.ajax({
            url: '/utility/session/reset-status',
            method: 'GET',
            success: function(response) {
                if (response.success) {
                    displaySessionResults('Reset Status & History', formatResetStatus(response));
                    showToast('Info', 'Reset status retrieved successfully', 'info');
                } else {
                    showToast('Error', response.message || 'Failed to get reset status', 'error');
                }
            },
            error: function(xhr, status, error) {
                handleAjaxError(xhr, status, error, 'get reset status');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    // ========================================================================
    // DISPLAY FUNCTIONS
    // ========================================================================

    function displaySessionResults(title, content) {
        $('#session-content').html(`
            <div class="session-result">
                <h5>${title}</h5>
                <div class="result-content">
                    ${content}
                </div>
                <div class="result-meta">
                    <small class="text-muted">Retrieved at: ${new Date().toLocaleString()}</small>
                </div>
            </div>
        `);
        $('#session-results').fadeIn();
    }

    function formatSessionStatus(data) {
        return `
            <div class="status-grid">
                <div class="status-item">
                    <strong>Current User:</strong> ${data.currentUsername || 'Unknown'}
                </div>
                <div class="status-item">
                    <strong>User ID:</strong> ${data.currentUserId || 'N/A'}
                </div>
                <div class="status-item">
                    <strong>Cache Healthy:</strong>
                    <span class="${data.isHealthy ? 'text-success' : 'text-danger'}">
                        ${data.isHealthy ? 'Yes' : 'No'}
                    </span>
                </div>
                <div class="status-item">
                    <strong>Real User:</strong>
                    <span class="${data.hasRealUser ? 'text-success' : 'text-warning'}">
                        ${data.hasRealUser ? 'Yes' : 'System User'}
                    </span>
                </div>
                <div class="status-item">
                    <strong>Cache Initialized:</strong>
                    <span class="${data.isInitialized ? 'text-success' : 'text-danger'}">
                        ${data.isInitialized ? 'Yes' : 'No'}
                    </span>
                </div>
            </div>
        `;
    }

    function formatContextStatus(data) {
        return `
            <div class="context-details">
                <h6>User Context Information</h6>
                <div class="status-grid">
                    <div class="status-item">
                        <strong>Username:</strong> ${data.currentUsername || 'Unknown'}
                    </div>
                    <div class="status-item">
                        <strong>User ID:</strong> ${data.currentUserId || 'N/A'}
                    </div>
                    <div class="status-item">
                        <strong>Health Status:</strong>
                        <span class="${data.isHealthy ? 'text-success' : 'text-danger'}">
                            ${data.isHealthy ? 'Healthy' : 'Issues Detected'}
                        </span>
                    </div>
                    <div class="status-item">
                        <strong>User Type:</strong>
                        <span class="${data.hasRealUser ? 'text-success' : 'text-info'}">
                            ${data.hasRealUser ? 'Authenticated User' : 'System User'}
                        </span>
                    </div>
                    <div class="status-item">
                        <strong>Initialization:</strong>
                        <span class="${data.isInitialized ? 'text-success' : 'text-warning'}">
                            ${data.isInitialized ? 'Initialized' : 'Not Initialized'}
                        </span>
                    </div>
                </div>
            </div>
        `;
    }

    function formatResetResult(data) {
        return `
            <div class="reset-result">
                <div class="alert alert-success">
                    <i class="bi bi-check-circle"></i>
                    ${data.message}
                </div>
                <div class="reset-details">
                    <div class="status-item">
                        <strong>Reset User:</strong> ${data.username}
                    </div>
                    <div class="status-item">
                        <strong>Reset Time:</strong> ${data.timestamp}
                    </div>
                </div>
            </div>
        `;
    }

    function formatResetStatus(data) {
        return `
            <div class="reset-status">
                <h6>Midnight Reset System Status</h6>
                <div class="status-text">
                    <pre>${data.resetStatus}</pre>
                </div>
            </div>
        `;
    }

    // ========================================================================
    // UTILITY FUNCTIONS
    // ========================================================================

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
    // GLOBAL FUNCTIONS (for utility-main.js integration)
    // ========================================================================

    // Expose session functions globally
    window.SessionUtility = {
        checkStatus: checkSessionStatus,
        performReset: performManualSessionReset,
        refreshStatus: loadSessionStatus
    };

    console.log('✅ Session utility initialized');
});