/**
 * Toast Alert System
 * A complete replacement for both static alerts and toasts
 * Shows notifications in the top right corner of the screen
 */

// Guard against double initialization
if (typeof window.ToastAlertSystem === 'undefined') {

    class ToastAlertSystem {
        constructor() {
            this.toastContainer = document.getElementById('toastAlertContainer');
            this.toastCounter = 0;
            this.toasts = new Map(); // Store references to active toasts

            // Initialize the system
            this.init();
        }

        /**
         * Initialize the toast alert system
         */
        init() {
            // Create container if it doesn't exist
            if (!this.toastContainer) {
                this.createToastContainer();
            }

            // Make methods available globally
            window.showToast = this.showToast.bind(this);
            window.hideToast = this.hideToast.bind(this);
            window.hideAllToasts = this.hideAllToasts.bind(this);

            // Process server-side alerts on page load
            this.processServerSideAlerts();

            console.log('Toast Alert System initialized');
        }

        /**
         * Create the toast container if it doesn't exist
         */
        createToastContainer() {
            const container = document.createElement('div');
            container.id = 'toastAlertContainer';
            container.className = 'toast-alert-container position-fixed top-0 end-0 p-3';
            document.body.appendChild(container);
            this.toastContainer = container;
        }

        /**
         * Process server-side alerts and convert them to toasts
         * This handles the existing model attributes for compatibility
         */
        processServerSideAlerts() {
            // Convert success messages
            const successMessage = document.querySelector('[th\\:text="${successMessage}"]');
            if (successMessage && successMessage.textContent) {
                this.showToast('Success', successMessage.textContent, 'success');
                // Hide the original element
                const parentAlert = this.findParentAlert(successMessage);
                if (parentAlert) parentAlert.style.display = 'none';
            }

            // Convert error messages
            const errorMessage = document.querySelector('[th\\:text="${errorMessage}"]');
            if (errorMessage && errorMessage.textContent) {
                this.showToast('Error', errorMessage.textContent, 'error');
                // Hide the original element
                const parentAlert = this.findParentAlert(errorMessage);
                if (parentAlert) parentAlert.style.display = 'none';
            }

            // Convert period errors
            const periodError = document.querySelector('[th\\:text="${periodError}"]');
            if (periodError && periodError.textContent) {
                this.showToast('Period Error', periodError.textContent, 'error');
                // Hide the original element
                const parentAlert = this.findParentAlert(periodError);
                if (parentAlert) parentAlert.style.display = 'none';
            }

            // Check URL for error parameters
            const urlParams = new URLSearchParams(window.location.search);
            const errorParam = urlParams.get('error');
            if (errorParam) {
                let errorMessage = 'An unexpected error occurred.';

                // Map error codes to messages (similar to your existing system)
                switch (errorParam) {
                    case 'date_required':
                        errorMessage = 'Please select a date before adding.';
                        break;
                    case 'missing_date':
                        errorMessage = 'Please select a date.';
                        break;
                    case 'missing_oms_id':
                        errorMessage = 'OMS ID is required.';
                        break;
                    case 'missing_designer_name':
                        errorMessage = 'Designer name is required.';
                        break;
                    case 'missing_check_type':
                        errorMessage = 'Check Type is required.';
                        break;
                    case 'missing_article_numbers':
                        errorMessage = 'Article numbers are required.';
                        break;
                    case 'missing_file_numbers':
                        errorMessage = 'File numbers are required.';
                        break;
                    case 'missing_approval_status':
                        errorMessage = 'Approval status is required.';
                        break;
                    case 'add_failed':
                        errorMessage = 'Failed to add entry. Please try again.';
                        break;
                    case 'update_failed':
                        errorMessage = 'Failed to update entry. Please try again.';
                        break;
                    case 'delete_failed':
                        errorMessage = 'Failed to delete entry. Please try again.';
                        break;
                }

                this.showToast('Warning', errorMessage, 'warning');

                // Optional: Clean up the URL
                if (window.history.replaceState) {
                    const cleanUrl = window.location.pathname + window.location.hash;
                    window.history.replaceState({}, document.title, cleanUrl);
                }
            }
        }

        /**
         * Find the parent alert element of a given child
         */
        findParentAlert(element) {
            let current = element;
            while (current !== null) {
                if (current.classList && current.classList.contains('alert')) {
                    return current;
                }
                current = current.parentElement;
            }
            return null;
        }

        /**
         * Show a toast alert
         * @param {string} title - The toast title
         * @param {string} message - The toast message
         * @param {string} type - The toast type (success, error, warning, info)
         * @param {Object} options - Additional options
         * @param {boolean} options.persistent - If true, the toast won't auto-dismiss
         * @param {string} options.icon - Custom icon class (bi-*)
         * @param {number} options.duration - Duration in ms before auto-dismiss
         * @returns {string} - The ID of the created toast
         */
        showToast(title, message, type = 'info', options = {}) {
            const defaultOptions = {
                persistent: false,
                icon: null,
                duration: 5000
            };

            const settings = { ...defaultOptions, ...options };

            // Validate type
            const validTypes = ['success', 'error', 'warning', 'info'];
            if (!validTypes.includes(type)) {
                type = 'info';
            }

            // Determine icon based on type if not specified
            if (!settings.icon) {
                settings.icon = this.getIconForType(type);
            }

            // Generate unique ID
            const toastId = `toast-${Date.now()}-${this.toastCounter++}`;

            // Create toast element
            const toastHTML = `
                <div id="${toastId}" class="toast-alert toast-${type}">
                    <div class="toast-alert-header">
                        <div class="d-flex align-items-center">
                            <div class="toast-alert-icon">
                                <i class="bi ${settings.icon}"></i>
                            </div>
                            <h6 class="toast-alert-title">${title}</h6>
                        </div>
                        <button type="button" class="toast-alert-close" aria-label="Close">
                            <i class="bi bi-x"></i>
                        </button>
                    </div>
                    <div class="toast-alert-body">
                        <p class="toast-alert-message">${message}</p>
                    </div>
                    <div class="toast-alert-progress"></div>
                </div>
            `;

            // Add toast to container
            this.toastContainer.insertAdjacentHTML('beforeend', toastHTML);
            const toastElement = document.getElementById(toastId);

            // Store reference to toast
            this.toasts.set(toastId, {
                element: toastElement,
                timeoutId: null,
                type: type,
                persistent: settings.persistent
            });

            // Add close button event listener
            const closeButton = toastElement.querySelector('.toast-alert-close');
            if (closeButton) {
                closeButton.addEventListener('click', () => this.hideToast(toastId));
            }

            // Show toast (slight delay for animation)
            setTimeout(() => {
                toastElement.classList.add('showing');
            }, 10);

            // Auto-dismiss non-persistent toasts
            if (!settings.persistent) {
                // Add progress bar animation
                const progressBar = toastElement.querySelector('.toast-alert-progress');
                if (progressBar) {
                    progressBar.style.transition = `width ${settings.duration}ms linear`;
                    setTimeout(() => {
                        progressBar.style.width = '100%';
                    }, 10);
                }

                // Set timeout for dismissal
                const timeoutId = setTimeout(() => {
                    this.hideToast(toastId);
                }, settings.duration);

                // Store timeout ID
                const toast = this.toasts.get(toastId);
                if (toast) {
                    toast.timeoutId = timeoutId;
                    this.toasts.set(toastId, toast);
                }
            }

            return toastId;
        }

        /**
         * Hide a specific toast
         * @param {string} toastId - The ID of the toast to hide
         */
        hideToast(toastId) {
            const toast = this.toasts.get(toastId);
            if (!toast) return;

            const { element, timeoutId } = toast;

            // Clear any existing timeout
            if (timeoutId) {
                clearTimeout(timeoutId);
            }

            // Add hiding class for animation
            element.classList.remove('showing');
            element.classList.add('hiding');

            // Remove after animation completes
            setTimeout(() => {
                if (element && element.parentNode) {
                    element.parentNode.removeChild(element);
                }
                this.toasts.delete(toastId);
            }, 300);
        }

        /**
         * Hide all toasts
         * @param {boolean} includePersistent - Whether to also hide persistent toasts
         */
        hideAllToasts(includePersistent = true) {
            this.toasts.forEach((toast, toastId) => {
                if (includePersistent || !toast.persistent) {
                    this.hideToast(toastId);
                }
            });
        }

        /**
         * Get the appropriate icon for a toast type
         */
        getIconForType(type) {
            switch (type) {
                case 'success':
                    return 'bi-check-circle-fill';
                case 'error':
                    return 'bi-exclamation-circle-fill';
                case 'warning':
                    return 'bi-exclamation-triangle-fill';
                case 'info':
                default:
                    return 'bi-info-circle-fill';
            }
        }
    }

    // Add the class to the window for instanceof checks
    window.ToastAlertSystem = ToastAlertSystem;

    // Initialize the toast alert system when the DOM is loaded - with guard against multiple initializations
    document.addEventListener('DOMContentLoaded', function() {
        if (!window.toastAlertSystem) {
            window.toastAlertSystem = new ToastAlertSystem();
        }
    });
}