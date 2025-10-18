/**
 * Check Bonus Dashboard JavaScript
 * Handles display and management of all team members' check bonuses
 */

(function() {
    'use strict';

    // CSRF Token handling
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;

    // DOM Elements
    let yearSelect, monthSelect, loadDataBtn, exportExcelBtn;
    let checkBonusTableBody, totalEntriesCount, emptyRow;

    // Current data
    let currentBonusData = [];
    let currentSortColumn = null;
    let currentSortDirection = 'asc';

    /**
     * Initialize the dashboard
     */
    function init() {
        // Get DOM elements
        yearSelect = document.getElementById('yearSelect');
        monthSelect = document.getElementById('monthSelect');
        loadDataBtn = document.getElementById('loadData');
        exportExcelBtn = document.getElementById('exportExcel');
        checkBonusTableBody = document.getElementById('checkBonusTableBody');
        totalEntriesCount = document.getElementById('totalEntriesCount');
        emptyRow = document.getElementById('emptyRow');

        // Check if elements exist
        if (!yearSelect || !monthSelect || !loadDataBtn) {
            console.error('Required elements not found');
            return;
        }

        // Attach event listeners
        loadDataBtn.addEventListener('click', handleLoadData);
        exportExcelBtn?.addEventListener('click', handleExportExcel);

        // Setup sortable columns
        setupSortableColumns();

        console.log('Check bonus dashboard initialized');

        // Don't auto-load data - wait for user to click Load Data button
    }

    /**
     * Setup sortable column headers
     */
    function setupSortableColumns() {
        const sortableHeaders = document.querySelectorAll('.sortable');
        sortableHeaders.forEach(header => {
            header.addEventListener('click', function() {
                const column = this.dataset.sort;
                handleSort(column);
            });
        });
    }

    /**
     * Handle load data button click
     */
    async function handleLoadData() {
        const year = yearSelect.value;
        const month = monthSelect.value;

        if (!year || !month) {
            showToastAlert('Please select year and month', 'warning');
            return;
        }

        // Show loading state
        loadDataBtn.disabled = true;
        loadDataBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Loading...';

        try {
            // Make API call to load bonus data
            const response = await fetch(`/team/check-register/load-bonus?year=${year}&month=${month}`, {
                method: 'GET',
                headers: {
                    [header]: token
                }
            });

            if (!response.ok) {
                if (response.status === 404) {
                    // No bonus file found - show empty state
                    currentBonusData = [];
                    displayBonusData([]);
                    showToastAlert('No bonus data found for this period', 'warning');
                    return;
                }
                throw new Error('Failed to load bonus data');
            }

            const bonusData = await response.json();

            // Handle null or empty data gracefully
            if (!bonusData || !Array.isArray(bonusData) || bonusData.length === 0) {
                currentBonusData = [];
                displayBonusData([]);
                showToastAlert('No bonus entries found for this period', 'warning');
                return;
            }

            // Store and display data
            currentBonusData = bonusData;
            displayBonusData(bonusData);
            showToastAlert(`Loaded ${bonusData.length} bonus entries`, 'success');

        } catch (error) {
            console.error('Error loading bonus data:', error);
            showToastAlert(error.message || 'Failed to load bonus data', 'danger');
            currentBonusData = [];
            displayBonusData([]);
        } finally {
            // Reset button state
            loadDataBtn.disabled = false;
            loadDataBtn.innerHTML = '<i class="bi bi-sync me-2"></i>Load Data';
        }
    }

    /**
     * Display bonus data in table
     */
    function displayBonusData(bonusData) {
        // Clear table body except empty row
        checkBonusTableBody.innerHTML = '';

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
            checkBonusTableBody.appendChild(emptyRow);

            // Update count
            if (totalEntriesCount) {
                totalEntriesCount.textContent = '0 users';
            }
            return;
        }

        // Add rows for each bonus entry
        bonusData.forEach(entry => {
            const row = createBonusRow(entry);
            checkBonusTableBody.appendChild(row);
        });

        // Update count
        if (totalEntriesCount) {
            totalEntriesCount.textContent = `${bonusData.length} user${bonusData.length !== 1 ? 's' : ''}`;
        }
    }

    /**
     * Create table row for bonus entry
     */
    function createBonusRow(entry) {
        const row = document.createElement('tr');

        // Null-safe value extraction
        const name = entry.name || entry.username || 'Unknown';
        const totalWUM = entry.totalWUM || 0;
        const workingHours = entry.workingHours || 0;
        const targetWUHR = entry.targetWUHR || 0;
        const totalWUHRM = entry.totalWUHRM || 0;
        const efficiencyPercent = entry.efficiencyPercent || 0;
        const bonusAmount = entry.bonusAmount || 0;

        // Determine efficiency badge class
        let efficiencyClass = 'efficiency-low';
        if (efficiencyPercent >= 100) {
            efficiencyClass = 'efficiency-high';
        } else if (efficiencyPercent >= 70) {
            efficiencyClass = 'efficiency-medium';
        }

        // Determine bonus amount class
        let bonusClass = 'bonus-amount-low';
        if (bonusAmount >= 1200) {
            bonusClass = 'bonus-amount-high';
        } else if (bonusAmount >= 800) {
            bonusClass = 'bonus-amount-medium';
        }

        row.innerHTML = `
            <td>${escapeHtml(name)}</td>
            <td class="text-center">${formatNumber(totalWUM)}</td>
            <td class="text-center">${formatNumber(workingHours)}</td>
            <td class="text-center">${formatNumber(targetWUHR)}</td>
            <td class="text-center">${formatNumber(totalWUHRM)}</td>
            <td class="text-center">
                <span class="efficiency-badge ${efficiencyClass}">${efficiencyPercent}%</span>
            </td>
            <td class="text-center ${bonusClass}">${formatNumber(bonusAmount)}</td>
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

    /**
     * Handle table column sorting
     */
    function handleSort(column) {
        // Toggle sort direction if same column
        if (currentSortColumn === column) {
            currentSortDirection = currentSortDirection === 'asc' ? 'desc' : 'asc';
        } else {
            currentSortColumn = column;
            currentSortDirection = 'asc';
        }

        // Sort data
        const sortedData = [...currentBonusData].sort((a, b) => {
            let aVal = a[column];
            let bVal = b[column];

            // Handle nulls
            if (aVal === null || aVal === undefined) aVal = 0;
            if (bVal === null || bVal === undefined) bVal = 0;

            // String comparison for name
            if (column === 'name' || column === 'username') {
                aVal = String(aVal).toLowerCase();
                bVal = String(bVal).toLowerCase();
                return currentSortDirection === 'asc' ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal);
            }

            // Numeric comparison
            return currentSortDirection === 'asc' ? aVal - bVal : bVal - aVal;
        });

        // Update display
        displayBonusData(sortedData);

        // Update sort icons
        updateSortIcons(column);
    }

    /**
     * Update sort icons on headers
     */
    function updateSortIcons(activeColumn) {
        const sortableHeaders = document.querySelectorAll('.sortable');
        sortableHeaders.forEach(header => {
            header.classList.remove('asc', 'desc');
            if (header.dataset.sort === activeColumn) {
                header.classList.add(currentSortDirection);
            }
        });
    }

    /**
     * Handle export to Excel
     */
    async function handleExportExcel() {
        const year = yearSelect.value;
        const month = monthSelect.value;

        if (!year || !month) {
            showToastAlert('Please select year and month', 'warning');
            return;
        }

        if (!currentBonusData || currentBonusData.length === 0) {
            showToastAlert('No data to export', 'warning');
            return;
        }

        // Show loading state
        exportExcelBtn.disabled = true;
        exportExcelBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Exporting...';

        try {
            // Make API call to export
            const response = await fetch(`/team/check-register/export-bonus?year=${year}&month=${month}`, {
                method: 'GET',
                headers: {
                    [header]: token
                }
            });

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

            showToastAlert('Bonus data exported successfully', 'success');

        } catch (error) {
            console.error('Error exporting bonus data:', error);
            showToastAlert(error.message || 'Failed to export bonus data', 'danger');
        } finally {
            // Reset button state
            exportExcelBtn.disabled = false;
            exportExcelBtn.innerHTML = '<i class="bi bi-file-earmark-excel me-2"></i>Export Excel';
        }
    }

    /**
     * Format number with 2 decimal places
     */
    function formatNumber(value) {
        if (value === null || value === undefined || isNaN(value)) {
            return '-';
        }
        return parseFloat(value).toFixed(2);
    }

    /**
     * Escape HTML to prevent XSS
     */
    function escapeHtml(text) {
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

    /**
     * Show toast alert
     */
    function showToastAlert(message, type) {
        // Use existing toast alert system if available
        if (typeof window.showToast === 'function') {
            // Map type to title
            const titleMap = {
                'success': 'Success',
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

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();