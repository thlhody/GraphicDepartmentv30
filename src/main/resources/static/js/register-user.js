// Constants for complexity calculations
const ACTION_TYPE_VALUES = {
    'ORDIN': 2.5,
    'REORDIN': 1.0,
    'CAMPION': 2.5,
    'PROBA STAMPA': 2.5,
    'ORDIN SPIZED': 2.0,
    'CAMPION SPIZED': 2.0,
    'PROBA S SPIZED': 2.0,
    'PROBA CULOARE': 2.5,
    'CARTELA CULORI': 2.5,
    'DESIGN': 2.5,
    'DESIGN 3D': 3.0,
    'PATTERN PREP': 2.5,
    'IMPOSTARE': 0.0,
    'OTHER': 2.5
};

// Print prep types that add complexity
const COMPLEXITY_PRINT_PREPS = {
    'SBS': 0.5,
    'NN': 0.5,
    'NAME': 0.5,
    'NUMBER': 0.5,
    'FLEX': 0.5,
    'BRODERIE': 0.5,
    'OTHER': 0.5
};

// Print prep types that don't affect complexity
const NEUTRAL_PRINT_PREPS = {
    'DIGITAL': 0.0,
    'GPT': 0.0,
    'FILM': 0.0
};

class RegisterFormHandler {
    constructor() {
        this.form = document.getElementById('registerForm');
        this.initializeFormElements();
        this.initializeForm();
    }

    initializeFormElements() {
        this.actionTypeSelect = document.getElementById('actionTypeSelect');
        this.printPrepSelect = document.getElementById('printPrepTypeSelect');
        this.complexityInput = document.getElementById('complexityInput');
        this.colorsInput = document.getElementById('colorsInput');
        this.defaultUrl = '/user/register/entry';
    }

    initializeForm() {
        if (!this.form) return;

        // Make sure the original select element has a tabindex
        $(this.printPrepSelect).attr('tabindex', '0');

        // Add custom CSS to control Select2 styling
        $('<style>')
            .text(`
            /* Select2 focus outline */
            .select2-container:focus {
                outline: 2px solid #007bff;
                border-radius: 4px;
            }

            /* Override default Select2 hover highlighting behavior */
            .select2-results__option--highlighted[aria-selected] {
                background-color: inherit !important;
                color: inherit !important;
            }

            /* Custom hover highlighting - only affects the exact option being hovered */
            .select2-results__option:hover {
                background-color: #f0f0f0 !important;
                color: #333 !important;
            }

            /* Custom highlighting for keyboard navigation */
            .select2-results__option.keyboard-highlight {
                background-color: #0d6efd !important;
                color: #fff !important;
            }
        `)
            .appendTo('head');

        // Fixed Select2 configuration
        $(this.printPrepSelect).select2({
            theme: 'bootstrap-5',
            width: '100%',
            placeholder: 'Select',
            multiple: true,
            maximumSelectionLength: 10,
            dropdownParent: $('body'),
            minimumResultsForSearch: 0, // Always show search
            tags: false,
            selectOnClose: false, // Changed false to prevent auto-selection
            closeOnSelect: false,

            // Custom formatting of selection with first letters
//            templateSelection: (data, container) => {
//                // Get all selected items
//                const selectedItems = $(this.printPrepSelect).select2('data');
//
//                if (!selectedItems || selectedItems.length === 0) {
//                    return $('<span class="select2-placeholder">Select</span>');
//                }
//
//                if (selectedItems.length === 1) {
//                    // If only 1 item, show it normally but shortened if needed
//                    const text = data.text.length > 7 ? data.text.substring(0, 7) + '...' : data.text;
//                    return $(`<span class="select2-single-selection">${text}</span>`);
//                }
//
//                // For multiple selections, show first letters
//                const initials = selectedItems.map(item => item.text.charAt(0).toUpperCase()).join('');
//
//                // Limit to 7 characters plus counter
//                const displayInitials = initials.length > 7 ? initials.substring(0, 7) + '...' : initials;
//
//                return $(`<span class="select2-initials">${displayInitials} <span class="select2-selection__pill-count">${selectedItems.length}</span></span>`);
//            },
//
//            // Clean dropdown formatting
//            templateResult: (data) => {
//                if (!data.id) return data.text;
//                return $(`<span>${data.text}</span>`);
//            }
            // Custom formatting of selection with first letters
            templateSelection: (data, container) => {
                // Check if this is the container for the entire selection
                if ($(container).hasClass('select2-selection__rendered')) {
                    // This is the full selection container — no need to handle here
                    return null;
                }

                // This is called for each individual pill/item
                if (data.text) {
                    const initial = data.text.charAt(0).toUpperCase();
                    return $(`<span class="select2-pill-initial">${initial}</span>`);
                }

                return null;
            },

        });

        // Get the Select2 container
        const select2Container = $(this.printPrepSelect).next('.select2-container');

        // Make the container focusable
        select2Container.attr('tabindex', '0');

        // Clean up any existing event handlers to prevent conflicts
        $(document).off('mouseenter mouseleave', '.select2-results__option');

        // Disable auto-selection behavior by overriding Select2's internal functions
        $.fn.select2.amd.require(['select2/results'], function(ResultsAdapter) {
            const origSetClasses = ResultsAdapter.prototype.setClasses;

            // Override the setClasses method to prevent highlighting on hover
            ResultsAdapter.prototype.setClasses = function() {
                // Call the original method but modify its behavior
                const result = origSetClasses.apply(this, arguments);

                // Find elements that got highlighted due to hover and remove the class
                const $highlighted = this.$results.find('.select2-results__option--highlighted');
                $highlighted.each(function() {
                    // Only modify elements that were highlighted by hover, not keyboard
                    if (!$(this).hasClass('keyboard-highlight')) {
                        $(this).removeClass('select2-results__option--highlighted');
                    }
                });

                return result;
            };
        });

        // Set up proper event listeners for the Select2 container
        this.setupSelect2Events();

        // Setup other event listeners
        this.setupEventListeners();
        this.initializeDefaultValues();
    }

    setupSelect2Events() {
        // When the container receives focus, open the dropdown
        $(this.printPrepSelect).next('.select2-container').on('focus', () => {
            $(this.printPrepSelect).select2('open');
        });

        // Make sure the search field gets focus when dropdown opens
        $(document).on('select2:open', () => {
            setTimeout(() => {
                $('.select2-search__field').focus();
            }, 0);
        });

        // Fix dropdown positioning
        $(this.printPrepSelect).on('select2:opening', function() {
            $(this).closest('.print-prep-container').css('z-index', 1055);
        });

        $(this.printPrepSelect).on('select2:closing', function() {
            setTimeout(() => {
                $(this).closest('.print-prep-container').css('z-index', '');
            }, 100);
        });

        // Add custom keyboard navigation handling
        $(document).on('keydown', '.select2-search__field', (e) => {
            if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                // Add custom class for keyboard navigation highlighting
                setTimeout(() => {
                    $('.select2-results__option--highlighted')
                        .addClass('keyboard-highlight');
                }, 0);
            }
        });

