/**
 * FormHandler - Base class for form handling
 *
 * This component provides common form functionality including validation,
 * submission, error handling, and reset capabilities. It consolidates
 * form handling patterns from multiple legacy files.
 *
 * @module components/FormHandler
 * @version 1.0.0
 * @since 2025-11-05
 *
 * Features:
 * - HTML5 validation support
 * - Custom validation rules
 * - Bootstrap 5 validation classes
 * - AJAX submission via core/api.js
 * - Error display and clearing
 * - Loading states
 * - Form reset
 * - FormData collection
 * - Success/error callbacks
 *
 * Usage:
 *   import { FormHandler } from './components/FormHandler.js';
 *
 *   const form = new FormHandler('#myForm', {
 *       url: '/api/submit',
 *       method: 'POST',
 *       onSuccess: (response) => console.log('Success:', response),
 *       onError: (error) => console.log('Error:', error)
 *   });
 */

import { API } from '../core/api.js';
import { ToastNotification } from './ToastNotification.js';

/**
 * FormHandler class
 * Manages form validation, submission, and error handling
 */
export class FormHandler {
    /**
     * Default configuration
     * @private
     */
    static #defaultConfig = {
        url: null,                      // Submission URL (required for AJAX)
        method: 'POST',                 // HTTP method
        useAjax: true,                  // Use AJAX submission (vs standard form POST)
        validateOnSubmit: true,         // Validate before submission
        validateOnBlur: false,          // Validate individual fields on blur
        clearOnSuccess: false,          // Clear form after successful submission
        showToastOnSuccess: true,       // Show success toast notification
        showToastOnError: true,         // Show error toast notification
        successMessage: 'Form submitted successfully',
        errorMessage: 'Form submission failed',
        loadingText: 'Submitting...',
        submitButton: null,             // Submit button selector (auto-detected if null)
        customValidation: null,         // Custom validation function: (formData) => { field: 'error message' }
        onSuccess: null,                // Success callback: (response, form) => {}
        onError: null,                  // Error callback: (error, form) => {}
        onValidationError: null,        // Validation error callback: (errors, form) => {}
        transformData: null             // Transform FormData before submission: (formData) => modifiedData
    };

    /**
     * Create FormHandler instance
     * @param {string|HTMLFormElement} formSelector - Form selector or element
     * @param {Object} config - Configuration options
     */
    constructor(formSelector, config = {}) {
        // Get form element
        this.form = typeof formSelector === 'string'
            ? document.querySelector(formSelector)
            : formSelector;

        if (!this.form) {
            throw new Error(`Form not found: ${formSelector}`);
        }

        if (this.form.tagName !== 'FORM') {
            throw new Error('Element must be a <form> tag');
        }

        // Merge config
        this.config = { ...FormHandler.#defaultConfig, ...config };

        // State
        this.isSubmitting = false;
        this.submitButton = null;
        this.originalButtonText = '';

        // Initialize
        this.#init();
    }

    // =========================================================================
    // INITIALIZATION
    // =========================================================================

    /**
     * Initialize form handler
     * @private
     */
    #init() {
        // Find submit button
        this.submitButton = this.config.submitButton
            ? this.form.querySelector(this.config.submitButton)
            : this.form.querySelector('button[type="submit"]');

        if (this.submitButton) {
            this.originalButtonText = this.submitButton.innerHTML;
        }

        // Attach submit handler
        this.form.addEventListener('submit', (e) => this.#handleSubmit(e));

        // Attach blur validation if enabled
        if (this.config.validateOnBlur) {
            this.form.addEventListener('blur', (e) => {
                if (e.target.matches('input, select, textarea')) {
                    this.#validateField(e.target);
                }
            }, true);
        }

        // Mark as initialized
        this.form.dataset.formHandlerInitialized = 'true';
    }

    // =========================================================================
    // SUBMISSION
    // =========================================================================

    /**
     * Handle form submission
     * @private
     */
    async #handleSubmit(event) {
        event.preventDefault();

        // Prevent double submission
        if (this.isSubmitting) {
            return false;
        }

        // Clear previous errors
        this.clearErrors();

        // Validate if enabled
        if (this.config.validateOnSubmit) {
            const isValid = this.validate();
            if (!isValid) {
                return false;
            }
        }

        // Collect form data
        const formData = new FormData(this.form);

        // Transform data if needed
        const data = this.config.transformData
            ? this.config.transformData(formData)
            : formData;

        // Submit
        if (this.config.useAjax) {
            await this.#submitAjax(data);
        } else {
            this.form.submit();
        }

        return false;
    }

    /**
     * Submit form via AJAX
     * @private
     */
    async #submitAjax(data) {
        if (!this.config.url) {
            throw new Error('Form URL is required for AJAX submission');
        }

        this.#setLoadingState(true);

        try {
            // Determine if data is FormData or JSON
            const isFormData = data instanceof FormData;

            // Make API call
            const response = isFormData
                ? await API.postForm(this.config.url, data)
                : await API[this.config.method.toLowerCase()](this.config.url, data);

            // Handle success
            this.#handleSuccess(response);

        } catch (error) {
            // Handle error
            this.#handleError(error);
        } finally {
            this.#setLoadingState(false);
        }
    }

    /**
     * Handle successful submission
     * @private
     */
    #handleSuccess(response) {
        // Show toast
        if (this.config.showToastOnSuccess) {
            ToastNotification.success('Success', this.config.successMessage);
        }

        // Clear form if enabled
        if (this.config.clearOnSuccess) {
            this.reset();
        }

        // Call success callback
        if (this.config.onSuccess) {
            this.config.onSuccess(response, this.form);
        }
    }

    /**
     * Handle submission error
     * @private
     */
    #handleError(error) {
        // Show toast
        if (this.config.showToastOnError) {
            const errorMessage = error.message || this.config.errorMessage;
            ToastNotification.error('Error', errorMessage);
        }

        // Call error callback
        if (this.config.onError) {
            this.config.onError(error, this.form);
        }
    }

    // =========================================================================
    // VALIDATION
    // =========================================================================

    /**
     * Validate entire form
     * @returns {boolean} True if valid
     */
    validate() {
        // HTML5 validation
        if (!this.form.checkValidity()) {
            this.form.classList.add('was-validated');
            return false;
        }

        // Custom validation
        if (this.config.customValidation) {
            const formData = new FormData(this.form);
            const errors = this.config.customValidation(formData);

            if (errors && Object.keys(errors).length > 0) {
                this.showErrors(errors);

                // Call validation error callback
                if (this.config.onValidationError) {
                    this.config.onValidationError(errors, this.form);
                }

                return false;
            }
        }

        return true;
    }

    /**
     * Validate single field
     * @param {HTMLElement} field - Field to validate
     * @returns {boolean} True if valid
     * @private
     */
    #validateField(field) {
        // Remove previous validation state
        field.classList.remove('is-invalid', 'is-valid');

        const nextSibling = field.nextElementSibling;
        if (nextSibling && nextSibling.classList.contains('invalid-feedback')) {
            nextSibling.remove();
        }

        // HTML5 validation
        if (!field.checkValidity()) {
            this.showFieldError(field, field.validationMessage);
            return false;
        }

        // Mark as valid
        field.classList.add('is-valid');
        return true;
    }

    // =========================================================================
    // ERROR DISPLAY
    // =========================================================================

    /**
     * Show validation errors for multiple fields
     * @param {Object} errors - Object with field names as keys and error messages as values
     *
     * @example
     *   form.showErrors({
     *       email: 'Invalid email address',
     *       password: 'Password is required'
     *   });
     */
    showErrors(errors) {
        for (const [fieldName, errorMessage] of Object.entries(errors)) {
            const field = this.form.querySelector(`[name="${fieldName}"]`);
            if (field) {
                this.showFieldError(field, errorMessage);
            }
        }
    }

    /**
     * Show error for a single field
     * @param {HTMLElement|string} fieldSelector - Field element or selector
     * @param {string} errorMessage - Error message to display
     */
    showFieldError(fieldSelector, errorMessage) {
        const field = typeof fieldSelector === 'string'
            ? this.form.querySelector(fieldSelector)
            : fieldSelector;

        if (!field) return;

        // Add invalid class
        field.classList.add('is-invalid');
        field.classList.remove('is-valid');

        // Remove existing feedback
        const existingFeedback = field.parentElement.querySelector('.invalid-feedback');
        if (existingFeedback) {
            existingFeedback.remove();
        }

        // Create feedback element
        const feedback = document.createElement('div');
        feedback.className = 'invalid-feedback';
        feedback.textContent = errorMessage;
        feedback.style.display = 'block';

        // Insert after field
        field.parentElement.appendChild(feedback);
    }

    /**
     * Clear all validation errors
     */
    clearErrors() {
        // Remove was-validated class
        this.form.classList.remove('was-validated');

        // Remove is-invalid classes
        this.form.querySelectorAll('.is-invalid').forEach(el => {
            el.classList.remove('is-invalid');
        });

        // Remove is-valid classes
        this.form.querySelectorAll('.is-valid').forEach(el => {
            el.classList.remove('is-valid');
        });

        // Remove feedback elements
        this.form.querySelectorAll('.invalid-feedback').forEach(el => {
            el.remove();
        });
    }

    /**
     * Clear errors for a specific field
     * @param {HTMLElement|string} fieldSelector - Field element or selector
     */
    clearFieldError(fieldSelector) {
        const field = typeof fieldSelector === 'string'
            ? this.form.querySelector(fieldSelector)
            : fieldSelector;

        if (!field) return;

        field.classList.remove('is-invalid', 'is-valid');

        const feedback = field.parentElement.querySelector('.invalid-feedback');
        if (feedback) {
            feedback.remove();
        }
    }

    // =========================================================================
    // FORM UTILITIES
    // =========================================================================

    /**
     * Reset form to initial state
     */
    reset() {
        this.form.reset();
        this.clearErrors();
    }

    /**
     * Get form data as FormData object
     * @returns {FormData} Form data
     */
    getFormData() {
        return new FormData(this.form);
    }

    /**
     * Get form data as plain object
     * @returns {Object} Form data as key-value pairs
     */
    getFormDataAsObject() {
        const formData = new FormData(this.form);
        const obj = {};

        for (const [key, value] of formData.entries()) {
            // Handle multiple values (e.g., checkboxes with same name)
            if (obj[key]) {
                if (!Array.isArray(obj[key])) {
                    obj[key] = [obj[key]];
                }
                obj[key].push(value);
            } else {
                obj[key] = value;
            }
        }

        return obj;
    }

    /**
     * Set form field value
     * @param {string} fieldName - Field name
     * @param {any} value - Field value
     */
    setFieldValue(fieldName, value) {
        const field = this.form.querySelector(`[name="${fieldName}"]`);
        if (!field) return;

        if (field.type === 'checkbox' || field.type === 'radio') {
            field.checked = !!value;
        } else {
            field.value = value;
        }
    }

    /**
     * Get form field value
     * @param {string} fieldName - Field name
     * @returns {any} Field value
     */
    getFieldValue(fieldName) {
        const field = this.form.querySelector(`[name="${fieldName}"]`);
        if (!field) return null;

        if (field.type === 'checkbox') {
            return field.checked;
        } else if (field.type === 'radio') {
            const checked = this.form.querySelector(`[name="${fieldName}"]:checked`);
            return checked ? checked.value : null;
        } else {
            return field.value;
        }
    }

    /**
     * Populate form with data
     * @param {Object} data - Data object with field names as keys
     */
    populate(data) {
        for (const [fieldName, value] of Object.entries(data)) {
            this.setFieldValue(fieldName, value);
        }
    }

    /**
     * Enable/disable form
     * @param {boolean} enabled - True to enable, false to disable
     */
    setEnabled(enabled) {
        const elements = this.form.querySelectorAll('input, select, textarea, button');
        elements.forEach(el => {
            el.disabled = !enabled;
        });
    }

    // =========================================================================
    // LOADING STATE
    // =========================================================================

    /**
     * Set form loading state
     * @param {boolean} loading - True for loading, false for normal
     * @private
     */
    #setLoadingState(loading) {
        this.isSubmitting = loading;

        if (this.submitButton) {
            this.submitButton.disabled = loading;

            if (loading) {
                this.submitButton.innerHTML = `
                    <span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                    ${this.config.loadingText}
                `;
            } else {
                this.submitButton.innerHTML = this.originalButtonText;
            }
        }

        // Disable/enable all form inputs
        this.setEnabled(!loading);
    }

    /**
     * Check if form is currently submitting
     * @returns {boolean} True if submitting
     */
    isLoading() {
        return this.isSubmitting;
    }

    // =========================================================================
    // PROGRAMMATIC SUBMISSION
    // =========================================================================

    /**
     * Submit form programmatically
     * @param {Object} additionalData - Additional data to include in submission
     * @returns {Promise<any>} Submission result
     */
    async submit(additionalData = {}) {
        // Create form data
        const formData = this.getFormData();

        // Add additional data
        for (const [key, value] of Object.entries(additionalData)) {
            formData.append(key, value);
        }

        // Validate
        if (this.config.validateOnSubmit) {
            const isValid = this.validate();
            if (!isValid) {
                throw new Error('Form validation failed');
            }
        }

        // Transform data if needed
        const data = this.config.transformData
            ? this.config.transformData(formData)
            : formData;

        // Submit
        if (this.config.useAjax) {
            this.#setLoadingState(true);
            try {
                const isFormData = data instanceof FormData;
                const response = isFormData
                    ? await API.postForm(this.config.url, data)
                    : await API[this.config.method.toLowerCase()](this.config.url, data);

                this.#handleSuccess(response);
                return response;
            } catch (error) {
                this.#handleError(error);
                throw error;
            } finally {
                this.#setLoadingState(false);
            }
        } else {
            this.form.submit();
        }
    }

    // =========================================================================
    // DESTROY
    // =========================================================================

    /**
     * Destroy form handler and clean up
     */
    destroy() {
        // Remove event listeners
        this.form.removeEventListener('submit', this.#handleSubmit);

        // Clear state
        this.clearErrors();
        this.#setLoadingState(false);

        // Remove initialization marker
        delete this.form.dataset.formHandlerInitialized;
    }
}

/**
 * Export as default for convenience
 */
export default FormHandler;
