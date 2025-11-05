/**
 * Modal - Bootstrap 5 modal wrapper
 *
 * This component provides a clean API for working with Bootstrap 5 modals,
 * including creating dynamic modals, confirmation dialogs, and managing
 * modal state. It consolidates modal patterns from multiple legacy files.
 *
 * @module components/Modal
 * @version 1.0.0
 * @since 2025-11-05
 *
 * Features:
 * - Programmatic modal creation
 * - Confirmation dialogs
 * - Loading/progress modals
 * - Dynamic content updates
 * - Event callbacks
 * - Keyboard shortcuts
 * - Size variants
 * - Centered positioning
 * - Backdrop control
 * - Promise-based confirmations
 *
 * Usage:
 *   import { Modal } from './components/Modal.js';
 *
 *   // Existing modal
 *   const modal = new Modal('#myModal');
 *   modal.show();
 *
 *   // Create dynamic modal
 *   const confirmModal = Modal.confirm({
 *       title: 'Delete Item',
 *       message: 'Are you sure you want to delete this item?',
 *       onConfirm: () => console.log('Confirmed')
 *   });
 */

/**
 * Modal class
 * Wraps Bootstrap 5 modal functionality
 */
export class Modal {
    /**
     * Default configuration
     * @private
     */
    static #defaultConfig = {
        backdrop: true,                // true, false, or 'static'
        keyboard: true,                // Close on Escape key
        focus: true,                   // Focus modal when shown
        size: null,                    // null, 'sm', 'lg', 'xl'
        centered: false,               // Vertically center modal
        scrollable: false,             // Make modal body scrollable
        onShow: null,                  // Callback before show
        onShown: null,                 // Callback after show
        onHide: null,                  // Callback before hide
        onHidden: null                 // Callback after hidden
    };

    /**
     * Create Modal instance
     * @param {string|HTMLElement} selector - Modal selector or element
     * @param {Object} config - Configuration options
     */
    constructor(selector, config = {}) {
        // Get modal element
        this.element = typeof selector === 'string'
            ? document.querySelector(selector)
            : selector;

        if (!this.element) {
            throw new Error(`Modal element not found: ${selector}`);
        }

        // Merge config
        this.config = { ...Modal.#defaultConfig, ...config };

        // Initialize Bootstrap modal
        this.#initBootstrapModal();

        // Set up event listeners
        this.#setupEventListeners();
    }

    // =========================================================================
    // INITIALIZATION
    // =========================================================================

    /**
     * Initialize Bootstrap modal
     * @private
     */
    #initBootstrapModal() {
        // Check if Bootstrap is available
        if (typeof bootstrap === 'undefined' || !bootstrap.Modal) {
            throw new Error('Bootstrap 5 is required for Modal component');
        }

        // Get or create Bootstrap modal instance
        this.modal = bootstrap.Modal.getInstance(this.element);

        if (!this.modal) {
            this.modal = new bootstrap.Modal(this.element, {
                backdrop: this.config.backdrop,
                keyboard: this.config.keyboard,
                focus: this.config.focus
            });
        }
    }

    /**
     * Set up event listeners
     * @private
     */
    #setupEventListeners() {
        // Before show
        this.element.addEventListener('show.bs.modal', () => {
            if (this.config.onShow) {
                this.config.onShow(this);
            }
        });

        // After shown
        this.element.addEventListener('shown.bs.modal', () => {
            if (this.config.onShown) {
                this.config.onShown(this);
            }
        });

        // Before hide
        this.element.addEventListener('hide.bs.modal', () => {
            if (this.config.onHide) {
                this.config.onHide(this);
            }
        });

        // After hidden
        this.element.addEventListener('hidden.bs.modal', () => {
            if (this.config.onHidden) {
                this.config.onHidden(this);
            }
        });
    }

    // =========================================================================
    // MODAL CONTROL
    // =========================================================================

    /**
     * Show modal
     */
    show() {
        this.modal.show();
        return this;
    }

    /**
     * Hide modal
     */
    hide() {
        this.modal.hide();
        return this;
    }

    /**
     * Toggle modal
     */
    toggle() {
        this.modal.toggle();
        return this;
    }

    /**
     * Dispose modal and remove from DOM
     */
    dispose() {
        this.modal.dispose();
        if (this.element.parentNode) {
            this.element.parentNode.removeChild(this.element);
        }
    }

    // =========================================================================
    // CONTENT MANAGEMENT
    // =========================================================================

    /**
     * Set modal title
     * @param {string} title - Modal title
     */
    setTitle(title) {
        const titleElement = this.element.querySelector('.modal-title');
        if (titleElement) {
            titleElement.textContent = title;
        }
        return this;
    }

    /**
     * Set modal body content
     * @param {string|HTMLElement} content - Content (HTML string or element)
     */
    setBody(content) {
        const bodyElement = this.element.querySelector('.modal-body');
        if (bodyElement) {
            if (typeof content === 'string') {
                bodyElement.innerHTML = content;
            } else if (content instanceof HTMLElement) {
                bodyElement.innerHTML = '';
                bodyElement.appendChild(content);
            }
        }
        return this;
    }

    /**
     * Set modal footer content
     * @param {string|HTMLElement} content - Content (HTML string or element)
     */
    setFooter(content) {
        const footerElement = this.element.querySelector('.modal-footer');
        if (footerElement) {
            if (typeof content === 'string') {
                footerElement.innerHTML = content;
            } else if (content instanceof HTMLElement) {
                footerElement.innerHTML = '';
                footerElement.appendChild(content);
            }
        }
        return this;
    }

    /**
     * Get modal body element
     * @returns {HTMLElement} Body element
     */
    getBody() {
        return this.element.querySelector('.modal-body');
    }

    /**
     * Get modal footer element
     * @returns {HTMLElement} Footer element
     */
    getFooter() {
        return this.element.querySelector('.modal-footer');
    }

    // =========================================================================
    // STATE
    // =========================================================================

    /**
     * Check if modal is visible
     * @returns {boolean} True if visible
     */
    isVisible() {
        return this.element.classList.contains('show');
    }

    /**
     * Enable/disable modal backdrop
     * @param {boolean|string} backdrop - true, false, or 'static'
     */
    setBackdrop(backdrop) {
        this.config.backdrop = backdrop;
        // Note: Bootstrap doesn't support changing backdrop dynamically
        // This will take effect on next show
        return this;
    }

    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================

    /**
     * Create a dynamic modal
     * @param {Object} options - Modal options
     * @returns {Modal} Modal instance
     *
     * @example
     *   const modal = Modal.create({
     *       title: 'My Modal',
     *       body: 'Modal content here',
     *       size: 'lg',
     *       buttons: [
     *           { text: 'Close', className: 'btn-secondary', onClick: (modal) => modal.hide() }
     *       ]
     *   });
     */
    static create(options = {}) {
        const {
            title = 'Modal',
            body = '',
            footer = null,
            buttons = null,
            size = null,
            centered = false,
            scrollable = false,
            closeButton = true,
            ...config
        } = options;

        // Create modal HTML
        const modalId = `modal-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

        const modalHTML = `
            <div class="modal fade" id="${modalId}" tabindex="-1" aria-labelledby="${modalId}Label" aria-hidden="true">
                <div class="modal-dialog ${size ? `modal-${size}` : ''} ${centered ? 'modal-dialog-centered' : ''} ${scrollable ? 'modal-dialog-scrollable' : ''}">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h5 class="modal-title" id="${modalId}Label">${title}</h5>
                            ${closeButton ? '<button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>' : ''}
                        </div>
                        <div class="modal-body">
                            ${typeof body === 'string' ? body : ''}
                        </div>
                        ${footer !== null || buttons !== null ? '<div class="modal-footer"></div>' : ''}
                    </div>
                </div>
            </div>
        `;

        // Add to document
        const temp = document.createElement('div');
        temp.innerHTML = modalHTML.trim();
        const modalElement = temp.firstChild;
        document.body.appendChild(modalElement);

        // Create Modal instance
        const modal = new Modal(modalElement, config);

        // Set body if it's an element
        if (body instanceof HTMLElement) {
            modal.setBody(body);
        }

        // Set footer or buttons
        if (footer !== null) {
            modal.setFooter(footer);
        } else if (buttons && Array.isArray(buttons)) {
            const footerElement = modal.getFooter();
            if (footerElement) {
                buttons.forEach(btnConfig => {
                    const button = document.createElement('button');
                    button.type = 'button';
                    button.className = `btn ${btnConfig.className || 'btn-primary'}`;
                    button.textContent = btnConfig.text || 'Button';

                    if (btnConfig.dismiss) {
                        button.setAttribute('data-bs-dismiss', 'modal');
                    }

                    if (btnConfig.onClick) {
                        button.addEventListener('click', () => btnConfig.onClick(modal));
                    }

                    footerElement.appendChild(button);
                });
            }
        }

        // Auto-dispose on hidden
        modal.element.addEventListener('hidden.bs.modal', () => {
            setTimeout(() => modal.dispose(), 300);
        });

        return modal;
    }

    /**
     * Create a confirmation dialog
     * @param {Object} options - Confirmation options
     * @returns {Promise<boolean>} Promise that resolves with user's choice
     *
     * @example
     *   const confirmed = await Modal.confirm({
     *       title: 'Delete Item',
     *       message: 'Are you sure you want to delete this item?',
     *       confirmText: 'Delete',
     *       confirmClass: 'btn-danger'
     *   });
     */
    static confirm(options = {}) {
        const {
            title = 'Confirm',
            message = 'Are you sure?',
            confirmText = 'Confirm',
            cancelText = 'Cancel',
            confirmClass = 'btn-primary',
            cancelClass = 'btn-secondary',
            icon = null,
            size = null,
            onConfirm = null,
            onCancel = null
        } = options;

        return new Promise((resolve) => {
            // Build message with icon
            let bodyHTML = '';
            if (icon) {
                bodyHTML += `<div class="text-center mb-3"><i class="bi ${icon}" style="font-size: 3rem;"></i></div>`;
            }
            bodyHTML += `<p class="mb-0">${message}</p>`;

            // Create modal
            const modal = Modal.create({
                title,
                body: bodyHTML,
                size,
                centered: true,
                buttons: [
                    {
                        text: cancelText,
                        className: cancelClass,
                        dismiss: true,
                        onClick: () => {
                            if (onCancel) onCancel();
                            resolve(false);
                        }
                    },
                    {
                        text: confirmText,
                        className: confirmClass,
                        onClick: () => {
                            if (onConfirm) onConfirm();
                            modal.hide();
                            resolve(true);
                        }
                    }
                ]
            });

            // Resolve false if dismissed with Escape or backdrop click
            modal.element.addEventListener('hidden.bs.modal', () => {
                resolve(false);
            }, { once: true });

            modal.show();
        });
    }

    /**
     * Create an alert dialog
     * @param {Object} options - Alert options
     * @returns {Promise<void>} Promise that resolves when closed
     *
     * @example
     *   await Modal.alert({
     *       title: 'Success',
     *       message: 'Operation completed successfully',
     *       icon: 'bi-check-circle-fill text-success'
     *   });
     */
    static async alert(options = {}) {
        const {
            title = 'Alert',
            message = '',
            buttonText = 'OK',
            buttonClass = 'btn-primary',
            icon = null,
            size = null
        } = options;

        return new Promise((resolve) => {
            // Build message with icon
            let bodyHTML = '';
            if (icon) {
                bodyHTML += `<div class="text-center mb-3"><i class="bi ${icon}" style="font-size: 3rem;"></i></div>`;
            }
            bodyHTML += `<p class="mb-0">${message}</p>`;

            const modal = Modal.create({
                title,
                body: bodyHTML,
                size,
                centered: true,
                buttons: [
                    {
                        text: buttonText,
                        className: buttonClass,
                        onClick: () => {
                            modal.hide();
                            resolve();
                        }
                    }
                ]
            });

            modal.show();
        });
    }

    /**
     * Create a loading/progress modal
     * @param {Object} options - Loading options
     * @returns {Modal} Modal instance
     *
     * @example
     *   const loader = Modal.loading({ message: 'Processing...' });
     *   // Do work...
     *   loader.hide();
     */
    static loading(options = {}) {
        const {
            title = 'Please wait',
            message = 'Loading...',
            spinner = true
        } = options;

        let bodyHTML = '<div class="text-center">';
        if (spinner) {
            bodyHTML += `
                <div class="spinner-border text-primary mb-3" role="status">
                    <span class="visually-hidden">Loading...</span>
                </div>
            `;
        }
        bodyHTML += `<p class="mb-0">${message}</p></div>`;

        const modal = Modal.create({
            title,
            body: bodyHTML,
            centered: true,
            backdrop: 'static',
            keyboard: false,
            closeButton: false
        });

        modal.show();

        // Add method to update message
        modal.updateMessage = (newMessage) => {
            const messageElement = modal.getBody().querySelector('p');
            if (messageElement) {
                messageElement.textContent = newMessage;
            }
        };

        return modal;
    }

    /**
     * Create a prompt dialog
     * @param {Object} options - Prompt options
     * @returns {Promise<string|null>} Promise that resolves with input value or null
     *
     * @example
     *   const value = await Modal.prompt({
     *       title: 'Enter Name',
     *       message: 'Please enter your name:',
     *       defaultValue: 'John'
     *   });
     */
    static prompt(options = {}) {
        const {
            title = 'Input',
            message = 'Please enter a value:',
            defaultValue = '',
            inputType = 'text',
            placeholder = '',
            confirmText = 'OK',
            cancelText = 'Cancel'
        } = options;

        return new Promise((resolve) => {
            const inputId = `prompt-input-${Date.now()}`;

            const bodyHTML = `
                <div>
                    <p>${message}</p>
                    <input type="${inputType}"
                           class="form-control"
                           id="${inputId}"
                           value="${defaultValue}"
                           placeholder="${placeholder}">
                </div>
            `;

            const modal = Modal.create({
                title,
                body: bodyHTML,
                centered: true,
                buttons: [
                    {
                        text: cancelText,
                        className: 'btn-secondary',
                        dismiss: true,
                        onClick: () => resolve(null)
                    },
                    {
                        text: confirmText,
                        className: 'btn-primary',
                        onClick: () => {
                            const input = document.getElementById(inputId);
                            const value = input ? input.value : null;
                            modal.hide();
                            resolve(value);
                        }
                    }
                ]
            });

            modal.show();

            // Focus input after modal shown
            modal.element.addEventListener('shown.bs.modal', () => {
                const input = document.getElementById(inputId);
                if (input) {
                    input.focus();
                    input.select();
                }
            }, { once: true });

            // Submit on Enter key
            modal.element.addEventListener('shown.bs.modal', () => {
                const input = document.getElementById(inputId);
                if (input) {
                    input.addEventListener('keypress', (e) => {
                        if (e.key === 'Enter') {
                            const value = input.value;
                            modal.hide();
                            resolve(value);
                        }
                    });
                }
            }, { once: true });
        });
    }
}

/**
 * Export as default for convenience
 */
export default Modal;
