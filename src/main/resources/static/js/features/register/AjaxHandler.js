/**
 * AjaxHandler.js
 * Handles AJAX form submissions and deletions without page reload
 *
 * Features:
 * - Intercept form submissions and use fetch API
 * - Loading overlay during requests
 * - Toast notifications for success/error messages
 * - Automatic table reload after successful operation
 * - Delete confirmations
 * - Reattach event handlers to new DOM elements
 *
 * @module features/register/AjaxHandler
 */

import { API } from '../../core/api.js';
import { ToastNotification } from '../../components/ToastNotification.js';

/**
 * AjaxHandler - AJAX form submission and deletion handler
 */
export class AjaxHandler {

    /**
     * Create an AjaxHandler instance
     * @param {RegisterForm} registerForm - RegisterForm instance
     * @param {RegisterSummary} registerSummary - RegisterSummary instance
     */
    constructor(registerForm, registerSummary) {
        this.registerForm = registerForm;
        this.registerSummary = registerSummary;
        // ToastNotification uses static methods - no instantiation needed

        this.setupFormSubmissions();
        this.setupDeleteButtons();
    }

    /**
     * Setup form submission handler to use AJAX
     * @private
     */
    setupFormSubmissions() {
        const form = document.getElementById('registerForm');
        if (!form) {
            console.error('❌ AjaxHandler: registerForm not found');
            return;
        }

        console.log('✓ AjaxHandler: Form submission handler attached');

        // Override the form's submit handler
        form.addEventListener('submit', (e) => {
            e.preventDefault();
            console.log('Form submit intercepted - validating...');

            // Validate form
            const isValid = this.registerForm.validateForm();
            console.log('Validation result:', isValid);

            if (!isValid) {
                console.log('❌ Validation failed - submission blocked');
                return;
            }

            console.log('✓ Validation passed - submitting via AJAX');
            // Submit via AJAX
            this.submitFormViaAjax(form);
        });
    }

    /**
     * Setup delete button handlers
     * @private
     */
    setupDeleteButtons() {
        // Use event delegation for dynamically created delete buttons
        document.addEventListener('submit', (e) => {
            const form = e.target;

            // Check if this is a delete form
            if (form.matches('form[action*="/user/register/delete"]')) {
                e.preventDefault();

                // Note: Confirmation is handled by HTML onclick="return confirm(...)"
                // No need to confirm again here
                this.submitFormViaAjax(form);
            }
        });
    }

