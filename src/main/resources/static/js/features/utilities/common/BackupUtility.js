/**
 * BackupUtility.js (ES6 Module)
 *
 * Modern backup management utility for user self-service backup operations.
 * Handles listing, creating, restoring, and diagnosing backups.
 *
 * @module features/utilities/common/BackupUtility
 */

import { API } from '../../../core/api.js';
import { ToastNotification } from '../../../components/ToastNotification.js';

/**
 * BackupUtility class
 * Manages backup operations with modern ES6 patterns
 */
export class BackupUtility {
    constructor() {
        this.elements = {
            // Control elements
            fileTypeSelect: null,
            yearInput: null,
            monthInput: null,

            // Button elements
            listBtn: null,
            createBtn: null,
            restoreLatestBtn: null,
            diagnosticsBtn: null,
            memoryBackupBtn: null,
            cleanupBtn: null,
            clearBtn: null,

            // Display elements
            resultsContainer: null,
            backupList: null,
            backupCountDisplay: null,
            backupCount: null,
            lastOperation: null,
            operationTime: null,
            controls: null
        };

        console.log('🔧 Backup utility created (ES6)');
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize the backup utility
     */
    initialize() {
        console.log('🚀 Initializing Backup utility...');

        // Cache DOM elements
        this.cacheElements();

        // Setup event listeners
        this.setupEventListeners();

        // Set default values
        this.setDefaults();

        console.log('✅ Backup utility initialized');
    }

    /**
     * Cache DOM elements for better performance
     */
    cacheElements() {
        // Control elements
        this.elements.fileTypeSelect = document.getElementById('backup-file-type');
        this.elements.yearInput = document.getElementById('backup-year');
        this.elements.monthInput = document.getElementById('backup-month');

        // Button elements
        this.elements.listBtn = document.getElementById('list-backups-btn');
        this.elements.createBtn = document.getElementById('create-backup-btn');
        this.elements.restoreLatestBtn = document.getElementById('restore-latest-btn');
        this.elements.diagnosticsBtn = document.getElementById('backup-diagnostics-btn');
        this.elements.memoryBackupBtn = document.getElementById('memory-backup-btn');
        this.elements.cleanupBtn = document.getElementById('cleanup-backup-btn');
        this.elements.clearBtn = document.getElementById('clear-backup-results');

        // Display elements
        this.elements.resultsContainer = document.getElementById('backup-results');
        this.elements.backupList = document.getElementById('backup-list');
        this.elements.backupCountDisplay = document.getElementById('backup-count-display');
        this.elements.backupCount = document.getElementById('backup-count');
        this.elements.lastOperation = document.getElementById('last-backup-operation');
        this.elements.operationTime = document.getElementById('backup-operation-time');
        this.elements.controls = document.getElementById('backup-controls');
    }

    /**
     * Setup event listeners for all buttons
     */
    setupEventListeners() {
        // Collapse toggle animation
        if (this.elements.controls) {
            const backupUtility = document.querySelector('#backup-utility');
            const toggleIcon = backupUtility?.querySelector('.toggle-icon');

            $(this.elements.controls).on('show.bs.collapse', () => {
                toggleIcon?.classList.remove('bi-chevron-down');
                toggleIcon?.classList.add('bi-chevron-up');
            }).on('hide.bs.collapse', () => {
                toggleIcon?.classList.remove('bi-chevron-up');
                toggleIcon?.classList.add('bi-chevron-down');
            });
        }

        // Basic operations
        this.elements.listBtn?.addEventListener('click', (e) => {
            e.preventDefault();
            this.listBackups();
        });

        this.elements.createBtn?.addEventListener('click', (e) => {
            e.preventDefault();
            this.createBackup();
        });

        // Enhanced operations
        this.elements.restoreLatestBtn?.addEventListener('click', (e) => {
            e.preventDefault();
            this.restoreLatestBackup();
        });

        this.elements.diagnosticsBtn?.addEventListener('click', (e) => {
            e.preventDefault();
            this.getBackupDiagnostics();
        });

        this.elements.memoryBackupBtn?.addEventListener('click', (e) => {
            e.preventDefault();
            this.createMemoryBackup();
        });

        this.elements.cleanupBtn?.addEventListener('click', (e) => {
            e.preventDefault();
            this.cleanupBackup();
        });

        // Clear results
        this.elements.clearBtn?.addEventListener('click', () => {
            this.clearResults();
        });
    }

    /**
     * Set default values
     */
    setDefaults() {
        // Set current month as default
        if (this.elements.monthInput) {
            const currentMonth = new Date().getMonth() + 1;
            this.elements.monthInput.value = currentMonth;
        }
    }

    // ========================================================================
    // BACKUP OPERATIONS
    // ========================================================================

    /**
     * List available backups
     */
    async listBackups() {
        const fileType = this.elements.fileTypeSelect?.value;
        const btn = this.elements.listBtn;

        if (!fileType || btn?.disabled) return;

        this.setButtonLoading(btn, true);
        this.updateBackupInfo('List Backups', 'In Progress', this.getCurrentTime());

        try {
            const data = await API.get('/utility/backups/list', { fileType });

            if (data.success) {
                this.displayBackupList(data.backups, fileType);
                if (this.elements.resultsContainer) {
                    this.elements.resultsContainer.style.display = 'block';
                }
                if (this.elements.backupCount) {
                    this.elements.backupCount.textContent = data.totalFound;
                }
                if (this.elements.backupCountDisplay) {
                    this.elements.backupCountDisplay.textContent = data.totalFound;
                }

                this.updateBackupInfo('List Backups', 'Success', this.getCurrentTime());
                ToastNotification.success('Success', `Found ${data.totalFound} backup(s)`);
            } else {
                this.updateBackupInfo('List Backups', 'Failed', this.getCurrentTime());
                ToastNotification.error('Error', data.message || 'Failed to list backups');
            }
        } catch (error) {
            console.error('Backup list error:', error);
            this.updateBackupInfo('List Backups', 'Error', this.getCurrentTime());
            ToastNotification.error('Error', `Failed to list backups: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Create a new backup
     */
    async createBackup() {
        const fileType = this.elements.fileTypeSelect?.value;
        const year = this.elements.yearInput?.value;
        const month = this.elements.monthInput?.value;
        const btn = this.elements.createBtn;

        if (btn?.disabled) return;

        this.setButtonLoading(btn, true);
        this.updateBackupInfo('Create Backup', 'In Progress', this.getCurrentTime());

        try {
            // Backend expects form parameters, not JSON
            const data = await API.postForm('/utility/backups/create', {
                fileType,
                year,
                month
            });

            if (data.success) {
                this.updateBackupInfo('Create Backup', 'Success', this.getCurrentTime());
                ToastNotification.success('Success', 'Backup created successfully');

                // Refresh the list after creating
                setTimeout(() => {
                    this.listBackups();
                }, 1000);
            } else {
                this.updateBackupInfo('Create Backup', 'Failed', this.getCurrentTime());
                ToastNotification.error('Error', data.message || 'Failed to create backup');
            }
        } catch (error) {
            console.error('Backup create error:', error);
            this.updateBackupInfo('Create Backup', 'Error', this.getCurrentTime());
            ToastNotification.error('Error', `Failed to create backup: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Restore from latest backup
     */
    async restoreLatestBackup() {
        const fileType = this.elements.fileTypeSelect?.value;
        const year = this.elements.yearInput?.value;
        const month = this.elements.monthInput?.value;
        const btn = this.elements.restoreLatestBtn;

        if (!confirm(`Are you sure you want to restore the latest backup for ${fileType}? This will overwrite your current file.`)) {
            return;
        }

        this.setButtonLoading(btn, true);
        this.updateBackupInfo('Restore Latest', 'In Progress', this.getCurrentTime());

        try {
            // Backend expects form parameters, not JSON
            const data = await API.postForm('/utility/backups/restore-latest', {
                fileType,
                year,
                month
            });

            if (data.success) {
                this.updateBackupInfo('Restore Latest', 'Success', this.getCurrentTime());
                ToastNotification.success('Success', 'Latest backup restored successfully');
            } else {
                this.updateBackupInfo('Restore Latest', 'Failed', this.getCurrentTime());
                ToastNotification.error('Error', data.message || 'Failed to restore latest backup');
            }
        } catch (error) {
            console.error('Latest restore error:', error);
            this.updateBackupInfo('Restore Latest', 'Error', this.getCurrentTime());
            ToastNotification.error('Error', `Failed to restore latest backup: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Get backup diagnostics
     */
    async getBackupDiagnostics() {
        const fileType = this.elements.fileTypeSelect?.value;
        const year = this.elements.yearInput?.value;
        const month = this.elements.monthInput?.value;
        const btn = this.elements.diagnosticsBtn;

        this.setButtonLoading(btn, true);
        this.updateBackupInfo('Diagnostics', 'In Progress', this.getCurrentTime());

        const params = { fileType };
        if (year && month) {
            params.year = year;
            params.month = month;
        }

        try {
            const data = await API.get('/utility/backups/diagnostics', params);

            if (data.success) {
                this.displayBackupDiagnostics(data);
                this.updateBackupInfo('Diagnostics', 'Success', this.getCurrentTime());
                ToastNotification.info('Info', 'Backup diagnostics retrieved successfully');
            } else {
                this.updateBackupInfo('Diagnostics', 'Failed', this.getCurrentTime());
                ToastNotification.error('Error', data.message || 'Failed to get backup diagnostics');
            }
        } catch (error) {
            console.error('Diagnostics error:', error);
            this.updateBackupInfo('Diagnostics', 'Error', this.getCurrentTime());
            ToastNotification.error('Error', `Failed to get backup diagnostics: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Create memory backup
     */
    async createMemoryBackup() {
        const fileType = this.elements.fileTypeSelect?.value;
        const year = this.elements.yearInput?.value;
        const month = this.elements.monthInput?.value;
        const btn = this.elements.memoryBackupBtn;

        this.setButtonLoading(btn, true);
        this.updateBackupInfo('Memory Backup', 'In Progress', this.getCurrentTime());

        try {
            // Backend expects form parameters, not JSON
            const data = await API.postForm('/utility/backups/memory-backup', {
                fileType,
                year,
                month
            });

            if (data.success) {
                this.updateBackupInfo('Memory Backup', 'Success', this.getCurrentTime());
                ToastNotification.success('Success', `Memory backup created (${this.formatBytes(data.backupSize)})`);
            } else {
                this.updateBackupInfo('Memory Backup', 'Failed', this.getCurrentTime());
                ToastNotification.error('Error', data.message || 'Failed to create memory backup');
            }
        } catch (error) {
            console.error('Memory backup error:', error);
            this.updateBackupInfo('Memory Backup', 'Error', this.getCurrentTime());
            ToastNotification.error('Error', `Failed to create memory backup: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Cleanup simple backup
     */
    async cleanupBackup() {
        const fileType = this.elements.fileTypeSelect?.value;
        const year = this.elements.yearInput?.value;
        const month = this.elements.monthInput?.value;
        const btn = this.elements.cleanupBtn;

        if (!confirm('Are you sure you want to clean up simple backup files? This action cannot be undone.')) {
            return;
        }

        this.setButtonLoading(btn, true);
        this.updateBackupInfo('Cleanup', 'In Progress', this.getCurrentTime());

        try {
            // Backend expects query parameters for DELETE
            const params = new URLSearchParams({ fileType, year, month });
            const data = await API.delete(`/utility/backups/cleanup?${params}`);

            if (data.success) {
                this.updateBackupInfo('Cleanup', 'Success', this.getCurrentTime());
                ToastNotification.success('Success', 'Simple backup cleaned up successfully');

                // Refresh the list after cleanup
                setTimeout(() => {
                    this.listBackups();
                }, 1000);
            } else {
                this.updateBackupInfo('Cleanup', 'Failed', this.getCurrentTime());
                ToastNotification.error('Error', data.message || 'Failed to cleanup backup');
            }
        } catch (error) {
            console.error('Cleanup error:', error);
            this.updateBackupInfo('Cleanup', 'Error', this.getCurrentTime());
            ToastNotification.error('Error', `Failed to cleanup backup: ${error.message}`);
        } finally {
            this.setButtonLoading(btn, false);
        }
    }

    /**
     * Restore a specific backup
     * @param {string} backupPath - Path to the backup file
     * @param {string} fileType - Type of file
     */
    async restoreBackup(backupPath, fileType) {
        const year = this.elements.yearInput?.value;
        const month = this.elements.monthInput?.value;

        if (!confirm('Are you sure you want to restore this backup? This will overwrite your current file.')) {
            return;
        }

        this.updateBackupInfo('Restore Backup', 'In Progress', this.getCurrentTime());

        try {
            const data = await API.post('/utility/backups/restore', {
                backupPath,
                fileType,
                year,
                month
            });

            if (data.success) {
                this.updateBackupInfo('Restore Backup', 'Success', this.getCurrentTime());
                ToastNotification.success('Success', 'Backup restored successfully');
            } else {
                this.updateBackupInfo('Restore Backup', 'Failed', this.getCurrentTime());
                ToastNotification.error('Error', data.message || 'Failed to restore backup');
            }
        } catch (error) {
            console.error('Backup restore error:', error);
            this.updateBackupInfo('Restore Backup', 'Error', this.getCurrentTime());
            ToastNotification.error('Error', `Failed to restore backup: ${error.message}`);
        }
    }

    // ========================================================================
    // DISPLAY FUNCTIONS
    // ========================================================================

    /**
     * Display list of backups
     * @param {Array} backups - Array of backup objects
     * @param {string} fileType - File type being listed
     */
    displayBackupList(backups, fileType) {
        if (!this.elements.backupList) return;

        this.elements.backupList.innerHTML = '';

        if (backups.length === 0) {
            this.elements.backupList.innerHTML = `<div class="no-results">No backups found for ${fileType}</div>`;
            return;
        }

        backups.forEach(backup => {
            const backupItem = document.createElement('div');
            backupItem.className = 'backup-item';
            backupItem.innerHTML = `
                <div class="backup-info">
                    <div class="backup-name">${backup.displayPath || backup.path.split('/').pop()}</div>
                    <div class="backup-meta">
                        <span class="backup-date">${backup.formattedDate}</span>
                        ${backup.size ? `<span class="backup-size">${this.formatBytes(backup.size)}</span>` : ''}
                        ${backup.type ? `<span class="backup-type">${backup.type}</span>` : ''}
                        ${backup.criticalityLevel ? `<span class="backup-criticality">${backup.criticalityLevel}</span>` : ''}
                    </div>
                </div>
                <div class="backup-actions">
                    <button class="btn-restore" data-path="${backup.path}" data-type="${fileType}">
                        <i class="bi bi-arrow-clockwise"></i>
                        <span>Restore</span>
                    </button>
                </div>
            `;

            // Attach restore handler
            const restoreBtn = backupItem.querySelector('.btn-restore');
            restoreBtn?.addEventListener('click', () => {
                const path = restoreBtn.dataset.path;
                const type = restoreBtn.dataset.type;
                this.restoreBackup(path, type);
            });

            this.elements.backupList.appendChild(backupItem);
        });
    }

    /**
     * Display backup diagnostics
     * @param {Object} data - Diagnostics data
     */
    displayBackupDiagnostics(data) {
        if (!this.elements.backupList) return;

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

        this.elements.backupList.innerHTML = diagnosticsHtml;
        if (this.elements.resultsContainer) {
            this.elements.resultsContainer.style.display = 'block';
        }
    }

    /**
     * Clear results display
     */
    clearResults() {
        if (this.elements.resultsContainer) {
            this.elements.resultsContainer.style.display = 'none';
        }
        if (this.elements.backupList) {
            this.elements.backupList.innerHTML = '';
        }
        this.updateBackupInfo('List Backups', 'None', '--:--:--');
    }

    /**
     * Update backup operation info
     * @param {string} operation - Operation name
     * @param {string} result - Result status
     * @param {string} time - Time of operation
     */
    updateBackupInfo(operation, result, time) {
        if (this.elements.lastOperation) {
            this.elements.lastOperation.textContent = operation;
        }
        if (this.elements.operationTime) {
            this.elements.operationTime.textContent = time;
        }

        // Update result with color coding
        const statItems = document.querySelectorAll('.stat-item');
        for (const item of statItems) {
            const label = item.querySelector('.stat-label');
            if (label?.textContent === 'Last Operation') {
                const resultElement = item.querySelector('.stat-value');
                if (resultElement) {
                    resultElement.textContent = result;

                    // Color coding
                    resultElement.style.color = result === 'Success' ? '#28a745' :
                                                 result === 'Failed' || result === 'Error' ? '#dc3545' :
                                                 result === 'In Progress' ? '#007bff' : '#6c757d';
                }
                break;
            }
        }
    }

    // ========================================================================
    // PUBLIC API (for coordinator integration)
    // ========================================================================

    /**
     * Refresh status (for auto-refresh)
     */
    async refreshStatus() {
        const fileType = this.elements.fileTypeSelect?.value;
        if (!fileType) return;

        try {
            const data = await API.get('/utility/backups/list', { fileType });
            if (data.success && this.elements.backupCountDisplay) {
                this.elements.backupCountDisplay.textContent = data.totalFound;
                this.updateBackupInfo('Auto Refresh', 'Updated', this.getCurrentTime());
            }
        } catch (error) {
            console.error('Auto refresh error:', error);
        }
    }

    // ========================================================================
    // UTILITY FUNCTIONS
    // ========================================================================

    /**
     * Set button loading state
     * @param {HTMLElement} btn - Button element
     * @param {boolean} loading - Loading state
     */
    setButtonLoading(btn, loading) {
        if (!btn) return;

        if (loading) {
            btn.classList.add('loading');
            btn.disabled = true;
            const icon = btn.querySelector('i');
            if (icon) icon.classList.add('spin');
        } else {
            btn.classList.remove('loading');
            btn.disabled = false;
            const icon = btn.querySelector('i');
            if (icon) icon.classList.remove('spin');
        }
    }

    /**
     * Format bytes to human-readable string
     * @param {number} bytes - Bytes to format
     * @returns {string} Formatted string
     */
    formatBytes(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
    }

    /**
     * Get current time formatted
     * @returns {string} Current time
     */
    getCurrentTime() {
        return new Date().toLocaleTimeString('en-US', {
            hour12: false,
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    }
}

// Auto-initialize on DOM ready and expose globally for legacy compatibility
let instance = null;

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        instance = new BackupUtility();
        instance.initialize();
        window.BackupUtility = instance; // Legacy compatibility
    });
} else {
    instance = new BackupUtility();
    instance.initialize();
    window.BackupUtility = instance; // Legacy compatibility
}

export default BackupUtility;
