/**
 * LogViewerManager.js
 *
 * Handles log viewer interface functionality.
 * Features: user selection, log loading, searching, filtering,
 * auto-scroll, text wrap, and export.
 *
 * @module features/viewer/LogViewerManager
 */

/**
 * LogViewerManager class
 * Manages log viewer operations
 *
 * NOTE: This class uses jQuery for DOM manipulation as it's
 * tightly integrated throughout the legacy codebase.
 */
export class LogViewerManager {
    constructor() {
        this.currentUsername = null;
        this.logData = null;
        this.autoScroll = true;
        this.textWrap = true;

        console.log('LogViewerManager initialized');
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize log viewer
     */
    initialize() {
        console.log('🚀 Initializing Log Viewer...');

        // Check if jQuery is available
        if (typeof $ === 'undefined') {
            console.error('jQuery not loaded - Log Viewer requires jQuery');
            return;
        }

        // Update UI counts
        this.updateUserCount();

        // Initialize log viewer
        this.initializeLogViewer();

        // Setup event listeners
        this.setupEventListeners();

        console.log('✅ Log Viewer initialized successfully');
    }

    /**
     * Initialize log viewer settings
     */
    initializeLogViewer() {
        $('#logContent').toggleClass('nowrap', !this.textWrap);
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Refresh logs list
        $('#refreshLogsBtn').click(() => {
            this.refreshLogsList();
        });

        // Load user log when a user is clicked
        $(document).on('click', '.user-item', (e) => {
            e.preventDefault();
            $('.user-item').removeClass('active');
            $(e.currentTarget).addClass('active');

            this.currentUsername = $(e.currentTarget).data('username');
            this.loadUserLog(this.currentUsername);
        });

        // User search functionality
        $('#userSearch').on('input', function() {
            const searchTerm = $(this).val().toLowerCase();
            $('.user-item').each(function() {
                const username = $(this).find('span:first').text().toLowerCase();
                $(this).toggle(username.includes(searchTerm));
            });
        });

        // Log search functionality
        $('#searchBtn').click(() => {
            this.filterLogs();
        });

        $('#logSearch').on('keypress', (e) => {
            if (e.which === 13) {
                this.filterLogs();
            }
        });

        // Clear filters
        $('#clearFilterBtn').click(() => {
            $('#logSearch').val('');
            if (this.logData) {
                this.displayLogData(this.logData);
            }
        });

        // Auto-scroll toggle
        $('#autoScrollBtn').click((e) => {
            $(e.currentTarget).toggleClass('active');
            this.autoScroll = $(e.currentTarget).hasClass('active');
            if (this.autoScroll && this.logData) {
                this.scrollToBottom();
            }
        });

        // Text wrap toggle
        $('#wrapTextBtn').click((e) => {
            $(e.currentTarget).toggleClass('active');
            this.textWrap = $(e.currentTarget).hasClass('active');
            $('#logContent').toggleClass('nowrap', !this.textWrap);
        });

        // Export button
        $('#exportBtn').click(() => {
            if (!this.logData || !this.currentUsername) return;

            const blob = new Blob([this.logData], { type: 'text/plain' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `log_${this.currentUsername}_${this.formatDate(new Date())}.log`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
        });
    }

    // ========================================================================
    // USER COUNT
    // ========================================================================

    /**
     * Update user count
     */
    updateUserCount() {
        const count = $('.user-item').length;
        $('#userCount').text(count + (count === 1 ? ' user' : ' users'));
    }

    // ========================================================================
    // LOG LOADING
    // ========================================================================

    /**
     * Load user log
     * @param {string} username - Username to load logs for
     */
    loadUserLog(username) {
        $('#currentLogTitle').html(`<i class="bi bi-file-text me-2"></i>Log for <strong>${username}</strong>`);
        $('#logContent').html('<div class="log-loading"><div class="spinner-border text-primary" role="status"></div><p>Loading logs...</p></div>');
        $('#exportBtn').prop('disabled', true);

        // Get log data
        $.ajax({
            url: `/logs/${username}`,
            method: 'GET',
            success: (data) => {
                this.logData = data;
                if (data) {
                    this.displayLogData(data);
                    $('#exportBtn').prop('disabled', false);
                } else {
                    $('#logContent').html('<div class="log-placeholder"><div class="text-center"><i class="bi bi-exclamation-circle text-muted fs-1 mb-3"></i><p>No log entries found for this user</p></div></div>');
                    $('#logStats').text('No log entries found');
                }
            },
            error: (xhr, status, error) => {
                $('#logContent').html(`<div class="log-error"><div class="text-center"><i class="bi bi-exclamation-triangle text-danger fs-1 mb-3"></i><p>Error loading log: ${error}</p></div></div>`);
                $('#logStats').text('Error loading log');
                // Using the toast alert system for error notification
                if (typeof window.showToast === 'function') {
                    window.showToast('Error', 'Failed to load logs: ' + error, 'error');
                }
            }
        });
    }

    // ========================================================================
    // LOG DISPLAY
    // ========================================================================

    /**
     * Display log data with formatting
     * @param {string} logText - Log text to display
     */
    displayLogData(logText) {
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
        if (this.autoScroll) {
            this.scrollToBottom();
        }
    }

    // ========================================================================
    // FILTERING
    // ========================================================================

    /**
     * Filter logs based on search term
     */
    filterLogs() {
        if (!this.logData) return;

        const searchTerm = $('#logSearch').val().toLowerCase();
        if (!searchTerm) {
            this.displayLogData(this.logData);
            return;
        }

        const lines = this.logData.split('\n');
        let filteredLines = [];

        lines.forEach(line => {
            if (line.toLowerCase().includes(searchTerm)) {
                filteredLines.push(line);
            }
        });

        if (filteredLines.length === 0) {
            $('#logContent').html(`<div class="log-placeholder"><div class="text-center"><i class="bi bi-search text-muted fs-1 mb-3"></i><p>No matching logs found for "${searchTerm}"</p></div></div>`);
            $('#logStats').html(`<span>No matches for "${searchTerm}"</span>`);
            // Using the toast alert system
            if (typeof window.showToast === 'function') {
                window.showToast('Search Results', `No matches found for "${searchTerm}"`, 'info');
            }
        } else {
            this.displayLogData(filteredLines.join('\n'));
            $('#logStats').prepend(`<span class="badge bg-primary me-3">Filtered: ${filteredLines.length}/${lines.length}</span> `);
            // Using the toast alert system
            if (typeof window.showToast === 'function') {
                window.showToast('Search Results', `Found ${filteredLines.length} matches for "${searchTerm}"`, 'success');
            }
        }
    }

    // ========================================================================
    // REFRESH LOGS LIST
    // ========================================================================

    /**
     * Refresh logs list with version information
     */
    refreshLogsList() {
        $('#refreshLogsBtn').prop('disabled', true).html('<i class="bi bi-arrow-repeat spin me-2"></i>Refreshing...');

        $.ajax({
            url: '/logs/list',
            method: 'GET',
            success: (data) => {
                let userListHtml = '';
                if (data && data.length > 0) {
                    data.forEach((logInfo) => {
                        // Use the UserLogInfo structure with username and version
                        const username = logInfo.username;
                        const version = logInfo.version || 'Unknown';
                        const isActive = username === this.currentUsername ? 'active' : '';
                        const badgeClass = version === 'Unknown' ? 'bg-warning text-dark' : 'bg-info text-white';

                        userListHtml += `
                            <a href="#" data-username="${username}"
                               class="list-group-item list-group-item-action user-item ${isActive}">
                               <div class="d-flex justify-content-between align-items-center">
                                   <span>${username}</span>
                                   <span class="badge ${badgeClass}" title="Application Version">${version}</span>
                               </div>
                            </a>`;
                    });
                } else {
                    userListHtml = '<div class="list-group-item text-muted">No logs available</div>';
                }
                $('#usersList').html(userListHtml);
                this.updateUserCount();

                // If current user is still in the list, reload their log
                const usernames = data.map(info => info.username);
                if (this.currentUsername && usernames.includes(this.currentUsername)) {
                    this.loadUserLog(this.currentUsername);
                }

                // Using the toast alert system
                if (typeof window.showToast === 'function') {
                    window.showToast('Success', 'Log list refreshed from network', 'success');
                }
            },
            error: (xhr, status, error) => {
                // Using the toast alert system
                if (typeof window.showToast === 'function') {
                    window.showToast('Error', 'Failed to refresh logs: ' + error, 'error');
                }
            },
            complete: () => {
                $('#refreshLogsBtn').prop('disabled', false).html('<i class="bi bi-arrow-clockwise me-2"></i>Refresh Logs List');
            }
        });
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Scroll to bottom of logs
     */
    scrollToBottom() {
        const logContent = document.getElementById('logContent');
        if (logContent) {
            logContent.scrollTop = logContent.scrollHeight;
        }
    }

    /**
     * Format date for filename
     * @param {Date} date - Date to format
     * @returns {string} Formatted date (YYYY-MM-DD)
     */
    formatDate(date) {
        return date.toISOString().split('T')[0];
    }
}
