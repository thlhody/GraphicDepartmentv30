/**
 * CheckRegisterSummary.js
 *
 * Manages check register summary statistics and metrics.
 * Uses MutationObserver to automatically recalculate when table changes.
 *
 * @module features/check-register/CheckRegisterSummary
 */

/**
 * CheckRegisterSummary class
 * Calculates and displays summary statistics for check register entries
 */
export class CheckRegisterSummary {
    constructor() {
        // Initialize type counters
        this.typeCounts = {
            layout: 0,
            kipstaLayout: 0,
            layoutChanges: 0,
            gpt: 0,
            production: 0,
            reorder: 0,
            sample: 0,
            omsProduction: 0,
            kipstaProduction: 0,
            other: 0
        };

        // Initialize approval status counters
        this.approvalCounts = {
            approved: 0,
            partiallyApproved: 0,
            correction: 0
        };

        // Initialize metrics
        this.metrics = {
            totalEntries: 0,
            totalArticles: 0,
            totalFiles: 0,
            totalOrderValue: 0,
            standardHours: 0,
            liveWorkHours: 0,
            targetUnitsHour: 0
        };

        this.setupObserver();

        // Initial calculation
        try {
            this.calculateStats();
        } catch (error) {
            console.error('Error calculating initial stats:', error);
        }

        // Add form submission handler to recalculate after form actions
        const checkRegisterForm = document.getElementById('checkRegisterForm');
        if (checkRegisterForm) {
            checkRegisterForm.addEventListener('submit', () => {
                // Allow time for DOM to update
                setTimeout(() => this.calculateStats(), 100);
            });
        }
    }

    /**
     * Setup MutationObserver to watch for table changes
     */
    setupObserver() {
        try {
            const tableBody = document.querySelector('.table tbody');
            if (tableBody) {
                const observer = new MutationObserver((mutations) => {
                    // Check if mutations actually affect our data
                    const hasRelevantChanges = mutations.some(mutation =>
                        mutation.type === 'childList' ||
                        (mutation.type === 'characterData' &&
                            mutation.target.parentElement?.tagName === 'TD')
                    );

                    if (hasRelevantChanges) {
                        this.calculateStats();
                    }
                });

                observer.observe(tableBody, {
                    childList: true,
                    subtree: true,
                    characterData: true
                });
            }
        } catch (error) {
            console.error('Error setting up observer:', error);
        }
    }

    /**
     * Calculate statistics from table entries
     */
    calculateStats() {
        try {
            // Reset counters
            this.resetCounters();

            // Get metrics from page
            this.metrics.standardHours = parseFloat(document.getElementById('standard-hours')?.textContent || '0');
            this.metrics.liveWorkHours = parseFloat(document.getElementById('live-work-hours')?.textContent || '0');
            this.metrics.targetUnitsHour = parseFloat(document.getElementById('target-units-hour')?.textContent || '0');

            // Get table body (check register entries only, not bonus table)
            const tableBody = document.querySelector('.register-content .table tbody');
            if (!tableBody) {
                console.warn("Check register table body not found, cannot calculate metrics");
                this.updateUI();
                return;
            }

            // Collect entry IDs and valid rows
            const entryIds = new Set();
            const validRows = [];

            Array.from(tableBody.rows).forEach(row => {
                // Skip empty state rows
                if (row.querySelector('td[colspan]') || row.cells.length < 5) {
                    return;
                }

                // Get ID from first cell
                const idCell = row.cells[0];
                if (idCell) {
                    const idText = idCell.textContent.trim();
                    const id = parseInt(idText);
                    if (!isNaN(id)) {
                        entryIds.add(id);
                        validRows.push(row);
                    }
                }
            });

            console.log(`Found ${entryIds.size} distinct entry IDs: ${Array.from(entryIds).join(', ')}`);

            // Use ID count as definitive entry count
            this.metrics.totalEntries = entryIds.size;

            // Process valid rows for other metrics
            validRows.forEach(row => {
                this.processRow(row);
            });

            // Calculate efficiencies
            this.calculateEfficiencies();

            // Set entries count directly before other UI updates
            const entriesCountElement = document.getElementById('entries-count');
            if (entriesCountElement) {
                entriesCountElement.textContent = entryIds.size;
                console.log(`Directly set entries-count to ${entryIds.size}`);
            }

            // Update all other metrics
            this.updateUI();

        } catch (error) {
            console.error('Error calculating stats:', error);
            this.resetStats();
        }
    }

    /**
     * Reset all counters
     */
    resetCounters() {
        Object.keys(this.typeCounts).forEach(key => this.typeCounts[key] = 0);
        Object.keys(this.approvalCounts).forEach(key => this.approvalCounts[key] = 0);

        this.metrics.totalEntries = 0;
        this.metrics.totalArticles = 0;
        this.metrics.totalFiles = 0;
        this.metrics.totalOrderValue = 0;
    }