        // Prevent click propagation on selections
        $(document).on('click', '.select2-selection__choice__remove', function(e) {
            e.stopPropagation();
        });
    }

    prepareFormData() {
        // Ensure printPrepTypes are properly set in the form
        const selectedPrintTypes = $(this.printPrepSelect).val();
        if (selectedPrintTypes) {
            // Create hidden inputs for each selected print type
            selectedPrintTypes.forEach(type => {
                const input = document.createElement('input');
                input.type = 'hidden';
                input.name = 'printPrepTypes[]';
                input.value = type;
                this.form.appendChild(input);
            });
        }
    }

    copyEntry(button) {
        // Clear the form first
        this.resetForm();

        // Populate form with original entry's data
        const fields = [
            'date', 'orderId', 'productionId', 'omsId', 'clientName',
            'actionType', 'colorsProfile', 'articleNumbers',
            'graphicComplexity', 'observations'
        ];

        fields.forEach(field => {
            const input = this.form.querySelector(`[name="${field}"]`);
            if (input) {
                const value = button.getAttribute(`data-${field.replace(/([A-Z])/g, '-$1').toLowerCase()}`) || '';
                input.value = value;
            }
        });

        // Handle print prep types
        const printPrepTypesStr = button.getAttribute('data-print-prep-types');
        if (printPrepTypesStr) {
            const printPrepTypes = [...new Set(
                printPrepTypesStr.split(', ')
                    .map(type => type.trim())
                    .filter(type => type)
            )];
            $(this.printPrepSelect).val(printPrepTypes).trigger('change');
        }

        // Optionally modify some fields for a new entry
        // For example, reset the date to today or change action type
        const dateInput = this.form.querySelector('input[name="date"]');
        if (dateInput) {
            dateInput.value = new Date().toISOString().split('T')[0];
        }

        // Update calculated fields
        this.autoFillColors();
        this.updateComplexityField();

        // Scroll to form
        this.scrollToForm();
    }

    setupEventListeners() {

        // Add hover effect to show all selected items
        $(this.printPrepSelect).on('mouseenter', '.select2-selection__rendered', (e) => {
            const selectedItems = $(this.printPrepSelect).select2('data');
            if (selectedItems.length > 1) {
                // Create tooltip content
                const tooltipContent = selectedItems.map(item => item.text).join(', ');

                // You could use Bootstrap's tooltip or a simple title attribute
                $(e.currentTarget).attr('title', tooltipContent);
            }
        });
        // Form submission
        this.form.addEventListener('submit', (e) => this.handleSubmit(e));

        // Action type changes
        this.actionTypeSelect.addEventListener('change', () => {
            this.autoFillColors();
            this.updateComplexityField();
        });

        // Print prep type changes
        $(this.printPrepSelect).on('change', () => {
            this.updateComplexityField();
        });

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
                this.populateForm(button);
            });
        });

        // Clear button
        const clearButton = this.form.querySelector('button[type="button"]');
        if (clearButton) {
            clearButton.addEventListener('click', () => this.resetForm());
        }
    }

    initializeDefaultValues() {
        // Set default date to today
        const dateInput = this.form.querySelector('input[name="date"]');
        if (dateInput) {
            dateInput.value = new Date().toISOString().split('T')[0];
        }

        this.autoFillColors();
        this.updateComplexityField();
    }

    calculateComplexity(actionType, printPrepTypes) {
        if (!actionType) return 0;

        // Special cases first
        if (actionType === 'IMPOSTARE') return 0.0;
        if (actionType === 'REORDIN') return 1.0;
        if (actionType === 'ORDIN SPIZED') return 2.0;
        if (actionType === 'CAMPION SPIZED') return 2.0;
        if (actionType === 'PROBA S SPIZED') return 2.0;
        if (actionType === 'DESIGN 3D') return 3.0;

        // Fixed value actions
        const fixedValueActions = [
            'PROBA CULOARE', 'PROBA STAMPA', 'CARTELA CULORI',
            'DESIGN', 'PATTERN PREP', 'OTHER'
        ];

        if (fixedValueActions.includes(actionType)) {
            return ACTION_TYPE_VALUES[actionType];
        }

        // For ORDIN and CAMPION, check print prep types
        if (actionType === 'ORDIN' || actionType === 'CAMPION') {
            const baseValue = ACTION_TYPE_VALUES[actionType];

            // If no print prep types selected, return base value
            if (!printPrepTypes || !printPrepTypes.length) return baseValue;

            // Check if any complexity-adding print prep types are selected
            const hasComplexityType = printPrepTypes.some(type => type in COMPLEXITY_PRINT_PREPS);

            // If any complexity-adding type is selected, return maximum complexity (3.0)
            if (hasComplexityType) {
                return 3.0;
            }

            // If only neutral types are selected, return base value
            return baseValue;
        }

        console.log(`No special case match for "${cleanActionType}", using standard logic`);
        return ACTION_TYPE_VALUES[actionType] || 0;
    }

    updateComplexityField() {
        if (!this.complexityInput || !this.actionTypeSelect) return;

        // Get selected values correctly
        const selectedTypes = Array.from($(this.printPrepSelect).select2('data'))
            .map(item => item.text);

        const complexity = this.calculateComplexity(this.actionTypeSelect.value, selectedTypes);

        if (complexity >= 0) {
            this.complexityInput.value = complexity.toFixed(1);
        }
    }

    autoFillColors() {
        if (!this.colorsInput) return;

        // Auto-fill with 'A' if empty and action type is selected
        if (this.actionTypeSelect.value && (!this.colorsInput.value || this.colorsInput.value.trim() === '')) {
            this.colorsInput.value = 'A';
        }
    }

    validateForm() {
        let isValid = true;
        const requiredFields = [
            { name: 'date', message: 'Date is required.' },
            { name: 'orderId', message: 'Order ID is required.' },
            { name: 'omsId', message: 'OMS ID is required.' },
            { name: 'clientName', message: 'Client Name is required.' },
            { name: 'actionType', message: 'Action Type is required.' },
            { name: 'articleNumbers', message: 'Articles number is required.' }
        ];

        // Reset previous validation messages
        this.form.querySelectorAll('.is-invalid').forEach(el => {
            el.classList.remove('is-invalid');
        });
        this.form.querySelectorAll('.invalid-feedback').forEach(el => {
            el.remove();
        });

        // Check regular fields
        for (const field of requiredFields) {
            const element = this.form.elements[field.name];
            if (!element || !element.value || element.value.trim() === '') {
                this.addValidationError(element, field.message);
                isValid = false;
            }
        }

        // Special check for printPrepTypes (Select2 multiple)
        const selectedPrintTypes = $(this.printPrepSelect).val();
        if (!selectedPrintTypes || selectedPrintTypes.length === 0) {
            this.addValidationError(this.printPrepSelect, 'Print Prep Type is required.');
            isValid = false;
        }

        if (!isValid) {
            // Show error alert
            const alertContainer = document.querySelector('.alert-container');
            if (alertContainer) {
                const alert = document.createElement('div');
                alert.className = 'alert alert-danger alert-dismissible fade show';
                alert.innerHTML = `
                <strong>Warning</strong>
                <p>Please fill in all required fields.</p>
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            `;
                alertContainer.appendChild(alert);
            }
        }

        return isValid;
    }

    addValidationError(element, message) {
        element.classList.add('is-invalid');
        const feedback = document.createElement('div');
        feedback.className = 'invalid-feedback';
        feedback.textContent = message;
        element.parentElement.appendChild(feedback);
    }

    handleValidationError(errorMessage) {
        // Create a URL with the current path
        const url = new URL(window.location.pathname, window.location.origin);
        const formData = new FormData(this.form);

        // Add error parameter
        url.searchParams.append('error', errorMessage);

        // Add all form fields as parameters with 'form_' prefix
        formData.forEach((value, key) => {
            if (value && value.trim() !== '') {
                // Skip certain fields
                if (!['year', 'month', 'entryId', 'isEdit'].includes(key)) {
                    url.searchParams.append('form_' + key, value);
                }
            }
        });

        // Add year and month separately
        url.searchParams.append('year', formData.get('year'));
        url.searchParams.append('month', formData.get('month'));

        // Redirect with all parameters
        window.location.href = url.toString();
    }

    handleSubmit(event) {
        event.preventDefault();

        if (!this.validateForm()) {
            return;
        }

        // Clear any existing printPrepTypes hidden fields
        this.form.querySelectorAll('input[name="printPrepTypes"]').forEach(el => el.remove());

        // Get selected print types from Select2 and remove duplicates
        const selectedPrintTypes = Array.from(new Set($(this.printPrepSelect).val()));

        if (selectedPrintTypes && selectedPrintTypes.length > 0) {
            // Create a hidden input for each unique selected print type
            selectedPrintTypes.forEach(type => {
                const input = document.createElement('input');
                input.type = 'hidden';
                input.name = 'printPrepTypes';
                input.value = type;
                this.form.appendChild(input);
            });
        }

        // Submit the form
        this.form.submit();
    }

    scrollToForm() {
        // Calculate an offset to scroll a bit higher than the form
        const formContainer = document.querySelector('.card.shadow-sm[class*="mb-4"]:has(#registerForm)');
        if (formContainer) {
            // Get the current scroll position
            const currentScroll = window.pageYOffset;

            // Get the position of the form container
            const rect = formContainer.getBoundingClientRect();
            const scrollPosition = currentScroll + rect.top;

            // Subtract an additional offset (e.g., 100 pixels) to scroll higher
            window.scrollTo({
                top: Math.max(0, scrollPosition - 100),
                behavior: 'smooth'
            });
        } else {
            // Fallback if container not found
            this.form.scrollIntoView({
                behavior: 'smooth',
                block: 'start'
            });
        }
    }

    resetForm() {
        this.form.reset();
        this.form.action = this.defaultUrl;
        this.form.method = 'post';

        // Reset hidden fields
        document.getElementById('editingId').value = '';
        document.getElementById('isEdit').value = 'false';

        // Reset date to today
        const dateInput = this.form.querySelector('input[name="date"]');
        if (dateInput) {
            dateInput.value = new Date().toISOString().split('T')[0];
        }

        // Reset Select2
        $(this.printPrepSelect).val(null).trigger('change');

        // Reset submit button
        const submitButton = this.form.querySelector('button[type="submit"]');
        if (submitButton) {
            submitButton.innerHTML = '<i class="bi bi-plus-circle me-1"></i>Add Entry';
        }

        // Clear all validation states
        this.form.querySelectorAll('.is-invalid').forEach(el => {
            el.classList.remove('is-invalid');
        });

        this.form.querySelectorAll('.invalid-feedback').forEach(el => {
            el.remove();
        });

        // IMPORTANT: Remove Bootstrap's was-validated class from the form
        this.form.classList.remove('was-validated');

        // Reset the Select2 container style
        const select2Container = $(this.printPrepSelect).next('.select2-container');
        select2Container.removeClass('is-invalid');

        // Update calculated fields
        this.autoFillColors();
        this.updateComplexityField();
    }

    populateForm(button) {
        // Clear the form first to reset any previous values
        this.resetForm();

        const entryId = button.getAttribute('data-entry-id');
        this.form.action = `${this.defaultUrl}/${entryId}`;
        this.form.method = 'post';

        // Handle print prep types correctly - remove duplicates when populating
        const printPrepTypesStr = button.getAttribute('data-print-prep-types');
        if (printPrepTypesStr) {
            const printPrepTypes = [...new Set(
                printPrepTypesStr.split(', ')
                    .map(type => type.trim())
                    .filter(type => type)
            )];
            $(this.printPrepSelect).val(printPrepTypes).trigger('change');
        }

        // Populate all fields
        const fields = [
            'date', 'orderId', 'productionId', 'omsId', 'clientName',
            'actionType', 'colorsProfile', 'articleNumbers',
            'graphicComplexity', 'observations'
        ];

        fields.forEach(field => {
            const input = this.form.querySelector(`[name="${field}"]`);
            if (input) {
                input.value = button.getAttribute(`data-${field.replace(/([A-Z])/g, '-$1').toLowerCase()}`) || '';
            }
        });

        // Update button text
        const submitButton = this.form.querySelector('button[type="submit"]');
        submitButton.innerHTML = '<i class="bi bi-check-circle me-1"></i>Update';

        // Update hidden fields for editing
        const editingIdInput = document.getElementById('editingId');
        if (editingIdInput) {
            editingIdInput.value = entryId;
        }
        const isEditInput = document.getElementById('isEdit');
        if (isEditInput) {
            isEditInput.value = 'true';
        }

        // Recalculate complexity and colors
        this.autoFillColors();
        this.updateComplexityField();

        // Scroll to form - this will now scroll to the top
        this.scrollToForm();
    }

    // This should be called at the end of your document ready function
    setupTabNavigation() {
        // Add event listener for all Select2 dropdowns
        document.addEventListener('keydown', (e) => {
            // Check if Tab key was pressed
            if (e.key === 'Tab') {
                const activeElement = document.activeElement;

                // Check if we're tabbing to a Select2 element
                const select2Containers = document.querySelectorAll('.select2-container');
                select2Containers.forEach((container) => {
                    // Find the next Select2 in tab order
                    if (this.isNextTabStop(activeElement, container)) {
                        // Prevent default tab behavior
                        e.preventDefault();

                        // Focus the container
                        container.focus();

                        // Find the original select element
                        const selectId = container.getAttribute('data-select2-id');
                        if (selectId) {
                            const originalSelect = document.getElementById(selectId);
                            if (originalSelect) {
                                // Open the dropdown
                                $(originalSelect).select2('open');
                            }
                        }
                    }
                });
            }
        });
    }

    // Helper to check if an element is the next tab stop
    isNextTabStop(currentElement, targetElement) {
        if (!currentElement || !targetElement) return false;

        // Get all focusable elements
        const focusableElements = 'a, button, input, select, textarea, [tabindex]:not([tabindex="-1"])';
        const elements = Array.from(document.querySelectorAll(focusableElements))
            .filter(el => !el.disabled && el.offsetParent !== null); // Visible and enabled

        // Find current and target indices
        const currentIndex = elements.indexOf(currentElement);
        const targetIndex = elements.indexOf(targetElement);

        // Check if target is the next element in tab order
        return targetIndex === currentIndex + 1;
    }
}

