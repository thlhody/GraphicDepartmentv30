// health-utility.js - Fixed Version
$(document).ready(function() {
    console.log('🔧 Health utility loaded');

    // Auto-load health status on page load
    setTimeout(loadHealthOverview, 1500);

    // Toggle icon animation
    $('#health-controls').on('show.bs.collapse', function() {
        $('#health-utility .toggle-icon').removeClass('bi-chevron-down').addClass('bi-chevron-up');
    }).on('hide.bs.collapse', function() {
        $('#health-utility .toggle-icon').removeClass('bi-chevron-up').addClass('bi-chevron-down');
    });

    // Overall Health Check
    $('#overall-health-btn').click(function(e) {
        e.preventDefault();
        checkOverallHealth();
    });

    // Task Health Details
    $('#task-health-btn').click(function(e) {
        e.preventDefault();
        checkTaskHealth();
    });

    // Monitoring State
    $('#monitoring-state-btn').click(function(e) {
        e.preventDefault();
        checkMonitoringState();
    });

    // Health Summary
    $('#health-summary-btn').click(function(e) {
        e.preventDefault();
        getHealthSummary();
    });

    // Clear Results
    $('#clear-health-results').click(function() {
        $('#health-results').fadeOut();
        $('#health-content').empty();
    });

    // Auto-refresh health status every 5 minutes
    setInterval(loadHealthOverview, 300000);

    // ========================================================================
    // HEALTH OVERVIEW FUNCTIONS
    // ========================================================================

    function loadHealthOverview() {
        // Load basic health info without showing full results
        $.ajax({
            url: '/utility/health/overall',
            method: 'GET',
            success: function(response) {
                if (response.success) {
                    updateHealthStats(response);
                }
            },
            error: function() {
                updateHealthStats({
                    overallHealthy: false,
                    healthStatus: {}
                });
            }
        });
    }

    function updateHealthStats(data) {
        const isHealthy = data.overallHealthy || false;
        const healthyTasksCount = Object.values(data.healthStatus || {}).filter(Boolean).length;
        const totalTasks = Object.keys(data.healthStatus || {}).length;

        $('#overall-health').text(isHealthy ? 'Healthy' : 'Issues Detected');
        $('#healthy-tasks-count').text(`${healthyTasksCount}/${totalTasks}`);
        $('#last-health-check').text(new Date().toLocaleTimeString());

        // Update colors based on health
        const healthElement = $('#overall-health');
        if (isHealthy) {
            healthElement.css('color', '#28a745');
        } else {
            healthElement.css('color', '#dc3545');
        }

        // Update main health indicator
        updateMainHealthIndicator(isHealthy);
    }

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

    function checkOverallHealth() {
        const btn = $('#overall-health-btn');
        setButtonLoading(btn, true);

        $.ajax({
            url: '/utility/health/overall',
            method: 'GET',
            success: function(response) {
                if (response.success) {
                    displayHealthResults('Overall System Health', formatOverallHealth(response));
                    updateHealthStats(response);
                    showToast('Success', 'System health checked successfully', 'success');
                } else {
                    showToast('Error', response.message || 'Failed to check system health', 'error');
                }
            },
            error: function(xhr, status, error) {
                handleAjaxError(xhr, status, error, 'check system health');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function checkTaskHealth() {
        const btn = $('#task-health-btn');
        setButtonLoading(btn, true);

        $.ajax({
            url: '/utility/health/tasks',
            method: 'GET',
            success: function(response) {
                if (response.success) {
                    displayHealthResults('Task Health Details', formatTaskHealth(response));
                    showToast('Info', 'Task health details retrieved successfully', 'info');
                } else {
                    showToast('Error', response.message || 'Failed to get task health', 'error');
                }
            },
            error: function(xhr, status, error) {
                handleAjaxError(xhr, status, error, 'get task health details');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function checkMonitoringState() {
        const btn = $('#monitoring-state-btn');
        setButtonLoading(btn, true);

        $.ajax({
            url: '/utility/health/monitoring-state',
            method: 'GET',
            success: function(response) {
                if (response.success) {
                    displayHealthResults('Personal Monitoring State', formatMonitoringState(response));
                    showToast('Info', 'Monitoring state retrieved successfully', 'info');
                } else {
                    showToast('Error', response.message || 'Failed to get monitoring state', 'error');
                }
            },
            error: function(xhr, status, error) {
                handleAjaxError(xhr, status, error, 'get monitoring state');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function getHealthSummary() {
        const btn = $('#health-summary-btn');
        setButtonLoading(btn, true);

        // Get comprehensive health summary
        Promise.all([
            $.get('/utility/health/overall'),
            $.get('/utility/health/tasks'),
            $.get('/utility/health/monitoring-state')
        ]).then(function(responses) {
            const [overall, tasks, monitoring] = responses;
            const summaryData = {
                overall: overall,
                tasks: tasks,
                monitoring: monitoring
            };

            displayHealthResults('Comprehensive Health Summary', formatHealthSummary(summaryData));
            showToast('Success', 'Health summary generated successfully', 'success');
        }).catch(function(error) {
            handleAjaxError(error, 'error', 'Failed to load', 'generate health summary');
        }).finally(function() {
            setButtonLoading(btn, false);
        });
    }

    // ========================================================================
    // DISPLAY FUNCTIONS
    // ========================================================================

    function displayHealthResults(title, content) {
        $('#health-content').html(`
            <div class="health-result">
                <h5>${title}</h5>
                <div class="result-content">
                    ${content}
                </div>
                <div class="result-meta">
                    <small class="text-muted">Retrieved at: ${new Date().toLocaleString()}</small>
                </div>
            </div>
        `);
        $('#health-results').fadeIn();
    }

    function formatOverallHealth(data) {
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

    function formatTaskHealth(data) {
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

    function formatMonitoringState(data) {
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

    // FIXED: formatHealthSummary function with proper template literals
    function formatHealthSummary(data) {
        const overall = data.overall;
        const tasks = data.tasks;
        const monitoring = data.monitoring;

        // Build task status HTML separately to avoid template literal nesting issues
        let taskStatusHtml = '';
        if (tasks.success && tasks.healthStatus) {
            const taskEntries = Object.entries(tasks.healthStatus);
            taskStatusHtml = taskEntries.map(([taskId, healthy]) => {
                return `<div class="task-summary-item">
                    <span class="task-name">${taskId}</span>
                    <span class="${healthy ? 'text-success' : 'text-danger'}">
                        ${healthy ? '✓' : '✗'}
                    </span>
                </div>`;
            }).join('');
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

    // Expose health functions globally
    window.HealthUtility = {
        checkOverall: checkOverallHealth,
        checkTasks: checkTaskHealth,
        refreshOverview: loadHealthOverview,
        getSummary: getHealthSummary
    };

    console.log('✅ Health utility initialized');
});