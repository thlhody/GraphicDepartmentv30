/**
 * AboutManager.js
 *
 * Manages about modal functionality.
 * Features: auto-show modal on page load, logo easter egg for logs access,
 * and notification preview system for testing.
 *
 * @module features/about/AboutManager
 */

import { API } from '../../core/api.js';

/**
 * AboutManager class
 * Handles about page modal and notification previews
 */
export class AboutManager {
    constructor() {
        this.aboutModal = null;
        this.modalInstance = null;

        console.log('AboutManager initialized');
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize about manager
     */
    initialize() {
        console.log('🚀 Initializing About Manager...');

        // Setup modal (only if Bootstrap is available and modal exists)
        if (typeof bootstrap !== 'undefined') {
            this.setupModal();
        } else {
            console.log('Bootstrap not loaded - skipping modal setup (about page mode)');
        }

        // Setup logo easter egg
        this.setupLogoShortcut();

        // Setup notification preview buttons
        this.setupNotificationPreviews();

        console.log('✅ About Manager initialized successfully');
    }

    // ========================================================================
    // MODAL HANDLING
    // ========================================================================

    /**
     * Setup and auto-show about modal
     */
    setupModal() {
        this.aboutModal = document.getElementById('aboutModal');

        if (this.aboutModal) {
            // Create Bootstrap modal instance
            this.modalInstance = new bootstrap.Modal(this.aboutModal);

            // Auto-show modal on page load
            this.modalInstance.show();

            console.log('About modal auto-shown');
        } else {
            console.warn('About modal element not found');
        }
    }

    /**
     * Show about modal programmatically
     */
    showModal() {
        if (this.modalInstance) {
            this.modalInstance.show();
        } else if (this.aboutModal) {
            this.modalInstance = new bootstrap.Modal(this.aboutModal);
            this.modalInstance.show();
        }
    }

    /**
     * Hide about modal programmatically
     */
    hideModal() {
        if (this.modalInstance) {
            this.modalInstance.hide();
        }
    }

    // ========================================================================
    // LOGO EASTER EGG
    // ========================================================================

    /**
     * Setup Ctrl+Click logo easter egg to access logs
     */
    setupLogoShortcut() {
        const logoImage = document.getElementById('logo-image');

        if (logoImage) {
            logoImage.addEventListener('click', (event) => {
                // Ctrl+Click to access logs
                if (event.ctrlKey) {
                    console.log('Logo easter egg activated - navigating to logs');
                    window.location.href = '/logs';
                    event.preventDefault();
                }
            });

            // Add visual hint (optional - can be removed if not desired)
            logoImage.style.cursor = 'pointer';
            logoImage.title = 'Ctrl+Click to access logs';

            console.log('Logo easter egg setup complete');
        }
    }

    // ========================================================================
    // NOTIFICATION PREVIEWS
    // ========================================================================

    /**
     * Setup notification preview buttons
     */
    setupNotificationPreviews() {
        const notificationButtons = document.querySelectorAll('.notification-btn');

        notificationButtons.forEach(button => {
            button.addEventListener('click', () => {
                const type = button.getAttribute('data-type');
                this.triggerNotificationPreview(type, button);
            });
        });

        console.log(`Setup ${notificationButtons.length} notification preview buttons`);
    }

    /**
     * Trigger notification preview
     * @param {string} type - Notification type
     * @param {HTMLElement} button - Button element
     */
    async triggerNotificationPreview(type, button) {
        const originalButtonHtml = button.innerHTML;

        try {
            // Show loading state
            button.disabled = true;
            button.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Sending...';

            // Make API request using API class (handles CSRF automatically)
            const formData = new FormData();
            formData.append('type', type);

            const data = await API.postForm('/about/trigger-mockup', formData);

            // Show success feedback
            if (data.success) {
                button.innerHTML = '<i class="bi bi-check-circle me-2"></i>Sent!';
                button.classList.add('btn-success');
                button.classList.remove('btn-outline-primary');

                console.log(`Notification preview sent: ${type}`);

                // Show toast if available
                if (typeof window.showToast === 'function') {
                    window.showToast('Preview Sent', `${type} notification preview triggered`, 'success');
                }
            } else {
                throw new Error(data.message || 'Failed to send notification');
            }

            // Reset button after delay
            setTimeout(() => {
                button.innerHTML = originalButtonHtml;
                button.classList.remove('btn-success');
                button.classList.add('btn-outline-primary');
                button.disabled = false;
            }, 2000);

        } catch (error) {
            console.error('Error triggering notification preview:', error);

            // Show error feedback
            button.innerHTML = '<i class="bi bi-exclamation-triangle me-2"></i>Error';
            button.classList.add('btn-danger');
            button.classList.remove('btn-outline-primary');

            // Show toast if available
            if (typeof window.showToast === 'function') {
                window.showToast('Error', 'Failed to send notification preview', 'error');
            }

            // Reset button after delay
            setTimeout(() => {
                button.innerHTML = originalButtonHtml;
                button.classList.remove('btn-danger');
                button.classList.add('btn-outline-primary');
                button.disabled = false;
            }, 2000);
        }
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Get modal instance
     * @returns {bootstrap.Modal|null} Modal instance
     */
    getModalInstance() {
        return this.modalInstance;
    }

    /**
     * Check if modal is visible
     * @returns {boolean} True if modal is shown
     */
    isModalVisible() {
        if (!this.aboutModal) return false;
        return this.aboutModal.classList.contains('show');
    }

    /**
     * Cleanup and destroy
     */
    destroy() {
        if (this.modalInstance) {
            this.modalInstance.dispose();
            this.modalInstance = null;
        }
        console.log('AboutManager destroyed');
    }
}