class RegisterSummaryHandler {

    constructor() {
        // Initialize counters
        this.actionCounts = {
            ordin: 0,
            reordin: 0,
            campion: 0,
            probaStampa: 0,
            design: 0,
            others: 0,
            impostare: 0,
            ordinSpized: 0,
            campionSpized: 0,
            probaSSpized: 0
        };

        this.metrics = {
            totalEntries: 0,
            totalNoImpostare: 0,
            avgArticles: 0,
            avgComplexity: 0
        };

        this.setupObserver();
        // Initial calculation
        try {
            this.calculateStats();
        } catch (error) {
            console.error('Error calculating initial stats:', error);
        }

        // Add form submission handler to recalculate after form actions
        const registerForm = document.getElementById('registerForm');
        if (registerForm) {
            registerForm.addEventListener('submit', () => {
                // Allow time for DOM to update
                setTimeout(() => this.calculateStats(), 100);
            });
        }
    }

    setupObserver() {
        try {
            const tableBody = document.querySelector('.table tbody');
            if (tableBody) {
                const observer = new MutationObserver((mutations) => {
                    // Check if mutations actually affect our data
                    const hasRelevantChanges = mutations.some(mutation =>
                    mutation.type === 'childList' ||
                    (mutation.type === 'characterData' &&
                    mutation.target.parentElement?.tagName === 'TD')
                    );

                    if (hasRelevantChanges) {
                        this.calculateStats();
                    }
                });

                observer.observe(tableBody, {
                    childList: true,
                    subtree: true,
                    characterData: true
                });
            }
        } catch (error) {
            console.error('Error setting up observer:', error);
        }
    }

