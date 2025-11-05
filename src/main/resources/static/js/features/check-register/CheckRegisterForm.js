/**
 * CheckRegisterForm.js
 *
 * Manages check register form for both user and team views.
 * Handles form initialization, validation, calculations, and submissions.
 *
 * @module features/check-register/CheckRegisterForm
 */

import { FormHandler } from '../../components/FormHandler.js';
import { ValidationService } from '../../services/validationService.js';
import { CONSTANTS } from '../../core/constants.js';

/**
 * CheckRegisterForm class
 * Extends FormHandler for specialized check register functionality
 */
export class CheckRegisterForm extends FormHandler {
    constructor(options = {}) {
        // Determine URL based on team view context
        const isTeamView = typeof IS_TEAM_VIEW !== 'undefined' && IS_TEAM_VIEW;
        const url = isTeamView ? '/team/check-register/entry' : '/user/check-register/entry';

        super({
            formId: 'checkRegisterForm',
            submitUrl: url,
            ...options
        });

        this.isTeamView = isTeamView;
        this.initializeFormElements();
        this.setupCheckRegisterListeners();
        this.initializeDefaultValues();
    }

    /**
     * Initialize form-specific elements
     */
    initializeFormElements() {
        this.checkTypeSelect = document.getElementById('checkTypeSelect');
        this.articleNumbersInput = document.getElementById('articleNumbersInput');
        this.filesNumbersInput = document.getElementById('filesNumbersInput');
        this.orderValueInput = document.getElementById('orderValueInput');
        this.approvalStatusSelect = document.getElementById('approvalStatusSelect');
    }

    /**
     * Setup check register specific event listeners
     */
    setupCheckRegisterListeners() {
        // Check type changes
        if (this.checkTypeSelect) {
            this.checkTypeSelect.addEventListener('change', () => {
                this.updateOrderValueField();
            });
        }

        // Article Numbers changes
        if (this.articleNumbersInput) {
            this.articleNumbersInput.addEventListener('input', () => {
                this.updateOrderValueField();
            });
        }

        // File Numbers changes
        if (this.filesNumbersInput) {
            this.filesNumbersInput.addEventListener('input', () => {
                this.updateOrderValueField();
            });
        }

        // Copy button handlers
        document.querySelectorAll('.copy-entry').forEach(button => {
            button.addEventListener('click', (e) => {
                e.preventDefault();
                this.copyEntry(button);
            });
        });

        // Edit button handlers
        document.querySelectorAll('.edit-entry').forEach(button => {
            button.addEventListener('click', (e) => {
                e.preventDefault();
                this.populateFormForEdit(button);
            });
        });
    }

    /**
     * Initialize default values
     */
    initializeDefaultValues() {
        // Set default date to today
        const dateInput = this.form.querySelector('input[name="date"]');
        if (dateInput && !dateInput.value) {
            dateInput.value = new Date().toISOString().split('T')[0];
        }

        // Set default articles and files to 1
        const articlesInput = this.form.querySelector('input[name="articleNumbers"]');
        if (articlesInput && !articlesInput.value) {
            articlesInput.value = '1';
        }

        const filesInput = this.form.querySelector('input[name="filesNumbers"]');
        if (filesInput && !filesInput.value) {
            filesInput.value = '1';
        }
    }

    /**
     * Calculate order value based on check type and numbers
     * @param {string} checkType - Check type
     * @param {number} articleNumbers - Number of articles
     * @param {number} filesNumbers - Number of files
     * @returns {number} Calculated order value
     */
    calculateOrderValue(checkType, articleNumbers, filesNumbers) {
        if (!checkType) return 0;

        // Get check type values from constants
        const checkTypeValues = CONSTANTS.CHECK_TYPE_VALUES;
        if (!checkTypeValues.has(checkType)) return 0;

        const typeValue = checkTypeValues.get(checkType);
        let orderValue = 0;

        // Get article-based and file-based types from constants
        const articleBasedTypes = CONSTANTS.ARTICLE_BASED_CHECK_TYPES || [
            'LAYOUT', 'KIPSTA LAYOUT', 'LAYOUT CHANGES', 'GPT'
        ];
        const fileBasedTypes = CONSTANTS.FILE_BASED_CHECK_TYPES || [
            'PRODUCTION', 'REORDER', 'SAMPLE', 'OMS PRODUCTION', 'KIPSTA PRODUCTION', 'GPT'
        ];

        // Calculate based on type
        if (checkType === 'GPT') {
            // GPT uses both articles and files
            orderValue = (articleNumbers * typeValue) + (filesNumbers * typeValue);
        } else if (articleBasedTypes.includes(checkType)) {
            // Types that use article numbers
            orderValue = articleNumbers * typeValue;
        } else if (fileBasedTypes.includes(checkType)) {
            // Types that use file numbers
            orderValue = filesNumbers * typeValue;
        }

        return orderValue;
    }

    /**
     * Update order value field based on current inputs
     */
    updateOrderValueField() {
        if (!this.checkTypeSelect || !this.articleNumbersInput ||
            !this.filesNumbersInput || !this.orderValueInput) {
            return;
        }

        const checkType = this.checkTypeSelect.value;
        const articleNumbers = parseInt(this.articleNumbersInput.value) || 0;
        const filesNumbers = parseInt(this.filesNumbersInput.value) || 0;

        const orderValue = this.calculateOrderValue(checkType, articleNumbers, filesNumbers);

        if (orderValue >= 0) {
            this.orderValueInput.value = orderValue.toFixed(2);
        }
    }

