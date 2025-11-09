/**
 * Toast Notification Component - Unified notification system
 *
 * This component consolidates two previous toast/alert systems:
 * - legacy/toast-alerts.js (321 lines) - Full toast system
 * - legacy/default.js (28 lines) - Simple Bootstrap alerts
 *
 * @module components/ToastNotification
 * @version 1.0.0
 * @since 2025-11-04
 *
 * ⚠️ IMPORTANT: This is the ONLY place for toast notifications.
 * Do NOT create duplicate toast systems.
 *
 * Features:
 * - Auto-dismiss with configurable timeout
 * - Progress bar animation
 * - Multiple toast types (success, error, warning, info)
 * - Position control (top-right, top-center, bottom-right, etc.)
 * - Queue management for multiple toasts
 * - Persistent toasts (manual dismiss only)
 * - Bootstrap 5 compatible
 *
 * Usage:
 *   import { ToastNotification } from './components/ToastNotification.js';
 *
 *   // Simple success toast
 *   ToastNotification.success('Success!', 'Operation completed successfully');
 *
 *   // Error toast with custom duration
 *   ToastNotification.error('Error!', 'Something went wrong', { duration: 8000 });
 *
 *   // Persistent toast (won't auto-dismiss)
 *   ToastNotification.warning('Warning!', 'This requires your attention', { persistent: true });
 */

/**
 * Toast Notification Service
 * All methods are static - no instantiation needed
 */
export class ToastNotification {
    // Private static properties
    static #container = null;
    static #toasts = new Map();
    static #counter = 0;
    static #initialized = false;

