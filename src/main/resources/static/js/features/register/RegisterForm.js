/**
 * RegisterForm.js
 * User registration form handler extending FormHandler base class
 *
 * Manages:
 * - Form initialization and validation
 * - Select2 multi-select for print prep types
 * - Complexity calculation based on action type and print prep
 * - Entry editing and duplication
 * - Auto-fill and default values
 *
 * @module features/register/RegisterForm
 */

import { FormHandler } from '../../components/FormHandler.js';
import { ValidationService } from '../../services/validationService.js';
import { ACTION_TYPE_VALUES, COMPLEXITY_PRINT_PREPS, NEUTRAL_PRINT_PREPS } from '../../core/constants.js';

/**
 * RegisterForm - User registration form handler
 * @extends FormHandler
 */
export class RegisterForm extends FormHandler {

    /**
     * Create a RegisterForm instance
     * @param {HTMLFormElement} formElement - Optional pre-validated form element
     */
    constructor(formElement = null) {
        // Call FormHandler with correct parameters: (formSelector, config)
        super(
            formElement || '#registerForm',  // First param: selector or element
            {  // Second param: config object
                submitUrl: '/user/register/entry',
                validationRules: {
                    date: ['required', 'date'],
                    orderId: ['required'],
                    productionId: ['required'],
                    actionTypeSelect: ['required'],
                    articleNumbers: ['required', 'number'],
                    colorsInput: ['required']
                },
                onSuccess: (response) => this.handleSuccess(response),
                onError: (error) => this.handleError(error)
            }
        );

        this.initializeFormElements();
        this.initializeForm();
        this.setupEventListeners();
        this.initializeDefaultValues();
    }

    /**
     * Cache DOM element references
     * @private
     */
    initializeFormElements() {
        this.actionTypeSelect = document.getElementById('actionTypeSelect');
        this.printPrepSelect = document.getElementById('printPrepTypeSelect');
        this.complexityInput = document.getElementById('complexityInput');
        this.colorsInput = document.getElementById('colorsInput');
        this.articleNumbersInput = document.getElementById('articlesInput');
        this.editingIdInput = document.getElementById('editingId');
        this.isEditInput = document.getElementById('isEdit');
    }

    /**
     * Initialize Select2 multi-select with custom styling and behavior
     * @private
     */
    initializeForm() {
        console.log('📝 InitializeForm called');
        console.log('  Form exists:', !!this.form);
        console.log('  PrintPrepSelect exists:', !!this.printPrepSelect);

        if (!this.form || !this.printPrepSelect) {
            console.error('❌ Cannot initialize Select2 - form or printPrepSelect missing');
            return;
        }

        console.log('✓ Initializing Select2...');

        // Make the original select element tabbable
        $(this.printPrepSelect).attr('tabindex', '0');

        // Inject custom CSS for Select2 styling
        this.injectSelect2Styles();

        // Configure Select2
        $(this.printPrepSelect).select2({
            theme: 'bootstrap-5',
            width: '100%',
            placeholder: 'Select',
            multiple: true,
            maximumSelectionLength: 10,
            dropdownParent: $('body'),
            minimumResultsForSearch: 0,
            tags: false,
            selectOnClose: false,
            closeOnSelect: false,

            // Custom pill formatting - show first letter only
            templateSelection: (data, container) => {
                if ($(container).hasClass('select2-selection__rendered')) {
                    return null;
                }

                if (data.text) {
                    const initial = data.text.charAt(0).toUpperCase();
                    return $(`<span class="select2-pill-initial">${initial}</span>`);
                }

                return null;
            }
        });

        console.log('✓ Select2 initialized');

        // Make Select2 container focusable
        const select2Container = $(this.printPrepSelect).next('.select2-container');
        select2Container.attr('tabindex', '0');
        console.log('✓ Select2 container made focusable');

        // TEMPORARILY DISABLED - these modifications are preventing dropdown from opening
        // TODO: Re-enable after fixing the blocking issues

        // Remove conflicting event handlers
        // $(document).off('mouseenter mouseleave', '.select2-results__option');

        // Override Select2 hover highlighting to prevent auto-selection
        // console.log('⚙️ Disabling Select2 auto-highlight...');
        // this.disableSelect2AutoHighlight();

        // Setup Select2-specific events
        console.log('⚙️ Setting up Select2 events (basic only)...');
        this.setupSelect2EventsBasic();

        // Setup tab navigation
        // console.log('⚙️ Setting up tab navigation...');
        // this.setupTabNavigation();

        console.log('✅ RegisterForm initialization complete (Select2 in basic mode)');
    }