    calculateStats() {

        try {
            // Reset counters
            Object.keys(this.actionCounts).forEach(key => this.actionCounts[key] = 0);
            // Get all valid entries (excluding empty state row)
            const entries = Array.from(document.querySelectorAll('.table tbody tr'))
                .filter(row => !row.querySelector('.text-muted') && row.cells.length > 10);


            let totalArticles = 0;
            let totalComplexity = 0;
            let nonImpostareCount = 0;

            // Process each entry
            entries.forEach(row => {
                const cells = row.cells;
                const actionType = cells[6]?.textContent?.trim().toUpperCase();
                console.log("Processing entry with action type:", actionType);
                const articles = parseInt(cells[9]?.textContent || '0');
                const complexity = parseFloat(cells[10]?.textContent || '0');

                // Count action types
                switch(actionType) {
                    case 'ORDIN': this.actionCounts.ordin++; break;
                    case 'REORDIN': this.actionCounts.reordin++; break;
                    case 'CAMPION': this.actionCounts.campion++; break;
                    case 'PROBA STAMPA': this.actionCounts.probaStampa++; break;
                    case 'DESIGN':
                    case 'DESIGN 3D':this.actionCounts.design++;break;
                    case 'IMPOSTARE': this.actionCounts.impostare++; break;
                    case 'ORDIN SPIZED': this.actionCounts.ordinSpized++; break;
                    case 'CAMPION SPIZED': this.actionCounts.campionSpized++; break;
                    case 'PROBA S SPIZED': this.actionCounts.probaSSpized++; break;
                    default:
                        console.log("Unmatched action type:", actionType);
                        this.actionCounts.others++;
                        break;
                }

                // Calculate metrics excluding IMPOSTARE entries
                if (actionType !== 'IMPOSTARE') {
                    nonImpostareCount++;
                    totalArticles += articles;
                    totalComplexity += complexity;
                }
            });

            // Calculate metrics
            this.metrics.totalEntries = entries.length;
            this.metrics.totalNoImpostare = nonImpostareCount;
            this.metrics.avgArticles = nonImpostareCount ?
            (totalArticles / nonImpostareCount).toFixed(2) : '0.00';
            this.metrics.avgComplexity = nonImpostareCount ?
            (totalComplexity / nonImpostareCount).toFixed(2) : '0.00';
            this.updateUI();

        } catch (error) {
            console.error('Error calculating stats:', error);
            // Reset to safe values on error
            this.resetStats();
        }
    }

    resetStats() {
        Object.keys(this.actionCounts).forEach(key => this.actionCounts[key] = 0);
        this.metrics = {
            totalEntries: 0,
            totalNoImpostare: 0,
            avgArticles: '0.00',
            avgComplexity: '0.00'
        };
        this.updateUI();
    }

    updateUI() {
        try {
            const elements = {
                'count-ordin': this.actionCounts.ordin,
                'count-reordin': this.actionCounts.reordin,
                'count-campion': this.actionCounts.campion,
                'count-proba-stampa': this.actionCounts.probaStampa,
                'count-design': this.actionCounts.design,
                'count-others': this.actionCounts.others,
                'count-impostare': this.actionCounts.impostare,
                'count-ordin-spized': this.actionCounts.ordinSpized,
                'count-campion-spized': this.actionCounts.campionSpized,
                'count-proba-s-spized': this.actionCounts.probaSSpized,
                'total-entries': this.metrics.totalEntries,
                'total-entries-no-impostare': this.metrics.totalNoImpostare,
                'avg-articles': this.metrics.avgArticles,
                'avg-complexity': this.metrics.avgComplexity
            };

            Object.entries(elements).forEach(([id, value]) => {
                const element = document.getElementById(id);
                if (element) {
                    element.textContent = value;
                }
            });
        } catch (error) {
            console.error('Error updating UI:', error);
        }
    }
}
// Full and Basic Search
class UnifiedSearchHandler {

    constructor() {
        // Initialize modal elements
        this.searchModal = document.getElementById('searchModal');
        this.searchInput = document.getElementById('searchInput');
        this.resultsContainer = document.getElementById('searchResultsContainer');
        this.searchButton = document.getElementById('searchModalTrigger');

        // Initialize loading indicator
        this.loadingIndicator = document.createElement('div');
        this.loadingIndicator.className = 'search-loading-indicator';
        this.loadingIndicator.innerHTML = `
            <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">Loading...</span>
            </div>
        `;

        // Flag to track search mode
        this.isFullSearchMode = false;
        this.localSearchTimeoutId = null;

        // Extract entries from table for local search
        this.localEntries = this.extractEntriesFromTable();

        // Set up event listeners
        this.setupEventListeners();
    }

    extractEntriesFromTable() {
        const entries = [];
        document.querySelectorAll('.table tbody tr').forEach(row => {
            // Skip the empty state row
            if (row.querySelector('.text-muted')) return;

            const cells = row.cells;
            if (cells.length < 10) return;

            const entry = {
                date: cells[1]?.textContent.trim(),
                orderId: cells[2]?.textContent.trim(),
                productionId: cells[3]?.textContent.trim(),
                omsId: cells[4]?.textContent.trim(),
                clientName: cells[5]?.textContent.trim(),
                actionType: cells[6]?.textContent.trim(),
                printPrepTypes: cells[7]?.textContent.trim(),
                colorsProfile: cells[8]?.textContent.trim(),
                articleNumbers: cells[9]?.textContent.trim(),
                graphicComplexity: cells[10]?.textContent.trim(),
                observations: cells[11]?.textContent.trim(),
                entryId: row.querySelector('.copy-entry')?.getAttribute('data-entry-id'),
                rawRow: row
            };

            entries.push(entry);
        });
        return entries;
    }

