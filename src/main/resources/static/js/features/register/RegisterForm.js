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
     */
    constructor() {
        super({
            formSelector: '#registerForm',
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
        });

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
        this.articleNumbersInput = document.getElementById('articleNumbers');
        this.editingIdInput = document.getElementById('editingId');
        this.isEditInput = document.getElementById('isEdit');
    }

    /**
     * Initialize Select2 multi-select with custom styling and behavior
     * @private
     */
    initializeForm() {
        if (!this.form || !this.printPrepSelect) return;

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

        // Make Select2 container focusable
        const select2Container = $(this.printPrepSelect).next('.select2-container');
        select2Container.attr('tabindex', '0');

        // Remove conflicting event handlers
        $(document).off('mouseenter mouseleave', '.select2-results__option');

        // Override Select2 hover highlighting to prevent auto-selection
        this.disableSelect2AutoHighlight();

        // Setup Select2-specific events
        this.setupSelect2Events();

        // Setup tab navigation
        this.setupTabNavigation();
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
        const dateInput = document.getElementById('date');
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
        // Special case: CHECKING uses article count
        if (actionType === 'CHECKING') {
            const articleCount = parseInt(this.articleNumbersInput?.value || '0');
            return this.calculateCheckingComplexity(articleCount);
        }

        // Get base complexity for action type
        let baseComplexity = ACTION_TYPE_VALUES.get(actionType) || 0;

        // IMPOSTARE always returns 0
        if (actionType === 'IMPOSTARE') {
            return 0;
        }

        // Add complexity from print prep types
        if (Array.isArray(printPrepTypes) && printPrepTypes.length > 0) {
            printPrepTypes.forEach(prepType => {
                const prepComplexity = COMPLEXITY_PRINT_PREPS.get(prepType) ||
                                       NEUTRAL_PRINT_PREPS.get(prepType) ||
                                       0;
                baseComplexity += prepComplexity;
            });
        }

        return baseComplexity;
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
        this.clearValidationState();

        let isValid = true;

        // Validate date
        const dateInput = document.getElementById('date');
        if (!dateInput?.value) {
            this.addValidationError(dateInput, 'Date is required');
            isValid = false;
        }

        // Validate order ID
        const orderIdInput = document.getElementById('orderId');
        if (!orderIdInput?.value) {
            this.addValidationError(orderIdInput, 'Order ID is required');
            isValid = false;
        }

        // Validate production ID
        const productionIdInput = document.getElementById('productionId');
        if (!productionIdInput?.value) {
            this.addValidationError(productionIdInput, 'Production ID is required');
            isValid = false;
        }

        // Validate action type
        if (!this.actionTypeSelect?.value) {
            this.addValidationError(this.actionTypeSelect, 'Action type is required');
            isValid = false;
        }

        // Validate article numbers
        if (!this.articleNumbersInput?.value || parseInt(this.articleNumbersInput.value) <= 0) {
            this.addValidationError(this.articleNumbersInput, 'Article numbers must be greater than 0');
            isValid = false;
        }

        // Validate colors
        if (!this.colorsInput?.value) {
            this.addValidationError(this.colorsInput, 'Colors profile is required');
            isValid = false;
        }

        if (!isValid) {
            this.scrollToForm();
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
        if (!element) return;

        element.classList.add('is-invalid');

        // Create or update feedback element
        let feedback = element.nextElementSibling;
        if (!feedback || !feedback.classList.contains('invalid-feedback')) {
            feedback = document.createElement('div');
            feedback.className = 'invalid-feedback';
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
    }

    /**
     * Populate form from edit button data
     * @param {HTMLButtonElement} button - Edit button with data attributes
     * @public
     */
    populateForm(button) {
        if (!button) return;

        const data = button.dataset;

        // Populate basic fields
        document.getElementById('date').value = data.date || '';
        document.getElementById('orderId').value = data.orderId || '';
        document.getElementById('productionId').value = data.productionId || '';
        document.getElementById('omsId').value = data.omsId || '';
        document.getElementById('clientName').value = data.clientName || '';

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
        this.articleNumbersInput.value = data.articleNumbers || '';
        this.complexityInput.value = data.graphicComplexity || '';
        this.colorsInput.value = data.colors || '';

        // Populate text areas
        document.getElementById('observations').value = data.observations || '';

        // Set edit mode
        if (this.editingIdInput) this.editingIdInput.value = data.id || '';
        if (this.isEditInput) this.isEditInput.value = 'true';

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
