
        // Initialize state management
        const state = {
            bonusCalculationData: {
                totalEntries: 0,
                averageArticleNumbers: 0,
                averageGraphicComplexity: 0,
                workedDays: /*[[${workedDays}]]*/ 0
            },
            currentUser: /*[[${selectedUser}]]*/ null,
            currentYear: /*[[${currentYear}]]*/ null,
            currentMonth: /*[[${currentMonth}]]*/ null,
            entries: /*[[${entries}]]*/ [],
            filteredEntries: []
        };

        // Initialize the page
        // Initialize the page
        document.addEventListener('DOMContentLoaded', function() {
            console.log("Initializing Admin Register Management");

            // Initialize state
            state.filteredEntries = Array.isArray(state.entries) ? [...state.entries] : [];

            try {
                initializeFormHandling();
                initializeControllers();
                initializeEventListeners();

                if (state.currentUser?.userId) {
                    console.log("Loading register summary for user:", state.currentUser);
                    loadRegisterSummary();
                }
            } catch (error) {
                console.error("Initialization error:", error);
                showError("Failed to initialize page. Please refresh and try again.");
            }
            $('.select2').select2({
                theme: 'bootstrap-5',
                width: '100%',
                placeholder: 'Select types...',
                allowClear: true
            });
        });

        // Add separate event listener for export button
        document.getElementById('exportExcel')?.addEventListener('click', function() {
            const userId = document.getElementById('userSelect').value;
            const year = document.getElementById('yearSelect').value;
            const month = document.getElementById('monthSelect').value;

            if (!userId) {
                showError("Please select a user");
                return;
            }

            // Create the export URL with query parameters
            const exportUrl = `/admin/register/export?userId=${userId}&year=${year}&month=${month}`;

            // Trigger the download
            window.location.href = exportUrl;
        });

        // Form handling
        function initializeFormHandling() {
            const form = document.querySelector('.card.shadow-sm.mb-4 .card-body');
            if (!form) return;

            const loadDataBtn = form.querySelector('button[type="submit"]');
            if (loadDataBtn) {
                loadDataBtn.addEventListener('click', function(e) {
                    e.preventDefault();
                    const userId = document.getElementById('userSelect')?.value;
                    const year = document.getElementById('yearSelect')?.value;
                    const month = document.getElementById('monthSelect')?.value;

                    if (!userId) {
                        showError("Please select a user");
                        return;
                    }

                    window.location.href = `/admin/register?userId=${userId}&year=${year}&month=${month}`;
                });
            }
        }

        // UI Controllers
        function initializeControllers() {
            // Initialize select all checkbox
            const selectAll = document.getElementById('selectAll');
            if (selectAll) {
                selectAll.addEventListener('change', function() {
                    document.querySelectorAll('.entry-select').forEach(checkbox => {
                        checkbox.checked = this.checked;
                    });
                });
            }
        }

        // Event Listeners
        function initializeEventListeners() {
            const listeners = {
                'filterBtn': applyFilters,
                'searchBtn': performSearch,
                'bulkUpdateBtn': performBulkUpdate,
                'calculateBonusBtn': calculateBonus,
                'saveChanges': saveChanges,
                'clearTable': clearTable
            };

            Object.entries(listeners).forEach(([id, handler]) => {
                document.getElementById(id)?.addEventListener('click', handler);
            });
        }

        // API Interactions
        async function loadRegisterSummary() {
            if (!validateUserContext()) return;

            try {
                // Load register summary
                const summaryResponse = await fetch(
                    `/admin/register/summary?username=${encodeURIComponent(state.currentUser.username)}` +
                    `&userId=${state.currentUser.userId}&year=${state.currentYear}&month=${state.currentMonth}`
                );

                if (!summaryResponse.ok) throw new Error(`HTTP error! status: ${summaryResponse.status}`);

                const summary = await summaryResponse.json();

                // Load worked days
                const workedDaysResponse = await fetch(
                    `/admin/register/worked-days?userId=${state.currentUser.userId}` +
                    `&year=${state.currentYear}&month=${state.currentMonth}`
                );

                if (!workedDaysResponse.ok) throw new Error('Failed to load worked days');

                const workedDays = await workedDaysResponse.json();

                // Update state with both summary and worked days
                state.bonusCalculationData = {
                    ...summary,
                    workedDays: workedDays
                };

                updateSummaryDisplay();

            } catch (error) {
                console.error('Summary loading error:', error);
                showError('Failed to load register summary');
            }
        }

        // Bulk Update Operations
        async function performBulkUpdate() {
            const selectedCheckboxes = document.querySelectorAll('.entry-select:checked');
            if (selectedCheckboxes.length === 0) {
                showError('Please select entries to update');
                return;
            }

            const updateValue = parseFloat(document.getElementById('bulkUpdateValue').value);
            if (isNaN(updateValue) || updateValue < 0) {
                showError('Please enter a valid number (0.0 format)');
                return;
            }

            const formattedValue = updateValue.toFixed(1);

            try {
                const selectedIds = Array.from(selectedCheckboxes).map(checkbox =>
                parseInt(checkbox.value)
                );

                // Convert date from dd/MM/yyyy to yyyy-MM-dd format
                const selectedEntries = Array.from(selectedCheckboxes).map(checkbox => {
                    const row = checkbox.closest('tr');
                    const dateText = row.querySelector('td:nth-child(2)').textContent; // "dd/MM/yyyy"
                    const [day, month, year] = dateText.split('/');
                    const isoDate = `${year}-${month}-${day}`; // Convert to "yyyy-MM-dd"

                    return {
                        entryId: parseInt(checkbox.value),
                        userId: state.currentUser?.userId,
                        date: isoDate,
                        orderId: row.querySelector('td:nth-child(3)').textContent,
                        productionId: row.querySelector('td:nth-child(4)').textContent,
                        omsId: row.querySelector('td:nth-child(5)').textContent,
                        clientName: row.querySelector('td:nth-child(6)').textContent,
                        actionType: row.querySelector('td:nth-child(7)').textContent,
                        printPrepTypes: row.querySelector('td:nth-child(8)').textContent,
                        colorsProfile: row.querySelector('td:nth-child(9)').textContent,
                        articleNumbers: parseInt(row.querySelector('td:nth-child(10)').textContent),
                        graphicComplexity: parseFloat(row.querySelector('td:nth-child(11)').textContent),
                        observations: row.querySelector('td:nth-child(12)').textContent,
                        adminSync: 'ADMIN_EDITED'
                    };
                });

                const response = await fetch('/admin/register/update', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        entries: selectedEntries,
                        selectedIds: selectedIds,
                        fieldName: 'graphicComplexity',
                        newValue: formattedValue
                    })
                });

                if (!response.ok) throw new Error('Update failed');

                // Update the UI
                selectedCheckboxes.forEach(checkbox => {
                    const row = checkbox.closest('tr');
                    const complexityCell = row.querySelector('td:nth-child(11)');
                    complexityCell.textContent = formattedValue;
                });

                // Show success message
                const alertsContainer = document.querySelector('.alerts');
                if (alertsContainer) {
                    alertsContainer.innerHTML = `
                <div class="alert alert-success">
                    Successfully updated ${selectedIds.length} entries
                </div>
            `;
                }

            } catch (error) {
                console.error('Update error:', error);
                showError(`Failed to update entries: ${error.message}`);
            }
        }

        // Bonus Calculations
        async function calculateBonus() {
            const config = getBonusConfig();
            if (!validateBonusConfig(config)) return;

            const workedDays = parseInt(document.getElementById('workedDays').value) || 0;
            if (workedDays <= 0) {
                showWarning('No worked days recorded for this period. The bonus calculation will show raw totals but bonus amounts will be zero.');
            }

            try {
                // Get visible entries from the table
                const visibleEntries = Array.from(document.querySelectorAll('#registerTable tbody tr'))
                    .filter(row => row.style.display !== 'none')
                    .map(row => {
                    // Convert date from dd/MM/yyyy to yyyy-MM-dd format
                    const dateText = row.querySelector('td:nth-child(2)').textContent;
                    const [day, month, year] = dateText.split('/');
                    const isoDate = `${year}-${month}-${day}`;
                    const printPrepTypesText = row.querySelector('td:nth-child(8)').textContent;

                    return {
                        entryId: parseInt(row.querySelector('.entry-select').value),
                        userId: state.currentUser.userId,
                        date: isoDate,
                        orderId: row.querySelector('td:nth-child(3)').textContent,
                        productionId: row.querySelector('td:nth-child(4)').textContent,
                        omsId: row.querySelector('td:nth-child(5)').textContent,
                        clientName: row.querySelector('td:nth-child(6)').textContent,
                        actionType: row.querySelector('td:nth-child(7)').textContent,
                        printPrepTypes: printPrepTypesText.split(',').map(t => t.trim()), // Convert to array
                        colorsProfile: row.querySelector('td:nth-child(9)').textContent,
                        articleNumbers: parseInt(row.querySelector('td:nth-child(10)').textContent),
                        graphicComplexity: parseFloat(row.querySelector('td:nth-child(11)').textContent),
                        observations: row.querySelector('td:nth-child(12)').textContent || "",
                        adminSync: row.querySelector('td:nth-child(13)').textContent
                    };
                });

                const response = await fetch('/admin/register/calculate-bonus', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        entries: visibleEntries,
                        userId: state.currentUser?.userId,
                        year: state.currentYear,
                        month: state.currentMonth,
                        bonusConfig: config
                    })
                });

                if (!response.ok) {
                    const errorData = await response.text();
                    throw new Error(`Bonus calculation failed: ${errorData}`);
                }

                const result = await response.json();
                displayBonusResults(result);
            } catch (error) {
                console.error('Bonus calculation error:', error);

                // More specific error messaging
                if (error.message.includes('Worked days must be greater than 0')) {
                    showError('No worked days recorded for this period. Please ensure worked days are properly set.');
                } else {
                    showError(`Failed to calculate bonus: ${error.message}`);
                }
            }
        }

        // Utility Functions
        //updateSummaryDisplay to use state
        function updateSummaryDisplay() {
            try {
                const totalEntries = document.getElementById('totalEntries');
                const averageArticles = document.getElementById('averageArticles');
                const averageComplexity = document.getElementById('averageComplexity');
                const workedDays = document.getElementById('workedDays');

                if (totalEntries) totalEntries.value = state.bonusCalculationData.totalEntries || 0;
                if (averageArticles) averageArticles.value = (state.bonusCalculationData.averageArticleNumbers || 0).toFixed(2);
                if (averageComplexity) averageComplexity.value = (state.bonusCalculationData.averageGraphicComplexity || 0).toFixed(2);
                if (workedDays) workedDays.value = state.bonusCalculationData.workedDays || 0;
            } catch (error) {
                console.error("Error updating summary display:", error);
                showError("Failed to update summary display");
            }
        }

        //updateSummaryFromEntries to use state
        function updateSummaryFromEntries(entries) {
            const workedDays = state.bonusCalculationData.workedDays; // Preserve worked days

            state.bonusCalculationData = {
                totalEntries: entries.length,
                averageArticleNumbers: entries.reduce((sum, entry) => sum + entry.articleNumbers, 0) / entries.length || 0,
                averageGraphicComplexity: entries.reduce((sum, entry) => sum + entry.graphicComplexity, 0) / entries.length || 0,
                workedDays: workedDays // Keep the worked days value
            };

            updateSummaryDisplay();
        }

        //applyFilters to use state
        function applyFilters() {
            console.log("Applying filters");

            const actionType = document.getElementById('actionType').value;
            const printPrepType = document.getElementById('printPrepTypes').value;
            const rows = document.querySelectorAll('#registerTable tbody tr');

            rows.forEach(row => {
                const rowActionType = row.querySelector('td:nth-child(7)').textContent;
                const rowPrintPrepTypes = row.querySelector('td:nth-child(8)').textContent.split(',').map(t => t.trim());

                let showRow = true;

                if (actionType) {
                    showRow = rowActionType === actionType;
                }

                if (printPrepType && showRow) {
                    showRow = rowPrintPrepTypes.includes(printPrepTypes);
                }

                row.style.display = showRow ? '' : 'none';
            });

            // Update filtered entries in state by getting visible rows
            state.filteredEntries = Array.from(rows)
                .filter(row => row.style.display !== 'none')
                .map(row => ({
                entryId: parseInt(row.querySelector('.entry-select').value),
                userId: state.currentUser?.userId,
                date: row.querySelector('td:nth-child(2)').textContent,
                orderId: row.querySelector('td:nth-child(3)').textContent,
                productionId: row.querySelector('td:nth-child(4)').textContent,
                omsId: row.querySelector('td:nth-child(5)').textContent,
                clientName: row.querySelector('td:nth-child(6)').textContent,
                actionType: row.querySelector('td:nth-child(7)').textContent,
                printPrepTypes: row.querySelector('td:nth-child(8)').textContent.split(',').map(t => t.trim()),
                colorsProfile: row.querySelector('td:nth-child(9)').textContent,
                articleNumbers: parseInt(row.querySelector('td:nth-child(10)').textContent),
                graphicComplexity: parseFloat(row.querySelector('td:nth-child(11)').textContent),
                observations: row.querySelector('td:nth-child(12)').textContent || "",
                adminSync: row.querySelector('td:nth-child(13)').textContent
            }));

            updateSummaryFromEntries(state.filteredEntries);
        }

        //saveChanges to use state and async/await pattern
        async function saveChanges() {
            if (!state.currentUser) {
                showError('No user selected');
                return;
            }

            try {
                // Get all entries from the table
                const entries = Array.from(document.querySelectorAll('#registerTable tbody tr'))
                    .filter(row => row.style.display !== 'none')
                    .map(row => {
                    // Convert date from dd/MM/yyyy to yyyy-MM-dd format
                    const dateText = row.querySelector('td:nth-child(2)').textContent;
                    const [day, month, year] = dateText.split('/');
                    const isoDate = `${year}-${month}-${day}`;

                    return {
                        entryId: parseInt(row.querySelector('.entry-select').value),
                        userId: state.currentUser.userId,
                        date: isoDate,
                        orderId: row.querySelector('td:nth-child(3)').textContent,
                        productionId: row.querySelector('td:nth-child(4)').textContent,
                        omsId: row.querySelector('td:nth-child(5)').textContent,
                        clientName: row.querySelector('td:nth-child(6)').textContent,
                        actionType: row.querySelector('td:nth-child(7)').textContent,
                        printPrepTypes: row.querySelector('td:nth-child(8)').textContent,
                        colorsProfile: row.querySelector('td:nth-child(9)').textContent,
                        articleNumbers: parseInt(row.querySelector('td:nth-child(10)').textContent),
                        graphicComplexity: parseFloat(row.querySelector('td:nth-child(11)').textContent),
                        observations: row.querySelector('td:nth-child(12)').textContent || "",
                        adminSync: "ADMIN_EDITED"
                    };
                });

                const response = await fetch('/admin/register/save', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        username: state.currentUser.username,
                        userId: state.currentUser.userId,
                        year: state.currentYear,
                        month: state.currentMonth,
                        entries: entries
                    })
                });

                if (!response.ok) {
                    throw new Error('Save failed');
                }

                // Show success message
                const alertsContainer = document.querySelector('.alerts');
                if (alertsContainer) {
                    alertsContainer.innerHTML = `
                <div class="alert alert-success">
                    Changes saved successfully. Reloading page...
                </div>
            `;
                }

                // Reload after successful save
                setTimeout(() => {
                    window.location.reload();
                }, 1500);

            } catch (error) {
                console.error('Save error:', error);
                showError('Failed to save changes: ' + error.message);
            }
        }

        // Update validation to include miscValue check
        function validateBonusConfig(config) {
            const totalPercentage = config.entriesPercentage +
            config.articlesPercentage +
            config.complexityPercentage +
            config.miscPercentage;

            if (Math.abs(totalPercentage - 1.0) > 0.0001) {
                showError('Percentages must sum to 1.0');
                return false;
            }

            if (config.miscValue < 0) {
                showError('Misc value must be non-negative');
                return false;
            }

            return true;
        }
        //displayBonusResults to use state
        function displayBonusResults(result) {
            const bonusResults = document.getElementById('bonusResults');
            const tbody = document.getElementById('bonusResultsBody');

            if (!bonusResults || !tbody) {
                console.error('Bonus results elements not found');
                return;
            }

            // Get previous months
            const currentDate = new Date(state.currentYear, state.currentMonth - 1);
            const prevMonths = [];

            for (let i = 1; i <= 3; i++) {
                const prevDate = new Date(currentDate);
                prevDate.setMonth(currentDate.getMonth() - i);
                const monthName = prevDate.toLocaleString('en-US', { month: 'short' });
                const yearStr = prevDate.getFullYear().toString().slice(2);
                prevMonths.push(`${monthName}/${yearStr}`);
            }

            // Add warning class if worked days is 0
            const rowClass = result.workedDays <= 0 ? 'table-warning' : '';

            // Update the table headers dynamically
            const thead = bonusResults.querySelector('thead tr');
            if (thead) {
                thead.innerHTML = `
            <th>Name</th>
            <th>Entries</th>
            <th>Art Nr.</th>
            <th>CG</th>
            <th>Misc</th>
            <th>Worked D</th>
            <th>Worked%</th>
            <th>Bonus%</th>
            <th>Bonus$</th>
            <th>${prevMonths[0]}</th>
            <th>${prevMonths[1]}</th>
            <th>${prevMonths[2]}</th>
        `;
            }

            tbody.innerHTML = `
        <tr class="${rowClass}">
            <td>${state.currentUser?.name || ''}</td>
            <td>${result.entries}</td>
            <td>${result.articleNumbers.toFixed(2)}</td>
            <td>${result.graphicComplexity.toFixed(2)}</td>
            <td>${result.misc.toFixed(2)}</td>
            <td>${result.workedDays}</td>
            <td>${result.workedPercentage.toFixed(2)}</td>
            <td>${result.bonusPercentage.toFixed(2)}</td>
            <td>${result.bonusAmount.toFixed(2)}</td>
            <td>${(result.previousMonths?.month1 || 0).toFixed(2)}</td>
            <td>${(result.previousMonths?.month2 || 0).toFixed(2)}</td>
            <td>${(result.previousMonths?.month3 || 0).toFixed(2)}</td>
        </tr>
    `;

            // Add explanatory message if worked days is 0
            if (result.workedDays <= 0) {
                tbody.innerHTML += `
            <tr>
                <td colspan="12" class="text-center text-warning">
                    <small><i>* No worked days recorded - showing raw totals with zero bonus amounts</i></small>
                </td>
            </tr>
        `;
            }

            bonusResults.style.display = 'block';
        }

        function validateUserContext() {
            return !!(state.currentUser?.userId && state.currentYear && state.currentMonth);
        }

        function getSelectedEntryIds() {
            return Array.from(document.querySelectorAll('.entry-select:checked'))
                .map(checkbox => checkbox.value);
        }

        function getBonusConfig() {
            return {
                sumValue: parseFloat(document.getElementById('bonusSum').value) || 0.0,
                entriesPercentage: parseFloat(document.getElementById('entriesPercentage').value) || 0.0,
                articlesPercentage: parseFloat(document.getElementById('articlesPercentage').value) || 0.0,
                complexityPercentage: parseFloat(document.getElementById('complexityPercentage').value) || 0.0,
                miscPercentage: parseFloat(document.getElementById('miscPercentage').value) || 0.0,
                normValue: parseFloat(document.getElementById('normValue').value) || 1.20,    // Use form value
                miscValue: parseFloat(document.getElementById('miscValue').value) || 1.50     // Use form value
            };
        }

        function validateBonusConfig(config) {
            const totalPercentage = config.entriesPercentage +
            config.articlesPercentage +
            config.complexityPercentage +
            config.miscPercentage;

            if (Math.abs(totalPercentage - 1.0) > 0.0001) {
                showError('Percentages must sum to 1.0');
                return false;
            }
            return true;
        }

        function showError(message) {
            const alertsContainer = document.querySelector('.alerts');
            if (!alertsContainer) return;

            const alertDiv = document.createElement('div');
            alertDiv.className = 'alert alert-danger';
            alertDiv.textContent = message;

            // Clear existing alerts
            alertsContainer.innerHTML = '';
            alertsContainer.appendChild(alertDiv);
        }

        // Add a warning function that shows an orange warning instead of a red error
        function showWarning(message) {
            const alertsContainer = document.querySelector('.alerts');
            if (!alertsContainer) return;

            const alertDiv = document.createElement('div');
            alertDiv.className = 'alert alert-warning'; // Use warning style
            alertDiv.textContent = message;

            // Clear existing alerts
            alertsContainer.innerHTML = '';
            alertsContainer.appendChild(alertDiv);
        }

        function performSearch() {
            const searchTerm = document.getElementById('searchInput').value.toLowerCase();
            const rows = document.querySelectorAll('#registerTable tbody tr');

            rows.forEach(row => {
                const textContent = row.textContent.toLowerCase();
                row.style.display = textContent.includes(searchTerm) ? '' : 'none';
            });
        }

        function clearTable() {
            if (confirm('Are you sure you want to clear all entries?')) {
                document.querySelectorAll('#registerTable tbody tr').forEach(row => {
                    row.style.display = 'none';
                });
                // Reset filtered entries
                filteredEntries = [];
                // Clear summary display
                updateSummaryDisplay();
            }
        }