    setupEventListeners() {
        // Global keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            if (e.ctrlKey && (e.key === 'f' || e.key === 'F')) {
                e.preventDefault();
                this.openSearchModal();
            }
            if (e.key === 'Escape') {
                this.closeSearchModal();
            }
        });

        // Search input listener with debounce
        this.searchInput.addEventListener('input', () => {
            clearTimeout(this.localSearchTimeoutId);
            this.localSearchTimeoutId = setTimeout(() => this.performSearch(), 250);
        });

        // Close modal when clicking outside
        this.searchModal.addEventListener('click', (e) => {
            if (e.target === this.searchModal) {
                this.closeSearchModal();
            }
        });

        // Add search modal trigger button event listener
        if (this.searchButton) {
            this.searchButton.addEventListener('click', () => {
                this.openSearchModal();
            });
        }

        // Toggle mode button
        const modeToggle = document.createElement('button');
        modeToggle.id = 'searchModeToggle';
        modeToggle.className = 'btn btn-sm btn-outline-secondary ms-2';
        modeToggle.innerHTML = 'Full Search Mode: OFF';
        this.searchModal.querySelector('.search-input-container').appendChild(modeToggle);

        modeToggle.addEventListener('click', () => {
            this.isFullSearchMode = !this.isFullSearchMode;
            modeToggle.innerHTML = `Full Search Mode: ${this.isFullSearchMode ? 'ON' : 'OFF'}`;

            // Update placeholder text based on mode
            if (this.isFullSearchMode) {
                this.searchInput.placeholder = 'Search across all register files (backend search)...';
            } else {
                this.searchInput.placeholder = 'Search current page entries (client-side)...';
            }

            // Clear previous results and perform search with new mode
            this.performSearch();
        });
    }

    openSearchModal() {
        // Show modal
        this.searchModal.classList.add('show');
        // Focus search input
        this.searchInput.focus();
        // Clear previous search results
        this.resultsContainer.innerHTML = '';
        // Reset search input
        this.searchInput.value = '';
    }

    closeSearchModal() {
        this.searchModal.classList.remove('show');
        this.searchInput.value = '';
        this.resultsContainer.innerHTML = '';
    }

    async performSearch() {
        const query = this.searchInput.value.trim();

        // Clear results container
        this.resultsContainer.innerHTML = '';

        // If query is empty, exit early
        if (!query) {
            return;
        }

        // Show loading indicator
        this.resultsContainer.appendChild(this.loadingIndicator);

        if (this.isFullSearchMode) {
            // Perform backend search
            await this.performFullSearch(query);
        } else {
            // Perform local search
            this.performLocalSearch(query);
        }
    }

    // Create a consistent header for both search types
    createResultsHeader() {
        const header = document.createElement('div');
        header.className = 'search-result-header';
        header.innerHTML = `
            <div>Date</div>
            <div>Order ID</div>
            <div>Prod ID</div>
            <div>OMS ID</div>
            <div>Client</div>
            <div>Action</div>
            <div>Print Types</div>
            <div>Mod</div>
        `;
        return header;
    }

    performLocalSearch(query) {
        // Remove loading indicator
        this.loadingIndicator.remove();

        // If query is empty, exit early
        if (!query) {
            this.resultsContainer.innerHTML = '';
            return;
        }

        // Convert query to lowercase and split into search terms
        const searchTerms = query.toLowerCase().split(/\s+/).filter(term => term.length > 0);

        // Filter entries based on search terms
        const matchingEntries = this.localEntries.filter(entry => {
            // Check if ALL search terms match at least one field
            return searchTerms.every(term =>
            (entry.orderId && entry.orderId.toLowerCase().includes(term)) ||
            (entry.productionId && entry.productionId.toLowerCase().includes(term)) ||
            (entry.omsId && entry.omsId.toLowerCase().includes(term)) ||
            (entry.clientName && entry.clientName.toLowerCase().includes(term)) ||
            (entry.actionType && entry.actionType.toLowerCase().includes(term)) ||
            (entry.printPrepTypes && entry.printPrepTypes.toLowerCase().includes(term)) ||
            (entry.observations && entry.observations.toLowerCase().includes(term))
            );
        });

        this.displayLocalSearchResults(matchingEntries);
    }

    displayLocalSearchResults(entries) {
        // Clear previous results
        this.resultsContainer.innerHTML = '';

        // If no results
        if (entries.length === 0) {
            this.resultsContainer.innerHTML = `
                <div class="p-3 text-center text-muted">
                    No entries found matching your search.
                </div>
            `;
            return;
        }

        // Create header for search results - using the consistent header
        this.resultsContainer.appendChild(this.createResultsHeader());

        // Render results
        entries.forEach(entry => {
            const resultRow = document.createElement('div');
            resultRow.className = 'search-result-row';
            resultRow.innerHTML = `
                <div>${entry.date}</div>
                <div>${entry.orderId}</div>
                <div>${entry.productionId || ''}</div>
                <div>${entry.omsId}</div>
                <div>${entry.clientName}</div>
                <div>
                    <span class="badge ${this.getActionTypeBadgeClass(entry.actionType)}">
                        ${entry.actionType || ''}
                    </span>
                </div>
                <div>${entry.printPrepTypes || ''}</div>
                <div>
                    <button class="btn btn-sm btn-outline-secondary copy-search-entry"
                        data-entry-id="${entry.entryId}"
                        data-date="${entry.date}"
                        data-order-id="${entry.orderId}"
                        data-production-id="${entry.productionId || ''}"
                        data-oms-id="${entry.omsId}"
                        data-client-name="${entry.clientName}"
                        data-action-type="${entry.actionType}"
                        data-print-prep-types="${entry.printPrepTypes}"
                        data-colors-profile="${entry.colorsProfile}"
                        data-article-numbers="${entry.articleNumbers}"
                        data-graphic-complexity="${entry.graphicComplexity}"
                        data-observations="${entry.observations || ''}">
                        <i class="bi bi-copy"></i> Copy
                    </button>
                </div>
            `;

            // Add click event to copy entry
            resultRow.querySelector('.copy-search-entry').addEventListener('click', (e) => {
                e.preventDefault();
                window.registerFormHandler.copyEntry(e.currentTarget);
                this.closeSearchModal();
            });

            this.resultsContainer.appendChild(resultRow);
        });
    }

    async performFullSearch(query) {
        try {
            // Fetch search results from backend
            const response = await fetch(`/user/register/full-search?query=${encodeURIComponent(query)}`, {
                headers: {
                    'Accept': 'application/json'
                }
            });

            // Remove loading indicator
            this.loadingIndicator.remove();

            const results = await response.json();

            // Check if no results
            if (results.length === 0) {
                this.resultsContainer.innerHTML = `
                    <div class="p-3 text-center text-muted">
                        No entries found matching your search.
                    </div>
                `;
                return;
            }

            // Create header for search results - using the consistent header
            this.resultsContainer.appendChild(this.createResultsHeader());

            // Render results
            results.forEach(entry => {
                const resultRow = document.createElement('div');
                resultRow.className = 'search-result-row full-search-result';

                // Format date for display
                const entryDate = entry.date ? new Date(entry.date).toLocaleDateString() : '';

                resultRow.innerHTML = `
                    <div>${entryDate}</div>
                    <div>${entry.orderId || ''}</div>
                    <div>${entry.productionId || ''}</div>
                    <div>${entry.omsId || ''}</div>
                    <div>${entry.clientName || ''}</div>
                    <div>
                        <span class="badge ${this.getActionTypeBadgeClass(entry.actionType)}">
                            ${entry.actionType || ''}
                        </span>
                    </div>
                    <div>${entry.printPrepTypes ? entry.printPrepTypes.join(', ') : ''}</div>
                    <div>
                        <button class="btn btn-sm btn-outline-primary copy-search-entry">
                            <i class="bi bi-copy"></i> Copy
                        </button>
                    </div>
                `;

                // Add copy functionality
                const copyButton = resultRow.querySelector('.copy-search-entry');
                copyButton.addEventListener('click', (e) => {
                    e.preventDefault();
                    this.copyEntryToMainForm(entry);
                    this.closeSearchModal();
                });

                this.resultsContainer.appendChild(resultRow);
            });

        } catch (error) {
            // Remove loading indicator
            this.loadingIndicator.remove();

            console.error('Full search error:', error);
            this.resultsContainer.innerHTML = `
                <div class="p-3 text-center text-danger">
                    <i class="bi bi-exclamation-triangle me-2"></i>
                    Error performing search: ${error.message}
                </div>
            `;
        }
    }

    getActionTypeBadgeClass(actionType) {
        if (!actionType) return 'bg-other';

        // Reuse existing badge classes from register-user.css
        const badgeClasses = {
            'ORDIN': 'bg-order',
            'REORDIN': 'bg-reorder',
            'CAMPION': 'bg-sample',
            'PROBA STAMPA': 'bg-strikeoff',
            'DESIGN': 'bg-designs',
            'IMPOSTARE': 'bg-layout',
            'ORDIN SPIZED': 'bg-spized',
            'CAMPION SPIZED': 'bg-spized',
            'PROBA S SPIZED': 'bg-spized'
        };
        return badgeClasses[actionType] || 'bg-other';
    }

    copyEntryToMainForm(entry) {
        // Use existing copyEntry method from RegisterFormHandler
        if (window.registerFormHandler && window.registerFormHandler.copyEntry) {
            // Create a temporary button with entry data
            const tempButton = document.createElement('button');

            // Format date if needed
            let dateStr = entry.date;
            if (typeof entry.date === 'string' && entry.date.includes('T')) {
                // If date contains time part, convert to YYYY-MM-DD
                dateStr = entry.date.split('T')[0];
            } else if (entry.date instanceof Date) {
                // If it's a Date object, convert to YYYY-MM-DD
                dateStr = entry.date.toISOString().split('T')[0];
            }

            tempButton.setAttribute('data-date', dateStr);
            tempButton.setAttribute('data-order-id', entry.orderId || '');
            tempButton.setAttribute('data-production-id', entry.productionId || '');
            tempButton.setAttribute('data-oms-id', entry.omsId || '');
            tempButton.setAttribute('data-client-name', entry.clientName || '');
            tempButton.setAttribute('data-action-type', entry.actionType || '');
            tempButton.setAttribute('data-print-prep-types',
                Array.isArray(entry.printPrepTypes) ? entry.printPrepTypes.join(', ') : entry.printPrepTypes || '');
            tempButton.setAttribute('data-colors-profile', entry.colorsProfile || '');
            tempButton.setAttribute('data-article-numbers', entry.articleNumbers || '1');
            tempButton.setAttribute('data-graphic-complexity', entry.graphicComplexity || '');
            tempButton.setAttribute('data-observations', entry.observations || '');

            // Call copyEntry with the temp button
            window.registerFormHandler.copyEntry(tempButton);
        }
    }
}

