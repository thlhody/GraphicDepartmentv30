// Chart data from server
const chartColors = ['#FF6384', '#36A2EB', '#FFCE56', '#4BC0C0','#9966FF', '#FF9F40', '#7CBA3D', '#6B8E23'];

// Create charts when DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    createChart('clientChart', window.clientData, 'Client Distribution');
    createChart('actionTypeChart', window.actionTypeData, 'Action Types');
    createChart('printPrepTypeChart', window.printPrepTypeData, 'Print Prep Types');
    createMonthlyEntriesChart('monthlyEntriesChart', window.monthlyEntriesData, 'Monthly Entries Distribution');
    createDailyEntriesChart('dailyEntriesChart', window.dailyEntriesData, 'Daily Entries Distribution');
});

function createMonthlyEntriesChart(elementId, data, title) {
    const ctx = document.getElementById(elementId).getContext('2d');
    new Chart(ctx, {
        type: 'line',
        data: {
            labels: Object.keys(data.regular),
            datasets: [{
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
                }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    position: 'top',
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
}

function createDailyEntriesChart(elementId, data, title) {
    const ctx = document.getElementById(elementId).getContext('2d');
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
                    position: 'top',
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
}

function createChart(elementId, data, title) {
    const ctx = document.getElementById(elementId).getContext('2d');
    new Chart(ctx, {
        type: 'pie',
        data: {
            labels: data.labels,
            datasets: [{
                data: data.data,
                backgroundColor: chartColors
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
}