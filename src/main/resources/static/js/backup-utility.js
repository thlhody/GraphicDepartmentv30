// backup-utility.js - Complete Enhanced Version
$(document).ready(function() {
    console.log('🔧 Backup utility loaded (enhanced)');

    // Set current month as default
    const currentMonth = new Date().getMonth() + 1;
    $('#backup-month').val(currentMonth);

    // Toggle icon animation
    $('#backup-controls').on('show.bs.collapse', function() {
        $('#backup-utility .toggle-icon').removeClass('bi-chevron-down').addClass('bi-chevron-up');
    }).on('hide.bs.collapse', function() {
        $('#backup-utility .toggle-icon').removeClass('bi-chevron-up').addClass('bi-chevron-down');
    });

    // Basic Operations
    $('#list-backups-btn').click(function(e) {
        e.preventDefault();
        listBackups();
    });

    $('#create-backup-btn').click(function(e) {
        e.preventDefault();
        createBackup();
    });

    // Enhanced Operations
    $('#restore-latest-btn').click(function(e) {
        e.preventDefault();
        restoreLatestBackup();
    });

    $('#backup-diagnostics-btn').click(function(e) {
        e.preventDefault();
        getBackupDiagnostics();
    });

    $('#memory-backup-btn').click(function(e) {
        e.preventDefault();
        createMemoryBackup();
    });

    $('#cleanup-backup-btn').click(function(e) {
        e.preventDefault();
        cleanupBackup();
    });

    // Clear Results
    $('#clear-backup-results').click(function() {
        $('#backup-results').fadeOut();
        $('#backup-list').empty();
        updateBackupInfo('List Backups', 'None', '--:--:--');
    });

    // ========================================================================
    // BACKUP OPERATIONS
    // ========================================================================

    function listBackups() {
        const fileType = $('#backup-file-type').val();
        const btn = $('#list-backups-btn');

        if (btn.prop('disabled')) return;

        setButtonLoading(btn, true);
        updateBackupInfo('List Backups', 'In Progress', getCurrentTime());

        $.ajax({
            url: '/utility/backups/list',
            method: 'GET',
            data: { fileType: fileType },
            success: function(response) {
                if (response.success) {
                    displayBackupList(response.backups, fileType);
                    $('#backup-results').show();
                    $('#backup-count').text(response.totalFound);
                    $('#backup-count-display').text(response.totalFound);

                    updateBackupInfo('List Backups', 'Success', getCurrentTime());
                    showToast('Success', `Found ${response.totalFound} backups`, 'success');
                } else {
                    updateBackupInfo('List Backups', 'Failed', getCurrentTime());
                    showToast('Error', response.message || 'Failed to list backups', 'error');
                }
            },
            error: function(xhr, status, error) {
                console.error('Backup list error:', error);
                updateBackupInfo('List Backups', 'Error', getCurrentTime());
                handleAjaxError(xhr, status, error, 'list backups');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function createBackup() {
        const fileType = $('#backup-file-type').val();
        const year = $('#backup-year').val();
        const month = $('#backup-month').val();
        const btn = $('#create-backup-btn');

        if (btn.prop('disabled')) return;

        setButtonLoading(btn, true);
        updateBackupInfo('Create Backup', 'In Progress', getCurrentTime());

        $.ajax({
            url: '/utility/backups/create',
            method: 'POST',
            data: {
                fileType: fileType,
                year: year,
                month: month
            },
            success: function(response) {
                if (response.success) {
                    updateBackupInfo('Create Backup', 'Success', getCurrentTime());
                    showToast('Success', 'Backup created successfully', 'success');

                    // Refresh the list after creating
                    setTimeout(() => {
                        $('#list-backups-btn').click();
                    }, 1000);
                } else {
                    updateBackupInfo('Create Backup', 'Failed', getCurrentTime());
                    showToast('Error', response.message || 'Failed to create backup', 'error');
                }
            },
            error: function(xhr, status, error) {
                console.error('Backup create error:', error);
                updateBackupInfo('Create Backup', 'Error', getCurrentTime());
                handleAjaxError(xhr, status, error, 'create backup');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function restoreLatestBackup() {
        const fileType = $('#backup-file-type').val();
        const year = $('#backup-year').val();
        const month = $('#backup-month').val();
        const btn = $('#restore-latest-btn');

        if (!confirm(`Are you sure you want to restore the latest backup for ${fileType}? This will overwrite your current file.`)) {
            return;
        }

        setButtonLoading(btn, true);
        updateBackupInfo('Restore Latest', 'In Progress', getCurrentTime());

        $.ajax({
            url: '/utility/backups/restore-latest',
            method: 'POST',
            data: {
                fileType: fileType,
                year: year,
                month: month
            },
            success: function(response) {
                if (response.success) {
                    updateBackupInfo('Restore Latest', 'Success', getCurrentTime());
                    showToast('Success', 'Latest backup restored successfully', 'success');
                } else {
                    updateBackupInfo('Restore Latest', 'Failed', getCurrentTime());
                    showToast('Error', response.message || 'Failed to restore latest backup', 'error');
                }
            },
            error: function(xhr, status, error) {
                console.error('Latest restore error:', error);
                updateBackupInfo('Restore Latest', 'Error', getCurrentTime());
                handleAjaxError(xhr, status, error, 'restore latest backup');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function getBackupDiagnostics() {
        const fileType = $('#backup-file-type').val();
        const year = $('#backup-year').val();
        const month = $('#backup-month').val();
        const btn = $('#backup-diagnostics-btn');

        setButtonLoading(btn, true);
        updateBackupInfo('Diagnostics', 'In Progress', getCurrentTime());

        const params = { fileType: fileType };
        if (year && month) {
            params.year = year;
            params.month = month;
        }

        $.ajax({
            url: '/utility/backups/diagnostics',
            method: 'GET',
            data: params,
            success: function(response) {
                if (response.success) {
                    displayBackupDiagnostics(response);
                    updateBackupInfo('Diagnostics', 'Success', getCurrentTime());
                    showToast('Info', 'Backup diagnostics retrieved successfully', 'info');
                } else {
                    updateBackupInfo('Diagnostics', 'Failed', getCurrentTime());
                    showToast('Error', response.message || 'Failed to get backup diagnostics', 'error');
                }
            },
            error: function(xhr, status, error) {
                console.error('Diagnostics error:', error);
                updateBackupInfo('Diagnostics', 'Error', getCurrentTime());
                handleAjaxError(xhr, status, error, 'get backup diagnostics');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function createMemoryBackup() {
        const fileType = $('#backup-file-type').val();
        const year = $('#backup-year').val();
        const month = $('#backup-month').val();
        const btn = $('#memory-backup-btn');

        setButtonLoading(btn, true);
        updateBackupInfo('Memory Backup', 'In Progress', getCurrentTime());

        $.ajax({
            url: '/utility/backups/memory-backup',
            method: 'POST',
            data: {
                fileType: fileType,
                year: year,
                month: month
            },
            success: function(response) {
                if (response.success) {
                    updateBackupInfo('Memory Backup', 'Success', getCurrentTime());
                    showToast('Success', `Memory backup created (${formatBytes(response.backupSize)})`, 'success');
                } else {
                    updateBackupInfo('Memory Backup', 'Failed', getCurrentTime());
                    showToast('Error', response.message || 'Failed to create memory backup', 'error');
                }
            },
            error: function(xhr, status, error) {
                console.error('Memory backup error:', error);
                updateBackupInfo('Memory Backup', 'Error', getCurrentTime());
                handleAjaxError(xhr, status, error, 'create memory backup');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    function cleanupBackup() {
        const fileType = $('#backup-file-type').val();
        const year = $('#backup-year').val();
        const month = $('#backup-month').val();
        const btn = $('#cleanup-backup-btn');

        if (!confirm('Are you sure you want to clean up simple backup files? This action cannot be undone.')) {
            return;
        }

        setButtonLoading(btn, true);
        updateBackupInfo('Cleanup', 'In Progress', getCurrentTime());

        $.ajax({
            url: '/utility/backups/cleanup',
            method: 'DELETE',
            data: {
                fileType: fileType,
                year: year,
                month: month
            },
            success: function(response) {
                if (response.success) {
                    updateBackupInfo('Cleanup', 'Success', getCurrentTime());
                    showToast('Success', 'Simple backup cleaned up successfully', 'success');

                    // Refresh the list after cleanup
                    setTimeout(() => {
                        $('#list-backups-btn').click();
                    }, 1000);
                } else {
                    updateBackupInfo('Cleanup', 'Failed', getCurrentTime());
                    showToast('Error', response.message || 'Failed to cleanup backup', 'error');
                }
            },
            error: function(xhr, status, error) {
                console.error('Cleanup error:', error);
                updateBackupInfo('Cleanup', 'Error', getCurrentTime());
                handleAjaxError(xhr, status, error, 'cleanup backup');
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    }

    // ========================================================================
    // DISPLAY FUNCTIONS
    // ========================================================================

    function displayBackupList(backups, fileType) {
        const backupList = $('#backup-list');
        backupList.empty();

        if (backups.length === 0) {
            backupList.html('<div class="no-results">No backups found for ' + fileType + '</div>');
            return;
        }

        backups.forEach(function(backup) {
            const backupItem = $(`
                <div class="backup-item">
                    <div class="backup-info">
                        <div class="backup-name">${backup.displayPath || backup.path.split('/').pop()}</div>
                        <div class="backup-meta">
                            <span class="backup-date">${backup.formattedDate}</span>
                            ${backup.size ? '<span class="backup-size">' + formatBytes(backup.size) + '</span>' : ''}
                            ${backup.type ? '<span class="backup-type">' + backup.type + '</span>' : ''}
                            ${backup.criticalityLevel ? '<span class="backup-criticality">' + backup.criticalityLevel + '</span>' : ''}
                        </div>
                    </div>
                    <div class="backup-actions">
                        <button class="btn-restore" data-path="${backup.path}" data-type="${fileType}">
                            <i class="bi bi-arrow-clockwise"></i>
                            <span>Restore</span>
                        </button>
                    </div>
                </div>
            `);
            backupList.append(backupItem);
        });

        // Attach restore click handlers
        attachRestoreHandlers();
    }

    function displayBackupDiagnostics(data) {
        const diagnosticsHtml = `
            <div class="backup-diagnostics-display">
                <h5>Backup System Diagnostics</h5>

                <div class="diagnostics-section">
                    <h6>Utility Diagnostics</h6>
                    <pre class="diagnostics-text">${data.utilityDiagnostics || 'No data available'}</pre>
                </div>

                ${data.fileDiagnostics ? `
                    <div class="diagnostics-section">
                        <h6>File-Specific Diagnostics</h6>
                        <pre class="diagnostics-text">${data.fileDiagnostics}</pre>
                    </div>
                ` : ''}

                ${data.eventDiagnostics ? `
                    <div class="diagnostics-section">
                        <h6>Event System Diagnostics</h6>
                        <pre class="diagnostics-text">${data.eventDiagnostics}</pre>
                    </div>
                ` : ''}

                <div class="diagnostics-meta">
                    <small class="text-muted">File Type: ${data.fileType || 'General'}</small>
                </div>
            </div>
        `;

        $('#backup-list').html(diagnosticsHtml);
        $('#backup-results').show();
    }

    function attachRestoreHandlers() {
        $('.btn-restore').off('click').on('click', function() {
            const btn = $(this);
            const backupPath = btn.data('path');
            const fileType = btn.data('type');
            const year = $('#backup-year').val();
            const month = $('#backup-month').val();

            // Confirm restore
            if (!confirm('Are you sure you want to restore this backup? This will overwrite your current file.')) {
                return;
            }

            setButtonLoading(btn, true);
            updateBackupInfo('Restore Backup', 'In Progress', getCurrentTime());

            $.ajax({
                url: '/utility/backups/restore',
                method: 'POST',
                data: {
                    backupPath: backupPath,
                    fileType: fileType,
                    year: year,
                    month: month
                },
                success: function(response) {
                    if (response.success) {
                        updateBackupInfo('Restore Backup', 'Success', getCurrentTime());
                        showToast('Success', 'Backup restored successfully', 'success');
                    } else {
                        updateBackupInfo('Restore Backup', 'Failed', getCurrentTime());
                        showToast('Error', response.message || 'Failed to restore backup', 'error');
                    }
                },
                error: function(xhr, status, error) {
                    console.error('Backup restore error:', error);
                    updateBackupInfo('Restore Backup', 'Error', getCurrentTime());
                    handleAjaxError(xhr, status, error, 'restore backup');
                },
                complete: function() {
                    setButtonLoading(btn, false);
                }
            });
        });
    }

    function updateBackupInfo(operation, result, time) {
        $('#last-backup-operation').text(operation);
        $('#backup-operation-time').text(time);

        // Update result with color coding - find the correct element
        const resultElements = $('.stat-item').filter(function() {
            return $(this).find('.stat-label').text() === 'Last Operation';
        });

        if (resultElements.length > 0) {
            const resultElement = resultElements.find('.stat-value');
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

    function formatBytes(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
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

    // Expose backup functions globally
    window.BackupUtility = {
        list: listBackups,
        create: createBackup,
        restoreLatest: restoreLatestBackup,
        diagnostics: getBackupDiagnostics,
        memoryBackup: createMemoryBackup,
        cleanup: cleanupBackup,
        refreshStatus: function() {
            // Refresh backup information without showing results
            const fileType = $('#backup-file-type').val();
            if (fileType) {
                $.get('/utility/backups/list', { fileType: fileType }, function(response) {
                    if (response.success) {
                        $('#backup-count-display').text(response.totalFound);
                        updateBackupInfo('Auto Refresh', 'Updated', getCurrentTime());
                    }
                });
            }
        }
    };

    console.log('✅ Backup utility initialized (enhanced)');
});