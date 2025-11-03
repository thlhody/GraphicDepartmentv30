document.addEventListener('DOMContentLoaded', function() {
    // Initialize tooltips
    initTooltips();

    // Initialize live clock
    initLiveClock();

    // Check for previously submitted forms
    checkFormSubmission();

    // Initialize end time scheduler
    initEndTimeScheduler();

    // Initialize end time checker
    initEndTimeChecker();

    // Handle URL parameters and flash messages with toast notifications
    initToastNotifications();

    // Initialize floating card if it exists
    initializeFloatingCard();

    console.log("Session page initialized with toast notifications");
});

// Initialize Bootstrap tooltips
function initTooltips() {
    const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    tooltipTriggerList.map(function (tooltipTriggerEl) {
        return new bootstrap.Tooltip(tooltipTriggerEl);
    });
}

// Initialize and update the live clock
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

// Check if a form was previously submitted and page reloaded
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

// Initialize toast notifications based on URL parameters and flash messages
function initToastNotifications() {
    // Get session page data from the script tag
    const sessionDataElement = document.getElementById('sessionPageData');
    if (!sessionDataElement) return;

    try {
        const sessionData = JSON.parse(sessionDataElement.textContent);

        // Handle URL action parameters
        if (sessionData.urlParams && sessionData.urlParams.action) {
            const action = sessionData.urlParams.action;

            switch (action) {
                case 'start':
                    window.showToast('Session Started', 'Work session started successfully', 'success');
                    break;
                case 'end':
                    window.showToast('Session Ended', 'Work session ended and recorded', 'info');
                    break;
                case 'pause':
                    window.showToast('Session Paused', 'Session paused - break time is being tracked', 'warning');
                    break;
                case 'resume':
                    // No toast notification for resume - handled by main notification system
                    break;
            }
        }

        // Handle flash messages
        if (sessionData.flashMessages) {
            if (sessionData.flashMessages.success) {
                window.showToast('Success', sessionData.flashMessages.success, 'success');
            }
            if (sessionData.flashMessages.error) {
                window.showToast('Error', sessionData.flashMessages.error, 'error');
            }
            if (sessionData.flashMessages.warning) {
                window.showToast('Warning', sessionData.flashMessages.warning, 'warning');
            }
        }

    } catch (error) {
        console.error('Error parsing session page data:', error);
    }
}

// Initializes the end time scheduler functionality with calculations
function initEndTimeScheduler() {
    const useRecommendedBtn = document.getElementById('useRecommendedTime');
    const endHourInput = document.getElementById('endHour');
    const endMinuteInput = document.getElementById('endMinute');

    // Early exit if elements don't exist
    if (!endHourInput || !endMinuteInput) return;

    const schedulerForm = endHourInput.closest('form');
    if (!schedulerForm) return;

    // Get the parent container of the scheduler
    const schedulerContainer = schedulerForm.closest('.stat-container');
    if (!schedulerContainer) return;

    // Create a flexible container that will hold the calculation preview
    let calculationContainer = schedulerContainer.querySelector('.end-time-calculation-result');
    if (!calculationContainer) {
        // Insert the calculation container after the heading but before the form
        calculationContainer = document.createElement('div');
        calculationContainer.className = 'end-time-calculation-result mt-2 mb-2';
        calculationContainer.style.display = 'none';

        // Find the title to insert after
        const titleElement = schedulerContainer.querySelector('.text-muted');
        if (titleElement && titleElement.nextSibling) {
            schedulerContainer.insertBefore(calculationContainer, titleElement.nextSibling);
        } else {
            // Fallback: add to the beginning of the container
            schedulerContainer.prepend(calculationContainer);
        }
    }

    // Fetch recommended end time for default values
    fetch('/user/session/recommended-end-time')
        .then(response => response.json())
        .then(data => {
        if (data.success && data.recommendedEndTime) {
            const [hours, minutes] = data.recommendedEndTime.split(':');
            endHourInput.value = hours;
            endMinuteInput.value = minutes;

            // Calculate work time with these values
            calculateEndTimeWorkTime();
        } else {
            // Fallback to 5 PM if recommendation fails
            const now = new Date();
            endHourInput.value = '17';
            endMinuteInput.value = '00';
        }
    })
        .catch(error => {
        console.error('Error fetching recommended end time:', error);
        // Fallback to 5 PM
        endHourInput.value = '17';
        endMinuteInput.value = '00';
    });

    // Add event listeners for hour/minute changes
    endHourInput.addEventListener('input', calculateEndTimeWorkTime);
    endMinuteInput.addEventListener('input', calculateEndTimeWorkTime);

    // Handle recommendation click
    if (useRecommendedBtn) {
        useRecommendedBtn.addEventListener('click', function(e) {
            e.preventDefault();

            // Fetch recommended end time from server
            fetch('/user/session/recommended-end-time')
                .then(response => response.json())
                .then(data => {
                if (data.success && data.recommendedEndTime) {
                    const [hours, minutes] = data.recommendedEndTime.split(':');
                    endHourInput.value = hours;
                    endMinuteInput.value = minutes;

                    // Calculate with the new values
                    calculateEndTimeWorkTime();

                    let messageText = 'Recommended end time loaded: ' + data.recommendedEndTime;
                    if (data.expectedEndTime) {
                        messageText += ' (Schedule end: ' + data.expectedEndTime + ')';
                    }

                    window.showToast('Recommended Time', messageText, 'info');
                } else {
                    window.showToast('Warning', data.message || 'Could not get recommended time', 'warning');
                }
            })
                .catch(error => {
                console.error('Error fetching recommended end time:', error);
                window.showToast('Error', 'Error fetching recommended end time', 'error');
            });
        });
    }

    // Initial calculation
    calculateEndTimeWorkTime();
}