    /**
     * Setup basic Select2 events (without custom modifications)
     * @private
     */
    setupSelect2EventsBasic() {
        const $printPrepSelect = $(this.printPrepSelect);

        // Open event - focus search field
        $printPrepSelect.on('select2:open', () => {
            setTimeout(() => {
                document.querySelector('.select2-search__field')?.focus();
            }, 10);
        });

        // Listen for selection changes to recalculate complexity
        $printPrepSelect.on('change', () => {
            this.updateComplexityField();
        });
    }

    /**
     * Inject custom CSS for Select2 styling
     * @private
     */
    injectSelect2Styles() {
        if (document.getElementById('select2-custom-styles')) return;

        const style = document.createElement('style');
        style.id = 'select2-custom-styles';
        style.textContent = `
            /* Select2 focus outline */
            .select2-container:focus {
                outline: 2px solid #007bff;
                border-radius: 4px;
            }

            /* Override default Select2 hover highlighting */
            .select2-results__option--highlighted[aria-selected] {
                background-color: inherit !important;
                color: inherit !important;
            }

            /* Custom hover highlighting - only on exact hover */
            .select2-results__option:hover {
                background-color: #f0f0f0 !important;
                color: #333 !important;
            }

            /* Custom highlighting for keyboard navigation */
            .select2-results__option.keyboard-highlight {
                background-color: #0d6efd !important;
                color: #fff !important;
            }
        `;
        document.head.appendChild(style);
    }

    /**
     * Disable Select2 auto-highlighting on hover
     * @private
     */
    disableSelect2AutoHighlight() {
        $.fn.select2.amd.require(['select2/results'], function(ResultsAdapter) {
            const origSetClasses = ResultsAdapter.prototype.setClasses;

            ResultsAdapter.prototype.setClasses = function() {
                const result = origSetClasses.apply(this, arguments);

                const $highlighted = this.$results.find('.select2-results__option--highlighted');
                $highlighted.each(function() {
                    if (!$(this).hasClass('keyboard-highlight')) {
                        $(this).removeClass('select2-results__option--highlighted');
                    }
                });

                return result;
            };
        });
    }

    /**
     * Setup Select2-specific event handlers
     * @private
     */
    setupSelect2Events() {
        const $printPrepSelect = $(this.printPrepSelect);

        // Open event - focus search field
        $printPrepSelect.on('select2:open', () => {
            setTimeout(() => {
                document.querySelector('.select2-search__field')?.focus();
            }, 10);
        });

        // Adjust dropdown position
        $printPrepSelect.on('select2:open', () => {
            const $dropdown = $('.select2-dropdown');
            const $container = $printPrepSelect.next('.select2-container');

            const containerOffset = $container.offset();
            const containerHeight = $container.outerHeight();

            $dropdown.css({
                'top': (containerOffset.top + containerHeight) + 'px',
                'left': containerOffset.left + 'px',
                'width': $container.outerWidth() + 'px'
            });
        });
    }

    /**
     * Setup keyboard tab navigation into Select2 dropdown
     * @private
     */
    setupTabNavigation() {
        const $printPrepSelect = $(this.printPrepSelect);

        // Handle Tab key on focused element before Select2
        $(document).on('keydown', (e) => {
            if (e.key === 'Tab' && !e.shiftKey) {
                const activeElement = document.activeElement;
                const select2Container = $printPrepSelect.next('.select2-container')[0];

                if (this.isNextTabStop(activeElement, select2Container)) {
                    e.preventDefault();
                    $printPrepSelect.select2('open');
                }
            }
        });

        // Handle Tab inside Select2 search
        $(document).on('keydown', '.select2-search__field', (e) => {
            if (e.key === 'Tab') {
                e.preventDefault();
                $printPrepSelect.select2('close');

                // Focus next element after Select2
                const allInputs = Array.from(document.querySelectorAll('input, select, textarea, button'))
                    .filter(el => !el.disabled && el.tabIndex >= 0);
                const currentIndex = allInputs.indexOf($printPrepSelect[0]);

                if (currentIndex >= 0 && currentIndex < allInputs.length - 1) {
                    allInputs[currentIndex + 1].focus();
                }
            }
        });
    }

    /**
     * Check if target element is the next tab stop
     * @param {HTMLElement} currentElement - Currently focused element
     * @param {HTMLElement} targetElement - Potential next element
     * @returns {boolean}
     * @private
     */
    isNextTabStop(currentElement, targetElement) {
        const focusableElements = Array.from(
            document.querySelectorAll('input, select, textarea, button, [tabindex]:not([tabindex="-1"])')
        ).filter(el => !el.disabled && el.offsetParent !== null);

        const currentIndex = focusableElements.indexOf(currentElement);
        const targetIndex = focusableElements.indexOf(targetElement);

        return targetIndex === currentIndex + 1;
    }