/**
 * AJAX Form Handler with Toast Alerts
 * Add this at the end of your register-user.js file
 */
class AjaxFormHandler {
    constructor() {
        // Store references to the original form handler methods we need to use
        this.originalHandleSubmit = null;
        this.setupFormSubmissions();

        // Add a namespace to our event to avoid conflicts
        this.deleteEventNamespace = 'ajaxFormHandler.delete';
        this.setupDeleteButtons();

        // Add hidden div for flash messages
        this.createFlashMessageElements();
    }

    clearValidationState(form) {
        // Remove all 'is-invalid' classes from form elements
        form.querySelectorAll('.is-invalid').forEach(el => {
            el.classList.remove('is-invalid');
        });

        // Remove all validation feedback messages
        form.querySelectorAll('.invalid-feedback').forEach(el => {
            el.remove();
        });
    }

    createFlashMessageElements() {
        // Add hidden container for flash messages if it doesn't exist
        if (!document.getElementById('flashMessagesHidden')) {
            const container = document.createElement('div');
            container.id = 'flashMessagesHidden';
            container.style.display = 'none';
            container.innerHTML = `
                <div id="successMessageContainer"></div>
                <div id="errorMessageContainer"></div>
                <div id="periodErrorContainer"></div>
            `;
            document.body.appendChild(container);
        }
    }

    setupFormSubmissions() {
        // Get the register form
        const form = document.getElementById('registerForm');
        if (!form) return;

        // Store reference to the original handler if it exists
        if (window.registerFormHandler && window.registerFormHandler.handleSubmit) {
            this.originalHandleSubmit = window.registerFormHandler.handleSubmit.bind(window.registerFormHandler);

            // Override the handleSubmit method in the original handler
            window.registerFormHandler.handleSubmit = (event) => {
                event.preventDefault();

                // Let the original form handler validate the form
                if (!window.registerFormHandler.validateForm()) {
                    return;
                }

                // Prepare form data just like the original handler
                form.querySelectorAll('input[name="printPrepTypes"]').forEach(el => el.remove());
                const selectedPrintTypes = Array.from(new Set($(window.registerFormHandler.printPrepSelect).val()));
                if (selectedPrintTypes && selectedPrintTypes.length > 0) {
                    selectedPrintTypes.forEach(type => {
                        const input = document.createElement('input');
                        input.type = 'hidden';
                        input.name = 'printPrepTypes';
                        input.value = type;
                        form.appendChild(input);
                    });
                }

                // Submit via AJAX instead of normal form submission
                this.submitFormViaAjax(form);
            };
        } else {
            // If registerFormHandler is not available, add our own handler
            form.addEventListener('submit', (event) => {
                event.preventDefault();
                this.submitFormViaAjax(form);
            });
        }
    }

    setupDeleteButtons() {
        // Use event delegation from a parent element that doesn't change
        // This way we don't need to rebind events after the table refreshes
        const tableBody = document.querySelector('.table tbody');
        if (!tableBody) return;

        // Remove any existing listeners with our namespace to avoid duplicates
        $(tableBody).off(this.deleteEventNamespace);

        // Use event delegation - attach a single listener to the table body
        // that watches for submit events from delete forms
        $(tableBody).on('submit' + this.deleteEventNamespace, 'form[action*="/user/register/delete"]', (event) => {
            event.preventDefault();
            event.stopPropagation(); // Prevent event bubbling

            // Get the actual form element
            const form = event.target;

            // Show confirmation dialog only once
            if (confirm('Are you sure you want to delete this entry?')) {
                this.submitFormViaAjax(form);
            }
        });
    }

    submitFormViaAjax(form) {
        // Show a loading indicator
        this.showLoading();

        // Create FormData object from the form
        const formData = new FormData(form);

        // Get form action and method
        const url = form.action;
        const method = form.method.toUpperCase();

        // Make the AJAX request
        fetch(url, {
            method: method,
            body: formData,
            headers: {
                'X-Requested-With': 'XMLHttpRequest'
            }
        })
            .then(response => {
            // For redirect responses, we'll handle them specially
            if (response.redirected) {
                console.log('Redirected to:', response.url);

                // Parse the redirect URL
                const redirectUrl = new URL(response.url);
                const params = new URLSearchParams(redirectUrl.search);

                // Check if we need to update the year and month in URL
                const year = params.get('year');
                const month = params.get('month');

                if (year && month) {
                    // Update URL without page reload
                    const newUrl = `/user/register?year=${year}&month=${month}`;
                    window.history.pushState({ path: newUrl }, '', newUrl);
                }

                // Load the response HTML to extract messages
                return response.text().then(html => {
                    // We got a successful response with a redirect
                    this.handleSuccessfulSubmission(form, html);
                    return { success: true, html };
                });
            } else {
                // Non-redirect response, likely an error
                console.log('Non-redirect response:', response.status);
                return response.text().then(text => ({ success: false, text }));
            }
        })
            .then(result => {
            if (!result.success) {
                this.showToast('Error', 'Failed to process the request. Please try again.', 'error');
            }
        })
            .catch(error => {
            console.error('Error submitting form:', error);
            this.showToast('Error', 'An error occurred while submitting the form. Please try again.', 'error');
        })
            .finally(() => {
            // Hide loading indicator
            this.hideLoading();

            // Reset form if it was an add operation (not edit or delete)
            const isEdit = form.querySelector('input[name="isEdit"]')?.value === 'true';
            const isDelete = form.action.includes('/delete');

            if (!isDelete) {
                // Reset form for new entries
                if (window.registerFormHandler) {
                    window.registerFormHandler.resetForm();
                } else {
                    form.reset();
                    // Also clear validation state if registerFormHandler is not available
                    this.clearValidationState(form);
                }
            } else {
                // For edit/delete operations, just clear validation state
                this.clearValidationState(form);
            }

            // Reload entries
            this.reloadEntries();
        });
    }