// Calculates work time based on the end time inputs by fetching current session data from the server
function calculateEndTimeWorkTime() {
    const endHourInput = document.getElementById('endHour');
    const endMinuteInput = document.getElementById('endMinute');
    const calculationPreview = document.getElementById('endTimeCalculationPreview');
    const calculationContainer = document.querySelector('.end-time-calculation-result');

    if (!endHourInput || !endMinuteInput || !calculationPreview) return;

    // Clear previous calculation
    calculationPreview.innerHTML = '<div class="text-muted small">Calculating...</div>';

    // Get input values
    const endHour = parseInt(endHourInput.value);
    const endMinute = parseInt(endMinuteInput.value);

    // Validate inputs
    if (isNaN(endHour) || isNaN(endMinute)) {
        calculationPreview.innerHTML = '<div class="text-muted small">Select end time to see calculation</div>';
        // Also hide the old container if it exists
        if (calculationContainer) calculationContainer.style.display = 'none';
        return;
    }

    // Get CSRF token - fix the issue with null reference
    const csrfToken = document.querySelector('meta[name="_csrf"]');
    const headers = {
        'Content-Type': 'application/json'
    };

    // Only add CSRF token if it exists
    if (csrfToken) {
        headers['X-CSRF-TOKEN'] = csrfToken.getAttribute('content');
    }

    // Create a new endpoint to fetch session data and calculate end result
    fetch('/user/session/calculate-end-time', {
        method: 'POST',
        headers: headers,
        body: JSON.stringify({
            endHour: endHour,
            endMinute: endMinute
        })
    })
        .then(response => response.json())
        .then(data => {
        if (data.success) {
            // Generate calculation HTML using the formatted fields from the DTO
            let previewHTML = '<div class="calculation-details small">';

            // Total elapsed time
            previewHTML += `<div class="mb-1">
            <i class="bi bi-stopwatch me-1 text-muted"></i>
            <span>Total: ${data.formattedTotalElapsed || formatMinutes(data.totalElapsedMinutes)}</span>
        </div>`;

            // Temporary stops/breaks if any
            if (data.breakMinutes > 0) {
                previewHTML += `<div class="mb-1">
                <i class="bi bi-dash-circle me-1 text-warning"></i>
                <span>Breaks: ${data.formattedBreakTime || formatMinutes(data.breakMinutes)}</span>
            </div>`;
            }

            // Lunch break deduction if applicable
            if (data.lunchDeducted) {
                previewHTML += `<div class="mb-1">
                <i class="bi bi-cup-hot me-1 text-warning"></i>
                <span>Lunch: ${formatMinutes(data.lunchBreakMinutes)}</span>
            </div>`;
            }

            // Net work time
            previewHTML += `<div class="mb-1">
            <i class="bi bi-check-circle me-1 text-success"></i>
            <strong>Net: ${data.formattedNetWorkTime || formatMinutes(data.netWorkMinutes)}</strong>
        </div>`;

            // Add overtime information if applicable
            if (data.overtimeMinutes > 0) {
                previewHTML += `<div class="mb-0">
                <i class="bi bi-clock-history me-1 text-danger"></i>
                <span>OT: ${data.formattedOvertimeMinutes || formatMinutes(data.overtimeMinutes)}</span>
            </div>`;
            }

            previewHTML += '</div>';

            // Set HTML and animate
            calculationPreview.innerHTML = previewHTML;
            calculationPreview.classList.remove('highlight-result');
            void calculationPreview.offsetWidth;
            calculationPreview.classList.add('highlight-result');
        } else {
            // Error handling
            calculationPreview.innerHTML = `<div class="alert alert-warning py-1 px-2 mb-0">
            <i class="bi bi-exclamation-triangle-fill me-1"></i> ${data.message || 'Error calculating work time'}
        </div>`;
        }
    })
        .catch(error => {
        console.error('Error calculating end time work time:', error);
        calculationPreview.innerHTML = '<div class="alert alert-danger py-1 px-2 mb-0"><i class="bi bi-exclamation-triangle-fill me-1"></i> Error calculating work time</div>';

        // Hide the old container if it exists
        if (calculationContainer) calculationContainer.style.display = 'none';
    });
}

