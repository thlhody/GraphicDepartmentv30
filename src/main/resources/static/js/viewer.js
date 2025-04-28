/**
 * Log Viewer JavaScript
 * Handles the functionality for the log viewer interface
 */
document.addEventListener('DOMContentLoaded', function() {
    let currentUsername = null;
    let logData = null;
    let autoScroll = true;
    let textWrap = true;

    // Update UI counts
    updateUserCount();

    // Initialize log viewer
    initializeLogViewer();

    // Refresh logs list
    $('#refreshLogsBtn').click(function() {
        refreshLogsList();
    });

    // Load user log when a user is clicked
    $(document).on('click', '.user-item', function(e) {
        e.preventDefault();
        $('.user-item').removeClass('active');
        $(this).addClass('active');

        currentUsername = $(this).data('username');
        loadUserLog(currentUsername);
    });

    // User search functionality
    $('#userSearch').on('input', function() {
        const searchTerm = $(this).val().toLowerCase();
        $('.user-item').each(function() {
            const username = $(this).text().toLowerCase();
            $(this).toggle(username.includes(searchTerm));
        });
    });

    // Log search functionality
    $('#searchBtn').click(function() {
        filterLogs();
    });

    $('#logSearch').on('keypress', function(e) {
        if (e.which === 13) {
            filterLogs();
        }
    });

    // Clear filters
    $('#clearFilterBtn').click(function() {
        $('#logSearch').val('');
        if (logData) {
            displayLogData(logData);
        }
    });

    // Auto-scroll toggle
    $('#autoScrollBtn').click(function() {
        $(this).toggleClass('active');
        autoScroll = $(this).hasClass('active');
        if (autoScroll && logData) {
            scrollToBottom();
        }
    });

    // Text wrap toggle
    $('#wrapTextBtn').click(function() {
        $(this).toggleClass('active');
        textWrap = $(this).hasClass('active');
        $('#logContent').toggleClass('nowrap', !textWrap);
    });

    // Export button
    $('#exportBtn').click(function() {
        if (!logData || !currentUsername) return;

        const blob = new Blob([logData], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `log_${currentUsername}_${formatDate(new Date())}.log`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    });

    // Function to update user count
    function updateUserCount() {
        const count = $('.user-item').length;
        $('#userCount').text(count + (count === 1 ? ' user' : ' users'));
    }

    // Function to format date for filename
    function formatDate(date) {
        return date.toISOString().split('T')[0];
    }

    // Initialize log viewer settings
    function initializeLogViewer() {
        $('#logContent').toggleClass('nowrap', !textWrap);
    }

    // Function to scroll to bottom of logs
    function scrollToBottom() {
        const logContent = document.getElementById('logContent');
        logContent.scrollTop = logContent.scrollHeight;
    }

    // Function to load user log
    function loadUserLog(username) {
        $('#currentLogTitle').html(`<i class="bi bi-file-text me-2"></i>Log for <strong>${username}</strong>`);
        $('#logContent').html('<div class="log-loading"><div class="spinner-border text-primary" role="status"></div><p>Loading logs...</p></div>');
        $('#exportBtn').prop('disabled', true);

        // Get log data
        $.ajax({
            url: `/logs/${username}`,
            method: 'GET',
            success: function(data) {
                logData = data;
                if (data) {
                    displayLogData(data);
                    $('#exportBtn').prop('disabled', false);
                } else {
                    $('#logContent').html('<div class="log-placeholder"><div class="text-center"><i class="bi bi-exclamation-circle text-muted fs-1 mb-3"></i><p>No log entries found for this user</p></div></div>');
                    $('#logStats').text('No log entries found');
                }
            },
            error: function(xhr, status, error) {
                $('#logContent').html(`<div class="log-error"><div class="text-center"><i class="bi bi-exclamation-triangle text-danger fs-1 mb-3"></i><p>Error loading log: ${error}</p></div></div>`);
                $('#logStats').text('Error loading log');
                // Using the new toast alert system for error notification
                window.showToast('Error', 'Failed to load logs: ' + error, 'error');
            }
        });
    }

    // Function to display log data with formatting
    function displayLogData(logText) {
        if (!logText) {
            $('#logContent').html('<div class="log-placeholder"><div class="text-center"><i class="bi bi-exclamation-circle text-muted fs-1 mb-3"></i><p>No log entries found</p></div></div>');
            $('#logStats').text('No logs to display');
            return;
        }

        const lines = logText.split('\n');
        let formattedHtml = '<div class="log-lines">';
        let lineCount = 0;
        let errorCount = 0;
        let warningCount = 0;
        let infoCount = 0;

        lines.forEach((line, index) => {
            if (!line.trim()) return;

            lineCount++;
            let lineClass = '';

            // Detect log levels
            if (line.includes(' ERROR ') || line.includes('[ERROR]')) {
                lineClass = 'log-error';
                errorCount++;
            } else if (line.includes(' WARN ') || line.includes('[WARN]')) {
                lineClass = 'log-warning';
                warningCount++;
            } else if (line.includes(' INFO ') || line.includes('[INFO]')) {
                lineClass = 'log-info';
                infoCount++;
            }

            // Try to parse timestamp, class name/username, and message
            const timestampMatch = line.match(/^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})/);
            if (timestampMatch) {
                const timestamp = timestampMatch[1];
                const rest = line.substring(timestamp.length);

                // Try to extract class name or username
                const contextMatch = rest.match(/\[(.*?)\]/);
                if (contextMatch) {
                    const context = contextMatch[1];
                    const message = rest.substring(contextMatch[0].length);

                    formattedHtml += `<div class="log-line ${lineClass}" data-line="${index}">
                        <span class="log-timestamp">${timestamp}</span>
                        <span class="log-context">[${context}]</span>
                        <span class="log-message">${message}</span>
                    </div>`;
                } else {
                    formattedHtml += `<div class="log-line ${lineClass}" data-line="${index}">
                        <span class="log-timestamp">${timestamp}</span>
                        <span class="log-message">${rest}</span>
                    </div>`;
                }
            } else {
                formattedHtml += `<div class="log-line ${lineClass}" data-line="${index}">${line}</div>`;
            }
        });

        formattedHtml += '</div>';
        $('#logContent').html(formattedHtml);

        // Update stats
        $('#logStats').html(`
            <span class="me-3"><i class="bi bi-list-ul me-1"></i>${lineCount} lines</span>
            <span class="badge bg-danger me-1">${errorCount} errors</span>
            <span class="badge bg-warning text-dark me-1">${warningCount} warnings</span>
            <span class="badge bg-info text-dark">${infoCount} info</span>
        `);

        // Scroll to bottom if auto-scroll is enabled
        if (autoScroll) {
            scrollToBottom();
        }
    }

    // Function to filter logs
    function filterLogs() {
        if (!logData) return;

        const searchTerm = $('#logSearch').val().toLowerCase();
        if (!searchTerm) {
            displayLogData(logData);
            return;
        }

        const lines = logData.split('\n');
        let filteredLines = [];

        lines.forEach(line => {
            if (line.toLowerCase().includes(searchTerm)) {
                filteredLines.push(line);
            }
        });

        if (filteredLines.length === 0) {
            $('#logContent').html(`<div class="log-placeholder"><div class="text-center"><i class="bi bi-search text-muted fs-1 mb-3"></i><p>No matching logs found for "${searchTerm}"</p></div></div>`);
            $('#logStats').html(`<span>No matches for "${searchTerm}"</span>`);
            // Using the new toast alert system
            window.showToast('Search Results', `No matches found for "${searchTerm}"`, 'info');
        } else {
            displayLogData(filteredLines.join('\n'));
            $('#logStats').prepend(`<span class="badge bg-primary me-3">Filtered: ${filteredLines.length}/${lines.length}</span> `);
            // Using the new toast alert system
            window.showToast('Search Results', `Found ${filteredLines.length} matches for "${searchTerm}"`, 'success');
        }
    }

    // Function to refresh logs list
    function refreshLogsList() {
        $('#refreshLogsBtn').prop('disabled', true).html('<i class="bi bi-arrow-repeat spin me-2"></i>Refreshing...');

        $.ajax({
            url: '/logs/list',
            method: 'GET',
            success: function(data) {
                let userListHtml = '';
                if (data && data.length > 0) {
                    data.forEach(function(username) {
                        const isActive = username === currentUsername ? 'active' : '';
                        userListHtml += `<a href="#" data-username="${username}"
                                        class="list-group-item list-group-item-action user-item ${isActive}">
                                        ${username}</a>`;
                    });
                } else {
                    userListHtml = '<div class="list-group-item text-muted">No logs available</div>';
                }
                $('#usersList').html(userListHtml);
                updateUserCount();

                // If current user is still in the list, reload their log
                if (currentUsername && data.includes(currentUsername)) {
                    loadUserLog(currentUsername);
                }

                // Using the new toast alert system
                window.showToast('Success', 'Log list refreshed from network', 'success');
            },
            error: function(xhr, status, error) {
                // Using the new toast alert system
                window.showToast('Error', 'Failed to refresh logs: ' + error, 'error');
            },
            complete: function() {
                $('#refreshLogsBtn').prop('disabled', false).html('<i class="bi bi-arrow-clockwise me-2"></i>Refresh Logs List');
            }
        });
    }
});