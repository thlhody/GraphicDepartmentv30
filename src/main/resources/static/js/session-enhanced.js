/**
 * Enhanced Session Management UI
 * Provides interactive features for the work session page
 */
document.addEventListener('DOMContentLoaded', function() {
    // Initialize tooltips
    initTooltips();

    // Initialize live clock
    initLiveClock();

    // Check for previously submitted forms
    checkFormSubmission();
});

/**
 * Initialize Bootstrap tooltips
 */
function initTooltips() {
    const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    tooltipTriggerList.map(function (tooltipTriggerEl) {
        return new bootstrap.Tooltip(tooltipTriggerEl);
    });
}

/**
 * Initialize and update the live clock
 */
function initLiveClock() {
    // Real-time clock update
    function updateClock() {
        const timeDisplay = document.getElementById('live-clock');
        if (timeDisplay) {
            const now = new Date();
            const formattedTime = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
            timeDisplay.textContent = formattedTime;

            // Add a subtle fade animation on minute change
            if (now.getSeconds() === 0) {
                timeDisplay.classList.add('time-pulse');
                setTimeout(() => timeDisplay.classList.remove('time-pulse'), 1000);
            }
        }
    }

    // Update clock every second
    setInterval(updateClock, 1000);
    updateClock(); // Initial call
}

/**
 * Check if a form was previously submitted and page reloaded
 */
function checkFormSubmission() {
    if (localStorage.getItem('formSubmitted') === 'true') {
        // Clear the flag
        localStorage.removeItem('formSubmitted');

        // If we have a success message, hide the resolution container
        const successMessage = document.querySelector('.alert-success');
        if (successMessage) {
            const container = document.getElementById('workTimeResolutionContainer');
            if (container) {
                container.style.display = 'none';
            }
        }
    }
}

/**
 * Creates and displays a temporary alert message
 * @param {string} type - The alert type (success, danger, warning, info)
 * @param {string} message - The message to display
 * @param {number} duration - How long to show the alert in milliseconds
 * @returns {HTMLElement} - The created alert element
 */
window.showTemporaryAlert = function(type, message, duration = 5000) {
    const alertContainer = document.createElement('div');
    alertContainer.className = `alert alert-${type} alert-dismissible fade show d-flex align-items-center`;
    alertContainer.role = 'alert';

    const icon = type === 'success' ? 'bi-check-circle-fill' :
    type === 'danger' ? 'bi-exclamation-circle-fill' :
    type === 'warning' ? 'bi-exclamation-triangle-fill' : 'bi-info-circle-fill';

    alertContainer.innerHTML = `
        <div class="d-flex w-100">
            <div class="alert-icon me-3">
                <i class="bi ${icon}"></i>
            </div>
            <div class="alert-content flex-grow-1">
                <div class="alert-text">${message}</div>
            </div>
            <button type="button" class="btn-close ms-2" data-bs-dismiss="alert" aria-label="Close"></button>
        </div>
    `;

    // Insert at the top of the container
    const container = document.querySelector('.container.py-4');
    container.insertBefore(alertContainer, container.firstChild);

    // Auto-remove after duration
    if (duration > 0) {
        setTimeout(() => {
            const alert = bootstrap.Alert.getOrCreateInstance(alertContainer);
            alert.close();
        }, duration);
    }

    return alertContainer;
};

/**
 * Helper function to format minutes as HH:MM
 * @param {number} minutes - Minutes to format
 * @returns {string} - Formatted time string
 */
window.formatMinutes = function(minutes) {
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    return `${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}`;
};