    /**
     * Custom validation for check register form
     * @returns {boolean} True if valid
     */
    validate() {
        // Use base validation first
        if (!super.validate()) {
            return false;
        }

        // Additional check register specific validation
        const rules = {
            date: { required: true },
            omsId: { required: true },
            designerName: { required: true },
            checkType: { required: true },
            articleNumbers: { required: true, number: true, min: 0 },
            filesNumbers: { required: true, number: true, min: 0 },
            approvalStatus: { required: true }
        };

        const data = this.getData();
        const result = ValidationService.validateForm(data, rules);

        if (!result.isValid) {
            this.showErrors(result.errors);
            return false;
        }

        return true;
    }

    /**
     * Copy entry (create new entry with same data except date)
     * @param {HTMLElement} button - Copy button element
     */
    copyEntry(button) {
        // Clear the form first
        this.reset();

        // Populate form with original entry's data
        const fields = [
            'omsId', 'productionId', 'designerName',
            'checkType', 'articleNumbers', 'filesNumbers', 'errorDescription',
            'approvalStatus'
        ];

        fields.forEach(field => {
            const input = this.form.querySelector(`[name="${field}"]`);
            if (input) {
                const value = button.getAttribute(`data-${field.replace(/([A-Z])/g, '-$1').toLowerCase()}`) || '';
                input.value = value;
            }
        });

        // Set today's date for the new entry
        const dateInput = this.form.querySelector('input[name="date"]');
        if (dateInput) {
            dateInput.value = new Date().toISOString().split('T')[0];
        }

        // Update the order value based on the current values
        this.updateOrderValueField();

        // Scroll to form
        this.scrollToForm();
    }

    /**
     * Populate form for editing an entry
     * @param {HTMLElement} button - Edit button element
     */
    populateFormForEdit(button) {
        // Get the adminSync status (for debugging)
        const adminSync = button.getAttribute('data-admin-sync');
        console.log('Edit button clicked with status:', adminSync);

        // Users can now edit ALL entries
        // Tombstone deletion system handles conflict resolution during merge

        // Clear the form first
        this.reset();

        const entryId = button.getAttribute('data-entry-id');
        const baseUrl = this.isTeamView ? '/team/check-register/entry' : '/user/check-register/entry';
        this.form.action = `${baseUrl}/${entryId}`;
        this.form.method = 'post';

        // Populate all fields
        const fields = [
            'date', 'omsId', 'productionId', 'designerName',
            'checkType', 'articleNumbers', 'filesNumbers', 'errorDescription',
            'approvalStatus', 'orderValue'
        ];

        fields.forEach(field => {
            const input = this.form.querySelector(`[name="${field}"]`);
            if (input) {
                input.value = button.getAttribute(`data-${field.replace(/([A-Z])/g, '-$1').toLowerCase()}`) || '';
            }
        });

        // Update button text
        const submitButton = this.form.querySelector('button[type="submit"]');
        if (submitButton) {
            submitButton.innerHTML = '<i class="bi bi-check-circle me-1"></i>Update';
        }

        // Update hidden fields for editing
        const editingIdInput = document.getElementById('editingId');
        if (editingIdInput) {
            editingIdInput.value = entryId;
        }
        const isEditInput = document.getElementById('isEdit');
        if (isEditInput) {
            isEditInput.value = 'true';
        }

        // Add team-specific fields if needed
        if (this.isTeamView) {
            const usernameInput = this.form.querySelector('input[name="username"]');
            const userIdInput = this.form.querySelector('input[name="userId"]');

            if (usernameInput && button.hasAttribute('data-username')) {
                usernameInput.value = button.getAttribute('data-username');
            }

            if (userIdInput && button.hasAttribute('data-user-id')) {
                userIdInput.value = button.getAttribute('data-user-id');
            }
        }

        // Scroll to form
        this.scrollToForm();
    }

    /**
     * Scroll to form with offset
     */
    scrollToForm() {
        const formContainer = document.querySelector('.card.shadow-sm:has(#checkRegisterForm)');
        if (formContainer) {
            const currentScroll = window.pageYOffset;
            const rect = formContainer.getBoundingClientRect();
            const scrollPosition = currentScroll + rect.top;

            window.scrollTo({
                top: Math.max(0, scrollPosition - 100),
                behavior: 'smooth'
            });
        } else {
            this.form.scrollIntoView({
                behavior: 'smooth',
                block: 'start'
            });
        }
    }

    /**
     * Reset form to default state
     */
    reset() {
        super.reset();

        // Reset form action and method
        const baseUrl = this.isTeamView ? '/team/check-register/entry' : '/user/check-register/entry';
        this.form.action = baseUrl;
        this.form.method = 'post';

        // Reset hidden fields
        const editingIdInput = document.getElementById('editingId');
        if (editingIdInput) {
            editingIdInput.value = '';
        }
        const isEditInput = document.getElementById('isEdit');
        if (isEditInput) {
            isEditInput.value = 'false';
        }

        // Reinitialize default values
        this.initializeDefaultValues();

        // Reset submit button
        const submitButton = this.form.querySelector('button[type="submit"]');
        if (submitButton) {
            submitButton.innerHTML = '<i class="bi bi-plus-circle me-1"></i>Add Entry';
        }

        // Update the order value field
        this.updateOrderValueField();
    }
}
