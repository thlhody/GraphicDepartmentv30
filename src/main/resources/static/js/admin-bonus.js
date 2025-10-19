document.addEventListener('DOMContentLoaded', function() {
    initializePage();
});

function initializePage() {
    // Set default values if not already set
    const yearSelect = document.getElementById('yearSelect');
    const monthSelect = document.getElementById('monthSelect');

    // Set current year if not selected
    if (!yearSelect.value) {
        const currentYear = new Date().getFullYear();
        yearSelect.value = currentYear;
    }

    // Set current month if not selected
    if (!monthSelect.value) {
        const currentMonth = new Date().getMonth() + 1; // JavaScript months are 0-based
        monthSelect.value = currentMonth;
    }

    // Add event listeners
    document.getElementById('loadData').addEventListener('click', loadBonusData);
    document.getElementById('exportExcel').addEventListener('click', exportToExcel);
    document.getElementById('exportUserExcel').addEventListener('click', exportUserToExcel);

    // Add sort listeners to all sortable headers
    document.querySelectorAll('.sortable').forEach(header => {
        header.addEventListener('click', () => sortTable(header.dataset.sort));
    });

    // Load initial data
    loadBonusData();
}

// Add global sort state
let currentSort = {
    column: 'name',
    direction: 'asc'
};

function displayBonusData(data) {
    const tbody = document.getElementById('bonusTableBody');
    const totalEntriesCount = document.getElementById('totalEntriesCount');
    tbody.innerHTML = '';

    console.log('Raw data received:', data); // Debug log

    if (!data || Object.keys(data).length === 0) {
        tbody.innerHTML = `
            <tr id="emptyRow">
                <td colspan="12" class="text-center py-5">
                    <div class="text-muted">
                        <i class="bi bi-inbox-fill fs-2 d-block mb-3"></i>
                        <h5>No Bonus Data Available</h5>
                        <p>Select a year and month, then click "Load Data" to view register bonuses.</p>
                        <p class="small">If no data exists for this period, the bonus file hasn't been created yet.</p>
                    </div>
                </td>
            </tr>
        `;
        if (totalEntriesCount) {
            totalEntriesCount.textContent = '0 users';
        }
        return;
    }

    const userCount = Object.keys(data).length;

    Object.entries(data).forEach(([userId, entry]) => {
        console.log(`Processing entry for user ${userId}:`, entry); // Debug log

        const entriesValue = Number(entry.entries || 0);
        let entriesLevel = 'low';
        if (entriesValue > 39) {
            entriesLevel = 'high';
        } else if (entriesValue > 14) {
            entriesLevel = 'medium';
        }

        const month1 = entry.previousMonths && entry.previousMonths.month1 ? entry.previousMonths.month1 : 0;
        const month2 = entry.previousMonths && entry.previousMonths.month2 ? entry.previousMonths.month2 : 0;
        const month3 = entry.previousMonths && entry.previousMonths.month3 ? entry.previousMonths.month3 : 0;

        const rowHtml = `
            <tr data-entries="${entriesLevel}">
                <td>${entry.displayName || entry.username || 'Unknown User'}</td>
                <td>${entriesValue}</td>
                <td>${formatNumber(entry.articleNumbers)}</td>
                <td>${formatNumber(entry.graphicComplexity)}</td>
                <td>${formatNumber(entry.misc)}</td>
                <td>${entry.workedDays || 0}</td>
                <td>${formatPercent(entry.workedPercentage)}</td>
                <td>${formatPercent(entry.bonusPercentage)}</td>
                <td>${formatCurrency(entry.bonusAmount)}</td>
                <td>${formatCurrency(month1)}</td>
                <td>${formatCurrency(month2)}</td>
                <td>${formatCurrency(month3)}</td>
            </tr>
        `;

        tbody.insertAdjacentHTML('beforeend', rowHtml);
    });

    // Update user count
    if (totalEntriesCount) {
        totalEntriesCount.textContent = `${userCount} user${userCount !== 1 ? 's' : ''}`;
    }

    sortTable('name');
}

