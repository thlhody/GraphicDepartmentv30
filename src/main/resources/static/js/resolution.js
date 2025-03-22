/**
 * Work Time Resolution Script
 * Handles the interactive functionality for resolving unfinished work sessions
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
                showTemporaryAlert('warning', 'Please select both hour and minute', 3000);
                return;
            }

            // Show processing message
            showTemporaryAlert('info', 'Processing your resolution request...', 3000);

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
 * Calculate work time based on form inputs with detailed breakdown
 * @param {HTMLFormElement} form - The form containing time inputs
 */
function calculateWorkTime(form) {
    const hourSelect = form.querySelector('select[name="endHour"]');
    const minuteSelect = form.querySelector('select[name="endMinute"]');
    const calculationResult = form.querySelector('.calculation-result');
    const calculationText = form.querySelector('.calculation-text');

    if (!hourSelect.value || !minuteSelect.value) return;

    // Get start time from the form
    const startTimeText = form.querySelector('.info-item:first-child span').textContent.trim();
    const [startHour, startMinute] = startTimeText.split(':').map(Number);

    // Get break minutes
    let breakMinutes = 0;
    const breakItem = form.querySelector('.info-item:nth-child(2)');
    if (breakItem) {
        const breakText = breakItem.textContent;
        const hourMatch = breakText.match(/(\d+)h/);
        const minuteMatch = breakText.match(/(\d+)m/);

        if (hourMatch) breakMinutes += parseInt(hourMatch[1]) * 60;
        if (minuteMatch) breakMinutes += parseInt(minuteMatch[1]);
    }

    // Calculate times in minutes
    const startMinutesTotal = startHour * 60 + startMinute;
    const endMinutesTotal = parseInt(hourSelect.value) * 60 + parseInt(minuteSelect.value);

    // Validate times
    if (endMinutesTotal <= startMinutesTotal) {
        calculationText.innerHTML = "<i class='bi bi-exclamation-triangle text-danger'></i> End time must be after start time";
        const calculationPanel = form.querySelector('.calculation-panel');
        if (calculationPanel) {
            calculationPanel.className = 'calculation-panel error';
        }
        calculationResult.style.display = 'block';
        return;
    }

    // Calculate total elapsed time
    const totalElapsedMinutes = endMinutesTotal - startMinutesTotal;

    // Calculate work minutes after deducting temporary stops
    const workMinutesAfterStops = totalElapsedMinutes - breakMinutes;

    // Constants - could be made configurable
    const scheduleHours = 8; // Default schedule hours - this would ideally come from user settings
    const scheduleMinutes = scheduleHours * 60;
    const lunchBreakMinutes = 30; // Standard lunch break
    const minLunchThreshold = 4 * 60; // 4 hours in minutes - minimum for lunch deduction
    const maxLunchThreshold = 12 * 60; // 12 hours in minutes - maximum for lunch deduction

    // Decide if lunch break should be deducted based on work time AFTER temporary stops
    const shouldDeductLunch = scheduleHours === 8 &&
    workMinutesAfterStops >= minLunchThreshold &&
    workMinutesAfterStops <= maxLunchThreshold;

    // Calculate final work minutes
    const workMinutes = shouldDeductLunch ? workMinutesAfterStops - lunchBreakMinutes : workMinutesAfterStops;

    // Calculate overtime
    const overtimeMinutes = Math.max(0, workMinutes - scheduleMinutes);

    // Generate detailed calculation breakdown
    let resultHTML = '<div class="calculation-breakdown">';

    // Total elapsed time
    resultHTML += `<div class="calculation-item">
        <i class="bi bi-stopwatch me-2 text-primary"></i>
        <strong>Total elapsed time:</strong> ${formatMinutes(totalElapsedMinutes)}
        <small class="text-muted">(from ${startTimeText} to ${hourSelect.value.padStart(2, '0')}:${minuteSelect.value.padStart(2, '0')})</small>
    </div>`;

    // Temporary stops/breaks if any
    if (breakMinutes > 0) {
        resultHTML += `<div class="calculation-item">
            <i class="bi bi-dash-circle me-2 text-warning"></i>
            <strong>Temporary stops:</strong> ${formatMinutes(breakMinutes)}
        </div>`;
    }

    // Lunch break deduction if applicable
    if (shouldDeductLunch) {
        resultHTML += `<div class="calculation-item">
            <i class="bi bi-cup-hot me-2 text-warning"></i>
            <strong>Lunch break:</strong> ${formatMinutes(lunchBreakMinutes)}
            <small class="text-muted">(for 8-hour schedule when work time is between 4-12 hours)</small>
        </div>`;
    }

    // Result line with total work time
    resultHTML += `<div class="calculation-item total">
        <i class="bi bi-check-circle me-2 text-success"></i>
        <strong>Net work time:</strong> <span class="h5 mb-0 ms-2 total-time">${formatMinutes(workMinutes)}</span>
        <small class="text-muted ms-2">(${workMinutes} minutes)</small>
    </div>`;

    // Add overtime information if applicable
    if (overtimeMinutes > 0) {
        resultHTML += `<div class="calculation-item overtime">
            <i class="bi bi-clock-history me-2 text-danger"></i>
            <strong>Includes overtime:</strong> ${formatMinutes(overtimeMinutes)}
        </div>`;
    }

    resultHTML += '</div>';

    // Set the HTML and show the result panel
    calculationText.innerHTML = resultHTML;
    const calculationPanel = form.querySelector('.calculation-panel');
    if (calculationPanel) {
        calculationPanel.className = 'calculation-panel';
    }
    calculationResult.style.display = 'block';

    // Add a subtle animation to highlight the results
    calculationResult.classList.remove('highlight-result');
    void calculationResult.offsetWidth; // Force reflow to restart animation
    calculationResult.classList.add('highlight-result');

    // Ensure it remains visible
    setTimeout(() => {
        if (calculationResult) calculationResult.style.display = 'block';
    }, 100);
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