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
    tbody.innerHTML = '';

    console.log('Raw data received:', data); // Debug log

    if (!data || Object.keys(data).length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="12" class="text-center">No bonus data available</td>
            </tr>
        `;
        return;
    }

    Object.entries(data).forEach(([userId, entry]) => {
        console.log(`Processing entry for user ${userId}:`, entry); // Debug log

        const entriesValue = Number(entry.entries || 0);
        let entriesLevel = 'low';
        if (entriesValue > 39) {
            entriesLevel = 'high';
        } else if (entriesValue > 14) {
            entriesLevel = 'medium';
        }

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
                <td>${formatCurrency(entry.previousMonths?.month1 || 0)}</td>
                <td>${formatCurrency(entry.previousMonths?.month2 || 0)}</td>
                <td>${formatCurrency(entry.previousMonths?.month3 || 0)}</td>
            </tr>
        `;

        tbody.insertAdjacentHTML('beforeend', rowHtml);
    });

    sortTable('name');
}

async function loadBonusData() {
    const year = document.getElementById('yearSelect').value;
    const month = document.getElementById('monthSelect').value;

    try {
        console.log(`Fetching data for year: ${year}, month: ${month}`);
        const response = await fetch(`/admin/bonus/data?year=${year}&month=${month}`);

        if (!response.ok) {
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
    } catch (error) {
        console.error('Error loading bonus data:', error);
        showError(`Failed to load bonus data: ${error.message}`);
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
    return value?.toFixed(2) || '0.00';
}

function formatPercent(value) {
    return (value?.toFixed(2) || '0.00') + '%';
}

function formatCurrency(value) {
    return new Intl.NumberFormat('ro-RO', {
        style: 'currency',
        currency: 'RON'
    }).format(value || 0);
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

function showError(message) {
    // You can implement your preferred error display method here
    alert(message);
}

async function exportUserToExcel() {
    const year = document.getElementById('yearSelect').value;
    const month = document.getElementById('monthSelect').value;

    try {
        window.location.href = `/admin/bonus/export/user?year=${year}&month=${month}`;
    } catch (error) {
        console.error('Error exporting user data:', error);
        showError('Failed to export user data');
    }
}
// Add the export function
async function exportToExcel() {
    const year = document.getElementById('yearSelect').value;
    const month = document.getElementById('monthSelect').value;

    try {
        window.location.href = `/admin/bonus/export?year=${year}&month=${month}`;
    } catch (error) {
        console.error('Error exporting data:', error);
        showError('Failed to export data');
    }
}