async function loadBonusData() {
    const year = document.getElementById('yearSelect').value;
    const month = document.getElementById('monthSelect').value;
    const loadDataBtn = document.getElementById('loadData');

    // Show loading state
    if (loadDataBtn) {
        loadDataBtn.disabled = true;
        loadDataBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Loading...';
    }

    try {
        console.log(`Fetching data for year: ${year}, month: ${month}`);
        const response = await fetch(`/admin/bonus/data?year=${year}&month=${month}`);

        if (!response.ok) {
            if (response.status === 404) {
                displayBonusData({});
                showToastAlert('No bonus data found for this period', 'warning');
                return;
            }
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        // Update month headers from response headers
        const month1 = response.headers.get('X-Previous-Month-1');
        const month2 = response.headers.get('X-Previous-Month-2');
        const month3 = response.headers.get('X-Previous-Month-3');

        // Update the header cells
        document.getElementById('prev-month-1').textContent = month1;
        document.getElementById('prev-month-2').textContent = month2;
        document.getElementById('prev-month-3').textContent = month3;

        const data = await response.json();
        console.log('Response data:', data);
        displayBonusData(data);

        const userCount = Object.keys(data || {}).length;
        if (userCount > 0) {
            showToastAlert(`Loaded ${userCount} bonus entries`, 'success');
        }
    } catch (error) {
        console.error('Error loading bonus data:', error);
        showToastAlert(error.message || 'Failed to load bonus data', 'danger');
        displayBonusData({});
    } finally {
        // Reset button state
        if (loadDataBtn) {
            loadDataBtn.disabled = false;
            loadDataBtn.innerHTML = '<i class="bi bi-sync me-2"></i>Load Data';
        }
    }
}

// Update the sort function to preserve the inline styles
function sortTable(column) {
    const tbody = document.getElementById('bonusTableBody');
    const rows = Array.from(tbody.getElementsByTagName('tr'));

    // Update sort direction
    if (currentSort.column === column) {
        currentSort.direction = currentSort.direction === 'asc' ? 'desc' : 'asc';
    } else {
        currentSort.column = column;
        currentSort.direction = 'asc';
    }

    // Update header styles
    document.querySelectorAll('.sortable').forEach(header => {
        header.classList.remove('sort-asc', 'sort-desc');
        if (header.dataset.sort === column) {
            header.classList.add(currentSort.direction === 'asc' ? 'sort-asc' : 'sort-desc');
        }
    });

    // Sort rows
    rows.sort((a, b) => {
        let aVal = a.cells[getColumnIndex(column)].textContent.trim();
        let bVal = b.cells[getColumnIndex(column)].textContent.trim();

        // Remove currency symbols and commas for numeric comparison
        if (column === 'bonusAmount' || column.startsWith('previous')) {
            aVal = aVal.replace(/[^0-9.-]+/g, '');
            bVal = bVal.replace(/[^0-9.-]+/g, '');
        }

        // Remove % sign for percentage comparison
        if (column === 'workedPercentage' || column === 'bonusPercentage') {
            aVal = aVal.replace('%', '');
            bVal = bVal.replace('%', '');
        }

        // Convert to numbers for numeric columns
        if (!isNaN(aVal) && !isNaN(bVal)) {
            aVal = parseFloat(aVal);
            bVal = parseFloat(bVal);
        }

        const comparison = currentSort.direction === 'asc' ?
        (aVal < bVal ? -1 : 1) :
        (aVal > bVal ? -1 : 1);

        return comparison;
    });

    // Reorder the rows
    tbody.innerHTML = '';
    rows.forEach(row => tbody.appendChild(row));
}

// Add formatting helper functions
function formatNumber(value) {
    if (value === null || value === undefined) {
        return '0.00';
    }
    return value.toFixed(2);
}

function formatPercent(value) {
    if (value === null || value === undefined) {
        return '0.00%';
    }
    return value.toFixed(2) + '%';
}

function formatCurrency(value) {
    // Format as number with 2 decimals and add RON suffix
    const numValue = parseFloat(value || 0);
    return numValue.toFixed(2) + ' RON';
}

function getColumnIndex(column) {
    const columns = {
        'name': 0,
        'entries': 1,
        'articleNumbers': 2,
        'graphicComplexity': 3,
        'misc': 4,
        'workedDays': 5,
        'workedPercentage': 6,
        'bonusPercentage': 7,
        'bonusAmount': 8,
        'previousMonth1': 9,
        'previousMonth2': 10,
        'previousMonth3': 11
    };
    return columns[column] || 0;
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

async function exportUserToExcel() {
    const year = document.getElementById('yearSelect').value;
    const month = document.getElementById('monthSelect').value;
    const exportUserExcelBtn = document.getElementById('exportUserExcel');

    if (!year || !month) {
        showToastAlert('Please select year and month', 'warning');
        return;
    }

    // Show loading state
    if (exportUserExcelBtn) {
        exportUserExcelBtn.disabled = true;
        exportUserExcelBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Exporting...';
    }

    try {
        window.location.href = `/admin/bonus/export/user?year=${year}&month=${month}`;
        showToastAlert('User Excel exported successfully', 'success');
    } catch (error) {
        console.error('Error exporting user data:', error);
        showToastAlert(error.message || 'Failed to export user data', 'danger');
    } finally {
        // Reset button state after a delay
        setTimeout(() => {
            if (exportUserExcelBtn) {
                exportUserExcelBtn.disabled = false;
                exportUserExcelBtn.innerHTML = '<i class="bi bi-file-earmark-person me-2"></i>Export User Excel';
            }
        }, 2000);
    }
}

async function exportToExcel() {
    const year = document.getElementById('yearSelect').value;
    const month = document.getElementById('monthSelect').value;
    const exportExcelBtn = document.getElementById('exportExcel');

    if (!year || !month) {
        showToastAlert('Please select year and month', 'warning');
        return;
    }

    // Show loading state
    if (exportExcelBtn) {
        exportExcelBtn.disabled = true;
        exportExcelBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Exporting...';
    }

    try {
        window.location.href = `/admin/bonus/export?year=${year}&month=${month}`;
        showToastAlert('Admin Excel exported successfully', 'success');
    } catch (error) {
        console.error('Error exporting data:', error);
        showToastAlert(error.message || 'Failed to export data', 'danger');
    } finally {
        // Reset button state after a delay
        setTimeout(() => {
            if (exportExcelBtn) {
                exportExcelBtn.disabled = false;
                exportExcelBtn.innerHTML = '<i class="bi bi-file-earmark-excel me-2"></i>Export Admin Excel';
            }
        }, 2000);
    }
}