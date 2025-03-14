/**
 * User Status Management JavaScript
 * Handles status page functionality including refreshing status data,
 * formatting dates, and updating UI elements.
 */

// Main initialization function when document is loaded
document.addEventListener('DOMContentLoaded', function() {
    // Initial setup
    updateOnlineCount();
    updateLastRefresh();
    formatDateDisplays();

    // Set up manual refresh button
    setupRefreshButton();

    // Setup auto-refresh every minute
    setInterval(function() {
        autoRefreshStatus();
        updateOnlineCount();
    }, 60000);
});

/**
 * Sets up event listener for the refresh button
 */
function setupRefreshButton() {
    const refreshButton = document.querySelector('a[href="/status/refresh"]');
    if (refreshButton) {
        refreshButton.addEventListener('click', function(e) {
            e.preventDefault();
            autoRefreshStatus();
            return false;
        });
    }
}

/**
 * Updates the online user count based on table data
 */
function updateOnlineCount() {
    const statusSpans = document.querySelectorAll('table tbody tr td span.badge span.ms-1');
    let onlineCount = 0;

    statusSpans.forEach(span => {
        if (span.textContent.trim() === 'Online') {
            onlineCount++;
        }
    });

    document.getElementById('onlineCount').textContent = onlineCount;
}

/**
 * Updates the "Last updated" timestamp
 */
function updateLastRefresh() {
    const now = new Date();

    // Get day of week
    const daysOfWeek = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
    const dayName = daysOfWeek[now.getDay()];

    // Format the date as DD/MM/YYYY
    const day = String(now.getDate()).padStart(2, '0');
    const month = String(now.getMonth() + 1).padStart(2, '0'); // January is 0
    const year = now.getFullYear();

    // Format the time
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');

    // Combine into Day :: DD/MM/YYYY :: HH:MM format with bold time
    const formattedDateTime = `${dayName} :: ${day}/${month}/${year} :: <strong>${hours}:${minutes}</strong>`;

    document.getElementById('lastUpdate').innerHTML = formattedDateTime;
}

/**
 * Formats all date elements on the page
 */
function formatDateDisplays() {
    document.querySelectorAll('.date-display').forEach(element => {
        const timestamp = element.textContent.trim();

        // Skip if it's "Never" or other non-date value
        if (timestamp === "Never" || !timestamp.includes('-')) {
            return;
        }

        try {
            // Parse timestamp (expected format: yyyy-MM-dd HH:mm:ss)
            const parts = timestamp.split(' ');
            if (parts.length !== 2) return;

            const dateParts = parts[0].split('-');
            const timeParts = parts[1].split(':');

            if (dateParts.length !== 3 || timeParts.length < 2) return;

            const year = parseInt(dateParts[0]);
            const month = parseInt(dateParts[1]) - 1; // Months are 0-indexed in JS
            const day = parseInt(dateParts[2]);
            const hours = parseInt(timeParts[0]);
            const minutes = parseInt(timeParts[1]);

            const date = new Date(year, month, day, hours, minutes);

            // Get day of week
            const daysOfWeek = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
            const dayName = daysOfWeek[date.getDay()];

            // Format the date parts
            const formattedDay = String(day).padStart(2, '0');
            const formattedMonth = String(month + 1).padStart(2, '0');
            const formattedHours = String(hours).padStart(2, '0');
            const formattedMinutes = String(minutes).padStart(2, '0');

            // Create the formatted string: Day :: DD/MM/YYYY :: bold(HH:MM)
            const formattedDate = `${dayName} :: ${formattedDay}/${formattedMonth}/${year} :: <strong>${formattedHours}:${formattedMinutes}</strong>`;

            // Update the element
            element.innerHTML = formattedDate;

        } catch (error) {
            console.error("Error formatting date:", error);
        }
    });
}

/**
 * Refreshes the status data via AJAX
 */
function autoRefreshStatus() {
    // Show a small loading indicator
    const refreshButton = document.querySelector('a[href="/status/refresh"]');
    if (refreshButton) {
        refreshButton.innerHTML = '<i class="bi bi-arrow-clockwise me-1 spin"></i> Refreshing...';
        refreshButton.classList.add('disabled');
    }

    // Make AJAX request to get fresh status data
    fetch('/status/ajax-refresh', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
            'X-Requested-With': 'XMLHttpRequest'
        },
        credentials: 'same-origin'
    })
        .then(response => {
        if (!response.ok) {
            throw new Error('Network response was not ok');
        }
        return response.json();
    })
        .then(data => {
        // Update the online count
        document.getElementById('onlineCount').textContent = data.onlineCount;

        // Update the table body with new HTML
        if (data.tableHtml) {
            const tableBody = document.querySelector('table tbody');
            tableBody.innerHTML = data.tableHtml;
        }

        // Update last refresh time
        updateLastRefresh();

        // Format dates again (since we replaced the DOM elements)
        formatDateDisplays();

        // Restore refresh button
        if (refreshButton) {
            refreshButton.innerHTML = '<i class="bi bi-arrow-clockwise me-1"></i> Refresh';
            refreshButton.classList.remove('disabled');
        }
    })
        .catch(error => {
        console.error('Error refreshing status data:', error);

        // Restore refresh button on error
        if (refreshButton) {
            refreshButton.innerHTML = '<i class="bi bi-arrow-clockwise me-1"></i> Refresh';
            refreshButton.classList.remove('disabled');
            refreshButton.classList.add('btn-danger');
            setTimeout(() => {
                refreshButton.classList.remove('btn-danger');
                refreshButton.classList.add('btn-outline-secondary');
            }, 2000);
        }
    });
}