/**
 * Helper function to format minutes as HH:MM
 * @param {number} minutes - Minutes to format
 * @returns {string} - Formatted time string
 */
window.formatMinutes = function(minutes) {
    if (minutes === undefined || minutes === null) return "00:00";
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    return `${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}`;
};

/**
 * Creates and displays a temporary alert message
 * @param {string} type - The alert type (success, danger, warning, info)
 * @param {string} message - The message to display
 * @param {number} duration - How long to show the alert in milliseconds
 * @returns {HTMLElement} - The created alert element
 */
window.showTemporaryAlert = function(type, message, duration = 5000) {
    // Implementation stays the same
};