    // Default configuration
    static #config = {
        position: 'top-end',        // Bootstrap positioning: top-start, top-center, top-end, bottom-start, etc.
        duration: 5000,              // Auto-dismiss duration in ms
        maxToasts: 5,                // Maximum concurrent toasts
        animation: true,             // Enable animations
        closeButton: true            // Show close button
    };

    // =========================================================================
    // INITIALIZATION
    // =========================================================================

    /**
     * Initialize the toast system
     * Creates container if it doesn't exist
     * Called automatically on first toast
     */
    static #init() {
        if (this.#initialized) return;

        this.#createContainer();
        this.#initialized = true;

        console.log('✅ ToastNotification initialized');
    }

    /**
     * Create the toast container
     * @private
     */
    static #createContainer() {
        if (this.#container) return;

        const container = document.createElement('div');
        container.id = 'toast-notification-container';
        container.className = `toast-container position-fixed ${this.#config.position} p-3`;
        container.setAttribute('style', 'z-index: 9999;');

        document.body.appendChild(container);
        this.#container = container;
    }

    /**
     * Configure toast system
     * @param {Object} options - Configuration options
     */
    static configure(options = {}) {
        this.#config = { ...this.#config, ...options };

        // Update container position if already created
        if (this.#container) {
            this.#container.className = `toast-container position-fixed ${this.#config.position} p-3`;
        }
    }

    // =========================================================================
    // PUBLIC API - CONVENIENCE METHODS
    // =========================================================================

    /**
     * Show success toast
     * @param {string} title - Toast title
     * @param {string} message - Toast message
     * @param {Object} options - Additional options
     * @returns {string} Toast ID
     */
    static success(title, message, options = {}) {
        return this.show(title, message, 'success', options);
    }

    /**
     * Show error toast
     * @param {string} title - Toast title
     * @param {string} message - Toast message
     * @param {Object} options - Additional options
     * @returns {string} Toast ID
     */
    static error(title, message, options = {}) {
        return this.show(title, message, 'error', options);
    }

    /**
     * Show warning toast
     * @param {string} title - Toast title
     * @param {string} message - Toast message
     * @param {Object} options - Additional options
     * @returns {string} Toast ID
     */
    static warning(title, message, options = {}) {
        return this.show(title, message, 'warning', options);
    }

    /**
     * Show info toast
     * @param {string} title - Toast title
     * @param {string} message - Toast message
     * @param {Object} options - Additional options
     * @returns {string} Toast ID
     */
    static info(title, message, options = {}) {
        return this.show(title, message, 'info', options);
    }

    /**
     * Show special unresolved session toast with action buttons
     * Replaces the orange floating card for better consistency
     * @param {number} count - Number of unresolved sessions
     * @param {Function} onResolve - Callback when "Resolve Now" is clicked
     * @returns {string} Toast ID
     */
    static showUnresolvedSessions(count, onResolve) {
        // Initialize if needed
        if (!this.#initialized) {
            this.#init();
        }

        // Generate unique ID
        const toastId = `toast-unresolved-${Date.now()}`;

        // Create custom toast element for unresolved sessions
        const toast = document.createElement('div');
        toast.id = toastId;
        toast.className = 'toast align-items-center border-0 toast-unresolved';
        toast.setAttribute('role', 'alert');
        toast.setAttribute('aria-live', 'assertive');
        toast.setAttribute('aria-atomic', 'true');

        toast.innerHTML = `
            <div class="d-flex" style="background-color: #ff9800; border-radius: 0.5rem;">
                <div class="toast-body text-white">
                    <div class="d-flex align-items-start mb-2">
                        <i class="bi bi-exclamation-triangle-fill me-2 fs-5"></i>
                        <div class="flex-grow-1">
                            <strong class="d-block mb-1">Action Required</strong>
                            <div>You have <strong>${count}</strong> unresolved work session(s) that need your attention.</div>
                        </div>
                    </div>
                    <div class="d-flex gap-2 mt-2">
                        <button class="btn btn-sm btn-light" onclick="window.scrollToUnresolved()">
                            <i class="bi bi-arrow-down me-1"></i>Resolve Now
                        </button>
                    </div>
                </div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
            </div>
        `;

        // Add to container
        this.#container.appendChild(toast);

        // Store reference (persistent toast)
        this.#toasts.set(toastId, {
            element: toast,
            type: 'unresolved',
            persistent: true,
            timeoutId: null,
            createdAt: Date.now()
        });

        // Initialize Bootstrap toast (persistent, no auto-hide)
        const bsToast = new bootstrap.Toast(toast, {
            autohide: false
        });

        // Show toast
        bsToast.show();

        // Cleanup on hide
        toast.addEventListener('hidden.bs.toast', () => {
            this.#removeToast(toastId);
        });

        // Store dismissal preference
        toast.addEventListener('hidden.bs.toast', () => {
            sessionStorage.setItem('unresolvedToastDismissed', 'true');
        });

        return toastId;
    }

    // =========================================================================
    // CORE FUNCTIONALITY
    // =========================================================================

    /**
     * Show a toast notification (main method)
     *
     * @param {string} title - Toast title
     * @param {string} message - Toast message
     * @param {string} type - Toast type: 'success', 'error', 'warning', 'info'
     * @param {Object} options - Additional options
     * @param {boolean} options.persistent - If true, won't auto-dismiss
     * @param {number} options.duration - Custom duration in ms
     * @param {string} options.icon - Custom Bootstrap icon class
     * @param {boolean} options.closeButton - Show close button
     * @returns {string} Toast ID
     *
     * @example
     *   ToastNotification.show('Success', 'Saved!', 'success');
     *   ToastNotification.show('Error', 'Failed!', 'error', { persistent: true });
     */
    static show(title, message, type = 'info', options = {}) {
        // Initialize if needed
        if (!this.#initialized) {
            this.#init();
        }

        // Validate type
        const validTypes = ['success', 'error', 'warning', 'info'];
        if (!validTypes.includes(type)) {
            console.warn(`Invalid toast type: ${type}, defaulting to 'info'`);
            type = 'info';
        }

        // Merge options with defaults
        const settings = {
            persistent: false,
            duration: this.#config.duration,
            icon: this.#getIconForType(type),
            closeButton: this.#config.closeButton,
            ...options
        };

        // Check max toasts limit
        if (this.#toasts.size >= this.#config.maxToasts) {
            // Remove oldest non-persistent toast
            this.#removeOldestToast();
        }

        // Generate unique ID
        const toastId = `toast-${Date.now()}-${this.#counter++}`;

        // Create toast element
        const toastElement = this.#createToastElement(toastId, title, message, type, settings);

        // Add to container
        this.#container.appendChild(toastElement);

        // Store reference
        this.#toasts.set(toastId, {
            element: toastElement,
            type: type,
            persistent: settings.persistent,
            timeoutId: null,
            createdAt: Date.now()
        });

        // Initialize Bootstrap toast
        const bsToast = new bootstrap.Toast(toastElement, {
            autohide: !settings.persistent,
            delay: settings.duration
        });

        // Show toast
        bsToast.show();

        // Setup auto-dismiss for non-persistent toasts
        if (!settings.persistent) {
            const timeoutId = setTimeout(() => {
                this.hide(toastId);
            }, settings.duration);

            const toast = this.#toasts.get(toastId);
            if (toast) {
                toast.timeoutId = timeoutId;
            }
        }

        // Cleanup on hide
        toastElement.addEventListener('hidden.bs.toast', () => {
            this.#removeToast(toastId);
        });

        return toastId;
    }

    /**
     * Hide a specific toast
     * @param {string} toastId - Toast ID to hide
     */
    static hide(toastId) {
        const toast = this.#toasts.get(toastId);
        if (!toast) return;

        // Clear timeout if exists
        if (toast.timeoutId) {
            clearTimeout(toast.timeoutId);
        }

        // Hide using Bootstrap
        const bsToast = bootstrap.Toast.getInstance(toast.element);
        if (bsToast) {
            bsToast.hide();
        } else {
            // Fallback if Bootstrap instance not found
            this.#removeToast(toastId);
        }
    }

    /**
     * Hide all toasts
     * @param {boolean} includePersistent - Whether to hide persistent toasts
     */
    static hideAll(includePersistent = false) {
        this.#toasts.forEach((toast, toastId) => {
            if (includePersistent || !toast.persistent) {
                this.hide(toastId);
            }
        });
    }

    /**
     * Get count of active toasts
     * @returns {number} Number of active toasts
     */
    static getCount() {
        return this.#toasts.size;
    }

    /**
     * Check if a specific toast is still active
     * @param {string} toastId - Toast ID
     * @returns {boolean} True if toast exists
     */
    static exists(toastId) {
        return this.#toasts.has(toastId);
    }

    // =========================================================================
    // PRIVATE HELPER METHODS
    // =========================================================================

    /**
     * Create toast DOM element
     * @private
     */
    static #createToastElement(toastId, title, message, type, settings) {
        const toast = document.createElement('div');
        toast.id = toastId;
        toast.className = `toast align-items-center border-0 toast-${type}`;
        toast.setAttribute('role', 'alert');
        toast.setAttribute('aria-live', 'assertive');
        toast.setAttribute('aria-atomic', 'true');

        toast.innerHTML = `
            <div class="d-flex">
                <div class="toast-body">
                    <div class="d-flex align-items-start">
                        <i class="bi ${settings.icon} me-2 fs-5"></i>
                        <div class="flex-grow-1">
                            <strong class="d-block mb-1">${this.#escapeHtml(title)}</strong>
                            <div>${this.#escapeHtml(message)}</div>
                        </div>
                    </div>
                </div>
                ${settings.closeButton ? `
                    <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
                ` : ''}
            </div>
        `;

        return toast;
    }

    /**
     * Remove oldest toast to make room for new one
     * @private
     */
    static #removeOldestToast() {
        let oldestId = null;
        let oldestTime = Infinity;

        // Find oldest non-persistent toast
        this.#toasts.forEach((toast, toastId) => {
            if (!toast.persistent && toast.createdAt < oldestTime) {
                oldestTime = toast.createdAt;
                oldestId = toastId;
            }
        });

        // Remove it
        if (oldestId) {
            this.hide(oldestId);
        }
    }

    /**
     * Remove toast from DOM and tracking
     * @private
     */
    static #removeToast(toastId) {
        const toast = this.#toasts.get(toastId);
        if (!toast) return;

        // Remove from DOM
        if (toast.element && toast.element.parentNode) {
            toast.element.parentNode.removeChild(toast.element);
        }

        // Remove from tracking
        this.#toasts.delete(toastId);
    }

    /**
     * Get icon class for toast type
     * @private
     */
    static #getIconForType(type) {
        const icons = {
            'success': 'bi-check-circle-fill',
            'error': 'bi-exclamation-circle-fill',
            'warning': 'bi-exclamation-triangle-fill',
            'info': 'bi-info-circle-fill'
        };
        return icons[type] || icons.info;
    }

    /**
     * Get background class for toast type
     * @private
     */
    static #getBgClassForType(type) {
        const bgClasses = {
            'success': 'bg-success',
            'error': 'bg-danger',
            'warning': 'bg-warning',
            'info': 'bg-info'
        };
        return bgClasses[type] || bgClasses.info;
    }

    /**
     * Get text class for toast type
     * @private
     */
    static #getTextClassForType(type) {
        // Warning uses dark text, others use white
        return type === 'warning' ? 'text-dark' : 'text-white';
    }

    /**
     * Escape HTML to prevent XSS
     * @private
     */
    static #escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // =========================================================================
    // SERVER-SIDE ALERT PROCESSING
    // =========================================================================

    /**
     * Process server-side alerts from page
     * Converts static alerts to toasts
     * Call this after page load if using Thymeleaf model attributes
     *
     * UPDATED: Now uses data attributes that survive Thymeleaf processing
     * Expects elements with: data-alert-type and data-alert-message attributes
     */
    static processServerAlerts() {
        // Check if user already dismissed unresolved toast in this session
        const unresolvedToastDismissed = sessionStorage.getItem('unresolvedToastDismissed') === 'true';

        // Check if unresolved card exists in HTML (from Thymeleaf)
        const unresolvedCard = document.getElementById('unresolvedCard');
        if (unresolvedCard && !unresolvedToastDismissed) {
            // Extract count from card
            const countElement = unresolvedCard.querySelector('strong');
            const count = countElement ? parseInt(countElement.textContent) : 1;

            // Show special unresolved toast instead of the card
            this.showUnresolvedSessions(count);

            // Hide/remove the HTML card
            unresolvedCard.style.display = 'none';

            console.log(`🔔 Showing special unresolved session toast (${count} sessions)`);
        }

        // NEW: Process data-attribute based alerts (modern approach)
        const dataAlerts = document.querySelectorAll('[data-alert-message]');
        dataAlerts.forEach(el => {
            const message = el.getAttribute('data-alert-message');
            const type = el.getAttribute('data-alert-type') || 'info';
            const title = el.getAttribute('data-alert-title') || this.#capitalize(type);

            if (message && message.trim()) {
                // SPECIAL HANDLING: Skip "unresolved" warning messages - handled by special toast above
                if (message.toLowerCase().includes('unresolved')) {
                    console.log('⏭️ Skipping unresolved warning message - special toast already shown');
                    this.#hideParentAlert(el);
                    return;
                }

                // Show toast based on type
                switch (type.toLowerCase()) {
                    case 'success':
                        this.success(title, message);
                        break;
                    case 'error':
                    case 'danger':
                        this.error(title, message);
                        break;
                    case 'warning':
                        this.warning(title, message);
                        break;
                    case 'info':
                        this.info(title, message);
                        break;
                    default:
                        this.info(title, message);
                }

                // Hide the source element (if it's a visible alert)
                this.#hideParentAlert(el);
            }
        });

        // LEGACY: Support old Bootstrap alert divs (for backward compatibility)
        // Look for .alert divs with text content (EXCLUDING those inside modals)
        const legacyAlerts = document.querySelectorAll('.alert:not([data-alert-message])');
        legacyAlerts.forEach(el => {
            // Skip alerts inside modals - they're intentional UI elements, not server messages
            if (el.closest('.modal')) {
                return;
            }

            const text = el.textContent.trim();
            if (!text) return;

            // Determine type from Bootstrap classes
            let type = 'info';
            let title = 'Notice';
            if (el.classList.contains('alert-success')) {
                type = 'success';
                title = 'Success';
            } else if (el.classList.contains('alert-danger')) {
                type = 'error';
                title = 'Error';
            } else if (el.classList.contains('alert-warning')) {
                type = 'warning';
                title = 'Warning';
            }

            // Show toast and hide alert
            this[type](title, text);
            el.style.display = 'none';
        });

        // Check URL parameters for errors
        const urlParams = new URLSearchParams(window.location.search);
        const errorParam = urlParams.get('error');
        if (errorParam) {
            const errorMessage = this.#getErrorMessageForParam(errorParam);
            this.warning('Warning', errorMessage);

            // Clean up URL
            if (window.history.replaceState) {
                const cleanUrl = window.location.pathname + window.location.hash;
                window.history.replaceState({}, document.title, cleanUrl);
            }
        }
    }

    /**
     * Hide parent alert element
     * @private
     */
    static #hideParentAlert(element) {
        let current = element;
        while (current && current !== document.body) {
            if (current.classList && current.classList.contains('alert')) {
                current.style.display = 'none';
                return;
            }
            current = current.parentElement;
        }
    }

    /**
     * Get error message for URL parameter
     * @private
     */
    static #getErrorMessageForParam(errorParam) {
        const errorMessages = {
            'date_required': 'Please select a date before adding.',
            'missing_date': 'Please select a date.',
            'missing_oms_id': 'OMS ID is required.',
            'missing_designer_name': 'Designer name is required.',
            'missing_check_type': 'Check Type is required.',
            'missing_article_numbers': 'Article numbers are required.',
            'missing_file_numbers': 'File numbers are required.',
            'missing_approval_status': 'Approval status is required.',
            'add_failed': 'Failed to add entry. Please try again.',
            'update_failed': 'Failed to update entry. Please try again.',
            'delete_failed': 'Failed to delete entry. Please try again.'
        };

        return errorMessages[errorParam] || 'An unexpected error occurred.';
    }

    /**
     * Capitalize first letter of string
     * @private
     */
    static #capitalize(str) {
        if (!str) return '';
        return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
    }
}

// Export as default for convenience
export default ToastNotification;