    handleSuccessfulSubmission(form, responseHtml) {
        // Reset all form validation states immediately
        this.fixFormValidationStates();

        // Create a DOM parser to extract messages from the response
        const parser = new DOMParser();
        const doc = parser.parseFromString(responseHtml, 'text/html');

        // Try to find success message in the redirect response
        const successMsgElement = doc.querySelector('[th\\:text="${successMessage}"]');
        if (successMsgElement && successMsgElement.textContent) {
            this.showToast('Success', successMsgElement.textContent, 'success');
            return;
        }

        // Try to find error message
        const errorMsgElement = doc.querySelector('[th\\:text="${errorMessage}"]');
        if (errorMsgElement && errorMsgElement.textContent) {
            this.showToast('Error', errorMsgElement.textContent, 'error');
            return;
        }

        // Try to find period error
        const periodErrorElement = doc.querySelector('[th\\:text="${periodError}"]');
        if (periodErrorElement && periodErrorElement.textContent) {
            this.showToast('Period Error', periodErrorElement.textContent, 'error');
            return;
        }

        // Check for URL parameters in the response URL
        const responseUrl = new URL(document.location.href);
        const params = new URLSearchParams(responseUrl.search);
        const errorParam = params.get('error');

        if (errorParam) {
            // Map error codes to messages
            const errorMessages = {
                'date_required': 'Please select a date before adding.',
                'missing_date': 'Please select a date.',
                'missing_order_id': 'Order ID is required.',
                'missing_oms_id': 'OMS ID is required.',
                'missing_client': 'Client name is required.',
                'missing_action_type': 'Action Type is required.',
                'missing_print_type': 'Print Type is required.',
                'missing_articles': 'Articles number is required.',
                'add_failed': 'Failed to add entry. Please try again.',
                'update_failed': 'Failed to update entry. Please try again.',
                'delete_failed': 'Failed to delete entry. Please try again.',
                'save_failed': 'Failed to save entry. Please try again.'
            };

            const errorMessage = errorMessages[errorParam] || 'An unexpected error occurred.';
            this.showToast('Warning', errorMessage, 'warning');

            // Clean up URL
            if (window.history.replaceState) {
                const cleanUrl = window.location.pathname + window.location.search;
                window.history.replaceState({}, document.title, cleanUrl);
            }
            return;
        }

        // If no specific message found, show a generic success message
        const isEdit = form.querySelector('input[name="isEdit"]')?.value === 'true';
        const isDelete = form.action.includes('/delete');

        let message = 'Operation completed successfully';
        if (isEdit) {
            message = 'Entry updated successfully';
        } else if (isDelete) {
            message = 'Entry deleted successfully';
        } else {
            message = 'Entry added successfully';
        }

        this.showToast('Success', message, 'success');
        this.fixFormValidationStates();
    }