    /**
     * Process a single table row
     * @param {HTMLTableRowElement} row - Table row
     */
    processRow(row) {
        const cells = row.cells;

        try {
            // Get check type
            const checkTypeElem = cells[5]?.querySelector('.badge');
            const checkType = checkTypeElem ? checkTypeElem.textContent.trim() : '';

            // Get articles and files count
            const articles = parseInt(cells[6]?.textContent || '0') || 0;
            const files = parseInt(cells[7]?.textContent || '0') || 0;

            // Get approval status
            const approvalElem = cells[9]?.querySelector('.badge');
            const approvalStatus = approvalElem ? approvalElem.textContent.trim() : '';

            // Get order value
            const orderValue = parseFloat(cells[10]?.textContent || '0') || 0;

            // Update totals
            this.metrics.totalArticles += articles;
            this.metrics.totalFiles += files;
            this.metrics.totalOrderValue += orderValue;

            // Count check types
            this.countCheckType(checkType);

            // Count approval statuses
            this.countApprovalStatus(approvalStatus);

        } catch (e) {
            console.error('Error processing row:', e);
        }
    }

    /**
     * Count check type
     * @param {string} checkType - Check type
     */
    countCheckType(checkType) {
        if (!checkType) return;

        switch (checkType) {
            case 'LAYOUT': this.typeCounts.layout++; break;
            case 'KIPSTA LAYOUT': this.typeCounts.kipstaLayout++; break;
            case 'LAYOUT CHANGES': this.typeCounts.layoutChanges++; break;
            case 'GPT': this.typeCounts.gpt++; break;
            case 'PRODUCTION': this.typeCounts.production++; break;
            case 'REORDER': this.typeCounts.reorder++; break;
            case 'SAMPLE': this.typeCounts.sample++; break;
            case 'OMS PRODUCTION': this.typeCounts.omsProduction++; break;
            case 'KIPSTA PRODUCTION': this.typeCounts.kipstaProduction++; break;
            default: this.typeCounts.other++; break;
        }
    }

    /**
     * Count approval status
     * @param {string} approvalStatus - Approval status
     */
    countApprovalStatus(approvalStatus) {
        if (!approvalStatus) return;

        switch (approvalStatus) {
            case 'APPROVED': this.approvalCounts.approved++; break;
            case 'PARTIALLY APPROVED': this.approvalCounts.partiallyApproved++; break;
            case 'CORRECTION': this.approvalCounts.correction++; break;
        }
    }

    /**
     * Calculate efficiency metrics
     */
    calculateEfficiencies() {
        // Calculate standard efficiency
        let efficiency = 0;
        if (this.metrics.standardHours > 0 && this.metrics.targetUnitsHour > 0) {
            const targetTotal = this.metrics.standardHours * this.metrics.targetUnitsHour;
            efficiency = targetTotal > 0 ? (this.metrics.totalOrderValue / targetTotal * 100) : 0;
        }

        this.updateEfficiencyDisplay('efficiency-level', efficiency);

        // Calculate live efficiency
        let liveEfficiency = 0;
        if (this.metrics.liveWorkHours > 0 && this.metrics.targetUnitsHour > 0) {
            const liveTargetTotal = this.metrics.liveWorkHours * this.metrics.targetUnitsHour;
            liveEfficiency = liveTargetTotal > 0 ? (this.metrics.totalOrderValue / liveTargetTotal * 100) : 0;
        }

        this.updateEfficiencyDisplay('live-efficiency-level', liveEfficiency);
    }

    /**
     * Update efficiency display element
     * @param {string} elementId - Element ID
     * @param {number} efficiency - Efficiency percentage
     */
    updateEfficiencyDisplay(elementId, efficiency) {
        const element = document.getElementById(elementId);
        if (!element) return;

        element.textContent = `${efficiency.toFixed(1)}%`;

        // Add class based on efficiency level
        element.classList.remove('high-efficiency', 'medium-efficiency', 'low-efficiency');
        if (efficiency >= 90) {
            element.classList.add('high-efficiency');
        } else if (efficiency >= 70) {
            element.classList.add('medium-efficiency');
        } else {
            element.classList.add('low-efficiency');
        }
    }

    /**
     * Reset all stats to zero
     */
    resetStats() {
        this.resetCounters();
        this.metrics.standardHours = 0;
        this.metrics.liveWorkHours = 0;
        this.metrics.targetUnitsHour = 0;
        this.updateUI();
    }

    /**
     * Update UI with current metrics
     */
    updateUI() {
        try {
            const elements = {
                // Check types metrics
                'count-layout': this.typeCounts.layout,
                'count-kipsta-layout': this.typeCounts.kipstaLayout,
                'count-layout-changes': this.typeCounts.layoutChanges,
                'count-gpt': this.typeCounts.gpt,
                'count-production': this.typeCounts.production + this.typeCounts.reorder,
                'count-sample': this.typeCounts.sample,
                'count-oms-production': this.typeCounts.omsProduction,
                'count-kipsta-production': this.typeCounts.kipstaProduction,

                // Approval status metrics
                'count-approved': this.approvalCounts.approved,
                'count-partially-approved': this.approvalCounts.partiallyApproved,
                'count-correction': this.approvalCounts.correction,

                // Key metrics
                'total-entries': this.metrics.totalEntries,
                'total-articles': this.metrics.totalArticles,
                'total-files': this.metrics.totalFiles,
                'total-order-value': this.metrics.totalOrderValue.toFixed(2)
            };

            console.log("Updating UI with totalEntries =", this.metrics.totalEntries);

            // Update each element
            Object.entries(elements).forEach(([id, value]) => {
                const element = document.getElementById(id);
                if (element) {
                    element.textContent = value;
                }
            });
        } catch (error) {
            console.error('Error updating UI:', error);
        }
    }
}
