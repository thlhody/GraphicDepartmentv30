/**
 * StatisticsCharts.js
 *
 * Creates and manages Chart.js visualizations for user statistics.
 * Handles pie charts (client, action types, print prep types),
 * line charts (monthly entries), and bar charts (daily entries).
 *
 * @module features/statistics/StatisticsCharts
 */

/**
 * StatisticsCharts class
 * Manages Chart.js chart creation and configuration
 */
export class StatisticsCharts {
    constructor() {
        // Chart.js color palette
        this.chartColors = [
            '#FF6384', '#36A2EB', '#FFCE56', '#4BC0C0',
            '#9966FF', '#FF9F40', '#7CBA3D', '#6B8E23'
        ];

        console.log('StatisticsCharts initialized');
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize all charts with data from window globals
     */
    initializeCharts() {
        console.log('🚀 Initializing statistics charts...');

        try {
            // Check if Chart.js is loaded
            if (typeof Chart === 'undefined') {
                console.error('Chart.js library not loaded');
                return;
            }

            // Create pie charts
            if (window.clientData) {
                this.createPieChart('clientChart', window.clientData, 'Client Distribution');
            }

            if (window.actionTypeData) {
                this.createPieChart('actionTypeChart', window.actionTypeData, 'Action Types');
            }

            if (window.printPrepTypeData) {
                this.createPieChart('printPrepTypeChart', window.printPrepTypeData, 'Print Prep Types');
            }

            // Create line chart for monthly entries
            if (window.monthlyEntriesData) {
                this.createMonthlyEntriesChart('monthlyEntriesChart', window.monthlyEntriesData, 'Monthly Entries Distribution');
            }

            // Create bar chart for daily entries
            if (window.dailyEntriesData) {
                this.createDailyEntriesChart('dailyEntriesChart', window.dailyEntriesData, 'Daily Entries Distribution');
            }

            console.log('✅ All statistics charts initialized successfully');

        } catch (error) {
            console.error('❌ Error initializing statistics charts:', error);
        }
    }

    // ========================================================================
    // PIE CHARTS
    // ========================================================================

    /**
     * Create a pie chart
     * @param {string} elementId - Canvas element ID
     * @param {Object} data - Chart data with labels and data arrays
     * @param {string} title - Chart title
     */
    createPieChart(elementId, data, title) {
        const canvas = document.getElementById(elementId);
        if (!canvas) {
            console.warn(`Canvas element '${elementId}' not found`);
            return;
        }

        const ctx = canvas.getContext('2d');

        new Chart(ctx, {
            type: 'pie',
            data: {
                labels: data.labels,
                datasets: [{
                    data: data.data,
                    backgroundColor: this.chartColors
                }]
            },
            options: {
                responsive: true,
                plugins: {
                    legend: {
                        position: 'bottom'
                    },
                    title: {
                        display: true,
                        text: title
                    }
                }
            }
        });

        console.log(`✓ Created pie chart: ${title}`);
    }

    // ========================================================================
    // MONTHLY ENTRIES LINE CHART
    // ========================================================================

    /**
     * Create monthly entries line chart
     * Shows regular vs SPIZED entries over months
     * @param {string} elementId - Canvas element ID
     * @param {Object} data - Chart data with regular and spized objects
     * @param {string} title - Chart title
     */
    createMonthlyEntriesChart(elementId, data, title) {
        const canvas = document.getElementById(elementId);
        if (!canvas) {
            console.warn(`Canvas element '${elementId}' not found`);
            return;
        }

        const ctx = canvas.getContext('2d');

        new Chart(ctx, {
            type: 'line',
            data: {
                labels: Object.keys(data.regular),
                datasets: [
                    {
                        label: 'Regular Entries',
                        data: Object.values(data.regular),
                        fill: false,
                        borderColor: '#36A2EB',
                        tension: 0.1,
                        pointBackgroundColor: '#36A2EB'
                    },
                    {
                        label: 'SPIZED Entries',
                        data: Object.values(data.spized),
                        fill: false,
                        borderColor: '#FF6384',
                        tension: 0.1,
                        pointBackgroundColor: '#FF6384'
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                plugins: {
                    legend: {
                        position: 'top'
                    },
                    title: {
                        display: true,
                        text: title
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        ticks: {
                            stepSize: 1
                        }
                    }
                }
            }
        });

        console.log(`✓ Created monthly entries chart: ${title}`);
    }

    // ========================================================================
    // DAILY ENTRIES BAR CHART
    // ========================================================================

    /**
     * Create daily entries bar chart
     * Shows entries distribution by day of month
     * @param {string} elementId - Canvas element ID
     * @param {Object} data - Chart data with day as key and count as value
     * @param {string} title - Chart title
     */
    createDailyEntriesChart(elementId, data, title) {
        const canvas = document.getElementById(elementId);
        if (!canvas) {
            console.warn(`Canvas element '${elementId}' not found`);
            return;
        }

        const ctx = canvas.getContext('2d');

        new Chart(ctx, {
            type: 'bar',
            data: {
                labels: Object.keys(data),
                datasets: [{
                    label: 'Daily Entries',
                    data: Object.values(data),
                    backgroundColor: '#4BC0C0',
                    borderColor: '#4BC0C0',
                    borderWidth: 1
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                plugins: {
                    legend: {
                        position: 'top'
                    },
                    title: {
                        display: true,
                        text: title
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        ticks: {
                            stepSize: 1
                        }
                    },
                    x: {
                        title: {
                            display: true,
                            text: 'Day of Month'
                        }
                    }
                }
            }
        });

        console.log(`✓ Created daily entries chart: ${title}`);
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Get chart instance by canvas ID
     * @param {string} elementId - Canvas element ID
     * @returns {Chart|null} Chart instance or null
     */
    getChartInstance(elementId) {
        const canvas = document.getElementById(elementId);
        if (!canvas) return null;

        return Chart.getChart(canvas);
    }

    /**
     * Destroy chart by canvas ID
     * @param {string} elementId - Canvas element ID
     */
    destroyChart(elementId) {
        const chart = this.getChartInstance(elementId);
        if (chart) {
            chart.destroy();
            console.log(`Destroyed chart: ${elementId}`);
        }
    }

    /**
     * Destroy all charts
     */
    destroyAllCharts() {
        const chartIds = [
            'clientChart',
            'actionTypeChart',
            'printPrepTypeChart',
            'monthlyEntriesChart',
            'dailyEntriesChart'
        ];

        chartIds.forEach(id => this.destroyChart(id));
        console.log('All charts destroyed');
    }
}
