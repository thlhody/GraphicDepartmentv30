/**
 * Work Time Resolution Script
 * Handles the interactive functionality for resolving unfinished work sessions with toast notifications
 */
document.addEventListener('DOMContentLoaded', function() {
    // Set up close button for resolution container
    initResolutionContainer();

    // Set up time selectors and initial calculation
    initTimeSelectors();

    // Set up form submission
    initFormSubmission();

    // Ensure all calculation results are visible
    ensureCalculationResultsVisible();
});

/**
 * Safe wrapper for showing toasts with fallback
 * @param {string} title - Toast title
 * @param {string} message - Toast message
 * @param {string} type - Toast type (success, error, warning, info)
 */
function showToastSafe(title, message, type) {
    if (typeof window.showToast === 'function') {
        window.showToast(title, message, type);
    } else {
        // Fallback to console logging if toast system is not available
        console.log(`${type.toUpperCase()}: ${title} - ${message}`);

        // Create a simple alert div as fallback
        createFallbackAlert(title, message, type);
    }
}

/**
 * Creates a simple fallback alert when toast system is not available
 * @param {string} title - Alert title
 * @param {string} message - Alert message
 * @param {string} type - Alert type
 */
function createFallbackAlert(title, message, type) {
    const alertType = type === 'error' ? 'danger' : type;
    const alertContainer = document.createElement('div');
    alertContainer.className = `alert alert-${alertType} alert-dismissible fade show`;
    alertContainer.innerHTML = `
        <strong>${title}:</strong> ${message}
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
    `;

    // Insert at the top of the container
    const container = document.querySelector('.container.py-4');
    if (container) {
        container.insertBefore(alertContainer, container.firstChild);
    }

    // Auto-remove after 5 seconds
    setTimeout(() => {
        if (alertContainer.parentNode) {
            alertContainer.remove();
        }
    }, 5000);
}

/**
 * Makes sure all calculation results are visible
 */
function ensureCalculationResultsVisible() {
    // Make sure all calculation-result divs are visible
    document.querySelectorAll('.calculation-result').forEach(div => {
        div.style.display = 'block';
    });
}

/**
 * Initialize the resolution container functionality
 */
function initResolutionContainer() {
    // Close button functionality
    const closeBtn = document.getElementById('closeResolutionBtn');
    if (closeBtn) {
        closeBtn.addEventListener('click', function() {
            const container = document.getElementById('workTimeResolutionContainer');
            if (container) {
                container.style.display = 'none';
            }
        });
    }

    // Scroll to resolution section if URL hash exists
    const scrollToLinks = document.querySelectorAll('.js-scroll-to-resolution');
    scrollToLinks.forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            const container = document.getElementById('workTimeResolutionContainer');
            if (container) {
                container.scrollIntoView({ behavior: 'smooth' });
            }
        });
    });
}

/**
 * Initialize time selectors in the forms
 */
function initTimeSelectors() {
    // Hour/minute select change handlers
    document.querySelectorAll('.hour-select, .minute-select').forEach(select => {
        select.addEventListener('change', function() {
            const form = this.closest('form');
            calculateWorkTime(form);
        });
    });

    // Calculate initial values for all forms with pre-selected values
    document.querySelectorAll('.resolution-form').forEach(form => {
        const hourSelect = form.querySelector('select[name="endHour"]');
        const minuteSelect = form.querySelector('select[name="endMinute"]');

        // If both hour and minute have preselected values, calculate immediately
        if (hourSelect.value && minuteSelect.value) {
            calculateWorkTime(form);
        }

        // Make sure the calculation-result section is visible
        const calculationResult = form.querySelector('.calculation-result');
        if (calculationResult) {
            calculationResult.style.display = 'block';
        }
    });
}

/**
 * Initialize form submission handling
 */
function initFormSubmission() {
    // Handle form submission
    document.querySelectorAll('.resolution-form').forEach(form => {
        form.addEventListener('submit', function(e) {
            // Form validation
            const hourSelect = this.querySelector('select[name="endHour"]');
            const minuteSelect = this.querySelector('select[name="endMinute"]');

            if (!hourSelect.value || !minuteSelect.value) {
                e.preventDefault();
                showToastSafe('Validation Error', 'Please select both hour and minute', 'warning');
                return;
            }

            // Show processing message
            showToastSafe('Processing', 'Processing your resolution request...', 'info');

            // Store that this form was submitted
            localStorage.setItem('formSubmitted', 'true');
        });
    });
}

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
 * Calculate work time based on form inputs using backend calculations
 * @param {HTMLFormElement} form - The form containing time inputs
 */