// Sets up a direct timer to refresh the page at the scheduled end time
function initEndTimeChecker() {
    // Don't continue if we're not on the session page
    if (!document.getElementById('endHour')) return;

    // Check if there's a scheduled end time displayed on the page
    const scheduledEndBadge = document.querySelector('.badge[data-scheduled-end]');
    if (!scheduledEndBadge) return;

    // Extract the scheduled time text from the badge
    const scheduledTimeText = scheduledEndBadge.textContent;
    const timeMatch = scheduledTimeText.match(/(\d{1,2}):(\d{2})/);

    if (!timeMatch) return;

    const hours = parseInt(timeMatch[1], 10);
    const minutes = parseInt(timeMatch[2], 10);

    // Create date object for the scheduled end time
    const scheduledDate = new Date();
    scheduledDate.setHours(hours, minutes, 0, 0);

    // If time is in the past, ignore
    const now = new Date();
    if (scheduledDate <= now) return;

    // Calculate milliseconds until scheduled time
    const timeUntilEnd = scheduledDate - now;

    console.log(`Page refresh scheduled for ${scheduledDate.toLocaleTimeString()}, in ${Math.round(timeUntilEnd/1000)} seconds`);

    // Set a direct timeout to refresh the page at the exact time
    setTimeout(function() {
        console.log('Scheduled end time reached, refreshing page...');
        // Add a small delay to ensure the backend has time to process
        setTimeout(function() {
            window.location.reload();
        }, 3000);
    }, timeUntilEnd + 500); // Add 500ms to ensure we're past the time
}

/**
 * Scroll to the resolution container smoothly
 */
function scrollToResolution() {
    const resolutionContainer = document.getElementById('workTimeResolutionContainer');
    if (resolutionContainer) {
        resolutionContainer.scrollIntoView({
            behavior: 'smooth',
            block: 'center'
        });

        // Add a subtle highlight effect
        resolutionContainer.classList.add('highlight-container');
        setTimeout(() => {
            resolutionContainer.classList.remove('highlight-container');
        }, 2000);

        // Show a toast notification
        if (window.showToast) {
            window.showToast('Navigation', 'Scrolled to unresolved entries section', 'info', {
                duration: 2000
            });
        }
    } else {
        console.warn('Resolution container not found');
        if (window.showToast) {
            window.showToast('Error', 'Could not find resolution section', 'error');
        }
    }
}

/**
 * Dismiss the floating card with animation - Updated for left slide
 */
function dismissCard() {
    const card = document.getElementById('unresolvedCard');
    if (card) {
        // Add slide-out animation to the left
        card.style.animation = 'slideOutLeft 0.5s ease-in forwards';

        // Remove from DOM after animation
        setTimeout(() => {
            card.remove();
        }, 500);

        // Store dismissal in session storage to prevent showing again
        sessionStorage.setItem('unresolvedCardDismissed', 'true');

        // Show toast notification
        if (window.showToast) {
            window.showToast('Dismissed', 'Reminder dismissed. You can still resolve entries below.', 'info', {
                duration: 3000
            });
        }
    }
}

/**
 * Auto-dismiss the card after the progress bar completes (15 seconds)
 */
function initializeFloatingCard() {
    const card = document.getElementById('unresolvedCard');
    if (card) {
        // Check if card was previously dismissed
        const wasDismissed = sessionStorage.getItem('unresolvedCardDismissed');
        if (wasDismissed === 'true') {
            card.remove();
            return;
        }

        // Auto-dismiss after 15 seconds (when progress bar completes)
        setTimeout(() => {
            if (card && card.parentNode) {
                dismissCard();
            }
        }, 15000);

        // Add hover events to pause/resume progress bar
        const progressBar = card.querySelector('.progress-bar');
        if (progressBar) {
            card.addEventListener('mouseenter', () => {
                progressBar.style.animationPlayState = 'paused';
            });

            card.addEventListener('mouseleave', () => {
                progressBar.style.animationPlayState = 'running';
            });
        }
    }
}

// Add these styles for the highlight effect
const highlightStyles = `
<style>
.highlight-container {
    animation: highlightPulse 2s ease-in-out;
}

@keyframes highlightPulse {
    0%, 100% {
        background-color: transparent;
        box-shadow: none;
    }
    50% {
        background-color: rgba(255, 193, 7, 0.1);
        box-shadow: 0 0 20px rgba(255, 193, 7, 0.3);
    }
}
</style>
`;

document.head.insertAdjacentHTML('beforeend', highlightStyles);

// Helper function to format minutes as HH:MM
window.formatMinutes = function(minutes) {
    if (minutes === undefined || minutes === null) return "00:00";
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    return `${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}`;
};