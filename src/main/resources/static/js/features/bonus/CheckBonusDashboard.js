/**
 * CheckBonusDashboard.js
 *
 * Handles display and management of all team members' check bonuses.
 * Used by team leaders to view, sort, and export bonus data for their team.
 *
 * @module features/bonus/CheckBonusDashboard
 */

import { getCSRFToken, getCSRFHeader } from '../../core/api.js';

/**
 * CheckBonusDashboard class
 * Manages team bonus dashboard interface
 */
export class CheckBonusDashboard {
    constructor() {
        // Current data state
        this.currentBonusData = [];
        this.currentSortColumn = null;
        this.currentSortDirection = 'asc';

        // DOM element references
        this.elements = {};
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize check bonus dashboard
     */
    initialize() {
        console.log('Initializing Check Bonus Dashboard...');

        this.cacheElements();

        if (!this.validateElements()) {
            console.error('Required elements not found');
            return;
        }

        this.setupEventListeners();
        this.setupSortableColumns();

        console.log('✅ Check Bonus Dashboard initialized');
    }

    /**
     * Cache DOM element references
     */
    cacheElements() {
        this.elements = {
            yearSelect: document.getElementById('yearSelect'),
            monthSelect: document.getElementById('monthSelect'),
            loadDataBtn: document.getElementById('loadData'),
            exportExcelBtn: document.getElementById('exportExcel'),
            exportUserExcelBtn: document.getElementById('exportUserExcel'),
            checkBonusTableBody: document.getElementById('checkBonusTableBody'),
            totalEntriesCount: document.getElementById('totalEntriesCount')
        };
    }

    /**
     * Validate required elements exist
     */
    validateElements() {
        return this.elements.yearSelect &&
               this.elements.monthSelect &&
               this.elements.loadDataBtn;
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        this.elements.loadDataBtn.addEventListener('click', () => this.handleLoadData());

        if (this.elements.exportExcelBtn) {
            this.elements.exportExcelBtn.addEventListener('click', () => this.handleExportExcel());
        }

        if (this.elements.exportUserExcelBtn) {
            this.elements.exportUserExcelBtn.addEventListener('click', () => this.handleExportUserExcel());
        }
    }

    /**
     * Setup sortable column headers
     */
    setupSortableColumns() {
        const sortableHeaders = document.querySelectorAll('.sortable');
        sortableHeaders.forEach(header => {
            header.addEventListener('click', () => {
                const column = header.dataset.sort;
                this.handleSort(column);
            });
        });
    }

    // ========================================================================
    // DATA LOADING
    // ========================================================================

    /**
     * Handle load data button click
     */
    async handleLoadData() {
        const year = this.elements.yearSelect.value;
        const month = this.elements.monthSelect.value;

        if (!year || !month) {
            this.showToast('Please select year and month', 'warning');
            return;
        }

        // Show loading state
        this.setButtonLoading(this.elements.loadDataBtn, true);

        try {
            // Make API call to load bonus data
            const response = await fetch(
                `/team/check-register/load-bonus?year=${year}&month=${month}`,
                {
                    method: 'GET',
                    headers: {
                        [getCSRFHeader()]: getCSRFToken()
                    }
                }
            );

            if (!response.ok) {
                if (response.status === 404) {
                    // No bonus file found - show empty state
                    this.currentBonusData = [];
                    this.displayBonusData([]);
                    this.showToast('No bonus data found for this period', 'warning');
                    return;
                }
                throw new Error('Failed to load bonus data');
            }

            const bonusData = await response.json();

            // Handle null or empty data gracefully
            if (!bonusData || !Array.isArray(bonusData) || bonusData.length === 0) {
                this.currentBonusData = [];
                this.displayBonusData([]);
                this.showToast('No bonus entries found for this period', 'warning');
                return;
            }

            // Store and display data
            this.currentBonusData = bonusData;
            this.displayBonusData(bonusData);
            this.showToast(`Loaded ${bonusData.length} bonus entries`, 'success');

        } catch (error) {
            console.error('Error loading bonus data:', error);
            this.showToast(error.message || 'Failed to load bonus data', 'error');
            this.currentBonusData = [];
            this.displayBonusData([]);
        } finally {
            // Reset button state
            this.setButtonLoading(this.elements.loadDataBtn, false);
        }
    }

    // ========================================================================
    // DATA DISPLAY
    // ========================================================================

    /**
     * Display bonus data in table
     */
    displayBonusData(bonusData) {
        const tbody = this.elements.checkBonusTableBody;

        // Clear table body
        tbody.innerHTML = '';

        // Handle empty or null data
        if (!bonusData || bonusData.length === 0) {
            // Show empty row
            const emptyRow = document.createElement('tr');
            emptyRow.id = 'emptyRow';
            emptyRow.innerHTML = `
                <td colspan="7" class="text-center py-5">
                    <div class="text-muted">
                        <i class="bi bi-inbox-fill fs-2 d-block mb-3"></i>
                        <h5>No Bonus Data Available</h5>
                        <p>Select a year and month, then click "Load Data" to view team check bonuses.</p>
                        <p class="small">If no data exists for this period, the bonus file hasn't been created yet.</p>
                    </div>
                </td>
            `;
            tbody.appendChild(emptyRow);

            // Update count
            if (this.elements.totalEntriesCount) {
                this.elements.totalEntriesCount.textContent = '0 users';
            }
            return;
        }

        // Add rows for each bonus entry
        bonusData.forEach(entry => {
            const row = this.createBonusRow(entry);
            tbody.appendChild(row);
        });

        // Update count
        if (this.elements.totalEntriesCount) {
            this.elements.totalEntriesCount.textContent =
                `${bonusData.length} user${bonusData.length !== 1 ? 's' : ''}`;
        }
    }

    /**
     * Create table row for bonus entry
     */
    createBonusRow(entry) {
        const row = document.createElement('tr');

        // Null-safe value extraction
        const name = entry.name || entry.username || 'Unknown';
        const totalWUM = entry.totalWUM || 0;
        const workingHours = entry.workingHours || 0;
        const targetWUHR = entry.targetWUHR || 0;
        const totalWUHRM = entry.totalWUHRM || 0;
        const efficiencyPercent = entry.efficiencyPercent || 0;
        const bonusAmount = entry.bonusAmount || 0;

        // Determine efficiency level for row coloring
        let efficiencyLevel = 'low';
        if (efficiencyPercent >= 100) {
            efficiencyLevel = 'high';
        } else if (efficiencyPercent >= 70) {
            efficiencyLevel = 'medium';
        }

        // Determine efficiency badge class
        let efficiencyClass = 'efficiency-low';
        if (efficiencyPercent >= 100) {
            efficiencyClass = 'efficiency-high';
        } else if (efficiencyPercent >= 70) {
            efficiencyClass = 'efficiency-medium';
        }

        // Set row efficiency data attribute for CSS styling
        row.setAttribute('data-efficiency', efficiencyLevel);

        row.innerHTML = `
            <td>${this.escapeHtml(name)}</td>
            <td class="text-center">${this.formatNumber(totalWUM)}</td>
            <td class="text-center">${this.formatNumber(workingHours)}</td>
            <td class="text-center">${this.formatNumber(targetWUHR)}</td>
            <td class="text-center">${this.formatNumber(totalWUHRM)}</td>
            <td class="text-center">
                <span class="efficiency-badge ${efficiencyClass}">${efficiencyPercent}%</span>
            </td>
            <td class="text-center">${this.formatCurrency(bonusAmount)}</td>
        `;

        // Store data for sorting
        row.dataset.name = name;
        row.dataset.totalWUM = totalWUM;
        row.dataset.workingHours = workingHours;
        row.dataset.targetWUHR = targetWUHR;
        row.dataset.totalWUHRM = totalWUHRM;
        row.dataset.efficiencyPercent = efficiencyPercent;
        row.dataset.bonusAmount = bonusAmount;

        return row;
    }

    // ========================================================================
    // SORTING
    // ========================================================================

    /**
     * Handle table column sorting
     */
    handleSort(column) {
        // Toggle sort direction if same column
        if (this.currentSortColumn === column) {
            this.currentSortDirection = this.currentSortDirection === 'asc' ? 'desc' : 'asc';
        } else {
            this.currentSortColumn = column;
            this.currentSortDirection = 'asc';
        }

        // Sort data
        const sortedData = [...this.currentBonusData].sort((a, b) => {
            let aVal = a[column];
            let bVal = b[column];

            // Handle nulls
            if (aVal === null || aVal === undefined) aVal = 0;
            if (bVal === null || bVal === undefined) bVal = 0;

            // String comparison for name
            if (column === 'name' || column === 'username') {
                aVal = String(aVal).toLowerCase();
                bVal = String(bVal).toLowerCase();
                return this.currentSortDirection === 'asc' ?
                    aVal.localeCompare(bVal) : bVal.localeCompare(aVal);
            }

            // Numeric comparison
            return this.currentSortDirection === 'asc' ? aVal - bVal : bVal - aVal;
        });

        // Update display
        this.displayBonusData(sortedData);

        // Update sort icons
        this.updateSortIcons(column);
    }

    /**
     * Update sort icons on headers
     */
    updateSortIcons(activeColumn) {
        const sortableHeaders = document.querySelectorAll('.sortable');
        sortableHeaders.forEach(header => {
            header.classList.remove('asc', 'desc');
            if (header.dataset.sort === activeColumn) {
                header.classList.add(this.currentSortDirection);
            }
        });
    }

    // ========================================================================
    // EXPORT
    // ========================================================================

    /**
     * Handle export to Excel (Admin version with bonus amounts)
     */
    async handleExportExcel() {
        const year = this.elements.yearSelect.value;
        const month = this.elements.monthSelect.value;

        if (!year || !month) {
            this.showToast('Please select year and month', 'warning');
            return;
        }

        if (!this.currentBonusData || this.currentBonusData.length === 0) {
            this.showToast('No data to export', 'warning');
            return;
        }

        // Show loading state
        this.setButtonLoading(this.elements.exportExcelBtn, true, 'Exporting...');

        try {
            // Make API call to export
            const response = await fetch(
                `/team/check-register/export-bonus?year=${year}&month=${month}`,
                {
                    method: 'GET',
                    headers: {
                        [getCSRFHeader()]: getCSRFToken()
                    }
                }
            );

            if (!response.ok) {
                throw new Error('Failed to export bonus data');
            }

            // Download file
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `check_bonus_${year}_${month}.xlsx`;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);

            this.showToast('Admin bonus data exported successfully', 'success');

        } catch (error) {
            console.error('Error exporting bonus data:', error);
            this.showToast(error.message || 'Failed to export bonus data', 'error');
        } finally {
            // Reset button state
            this.setButtonLoading(this.elements.exportExcelBtn, false,
                '<i class="bi bi-file-earmark-excel me-2"></i>Export Admin');
        }
    }