function calculateWorkTime(form) {
    const hourSelect = form.querySelector('select[name="endHour"]');
    const minuteSelect = form.querySelector('select[name="endMinute"]');
    const calculationResult = form.querySelector('.calculation-result');
    const calculationText = form.querySelector('.calculation-text');

    if (!hourSelect.value || !minuteSelect.value) return;

    // Show loading indicator
    calculationText.innerHTML = "<div class='text-center'><i class='bi bi-hourglass-split me-2'></i>Calculating...</div>";
    calculationResult.style.display = 'block';

    // Get the entry date from the hidden input
    const entryDate = form.querySelector('input[name="entryDate"]').value;

    // Get CSRF token
    const csrfToken = document.querySelector('meta[name="_csrf"]');
    const headers = {'Content-Type': 'application/json'};
    if (csrfToken) {
        headers['X-CSRF-TOKEN'] = csrfToken.getAttribute('content');
    }

    // Fetch calculation from backend
    fetch('/user/session/calculate-resolution', {
        method: 'POST',
        headers: headers,
        body: JSON.stringify({
            entryDate: entryDate,
            endHour: parseInt(hourSelect.value),
            endMinute: parseInt(minuteSelect.value)
        })
    })
        .then(response => response.json())
        .then(data => {
        if (!data.success) {
            // Show error
            calculationText.innerHTML = `<i class='bi bi-exclamation-triangle text-danger'></i> ${data.errorMessage || 'End time must be after start time'}`;
            const calculationPanel = form.querySelector('.calculation-panel');
            if (calculationPanel) {
                calculationPanel.className = 'calculation-panel error';
            }
            return;
        }

        // Generate detailed calculation breakdown using server data
        let resultHTML = '<div class="calculation-breakdown">';

        // Total elapsed time
        resultHTML += `<div class="calculation-item">
            <i class="bi bi-stopwatch me-2 text-primary"></i>
            <strong>Total elapsed time:</strong> ${data.formattedTotalElapsed}
            <small class="text-muted">(from ${data.formattedStartTime} to ${data.formattedEndTime})</small>
        </div>`;

        // Temporary stops/breaks if any
        if (data.breakMinutes > 0) {
            resultHTML += `<div class="calculation-item">
                <i class="bi bi-dash-circle me-2 text-warning"></i>
                <strong>Temporary stops:</strong> ${data.formattedBreakTime}
            </div>`;
        }

        // Lunch break deduction if applicable
        if (data.lunchDeducted) {
            resultHTML += `<div class="calculation-item">
                <i class="bi bi-cup-hot me-2 text-warning"></i>
                <strong>Lunch break:</strong> ${formatMinutes(data.lunchBreakMinutes)}
                <small class="text-muted">(for 8-hour schedule when work time is between 4-11 hours)</small>
            </div>`;
        }

        // Result line with total work time
        resultHTML += `<div class="calculation-item total">
            <i class="bi bi-check-circle me-2 text-success"></i>
            <strong>Net work time:</strong> <span class="h5 mb-0 ms-2 total-time">${data.formattedNetWorkTime}</span>
            <small class="text-muted ms-2">(${data.netWorkMinutes} minutes)</small>
        </div>`;

        // Add overtime information if applicable
        if (data.overtimeMinutes > 0) {
            resultHTML += `<div class="calculation-item overtime">
                <i class="bi bi-clock-history me-2 text-danger"></i>
                <strong>Includes overtime:</strong> ${data.formattedOvertimeMinutes}
            </div>`;
        }

        resultHTML += '</div>';

        // Set the HTML and show the result panel
        calculationText.innerHTML = resultHTML;
        const calculationPanel = form.querySelector('.calculation-panel');
        if (calculationPanel) {
            calculationPanel.className = 'calculation-panel';
        }

        // Add animation to highlight results
        calculationResult.classList.remove('highlight-result');
        void calculationResult.offsetWidth; // Force reflow
        calculationResult.classList.add('highlight-result');
    })
        .catch(error => {
        console.error('Error calculating work time:', error);
        calculationText.innerHTML = "<i class='bi bi-exclamation-triangle text-danger'></i> Error calculating work time";
        const calculationPanel = form.querySelector('.calculation-panel');
        if (calculationPanel) {
            calculationPanel.className = 'calculation-panel error';
        }
    });
}

/**
 * Format minutes as hours and minutes (HH:MM)
 * @param {number} minutes - Total minutes to format
 * @returns {string} - Formatted time string
 */
function formatMinutes(minutes) {
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    return `${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}`;
}