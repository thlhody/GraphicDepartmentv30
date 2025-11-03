/**
 * Merge Management Utility
 * Handles pending merge operations monitoring and clearing
 */
(function() {
    'use strict';

    const MergeUtility = {
        // State
        pendingCount: 0,
        userHasPending: false,
        severity: 'none',

        /**
         * Initialize merge utility
         */
        init: function() {
            console.log('🔀 Initializing Merge Utility...');
            this.bindEvents();
            this.refreshOverview();
            console.log('✅ Merge Utility ready');
        },

        /**
         * Bind event handlers
         */
        bindEvents: function() {
            const self = this;

            // Check merge status
            $('#check-merge-status-btn').on('click', function() {
                self.checkMergeStatus();
            });

            // Refresh merge count
            $('#refresh-merge-count-btn').on('click', function() {
                self.refreshMergeCount();
            });

            // Clear pending merges
            $('#clear-pending-merges-btn').on('click', function() {
                self.confirmClearPendingMerges();
            });

            // NEW: Check strategy status
            $('#check-strategy-status-btn').on('click', function() {
                self.checkStrategyStatus();
            });

            // NEW: Force full merge
            $('#force-full-merge-btn').on('click', function() {
                self.confirmForceFullMerge();
            });

            // Clear results
            $('#clear-merge-results').on('click', function() {
                $('#merge-results').fadeOut();
            });
        },

        /**
         * Refresh overview (auto-called on load)
         */
        refreshOverview: function() {
            console.log('🔄 Refreshing merge overview...');
            this.refreshMergeCount();
        },

        /**
         * Check merge status for current user
         */
        checkMergeStatus: function() {
            const self = this;
            const csrfToken = $('meta[name="_csrf"]').attr('content');
            const csrfHeader = $('meta[name="_csrf_header"]').attr('content');

            console.log('🔍 Checking merge status...');

            $.ajax({
                url: '/utility/merge/pending-status',
                type: 'GET',
                headers: {
                    [csrfHeader]: csrfToken
                },
                success: function(data) {
                    if (data.success) {
                        self.displayMergeStatus(data);
                        self.updateStats(data);

                        // Show toast notification
                        if (typeof window.showToast === 'function') {
                            const message = data.hasPendingMerges
                                ? `You have ${data.pendingCount} pending merge(s)`
                                : 'No pending merges';
                            const type = data.hasPendingMerges ? 'warning' : 'success';
                            window.showToast('Merge Status', message, type);
                        }
                    } else {
                        self.showError('Failed to check merge status: ' + data.message);
                    }
                },
                error: function(xhr) {
                    const errorMsg = xhr.responseJSON?.message || 'Failed to check merge status';
                    self.showError(errorMsg);
                }
            });
        },

        /**
         * Refresh merge count (lighter operation)
         */
        refreshMergeCount: function() {
            const self = this;
            const csrfToken = $('meta[name="_csrf"]').attr('content');
            const csrfHeader = $('meta[name="_csrf_header"]').attr('content');

            $.ajax({
                url: '/utility/merge/pending-count',
                type: 'GET',
                headers: {
                    [csrfHeader]: csrfToken
                },
                success: function(data) {
                    if (data.success) {
                        self.pendingCount = data.pendingCount;
                        self.severity = data.severity || 'none';

                        // Update display
                        $('#pending-merge-count').text(data.pendingCount);
                        $('#merge-severity').text(self.getSeverityText(data.severity));

                        // Update severity styling
                        self.updateSeverityStyle(data.severity);

                        // Show warning banner if needed
                        if (data.pendingCount > 0 && data.recommendation) {
                            self.showWarningBanner(data.recommendation);
                        } else {
                            self.hideWarningBanner();
                        }
                    }
                },
                error: function() {
                    $('#pending-merge-count').text('Error');
                    $('#merge-severity').text('-');
                }
            });
        },

        /**
         * Confirm and clear pending merges
         */
        confirmClearPendingMerges: function() {
            const self = this;

            const message =
                '⚠️ CLEAR PENDING MERGES ⚠️\n\n' +
                'This will clear ALL pending merge operations from the queue.\n\n' +
                'ONLY proceed if:\n' +
                '• Merge operations are confirmed stuck\n' +
                '• Network issues prevented completion\n' +
                '• Merges have not completed after long wait\n\n' +
                'Are you sure you want to continue?';

            if (confirm(message)) {
                self.clearPendingMerges();
            }
        },

        /**
         * Clear all pending merges
         */
        clearPendingMerges: function() {
            const self = this;
            const csrfToken = $('meta[name="_csrf"]').attr('content');
            const csrfHeader = $('meta[name="_csrf_header"]').attr('content');

            console.log('🗑️ Clearing pending merges...');

            $.ajax({
                url: '/utility/merge/clear-pending',
                type: 'POST',
                headers: {
                    [csrfHeader]: csrfToken
                },
                success: function(data) {
                    if (data.success) {
                        self.displayClearResult(data);
                        self.refreshMergeCount(); // Refresh count after clear

                        // Show success notification
                        if (typeof window.showToast === 'function') {
                            window.showToast('Success',
                                `Cleared ${data.clearedCount} pending merge operation(s)`,
                                'success');
                        }
                    } else {
                        self.showError('Failed to clear pending merges: ' + data.message);
                    }
                },
                error: function(xhr) {
                    const errorMsg = xhr.responseJSON?.message || 'Failed to clear pending merges';
                    self.showError(errorMsg);
                }
            });
        },

        /**
         * Display merge status in results area
         */
        displayMergeStatus: function(data) {
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

            $('#merge-content').html(html);
            $('#merge-results').fadeIn();
        },

        /**
         * Display clear result
         */
        displayClearResult: function(data) {
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

            $('#merge-content').html(html);
            $('#merge-results').fadeIn();
        },

        /**
         * Update stats display
         */
        updateStats: function(data) {
            $('#pending-merge-count').text(data.pendingCount);
            $('#user-merge-status').text(data.hasPendingMerges ? 'Has Pending' : 'No Pending');

            // Update user status styling
            const statusElement = $('#user-merge-status');
            statusElement.removeClass('text-success text-warning text-danger');
            statusElement.addClass(data.hasPendingMerges ? 'text-warning' : 'text-success');
        },

        /**
         * Update severity styling
         */
        updateSeverityStyle: function(severity) {
            const severityElement = $('#merge-severity');
            severityElement.removeClass('text-success text-info text-warning text-danger');

            switch(severity) {
                case 'high':
                    severityElement.addClass('text-danger');
                    break;
                case 'medium':
                    severityElement.addClass('text-warning');
                    break;
                case 'low':
                    severityElement.addClass('text-info');
                    break;
                default:
                    severityElement.addClass('text-success');
            }
        },

        /**
         * Get severity text
         */
        getSeverityText: function(severity) {
            switch(severity) {
                case 'high': return 'High';
                case 'medium': return 'Medium';
                case 'low': return 'Low';
                case 'none': return 'None';
                default: return '-';
            }
        },

        /**
         * Show warning banner
         */
        showWarningBanner: function(message) {
            $('#merge-warning-text').text(message);
            $('#merge-warning-banner').fadeIn();
        },

        /**
         * Hide warning banner
         */
        hideWarningBanner: function() {
            $('#merge-warning-banner').fadeOut();
        },

        /**
         * Show error message
         */
        showError: function(message) {
            console.error('Merge Utility Error:', message);

            const html = '<div class="alert alert-danger">' +
                        '<i class="bi bi-exclamation-triangle"></i> ' + message +
                        '</div>';

            $('#merge-content').html(html);
            $('#merge-results').fadeIn();

            if (typeof window.showToast === 'function') {
                window.showToast('Error', message, 'error');
            }
        },

        // ========================================================================
        // MERGE STRATEGY FUNCTIONS (NEW)
        // ========================================================================

        /**
         * NEW: Check merge strategy status
         */
        checkStrategyStatus: function() {
            const self = this;
            const csrfToken = $('meta[name="_csrf"]').attr('content');
            const csrfHeader = $('meta[name="_csrf_header"]').attr('content');

            console.log('📊 Checking merge strategy status...');

            $.ajax({
                url: '/utility/merge/strategy-status',
                type: 'GET',
                headers: {
                    [csrfHeader]: csrfToken
                },
                success: function(data) {
                    if (data.success) {
                        self.displayStrategyStatus(data);
                        self.updateStrategyStats(data);

                        // Show toast notification
                        if (typeof window.showToast === 'function') {
                            window.showToast('Strategy Status', data.statusDescription, 'info');
                        }
                    } else {
                        self.showError('Failed to check strategy status: ' + data.message);
                    }
                },
                error: function(xhr) {
                    const errorMsg = xhr.responseJSON?.message || 'Failed to check strategy status';
                    self.showError(errorMsg);
                }
            });
        },

        /**
         * NEW: Confirm and force full merge
         */
        confirmForceFullMerge: function() {
            const self = this;

            const message =
                '🔄 FORCE FULL DATA REFRESH 🔄\n\n' +
                'This will force a complete data merge on your next login.\n\n' +
                'Use this when:\n' +
                '• Your data seems outdated or stale\n' +
                '• Changes from admin are not showing up\n' +
                '• You need to ensure you have the latest data\n\n' +
                'Next login will take ~7 seconds instead of ~0.1 seconds.\n\n' +
                'Continue?';

            if (confirm(message)) {
                self.forceFullMerge();
            }
        },

        /**
         * NEW: Force full merge on next login
         */
        forceFullMerge: function() {
            const self = this;
            const csrfToken = $('meta[name="_csrf"]').attr('content');
            const csrfHeader = $('meta[name="_csrf_header"]').attr('content');

            console.log('🔄 Forcing full merge on next login...');

            $.ajax({
                url: '/utility/merge/force-full-merge',
                type: 'POST',
                headers: {
                    [csrfHeader]: csrfToken
                },
                success: function(data) {
                    if (data.success) {
                        self.displayForceResult(data);
                        self.checkStrategyStatus(); // Refresh strategy status

                        // Show success notification
                        if (typeof window.showToast === 'function') {
                            window.showToast('Success',
                                'Full data refresh scheduled for next login',
                                'success');
                        }
                    } else {
                        self.showError('Failed to force full merge: ' + data.message);
                    }
                },
                error: function(xhr) {
                    const errorMsg = xhr.responseJSON?.message || 'Failed to force full merge';
                    self.showError(errorMsg);
                }
            });
        },

        /**
         * NEW: Display strategy status in results area
         */
        displayStrategyStatus: function(data) {
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

            $('#merge-content').html(html);
            $('#merge-results').fadeIn();
        },

        /**
         * NEW: Display force merge result
         */
        displayForceResult: function(data) {
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

            $('#merge-content').html(html);
            $('#merge-results').fadeIn();
        },

        /**
         * NEW: Update strategy stats display
         */
        updateStrategyStats: function(data) {
            $('#strategy-login-count').text(data.loginCount);

            // Determine next strategy text
            const nextStrategy = data.shouldPerformFullMerge ? 'Full Merge' : 'Fast Refresh';
            $('#strategy-next-type').text(nextStrategy);

            // Update next strategy styling
            const nextTypeElement = $('#strategy-next-type');
            nextTypeElement.removeClass('text-success text-warning text-info');
            nextTypeElement.addClass(data.shouldPerformFullMerge ? 'text-warning' : 'text-success');

            // Update performance display
            $('#strategy-performance').text(data.performanceBenefit);

            // Show info banner if first login
            if (data.isFirstLogin) {
                this.showStrategyInfoBanner('First login of the day - next login will perform full data merge');
            } else {
                this.hideStrategyInfoBanner();
            }
        },

        /**
         * NEW: Show strategy info banner
         */
        showStrategyInfoBanner: function(message) {
            $('#strategy-info-text').text(message);
            $('#strategy-info-banner').fadeIn();
        },

        /**
         * NEW: Hide strategy info banner
         */
        hideStrategyInfoBanner: function() {
            $('#strategy-info-banner').fadeOut();
        }
    };

    // Initialize when document is ready
    $(document).ready(function() {
        MergeUtility.init();
    });

    // Expose to global scope
    window.MergeUtility = MergeUtility;

    console.log('✅ Merge Utility module loaded');
})();