    /**
     * Handle export to Excel for users (without bonus amounts)
     */
    async handleExportUserExcel() {
        const year = this.elements.yearSelect.value;
        const month = this.elements.monthSelect.value;

        if (!year || !month) {
            this.showToast('Please select year and month', 'warning');
            return;
        }

        if (!this.currentBonusData || this.currentBonusData.length === 0) {
            this.showToast('No data to export', 'warning');
            return;
        }

        // Show loading state
        this.setButtonLoading(this.elements.exportUserExcelBtn, true, 'Exporting...');

        try {
            // Make API call to export user version
            const response = await fetch(
                `/team/check-register/export-bonus-user?year=${year}&month=${month}`,
                {
                    method: 'GET',
                    headers: {
                        [getCSRFHeader()]: getCSRFToken()
                    }
                }
            );

            if (!response.ok) {
                throw new Error('Failed to export performance data');
            }

            // Download file
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `check_performance_${year}_${month}.xlsx`;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);

            this.showToast('Performance data exported successfully', 'success');

        } catch (error) {
            console.error('Error exporting performance data:', error);
            this.showToast(error.message || 'Failed to export performance data', 'error');
        } finally {
            // Reset button state
            this.setButtonLoading(this.elements.exportUserExcelBtn, false,
                '<i class="bi bi-file-earmark-person me-2"></i>Export User');
        }
    }

    // ========================================================================
    // FORMATTING HELPERS
    // ========================================================================

    /**
     * Format number with 2 decimal places
     */
    formatNumber(value) {
        if (value === null || value === undefined || isNaN(value)) {
            return '-';
        }
        return parseFloat(value).toFixed(2);
    }

    /**
     * Format currency with RON suffix
     */
    formatCurrency(value) {
        if (value === null || value === undefined || isNaN(value)) {
            return '0.00 RON';
        }
        const numValue = parseFloat(value);
        return numValue.toFixed(2) + ' RON';
    }

    /**
     * Escape HTML to prevent XSS
     */
    escapeHtml(text) {
        if (!text) return '';
        const map = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        };
        return text.replace(/[&<>"']/g, m => map[m]);
    }

    // ========================================================================
    // UI HELPERS
    // ========================================================================

    /**
     * Set button loading state
     */
    setButtonLoading(button, loading, text = null) {
        if (loading) {
            button.disabled = true;
            button.innerHTML = text ||
                '<span class="spinner-border spinner-border-sm me-2"></span>Loading...';
        } else {
            button.disabled = false;
            button.innerHTML = text || '<i class="bi bi-sync me-2"></i>Load Data';
        }
    }

    /**
     * Show toast notification
     */
    showToast(message, type) {
        // Use existing toast system if available
        if (typeof window.showToast === 'function') {
            // Map type to title
            const titleMap = {
                'success': 'Success',
                'error': 'Error',
                'danger': 'Error',
                'warning': 'Warning',
                'info': 'Info'
            };
            const title = titleMap[type] || 'Notification';
            window.showToast(title, message, type === 'danger' ? 'error' : type);
        } else {
            // Fallback to console
            console.log(`[${type.toUpperCase()}] ${message}`);
        }
    }
}