    /**
     * Setup form event listeners
     * @private
     */
    setupEventListeners() {
        // Action type change - recalculate complexity
        if (this.actionTypeSelect) {
            this.actionTypeSelect.addEventListener('change', () => {
                this.updateComplexityField();
            });
        }

        // Print prep type change - recalculate complexity
        if (this.printPrepSelect) {
            $(this.printPrepSelect).on('change', () => {
                this.updateComplexityField();
            });
        }

        // Article numbers change - recalculate complexity (for CHECKING type)
        if (this.articleNumbersInput) {
            this.articleNumbersInput.addEventListener('input', () => {
                const actionType = this.actionTypeSelect?.value;
                if (actionType === 'CHECKING') {
                    this.updateComplexityField();
                }
            });
        }

        // Colors input blur - auto-fill if empty
        if (this.colorsInput) {
            this.colorsInput.addEventListener('blur', () => {
                this.autoFillColors();
            });
        }
    }

    /**
     * Initialize default form values
     * @private
     */
    initializeDefaultValues() {
        // Set default date to today if empty
        const dateInput = document.getElementById('dateInput');
        if (dateInput && !dateInput.value) {
            const today = new Date();
            const year = today.getFullYear();
            const month = String(today.getMonth() + 1).padStart(2, '0');
            const day = String(today.getDate()).padStart(2, '0');
            dateInput.value = `${year}-${month}-${day}`;
        }

        // Set default colors if empty
        if (this.colorsInput && !this.colorsInput.value) {
            this.colorsInput.value = 'A';
        }
    }

    /**
     * Calculate complexity for CHECKING action type based on article count
     * @param {number} articleCount - Number of articles
     * @returns {number} Complexity score (3.0, 3.5, or 4.0)
     * @private
     */
    calculateCheckingComplexity(articleCount) {
        if (articleCount >= 1 && articleCount <= 5) return 3.0;
        if (articleCount >= 6 && articleCount <= 10) return 3.5;
        return 4.0; // 11+
    }

    /**
     * Calculate graphical complexity based on action type and print prep types
     * @param {string} actionType - Selected action type
     * @param {Array<string>} printPrepTypes - Selected print prep types
     * @returns {number} Calculated complexity
     * @public
     */
    calculateComplexity(actionType, printPrepTypes) {
        // No action type selected
        if (!actionType) return 0;

        // IMPOSTARE always returns 0
        if (actionType === 'IMPOSTARE') {
            return 0;
        }

        // REORDIN always returns 1.0
        if (actionType === 'REORDIN') {
            return 1.0;
        }

        // Special case: CHECKING uses article count
        if (actionType === 'CHECKING') {
            // Check if LAYOUT is in print prep types
            if (Array.isArray(printPrepTypes) && printPrepTypes.includes('LAYOUT')) {
                const articleCount = parseInt(this.articleNumbersInput?.value || '0');
                return this.calculateCheckingComplexity(articleCount);
            }
            return 3.0;
        }

        // For ORDIN and CAMPION, check print prep types
        if (actionType === 'ORDIN' || actionType === 'CAMPION' || actionType === 'PROBA STAMPA') {
            const baseValue = ACTION_TYPE_VALUES.get(actionType) || 0;

            // If no print prep types, return base value
            if (!printPrepTypes || !Array.isArray(printPrepTypes) || printPrepTypes.length === 0) {
                return baseValue;
            }

            // Check if any print prep type adds complexity
            const hasComplexityType = printPrepTypes.some(type =>
                COMPLEXITY_PRINT_PREPS.has(type)
            );

            // If complex prep type found, return 3.0
            if (hasComplexityType) {
                return 3.0;
            }

            return baseValue;
        }

        // For all other action types, return the base value
        return ACTION_TYPE_VALUES.get(actionType) || 0;
    }

    /**
     * Update complexity field based on current form values
     * @public
     */
    updateComplexityField() {
        if (!this.complexityInput) return;

        const actionType = this.actionTypeSelect?.value;
        const printPrepTypes = $(this.printPrepSelect).val() || [];

        const complexity = this.calculateComplexity(actionType, printPrepTypes);
        this.complexityInput.value = complexity.toFixed(1);
    }

