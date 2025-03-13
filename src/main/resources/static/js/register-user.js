// Constants for complexity calculations
const ACTION_TYPE_VALUES = {
    'ORDIN': 2.5,
    'REORDIN': 1.0,
    'CAMPION': 2.5,
    'PROBA STAMPA': 2.5,
    'ORDIN SPIZED': 2.0,
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

        // Initialize Select2 with simplified configuration
        $(this.printPrepSelect).select2({
            theme: 'bootstrap-5',
            width: '100%',
            placeholder: 'Select types',
            multiple: true,
            dropdownParent: $(this.printPrepSelect).parent(),
            selectionCssClass: 'form-select-sm'
        });

        this.setupEventListeners();
        this.initializeDefaultValues();
    }

    initializeSelect2() {
        // Initialize Select2 with custom configuration
        this.printPrepSelect.select2({
            theme: 'bootstrap-5',
            width: '100%',
            placeholder: 'Select print types...',
            allowClear: true,
            multiple: true,
            closeOnSelect: false,
            dropdownCssClass: 'select2-dropdown-medium'
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
        this.form.scrollIntoView({ behavior: 'smooth', block: 'start' });
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
        submitButton.innerHTML = '<i class="bi bi-plus-circle me-1"></i>Add Entry';

        // Update calculated fields
        this.autoFillColors();
        this.updateComplexityField();
    }

    populateForm(button) {
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

        this.scrollToForm();
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
            impostare: 0
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
                    default: this.actionCounts.others++; break;
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

class RegisterSearchHandler {
    constructor() {
        this.modal = document.getElementById('searchModal');
        this.searchInput = document.getElementById('searchInput');
        this.resultsContainer = document.getElementById('searchResultsContainer');
        this.allEntries = this.extractEntriesFromTable();
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
                date: cells[1].textContent.trim(),
                orderId: cells[2].textContent.trim(),
                productionId: cells[3].textContent.trim(),
                omsId: cells[4].textContent.trim(),
                clientName: cells[5].textContent.trim(),
                actionType: cells[6].textContent.trim(),
                printPrepTypes: cells[7].textContent.trim(),
                colorsProfile: cells[8].textContent.trim(),
                articleNumbers: cells[9].textContent.trim(),
                graphicComplexity: cells[10].textContent.trim(),
                observations: cells[11].textContent.trim(),
                entryId: row.querySelector('.copy-entry')?.getAttribute('data-entry-id'),
                rawRow: row
            };

            entries.push(entry);
        });
        return entries;
    }

    setupEventListeners() {
        // Add global keydown listener for search modal
        document.addEventListener('keydown', (e) => {
            if (e.ctrlKey && (e.key === 'f' || e.key === 'F')) {
                e.preventDefault();
                this.openSearchModal();
            }
            if (e.key === 'Escape') {
                this.closeSearchModal();
            }
        });

        // Search input listener
        this.searchInput.addEventListener('input', () => this.performSearch());

        // Close modal when clicking outside
        this.modal.addEventListener('click', (e) => {
            if (e.target === this.modal) {
                this.closeSearchModal();
            }
        });
    }

    openSearchModal() {
        this.modal.classList.add('show');
        this.searchInput.focus();
    }

    closeSearchModal() {
        this.modal.classList.remove('show');
        this.searchInput.value = '';
        this.resultsContainer.innerHTML = '';
    }

    performSearch() {
        const query = this.searchInput.value.toLowerCase().trim();

        // If query is empty, clear results
        if (!query) {
            this.resultsContainer.innerHTML = '';
            return;
        }

        // Filter entries based on order ID or client name
        const matchingEntries = this.allEntries.filter(entry =>
        entry.orderId.toLowerCase().includes(query) ||
        entry.clientName.toLowerCase().includes(query)
        );

        // Display results
        this.displaySearchResults(matchingEntries);
    }

    displaySearchResults(entries) {
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

        // Render results
        entries.forEach(entry => {
            const resultRow = document.createElement('div');
            resultRow.classList.add('search-result-row');
            resultRow.innerHTML = `
                            <div>${entry.date}</div>
                            <div>${entry.orderId}</div>
                            <div>${entry.clientName}</div>
                            <div>${entry.actionType}</div>
                            <div>
                                <button class="btn btn-sm btn-outline-secondary copy-search-entry"
                                    data-entry-id="${entry.entryId}"
                                    data-date="${entry.date}"
                                    data-order-id="${entry.orderId}"
                                    data-production-id="${entry.productionId}"
                                    data-oms-id="${entry.omsId}"
                                    data-client-name="${entry.clientName}"
                                    data-action-type="${entry.actionType}"
                                    data-print-prep-types="${entry.printPrepTypes}"
                                    data-colors-profile="${entry.colorsProfile}"
                                    data-article-numbers="${entry.articleNumbers}"
                                    data-graphic-complexity="${entry.graphicComplexity}"
                                    data-observations="${entry.observations}">
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
}

// Initialize on DOM load
document.addEventListener('DOMContentLoaded', () => {
    window.registerFormHandler = new RegisterFormHandler();
    window.registerSummaryHandler = new RegisterSummaryHandler();
    window.registerSearchHandler = new RegisterSearchHandler();
});