    reloadEntries() {
        // Get current year and month from URL
        const params = new URLSearchParams(window.location.search);
        const year = params.get('year') || new Date().getFullYear();
        const month = params.get('month') || (new Date().getMonth() + 1);

        // Show loading in the table
        const tableBody = document.querySelector('.table tbody');
        if (tableBody) {
            tableBody.innerHTML = `
                <tr>
                    <td colspan="14" class="text-center py-3">
                        <div class="spinner-border text-primary" role="status"></div>
                        <p class="mt-2">Refreshing entries...</p>
                    </td>
                </tr>
            `;
        }

        // Fetch current page to get updated entries
        fetch(`/user/register?year=${year}&month=${month}`, {
            method: 'GET',
            headers: {
                'X-Requested-With': 'XMLHttpRequest'
            }
        })
            .then(response => response.text())
            .then(html => {
            // Parse the HTML response
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, 'text/html');

            // Extract the entries table body
            const newTableBody = doc.querySelector('.table tbody');
            if (newTableBody && tableBody) {
                tableBody.innerHTML = newTableBody.innerHTML;

                // Reattach event handlers to the new elements
                this.reattachEventHandlers();

                // Update stats
                if (window.registerSummaryHandler) {
                    window.registerSummaryHandler.calculateStats();
                }
            }
        })
            .catch(error => {
            console.error('Error reloading entries:', error);
            if (tableBody) {
                tableBody.innerHTML = `
                    <tr>
                        <td colspan="14" class="text-center py-3">
                            <div class="text-danger">
                                <i class="bi bi-exclamation-triangle"></i>
                                <p>Failed to refresh entries. Please reload the page.</p>
                            </div>
                        </td>
                    </tr>
                `;
            }
        });
    }

    reattachEventHandlers() {
        // Reattach edit handlers
        document.querySelectorAll('.edit-entry').forEach(button => {
            button.addEventListener('click', (e) => {
                e.preventDefault();
                if (window.registerFormHandler && window.registerFormHandler.populateForm) {
                    window.registerFormHandler.populateForm(button);
                }
            });
        });

        // Reattach copy handlers
        document.querySelectorAll('.copy-entry').forEach(button => {
            button.addEventListener('click', (e) => {
                e.preventDefault();
                if (window.registerFormHandler && window.registerFormHandler.copyEntry) {
                    window.registerFormHandler.copyEntry(button);
                }
            });
        });

        // No need to reattach delete handlers when using event delegation
        // The parent element listener handles all forms, even new ones
    }


    showLoading() {
        // Create or show a loading overlay
        let loadingOverlay = document.getElementById('loadingOverlay');

        if (!loadingOverlay) {
            loadingOverlay = document.createElement('div');
            loadingOverlay.id = 'loadingOverlay';
            loadingOverlay.className = 'loading-overlay';
            loadingOverlay.innerHTML = `
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">Loading...</span>
                </div>
            `;
            document.body.appendChild(loadingOverlay);

            // Add necessary styles if not already present
            if (!document.getElementById('loadingOverlayStyles')) {
                const style = document.createElement('style');
                style.id = 'loadingOverlayStyles';
                style.textContent = `
                    .loading-overlay {
                        position: fixed;
                        top: 0;
                        left: 0;
                        width: 100%;
                        height: 100%;
                        background-color: rgba(0, 0, 0, 0.3);
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        z-index: 9999;
                        opacity: 0;
                        transition: opacity 0.3s ease;
                    }
                    .loading-overlay.show {
                        opacity: 1;
                    }
                `;
                document.head.appendChild(style);
            }
        }

        // Show the overlay with animation
        setTimeout(() => {
            loadingOverlay.classList.add('show');
        }, 10);
    }

    hideLoading() {
        // Hide and remove the loading overlay
        const loadingOverlay = document.getElementById('loadingOverlay');
        if (loadingOverlay) {
            loadingOverlay.classList.remove('show');
            setTimeout(() => {
                if (loadingOverlay.parentNode) {
                    loadingOverlay.parentNode.removeChild(loadingOverlay);
                }
            }, 300);
        }
    }

    fixFormValidationStates() {
        // This function is called after any form action to ensure clean validation states
        const allForms = document.querySelectorAll('form');
        allForms.forEach(form => {
            // Remove Bootstrap's was-validated class
            form.classList.remove('was-validated');

            // Remove all is-invalid classes
            form.querySelectorAll('.is-invalid').forEach(el => {
                el.classList.remove('is-invalid');
            });

            // Remove all validation feedback messages
            form.querySelectorAll('.invalid-feedback').forEach(el => {
                el.remove();
            });
        });

        // Fix Select2 validation states
        document.querySelectorAll('.select2-container').forEach(container => {
            container.classList.remove('is-invalid');
        });
    }

    showToast(title, message, type) {
        // First try to use the existing toast system
        if (typeof window.showToast === 'function') {
            window.showToast(title, message, type);
            return;
        }

        // Otherwise use our own implementation
        let toastContainer = document.getElementById('toastAlertContainer');
        if (!toastContainer) {
            toastContainer = document.createElement('div');
            toastContainer.id = 'toastAlertContainer';
            toastContainer.className = 'toast-alert-container position-fixed top-0 end-0 p-3';
            document.body.appendChild(toastContainer);
        }

        // Generate unique ID for the toast
        const toastId = `toast-${Date.now()}-${Math.floor(Math.random() * 1000)}`;

        // Get icon based on type
        let icon = 'bi-info-circle-fill';
        if (type === 'success') icon = 'bi-check-circle-fill';
        if (type === 'error') icon = 'bi-exclamation-circle-fill';
        if (type === 'warning') icon = 'bi-exclamation-triangle-fill';

        // Create toast HTML
        const toastHTML = `
            <div id="${toastId}" class="toast-alert toast-${type}">
                <div class="toast-alert-header">
                    <div class="d-flex align-items-center">
                        <div class="toast-alert-icon">
                            <i class="bi ${icon}"></i>
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
        toastContainer.insertAdjacentHTML('beforeend', toastHTML);
        const toastElement = document.getElementById(toastId);

        // Show toast with animation
        setTimeout(() => {
            toastElement.classList.add('showing');
        }, 10);

        // Add progress bar animation
        const progressBar = toastElement.querySelector('.toast-alert-progress');
        if (progressBar) {
            progressBar.style.transition = 'width 5000ms linear';
            setTimeout(() => {
                progressBar.style.width = '100%';
            }, 10);
        }

        // Add close button event listener
        const closeButton = toastElement.querySelector('.toast-alert-close');
        if (closeButton) {
            closeButton.addEventListener('click', () => {
                toastElement.classList.remove('showing');
                toastElement.classList.add('hiding');

                // Remove after animation completes
                setTimeout(() => {
                    if (toastElement && toastElement.parentNode) {
                        toastElement.parentNode.removeChild(toastElement);
                    }
                }, 300);
            });
        }

        // Auto-dismiss after 5 seconds
        setTimeout(() => {
            if (toastElement) {
                toastElement.classList.remove('showing');
                toastElement.classList.add('hiding');

                // Remove after animation completes
                setTimeout(() => {
                    if (toastElement && toastElement.parentNode) {
                        toastElement.parentNode.removeChild(toastElement);
                    }
                }, 300);
            }
        }, 5000);
    }
}

// Ensure the script runs after DOM is fully loaded
(function() {
    // Function to toggle action buttons
    function toggleActionButtons(event) {
        event.stopPropagation();

        // Close all other open menus first
        const allActionButtons = document.querySelectorAll('.action-buttons');
        allActionButtons.forEach(buttons => {
            if (buttons !== event.currentTarget.nextElementSibling) {
                buttons.classList.remove('show');
            }
        });

        // Toggle the clicked button's action buttons
        const actionButtons = event.currentTarget.nextElementSibling;
        actionButtons.classList.toggle('show');
    }

    // Add event listeners to all toggle buttons
    function initializeActionToggles() {
        const toggleButtons = document.querySelectorAll('.action-toggle');
        toggleButtons.forEach(button => {
            button.addEventListener('click', toggleActionButtons);
        });
    }

    // Close action buttons when clicking outside
    function setupOutsideClickHandler() {
        document.addEventListener('click', (e) => {
            const actionContainers = document.querySelectorAll('.action-container');
            actionContainers.forEach(container => {
                // Check if the click is outside the action container
                if (!container.contains(e.target)) {
                    const actionButtons = container.querySelector('.action-buttons');
                    if (actionButtons) {
                        actionButtons.classList.remove('show');
                    }
                }
            });
        });
    }

    // Run initialization when DOM is ready
    function init() {
        initializeActionToggles();
        setupOutsideClickHandler();
    }

    // Use both DOMContentLoaded and init method for robustness
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();

// Initialize on DOM load
document.addEventListener('DOMContentLoaded', () => {
    console.log("Initializing RegisterFormHandler...");

    window.registerFormHandler = new RegisterFormHandler();
    window.registerFormHandler.setupTabNavigation();
    window.registerSummaryHandler = new RegisterSummaryHandler();
    window.unifiedSearchHandler = new UnifiedSearchHandler();
    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
        searchInput.placeholder = 'Search current page entries (client-side)...';
    }

    // Enhanced keyboard handling for Select2
    $(document).off('select2:open.kb').on('select2:open.kb', function() {
        console.log("Select2 opened, focusing search field...");
        setTimeout(function() {
            // Focus the search field
            const searchField = $('.select2-search__field');
            if (searchField.length) {
                searchField.focus();
                console.log("Search field focused");
            }
        }, 100);
    });

    // Improve keyboard navigation in search field
    $(document).off('keydown.select2nav').on('keydown.select2nav', '.select2-search__field', function(e) {
        console.log("Key pressed in search field:", e.key);

        // Allow normal typing for filtering
        if (e.key.length === 1) {
            return true;
        }

        // Handle arrow keys and Enter
        if (e.key === 'Enter') {
            const highlighted = $('.select2-results__option--highlighted');
            if (highlighted.length) {
                console.log("Enter pressed on highlighted option");
                e.preventDefault();
                e.stopPropagation();
                highlighted.trigger('mouseup');
                return false;
            }
        }
    });

    // Fix for direct tab to select2
    $('.select2-selection').on('focus', function() {
        console.log("Selection focused, opening dropdown");
        const container = $(this).closest('.select2-container');
        const selectId = container.attr('data-select2-id');
        if (selectId) {
            $('#' + selectId).select2('open');
        }
    });
    // Initialize our AJAX form handler after the page has loaded
    // This should be at the end of your existing DOMContentLoaded event
    setTimeout(() => {
        window.ajaxFormHandler = new AjaxFormHandler();
        console.log('AJAX Form Handler initialized');
    }, 500); // Small delay to ensure other handlers are initialized first
    // Add this at the end of your initialization
    setTimeout(() => {
        // Wait a short time then fix any validation states that might be present
        if (window.ajaxFormHandler) {
            window.ajaxFormHandler.fixFormValidationStates();
        }
    }, 1000);
});