    /**
     * Auto-fill colors field with 'A' if empty
     * @public
     */
    autoFillColors() {
        if (this.colorsInput && !this.colorsInput.value.trim()) {
            this.colorsInput.value = 'A';
        }
    }

    /**
     * Validate form fields
     * @returns {boolean} True if valid
     * @public
     */
    validateForm() {
        console.log('🔍 Starting form validation...');
        this.clearValidationState();

        let isValid = true;
        const errors = [];

        // Validate date
        const dateInput = document.getElementById('dateInput');
        if (!dateInput?.value) {
            this.addValidationError(dateInput, 'Date is required');
            errors.push('date');
            isValid = false;
        }

        // Validate order ID
        const orderIdInput = document.getElementById('orderIdInput');
        if (!orderIdInput?.value || !orderIdInput.value.trim()) {
            this.addValidationError(orderIdInput, 'Order ID is required');
            errors.push('orderId');
            isValid = false;
        }

        // Validate OMS ID
        const omsIdInput = document.getElementById('omsIdInput');
        if (!omsIdInput?.value || !omsIdInput.value.trim()) {
            this.addValidationError(omsIdInput, 'OMS ID is required');
            errors.push('omsId');
            isValid = false;
        }

        // Validate client name
        const clientNameInput = document.getElementById('clientNameInput');
        if (!clientNameInput?.value || !clientNameInput.value.trim()) {
            this.addValidationError(clientNameInput, 'Client name is required');
            errors.push('clientName');
            isValid = false;
        }

        // Validate action type
        if (!this.actionTypeSelect?.value) {
            this.addValidationError(this.actionTypeSelect, 'Action type is required');
            errors.push('actionType');
            isValid = false;
        }

        // Validate print prep types (Select2)
        const selectedPrintTypes = $(this.printPrepSelect).val();
        if (!selectedPrintTypes || selectedPrintTypes.length === 0) {
            this.addValidationError(this.printPrepSelect, 'Print prep type is required');
            errors.push('printPrepTypes');
            isValid = false;
        }

        // Validate article numbers
        if (!this.articleNumbersInput?.value || parseInt(this.articleNumbersInput.value) <= 0) {
            this.addValidationError(this.articleNumbersInput, 'Article numbers must be greater than 0');
            errors.push('articleNumbers');
            isValid = false;
        }

        // Validate colors
        if (!this.colorsInput?.value || !this.colorsInput.value.trim()) {
            this.addValidationError(this.colorsInput, 'Colors profile is required');
            errors.push('colors');
            isValid = false;
        }

        if (!isValid) {
            console.log('❌ Validation failed. Missing fields:', errors);
            this.scrollToForm();
        } else {
            console.log('✓ All fields valid');
        }

        return isValid;
    }

    /**
     * Add validation error to field
     * @param {HTMLElement} element - Input element
     * @param {string} message - Error message
     * @private
     */
    addValidationError(element, message) {
        if (!element) {
            console.warn('addValidationError called with null element for message:', message);
            return;
        }

        console.log('  → Adding error to', element.id || element.name, ':', message);
        element.classList.add('is-invalid');

        // Create or update feedback element
        let feedback = element.nextElementSibling;
        if (!feedback || !feedback.classList.contains('invalid-feedback')) {
            feedback = document.createElement('div');
            feedback.className = 'invalid-feedback';
            feedback.style.display = 'block'; // Ensure feedback is visible
            element.parentNode.insertBefore(feedback, element.nextSibling);
        }

        feedback.textContent = message;
    }

    /**
     * Clear all validation states
     * @private
     */
    clearValidationState() {
        this.form.querySelectorAll('.is-invalid').forEach(el => {
            el.classList.remove('is-invalid');
        });

        this.form.querySelectorAll('.invalid-feedback').forEach(el => {
            el.remove();
        });
    }

    /**
     * Scroll to form with smooth animation
     * @public
     */
    scrollToForm() {
        const formContainer = this.form?.closest('.card') || this.form;
        if (formContainer) {
            const offset = 80;
            const elementPosition = formContainer.getBoundingClientRect().top;
            const offsetPosition = elementPosition + window.pageYOffset - offset;

            window.scrollTo({
                top: offsetPosition,
                behavior: 'smooth'
            });
        }
    }

    /**
     * Reset form to initial state
     * @public
     */
    resetForm() {
        if (!this.form) return;

        // Reset form fields
        this.form.reset();

        // Clear Select2
        $(this.printPrepSelect).val(null).trigger('change');

        // Reset hidden fields
        if (this.editingIdInput) this.editingIdInput.value = '';
        if (this.isEditInput) this.isEditInput.value = 'false';

        // Clear validation
        this.clearValidationState();

        // Reset to defaults
        this.initializeDefaultValues();

        // Recalculate complexity
        this.updateComplexityField();

        // Reset submit button text
        const submitButton = this.form.querySelector('button[type="submit"]');
        if (submitButton) {
            submitButton.innerHTML = '<i class="bi bi-plus-circle me-1"></i>Add Entry';
        }
    }

