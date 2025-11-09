/**
 * SessionUI.js
 *
 * Manages UI components for the session page.
 * Handles tooltips, live clock, toast notifications, floating cards, and scroll functions.
 *
 * @module features/session/SessionUI
 */

/**
 * SessionUI class
 * Manages UI elements and interactions for session page
 */
export class SessionUI {
    constructor() {
        this.initTooltips();
        this.initLiveClock();
        this.initToastNotifications();
        this.initFloatingCard();
        this.checkFormSubmission();
        this.setupScrollFunctions();
        this.injectStyles();
    }

    /**
     * Initialize Bootstrap tooltips
     */
    initTooltips() {
        const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
        tooltipTriggerList.map(function (tooltipTriggerEl) {
            return new bootstrap.Tooltip(tooltipTriggerEl);
        });
    }

    /**
     * Initialize and update the live clock
     */
    initLiveClock() {
        const updateClock = () => {
            const timeDisplay = document.getElementById('live-clock');
            if (timeDisplay) {
                const now = new Date();
                const formattedTime = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
                timeDisplay.textContent = formattedTime;

                // Add subtle fade animation on minute change
                if (now.getSeconds() === 0) {
                    timeDisplay.classList.add('time-pulse');
                    setTimeout(() => timeDisplay.classList.remove('time-pulse'), 1000);
                }
            }
        };

        // Update clock every second
        setInterval(updateClock, 1000);
        updateClock(); // Initial call
    }

    /**
     * Check if a form was previously submitted and page reloaded
     */
    checkFormSubmission() {
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
     * Initialize toast notifications based on URL parameters and flash messages
     */
    initToastNotifications() {
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
                        // No toast notification for resume - handled by modal
                        break;
                }
            }

            // NOTE: Flash messages are already processed by ToastNotification.processServerAlerts()
            // in layout/default.html, so we don't need to process them again here
            // (this was causing duplicate toasts)

        } catch (error) {
            console.error('Error parsing session page data:', error);
        }
    }

    /**
     * Initialize floating card for unresolved entries
     */
    initFloatingCard() {
        // NOTE: Orange floating card now handled by ToastNotification.showUnresolvedSessions()
        // This function is kept for backward compatibility but does nothing
        // The card is converted to a special toast for consistency
    }

    /**
     * Setup scroll functions
     */
    setupScrollFunctions() {
        // Make scroll functions available globally
        window.scrollToResolution = () => this.scrollToResolution();
        window.scrollToUnresolved = () => this.scrollToUnresolved();
    }

    /**
     * Scroll to the unresolved tab
     */
    scrollToUnresolved() {
        const unresolvedTab = document.getElementById('unresolvedTab');
        if (unresolvedTab) {
            unresolvedTab.scrollIntoView({
                behavior: 'smooth',
                block: 'start'
            });
        } else {
            console.warn('Unresolved tab not found');
        }
    }

    /**
     * Scroll to the resolution container smoothly
     */
    scrollToResolution() {
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
     * Inject CSS styles for animations and effects
     */
    injectStyles() {
        const styles = `
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

        document.head.insertAdjacentHTML('beforeend', styles);
    }

    /**
     * Show resume confirmation modal
     */
    static showResumeModal() {
        const modalElement = document.getElementById('resumeConfirmationModal');
        if (modalElement) {
            const resumeModal = new bootstrap.Modal(modalElement);
            resumeModal.show();
        }
    }
}
