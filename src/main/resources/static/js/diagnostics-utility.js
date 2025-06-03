// diagnostics-utility.js
$(document).ready(function() {
    console.log('🔧 Diagnostics utility loaded');

    // Set current month as default
    const currentMonth = new Date().getMonth() + 1;
    $('#diag-month').val(currentMonth);

    // Auto-update system time every second
    updateSystemTime();
    setInterval(updateSystemTime, 1000);

    // Toggle icon animation
    $('#diagnostics-controls').on('show.bs.collapse', function() {
        $('#diagnostics-utility .toggle-icon').removeClass('bi-chevron-down').addClass('bi-chevron-up');
    }).on('hide.bs.collapse', function() {
        $('#diagnostics-utility .toggle-icon').removeClass('bi-chevron-up').addClass('bi-chevron-down');
    });

    // Backup Events Diagnostics
    $('#backup-events-diag-btn').click(function(e) {
        e.preventDefault();
        getBackupEventDiagnostics();
    });

    // System Summary Diagnostics
    $('#system-summary-diag-btn').click(function(e) {
        e.preventDefault();
        getSystemSummary();
    });

    // Clear Results
    $('#clear-diagnostics-results').click(function() {
        $('#diagnostics-results').fadeOut();
        $('#diagnostics-content').empty();
    });

    // ========================================================================
    // DIAGNOSTIC OPERATIONS
    // ========================================================================

    function getBackupEventDiagnostics() {
        const fileType = $('#diag-file-type').val();
        const year = $('#diag-year').val();
        const month = $('#diag-month').val();
        const btn = $('#backup-events-diag-btn');

        setButtonLoading(btn, true);

        const params = {};
        if (fileType) params.fileType = fileType;
        if (year) params.year = year;
        if (month) params.month = month;

        $.ajax({
            url: '/utility/diagnostics/backup-events',
            method: 'GET',
            data: params,
            success: function(response) {
                if (response.success) {
                    displayDiagnosticsResults('Backup Event Diagnostics', formatBackupEventDiagnostics(response));
                    showToast('Success', 'Backup event diagnostics retrieved successfully', 'success');
                } else {
                    showToast('Error', response.message || 'Failed to get backup event diagnostics', 'error');
                }
            },
            error: function(xhr, status, error) {
                handleAjaxError(xhr, status, error, 'get backup event diagnostics');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function getSystemSummary() {
        const btn = $('#system-summary-diag-btn');
        setButtonLoading(btn, true);

        $.ajax({
            url: '/utility/diagnostics/system-summary',
            method: 'GET',
            success: function(response) {
                if (response.success) {
                    displayDiagnosticsResults('System Summary Report', formatSystemSummary(response));
                    showToast('Success', 'System summary generated successfully', 'success');
                } else {
                    showToast('Error', response.message || 'Failed to generate system summary', 'error');
                }
            },
            error: function(xhr, status, error) {
                handleAjaxError(xhr, status, error, 'generate system summary');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    // ========================================================================
    // DISPLAY FUNCTIONS
    // ========================================================================

    function displayDiagnosticsResults(title, content) {
        $('#diagnostics-content').html(`
            <div class="diagnostics-result">
                <h5>${title}</h5>
                <div class="result-content">
                    ${content}
                </div>
                <div class="result-meta">
                    <small class="text-muted">Generated at: ${new Date().toLocaleString()}</small>
                </div>
            </div>
        `);
        $('#diagnostics-results').fadeIn();
    }

    function formatBackupEventDiagnostics(data) {
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
                    <button class="btn btn-sm btn-outline-primary" onclick="refreshBackupEventDiagnostics()">
                        <i class="bi bi-arrow-repeat"></i> Refresh
                    </button>
                    <button class="btn btn-sm btn-outline-secondary" onclick="exportDiagnostics('backup-events')">
                        <i class="bi bi-download"></i> Export
                    </button>
                </div>
            </div>
        `;
    }

    function formatSystemSummary(data) {
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
                    <button class="btn btn-sm btn-outline-success" onclick="refreshSystemSummary()">
                        <i class="bi bi-arrow-repeat"></i> Refresh Report
                    </button>
                    <button class="btn btn-sm btn-outline-primary" onclick="exportDiagnostics('system-summary')">
                        <i class="bi bi-download"></i> Export Report
                    </button>
                    <button class="btn btn-sm btn-outline-info" onclick="scheduleHealthCheck()">
                        <i class="bi bi-calendar-plus"></i> Schedule Check
                    </button>
                </div>
            </div>
        `;
    }

    // ========================================================================
    // UTILITY FUNCTIONS
    // ========================================================================

    function updateSystemTime() {
        const now = new Date();
        const timeString = now.toLocaleString();
        $('#diag-system-time').text(timeString);
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

    window.refreshBackupEventDiagnostics = function() {
        $('#backup-events-diag-btn').click();
    };

    window.refreshSystemSummary = function() {
        $('#system-summary-diag-btn').click();
    };

    window.exportDiagnostics = function(type) {
        const content = $('#diagnostics-content').text();
        const blob = new Blob([content], { type: 'text/plain' });
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${type}-diagnostics-${new Date().toISOString().slice(0, 10)}.txt`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        showToast('Success', 'Diagnostics exported successfully', 'success');
    };

    window.scheduleHealthCheck = function() {
        showToast('Info', 'Health check scheduling is not yet implemented', 'info');
    };

    // ========================================================================
    // GLOBAL FUNCTIONS (for utility-main.js integration)
    // ========================================================================

    // Expose diagnostics functions globally
    window.DiagnosticsUtility = {
        getBackupEvents: getBackupEventDiagnostics,
        getSystemSummary: getSystemSummary,
        refreshSummary: getSystemSummary
    };

    console.log('✅ Diagnostics utility initialized');
});