    /**
     * Submit form via AJAX
     * @param {HTMLFormElement} form - Form element to submit
     * @private
     */
    async submitFormViaAjax(form) {
        const formData = new FormData(form);
        const action = form.getAttribute('action');
        const method = form.getAttribute('method') || 'POST';

        // Debug: Log all FormData entries
        console.log('📋 FormData being submitted to:', action);
        console.log('📋 FormData contents:');
        for (let [key, value] of formData.entries()) {
            console.log(`  ${key}: ${value}`);
        }

        // Convert FormData to URLSearchParams for application/x-www-form-urlencoded
        // Spring Boot @RequestParam expects this format, not multipart/form-data
        const urlEncodedData = new URLSearchParams();
        for (let [key, value] of formData.entries()) {
            urlEncodedData.append(key, value);
        }

        this.showLoading();

        try {
            // Use fetch directly with URLSearchParams
            const response = await fetch(action, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: urlEncodedData
            });

            if (response.redirected) {
                // Handle redirect
                const redirectUrl = response.url;

                if (redirectUrl.includes('error=')) {
                    // Error redirect
                    this.handleErrorRedirect(redirectUrl);
                } else {
                    // Success redirect
                    await this.handleSuccessfulSubmission(form, await response.text());
                }
            } else {
                // Direct response
                const responseHtml = await response.text();
                await this.handleSuccessfulSubmission(form, responseHtml);
            }

        } catch (error) {
            console.error('AJAX submission error:', error);
            ToastNotification.error('Submission Failed', error.message || 'An error occurred while submitting the form.');
        } finally {
            this.hideLoading();
        }
    }

    /**
     * Handle successful form submission
     * @param {HTMLFormElement} form - Submitted form
     * @param {string} responseHtml - HTML response from server
     * @private
     */
    async handleSuccessfulSubmission(form, responseHtml) {
        // Parse response to check for success messages
        const parser = new DOMParser();
        const doc = parser.parseFromString(responseHtml, 'text/html');

        // Look for success alert
        const successAlert = doc.querySelector('.alert-success');
        const errorAlert = doc.querySelector('.alert-danger');

        if (successAlert) {
            const message = successAlert.textContent.trim();
            ToastNotification.success('Success', message);

            // Reset form if this was a create/edit (not delete)
            if (!form.getAttribute('action').includes('delete')) {
                this.registerForm.resetForm();
            }

            // Reload entries
            await this.reloadEntries();

        } else if (errorAlert) {
            const message = errorAlert.textContent.trim();
            ToastNotification.error('Error', message);
        } else {
            // No specific alert found, show generic success
            ToastNotification.success('Success', 'Operation completed successfully.');

            // Reset form if this was a create/edit
            if (!form.getAttribute('action').includes('delete')) {
                this.registerForm.resetForm();
            }

            // Reload entries
            await this.reloadEntries();
        }
    }

    /**
     * Handle error redirect
     * @param {string} redirectUrl - Redirect URL with error parameters
     * @private
     */
    handleErrorRedirect(redirectUrl) {
        const url = new URL(redirectUrl);
        const errorMessage = url.searchParams.get('error') || 'An error occurred';

        ToastNotification.error('Validation Error', decodeURIComponent(errorMessage));
    }

    /**
     * Reload entries from server and update table
     * @private
     */
    async reloadEntries() {
        try {
            const currentUrl = window.location.pathname + window.location.search;

            // Use fetch directly to get raw response
            const response = await fetch(currentUrl);

            if (response.ok) {
                const html = await response.text();
                const parser = new DOMParser();
                const doc = parser.parseFromString(html, 'text/html');

                // Get the new table body
                const newTableBody = doc.querySelector('.table tbody');
                const currentTableBody = document.querySelector('.table tbody');

                if (newTableBody && currentTableBody) {
                    // Replace table content
                    currentTableBody.innerHTML = newTableBody.innerHTML;

                    // Reattach event handlers to new buttons
                    this.reattachEventHandlers();

                    // Recalculate summary statistics
                    if (this.registerSummary) {
                        this.registerSummary.calculateStats();
                    }

                    console.log('✅ Entries reloaded successfully');
                } else {
                    console.warn('⚠️ Could not find table body in response');
                }
            } else {
                console.error('❌ Failed to reload entries:', response.status, response.statusText);
            }
        } catch (error) {
            console.error('❌ Error reloading entries:', error);
            ToastNotification.warning('Reload Notice', 'Please refresh the page to see updated entries.');
        }
    }

    /**
     * Reattach event handlers to dynamically created elements
     * @private
     */
    reattachEventHandlers() {
        // Edit buttons - use correct class name from HTML
        const editButtons = document.querySelectorAll('.edit-entry');
        editButtons.forEach(button => {
            button.addEventListener('click', (e) => {
                e.preventDefault();
                this.registerForm.populateForm(button);
            });
        });

        // Copy buttons - use correct class name from HTML
        const copyButtons = document.querySelectorAll('.copy-entry');
        copyButtons.forEach(button => {
            button.addEventListener('click', (e) => {
                e.preventDefault();
                this.registerForm.copyEntry(button);
            });
        });

        console.log(`Reattached handlers to ${editButtons.length} edit and ${copyButtons.length} copy buttons`);
    }

    /**
     * Show loading overlay
     * @private
     */
    showLoading() {
        // Remove existing overlay if any
        this.hideLoading();

        const overlay = document.createElement('div');
        overlay.id = 'loadingOverlay';
        overlay.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.5);
            display: flex;
            justify-content: center;
            align-items: center;
            z-index: 9999;
        `;

        overlay.innerHTML = `
            <div class="text-center">
                <div class="spinner-border text-light" role="status" style="width: 3rem; height: 3rem;">
                    <span class="visually-hidden">Loading...</span>
                </div>
                <div class="text-light mt-3">Processing...</div>
            </div>
        `;

        document.body.appendChild(overlay);
    }

    /**
     * Hide loading overlay
     * @private
     */
    hideLoading() {
        const overlay = document.getElementById('loadingOverlay');
        if (overlay) {
            overlay.style.opacity = '0';
            overlay.style.transition = 'opacity 0.2s';

            setTimeout(() => {
                overlay.remove();
            }, 200);
        }
    }

    /**
     * Fix form validation states (cleanup utility)
     * @public
     */
    fixFormValidationStates() {
        const forms = document.querySelectorAll('form');

        forms.forEach(form => {
            // Remove all validation classes
            form.querySelectorAll('.is-invalid').forEach(el => {
                el.classList.remove('is-invalid');
            });

            form.querySelectorAll('.invalid-feedback').forEach(el => {
                el.remove();
            });
        });
    }
}