    /**
     * Convert date from DD/MM/YYYY to YYYY-MM-DD
     * @param {string} dateStr - Date in DD/MM/YYYY format
     * @returns {string} Date in YYYY-MM-DD format
     * @private
     */
    convertDateFormat(dateStr) {
        if (!dateStr) return '';

        // Check if already in YYYY-MM-DD format
        if (/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
            return dateStr;
        }

        // Convert DD/MM/YYYY to YYYY-MM-DD
        const parts = dateStr.split('/');
        if (parts.length === 3) {
            const [day, month, year] = parts;
            return `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}`;
        }

        return dateStr;
    }

    /**
     * Populate form from edit button data
     * @param {HTMLButtonElement} button - Edit button with data attributes
     * @public
     */
    populateForm(button) {
        if (!button) return;

        const data = button.dataset;

        // Populate basic fields using correct IDs
        const dateInput = document.getElementById('dateInput');
        if (dateInput) dateInput.value = this.convertDateFormat(data.date) || '';

        const orderIdInput = document.getElementById('orderIdInput');
        if (orderIdInput) orderIdInput.value = data.orderId || '';

        const productionIdInput = document.getElementById('productionIdInput');
        if (productionIdInput) productionIdInput.value = data.productionId || '';

        const omsIdInput = document.getElementById('omsIdInput');
        if (omsIdInput) omsIdInput.value = data.omsId || '';

        const clientNameInput = document.getElementById('clientNameInput');
        if (clientNameInput) clientNameInput.value = data.clientName || '';

        // Populate selects
        if (this.actionTypeSelect && data.actionType) {
            this.actionTypeSelect.value = data.actionType;
        }

        // Populate Select2 multi-select
        if (data.printPrepTypes) {
            const printPreps = data.printPrepTypes.split(',').map(s => s.trim());
            $(this.printPrepSelect).val(printPreps).trigger('change');
        }

        // Populate numeric fields
        if (this.articleNumbersInput) this.articleNumbersInput.value = data.articleNumbers || '';
        if (this.complexityInput) this.complexityInput.value = data.graphicComplexity || '';
        if (this.colorsInput) this.colorsInput.value = data.colorsProfile || data.colors || '';

        // Populate text areas
        const observationsInput = document.getElementById('observationsInput');
        if (observationsInput) observationsInput.value = data.observations || '';

        // Set edit mode
        if (this.editingIdInput) this.editingIdInput.value = data.entryId || data.id || '';
        if (this.isEditInput) this.isEditInput.value = 'true';

        // Update submit button text
        const submitButton = this.form.querySelector('button[type="submit"]');
        if (submitButton) {
            submitButton.innerHTML = '<i class="bi bi-pencil me-1"></i>Update Entry';
        }

        // Scroll to form
        this.scrollToForm();
    }

    /**
     * Copy entry data to form for duplication
     * @param {HTMLButtonElement} button - Copy button with data attributes
     * @public
     */
    copyEntry(button) {
        this.populateForm(button);

        // Clear ID fields for new entry
        if (this.editingIdInput) this.editingIdInput.value = '';
        if (this.isEditInput) this.isEditInput.value = 'false';

        // Set date to today
        const dateInput = document.getElementById('dateInput');
        if (dateInput) {
            const today = new Date();
            const year = today.getFullYear();
            const month = String(today.getMonth() + 1).padStart(2, '0');
            const day = String(today.getDate()).padStart(2, '0');
            dateInput.value = `${year}-${month}-${day}`;
        }

        // Update submit button text to "Add Entry"
        const submitButton = this.form.querySelector('button[type="submit"]');
        if (submitButton) {
            submitButton.innerHTML = '<i class="bi bi-plus-circle me-1"></i>Add Entry';
        }

        // Recalculate complexity
        this.updateComplexityField();
    }

    /**
     * Handle successful form submission
     * @param {Object} response - Server response
     * @private
     */
    handleSuccess(response) {
        this.resetForm();
        // Success will be handled by AjaxHandler
    }

    /**
     * Handle form submission error
     * @param {Error} error - Error object
     * @private
     */
    handleError(error) {
        console.error('Form submission error:', error);
        // Error will be handled by AjaxHandler
    }
}
