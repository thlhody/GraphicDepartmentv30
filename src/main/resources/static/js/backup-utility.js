// backup-utility.js
$(document).ready(function() {
    console.log('🔧 Backup utility loaded');

    // Set current month as default
    const currentMonth = new Date().getMonth() + 1;
    $('#backup-month').val(currentMonth);

    // Toggle icon animation
    $('#backup-controls').on('show.bs.collapse', function() {
        $('#backup-utility .toggle-icon').removeClass('bi-chevron-down').addClass('bi-chevron-up');
    }).on('hide.bs.collapse', function() {
        $('#backup-utility .toggle-icon').removeClass('bi-chevron-up').addClass('bi-chevron-down');
    });

    // List Backups
    $('#list-backups-btn').click(function(e) {
        e.preventDefault();

        const fileType = $('#backup-file-type').val();
        const btn = $(this);

        if (btn.prop('disabled')) return;

        setButtonLoading(btn, true);

        $.ajax({
            url: '/utility/backups/list',
            method: 'GET',
            data: { fileType: fileType },
            success: function(response) {
                if (response.success) {
                    displayBackupList(response.backups, fileType);
                    $('#backup-results').show();
                    $('#backup-count').text(response.totalFound);

                    if (typeof window.showToast === 'function') {
                        window.showToast('Success', `Found ${response.totalFound} backups`, 'success');
                    }
                } else {
                    if (typeof window.showToast === 'function') {
                        window.showToast('Error', response.message || 'Failed to list backups', 'error');
                    }
                }
            },
            error: function(xhr, status, error) {
                console.error('Backup list error:', error);
                if (typeof window.showToast === 'function') {
                    window.showToast('Error', 'Failed to list backups: ' + error, 'error');
                }
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    });

    // Create Backup
    $('#create-backup-btn').click(function() {
        const fileType = $('#backup-file-type').val();
        const year = $('#backup-year').val();
        const month = $('#backup-month').val();
        const btn = $(this);

        if (btn.prop('disabled')) return;

        setButtonLoading(btn, true);

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
                    if (typeof window.showToast === 'function') {
                        window.showToast('Success', 'Backup created successfully', 'success');
                    }
                    // Refresh the list
                    $('#list-backups-btn').click();
                } else {
                    if (typeof window.showToast === 'function') {
                        window.showToast('Error', response.message || 'Failed to create backup', 'error');
                    }
                }
            },
            error: function(xhr, status, error) {
                console.error('Backup create error:', error);
                if (typeof window.showToast === 'function') {
                    window.showToast('Error', 'Failed to create backup: ' + error, 'error');
                }
            },
            complete: function() {
                setButtonLoading(btn, false);
            }
        });
    });

    // Display backup list
    function displayBackupList(backups, fileType) {
        const backupList = $('#backup-list');
        backupList.empty();

        if (backups.length === 0) {
            backupList.html('<div class="no-results">No backups found for ' + fileType + '</div>');
            return;
        }

        backups.forEach(function(backup) {
            // Clean up the filename - show only the essential part
            let displayName = backup.path.split('/').pop();

            // Further cleanup - remove redundant parts
            displayName = displayName
                .replace(/^(local_)?/, '')  // Remove local_ prefix
                .replace(/\.bak$/, '')      // Remove .bak extension
                .replace(/\.\d{8}_\d{6}$/, ''); // Remove timestamp suffix

            const backupItem = $(`
                <div class="backup-item">
                    <div class="backup-info">
                            <div class="backup-name">${backup.displayPath || backup.path.split('/').pop()}</div>
                            <div class="backup-meta">
                            <span class="backup-date">${backup.formattedDate}</span>
                            ${backup.size ? '<span class="backup-size">' + formatBytes(backup.size) + '</span>' : ''}
                            ${backup.type ? '<span class="backup-type">' + backup.type + '</span>' : ''}
                        </div>
                    </div>
                    <button class="btn-restore" data-path="${backup.path}" data-type="${fileType}">
                        <i class="bi bi-arrow-clockwise"></i>
                        <span>Restore</span>
                    </button>
                </div>
            `);
            backupList.append(backupItem);
        });

        // Attach restore click handlers
        attachRestoreHandlers();
    }

    // Attach restore button handlers
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
                        if (typeof window.showToast === 'function') {
                            window.showToast('Success', 'Backup restored successfully', 'success');
                        }
                    } else {
                        if (typeof window.showToast === 'function') {
                            window.showToast('Error', response.message || 'Failed to restore backup', 'error');
                        }
                    }
                },
                error: function(xhr, status, error) {
                    console.error('Backup restore error:', error);
                    if (typeof window.showToast === 'function') {
                        window.showToast('Error', 'Failed to restore backup: ' + error, 'error');
                    }
                },
                complete: function() {
                    setButtonLoading(btn, false);
                }
            });
        });
    }

    // Button loading state
    function setButtonLoading(btn, loading) {
        if (loading) {
            btn.addClass('loading').prop('disabled', true);
            btn.find('i').addClass('spin');
        } else {
            btn.removeClass('loading').prop('disabled', false);
            btn.find('i').removeClass('spin');
        }
    }

    // Format bytes helper
    function formatBytes(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    }
});