// Constants for value calculations
const CHECK_TYPE_VALUES = {
    'LAYOUT': 1.0,
    'KIPSTA LAYOUT': 0.25,
    'LAYOUT CHANGES': 0.25,
    'GPT': 0.1,          // For articles; also 0.1 for pieces
    'PRODUCTION': 0.1,
    'REORDER': 0.1,
    'SAMPLE': 0.3,
    'OMS PRODUCTION': 0.1,
    'KIPSTA PRODUCTION': 0.1
};

// Then, check for server-provided values and update if needed
if (typeof SERVER_CHECK_TYPE_VALUES !== 'undefined' && SERVER_CHECK_TYPE_VALUES !== null) {
    console.log('Using server-provided check type values');
    // Update the constant with server values
    for (const [key, value] of Object.entries(SERVER_CHECK_TYPE_VALUES)) {
        CHECK_TYPE_VALUES[key] = value;
    }
}

// Types that use articlesNumbers for calculation
const ARTICLE_BASED_TYPES = [
    'LAYOUT',
    'KIPSTA LAYOUT',
    'LAYOUT CHANGES',
    'GPT'
];

// Types that use filesNumbers for calculation
const FILE_BASED_TYPES = [
    'PRODUCTION',
    'REORDER',
    'SAMPLE',
    'OMS PRODUCTION',
    'KIPSTA PRODUCTION',
    'GPT'  // GPT uses both articles and files
];

class CheckRegisterFormHandler {
    constructor() {
        this.form = document.getElementById('checkRegisterForm');
        this.initializeFormElements();
        this.initializeForm();
    }

    initializeFormElements() {
        this.checkTypeSelect = document.getElementById('checkTypeSelect');
        this.articleNumbersInput = document.getElementById('articleNumbersInput');
        this.filesNumbersInput = document.getElementById('filesNumbersInput');
        this.orderValueInput = document.getElementById('orderValueInput');
        this.approvalStatusSelect = document.getElementById('approvalStatusSelect');

        // Set URL dynamically based on team view context
        const isTeamView = typeof IS_TEAM_VIEW !== 'undefined' && IS_TEAM_VIEW;
        this.defaultUrl = isTeamView ? '/team/check-register/entry' : '/user/check-register/entry';
    }

    initializeForm() {
        if (!this.form) return;

        // Setup event listeners
        this.setupEventListeners();
        this.initializeDefaultValues();
    }

    setupEventListeners() {
        // Form submission
        this.form.addEventListener('submit', (e) => this.handleSubmit(e));

        // Check type changes
        this.checkTypeSelect.addEventListener('change', () => {
            this.updateOrderValueField();
        });

        // Article Numbers changes
        this.articleNumbersInput.addEventListener('input', () => {
            this.updateOrderValueField();
        });

        // File Numbers changes
        this.filesNumbersInput.addEventListener('input', () => {
            this.updateOrderValueField();
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

    calculateOrderValue(checkType, articleNumbers, filesNumbers) {
        if (!checkType || !CHECK_TYPE_VALUES[checkType]) return 0;

        let orderValue = 0;
        const typeValue = CHECK_TYPE_VALUES[checkType];

        // Calculate based on type
        if (checkType === 'GPT') {
            // GPT uses both articles and files
            const articleValue = articleNumbers * typeValue;
            const filesValue = filesNumbers * typeValue;
            orderValue = articleValue + filesValue;
        } else if (ARTICLE_BASED_TYPES.includes(checkType)) {
            // Types that use article numbers
            orderValue = articleNumbers * typeValue;
        } else if (FILE_BASED_TYPES.includes(checkType)) {
            // Types that use file numbers
            orderValue = filesNumbers * typeValue;
        }

        return orderValue;
    }

    updateOrderValueField() {
        if (!this.checkTypeSelect || !this.articleNumbersInput || !this.filesNumbersInput || !this.orderValueInput) return;

        const checkType = this.checkTypeSelect.value;
        const articleNumbers = parseInt(this.articleNumbersInput.value) || 0;
        const filesNumbers = parseInt(this.filesNumbersInput.value) || 0;

        const orderValue = this.calculateOrderValue(checkType, articleNumbers, filesNumbers);

        if (orderValue >= 0) {
            this.orderValueInput.value = orderValue.toFixed(2);
        }
    }

    validateForm() {
        let isValid = true;
        const requiredFields = [
            { name: 'date', message: 'Date is required.' },
            { name: 'omsId', message: 'OMS ID is required.' },
            { name: 'designerName', message: 'Designer Name is required.' },
            { name: 'checkType', message: 'Check Type is required.' },
            { name: 'articleNumbers', message: 'Articles number is required.' },
            { name: 'filesNumbers', message: 'Files number is required.' },
            { name: 'approvalStatus', message: 'Approval Status is required.' }
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

    handleSubmit(event) {
        event.preventDefault();

        if (!this.validateForm()) {
            return;
        }

        // Submit the form
        this.form.submit();
    }

    copyEntry(button) {
        // Clear the form first
        this.resetForm();

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

    scrollToForm() {
        // Calculate an offset to scroll a bit higher than the form
        const formContainer = document.querySelector('.card.shadow-sm:has(#checkRegisterForm)');
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

    populateForm(button) {

        // Get the adminSync status (for debugging purposes)
        const adminSync = button.getAttribute('data-admin-sync');
        console.log('Edit button clicked with status:', adminSync);

        // Users can now edit ALL entries (including team lead entries)
        // Tombstone deletion system handles conflict resolution during merge

        // Clear the form first
        this.resetForm();

        const entryId = button.getAttribute('data-entry-id');
        this.form.action = `${this.defaultUrl}/${entryId}`;
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

        // Add team-specific fields if needed
        if (typeof IS_TEAM_VIEW !== 'undefined' && IS_TEAM_VIEW) {
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

        // Reset default values for articles and files
        const articlesInput = this.form.querySelector('input[name="articleNumbers"]');
        if (articlesInput) {
            articlesInput.value = '1';
        }

        const filesInput = this.form.querySelector('input[name="filesNumbers"]');
        if (filesInput) {
            filesInput.value = '1';
        }

        // Reset submit button
        const submitButton = this.form.querySelector('button[type="submit"]');
        submitButton.innerHTML = '<i class="bi bi-plus-circle me-1"></i>Add Entry';

        // Update the order value field
        this.updateOrderValueField();
    }
}

class CheckRegisterSummaryHandler {
    constructor() {
        // Initialize type counters
        this.typeCounts = {
            layout: 0,
            kipstaLayout: 0,
            layoutChanges: 0,
            gpt: 0,
            production: 0,
            reorder: 0,
            sample: 0,
            omsProduction: 0,
            kipstaProduction: 0,
            other: 0
        };

        // Initialize approval status counters
        this.approvalCounts = {
            approved: 0,
            partiallyApproved: 0,
            correction: 0
        };

        this.metrics = {
            totalEntries: 0,
            totalArticles: 0,
            totalFiles: 0,
            totalOrderValue: 0,
            standardHours: 0,
            targetUnitsHour: 0
        };

        this.setupObserver();
        // Initial calculation
        try {
            this.calculateStats();
        } catch (error) {
            console.error('Error calculating initial stats:', error);
        }

        // Add form submission handler to recalculate after form actions
        const checkRegisterForm = document.getElementById('checkRegisterForm');
        if (checkRegisterForm) {
            checkRegisterForm.addEventListener('submit', () => {
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
            Object.keys(this.typeCounts).forEach(key => this.typeCounts[key] = 0);
            Object.keys(this.approvalCounts).forEach(key => this.approvalCounts[key] = 0);

            // Reset metrics
            this.metrics.totalEntries = 0;
            this.metrics.totalArticles = 0;
            this.metrics.totalFiles = 0;
            this.metrics.totalOrderValue = 0;

            // Get standard hours and target units/hour from the page
            this.metrics.standardHours = parseFloat(document.getElementById('standard-hours')?.textContent || '0');
            this.metrics.targetUnitsHour = parseFloat(document.getElementById('target-units-hour')?.textContent || '0');

            // DIRECT ID COUNTING APPROACH
            // Get all rows from the CHECK REGISTER table specifically (not bonus table)
            // Use the check register entries table which is inside check-register-content
            const tableBody = document.querySelector('.register-content .table tbody');
            if (!tableBody) {
                console.warn("Check register table body not found, cannot calculate metrics");
                this.updateUI();
                return;
            }

            // Direct row counting by entry ID
            const entryIds = new Set();
            const validRows = [];

            // First pass: collect all IDs and identify valid rows
            Array.from(tableBody.rows).forEach(row => {
                // Skip the empty state row (shows "No Entries Found")
                if (row.querySelector('td[colspan]') || row.cells.length < 5) {
                    return;
                }

                // Get the ID from the first cell
                const idCell = row.cells[0];
                if (idCell) {
                    const idText = idCell.textContent.trim();
                    const id = parseInt(idText);
                    if (!isNaN(id)) {
                        entryIds.add(id);
                        validRows.push(row);
                    }
                }
            });

            // Log the found IDs for debugging
            console.log(`Found ${entryIds.size} distinct entry IDs: ${Array.from(entryIds).join(', ')}`);

            // Use the ID count as our definitive entry count
            this.metrics.totalEntries = entryIds.size;

            // Process valid rows for other metrics
            validRows.forEach(row => {
                const cells = row.cells;

                try {
                    // Get check type
                    const checkTypeElem = cells[5]?.querySelector('.badge');
                    const checkType = checkTypeElem ? checkTypeElem.textContent.trim() : '';

                    // Get articles and files count
                    const articles = parseInt(cells[6]?.textContent || '0') || 0;
                    const files = parseInt(cells[7]?.textContent || '0') || 0;

                    // Get approval status
                    const approvalElem = cells[9]?.querySelector('.badge');
                    const approvalStatus = approvalElem ? approvalElem.textContent.trim() : '';

                    // Get order value
                    const orderValue = parseFloat(cells[10]?.textContent || '0') || 0;

                    // Update totals
                    this.metrics.totalArticles += articles;
                    this.metrics.totalFiles += files;
                    this.metrics.totalOrderValue += orderValue;

                    // Count check types
                    if (checkType) {
                        switch(checkType) {
                            case 'LAYOUT': this.typeCounts.layout++; break;
                            case 'KIPSTA LAYOUT': this.typeCounts.kipstaLayout++; break;
                            case 'LAYOUT CHANGES': this.typeCounts.layoutChanges++; break;
                            case 'GPT': this.typeCounts.gpt++; break;
                            case 'PRODUCTION': this.typeCounts.production++; break;
                            case 'REORDER': this.typeCounts.reorder++; break;
                            case 'SAMPLE': this.typeCounts.sample++; break;
                            case 'OMS PRODUCTION': this.typeCounts.omsProduction++; break;
                            case 'KIPSTA PRODUCTION': this.typeCounts.kipstaProduction++; break;
                            default: this.typeCounts.other++; break;
                        }
                    }

                    // Count approval statuses
                    if (approvalStatus) {
                        switch(approvalStatus) {
                            case 'APPROVED': this.approvalCounts.approved++; break;
                            case 'PARTIALLY APPROVED': this.approvalCounts.partiallyApproved++; break;
                            case 'CORRECTION': this.approvalCounts.correction++; break;
                        }
                    }
                } catch (e) {
                    console.error('Error processing row:', e);
                }
            });

            // Calculate efficiency
            let efficiency = 0;
            if (this.metrics.standardHours > 0 && this.metrics.targetUnitsHour > 0) {
                const targetTotal = this.metrics.standardHours * this.metrics.targetUnitsHour;
                efficiency = targetTotal > 0 ? (this.metrics.totalOrderValue / targetTotal * 100) : 0;
            }

            // Update the efficiency level element
            const efficiencyElement = document.getElementById('efficiency-level');
            if (efficiencyElement) {
                efficiencyElement.textContent = `${efficiency.toFixed(1)}%`;

                // Add class based on efficiency level
                efficiencyElement.classList.remove('high-efficiency', 'medium-efficiency', 'low-efficiency');
                if (efficiency >= 90) {
                    efficiencyElement.classList.add('high-efficiency');
                } else if (efficiency >= 70) {
                    efficiencyElement.classList.add('medium-efficiency');
                } else {
                    efficiencyElement.classList.add('low-efficiency');
                }
            }

            // CRITICAL: Directly set entries count before any other UI updates
            const entriesCountElement = document.getElementById('entries-count');
            if (entriesCountElement) {
                entriesCountElement.textContent = entryIds.size;
                console.log(`Directly set entries-count to ${entryIds.size}`);
            }

            // Update all other metrics
            this.updateUI();

        } catch (error) {
            console.error('Error calculating stats:', error);
            // Reset to safe values on error
            this.resetStats();
        }
    }

    resetStats() {
        Object.keys(this.typeCounts).forEach(key => this.typeCounts[key] = 0);
        Object.keys(this.approvalCounts).forEach(key => this.approvalCounts[key] = 0);
        this.metrics = {
            totalEntries: 0,
            totalArticles: 0,
            totalFiles: 0,
            totalOrderValue: 0,
            standardHours: 0,
            targetUnitsHour: 0
        };
        this.updateUI();
    }

    updateUI() {
        try {
            // CRITICAL: Using a hardcoded map for element updates to avoid any unexpected values
            const elements = {
                // Check types metrics
                'count-layout': this.typeCounts.layout,
                'count-kipsta-layout': this.typeCounts.kipstaLayout,
                'count-layout-changes': this.typeCounts.layoutChanges,
                'count-gpt': this.typeCounts.gpt,
                'count-production': this.typeCounts.production + this.typeCounts.reorder,
                'count-sample': this.typeCounts.sample,
                'count-oms-production': this.typeCounts.omsProduction,
                'count-kipsta-production': this.typeCounts.kipstaProduction,

                // Approval status metrics
                'count-approved': this.approvalCounts.approved,
                'count-partially-approved': this.approvalCounts.partiallyApproved,
                'count-correction': this.approvalCounts.correction,

                // Key metrics
                'total-entries': this.metrics.totalEntries,
                'total-articles': this.metrics.totalArticles,
                'total-files': this.metrics.totalFiles,
                'total-order-value': this.metrics.totalOrderValue.toFixed(2)
            };

            console.log("Updating UI with totalEntries =", this.metrics.totalEntries);

            // Update each element
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

class SearchHandler {
    constructor() {
        this.searchModal = document.getElementById('searchModal');
        this.searchInput = document.getElementById('searchInput');
        this.resultsContainer = document.getElementById('searchResultsContainer');
        this.searchButton = document.getElementById('searchModalTrigger');

        // Only initialize if the required elements exist
        if (this.searchModal && this.searchInput && this.resultsContainer) {
            // Initialize loading indicator
            this.loadingIndicator = document.createElement('div');
            this.loadingIndicator.className = 'search-loading-indicator';
            this.loadingIndicator.innerHTML = `
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">Loading...</span>
                </div>
            `;

            // Set up event listeners
            this.setupEventListeners();
        }
    }

    setupEventListeners() {
        // Only add listeners if elements exist
        if (!this.searchModal || !this.searchInput) return;

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
            clearTimeout(this.searchTimeoutId);
            this.searchTimeoutId = setTimeout(() => this.performSearch(), 250);
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
    }

    openSearchModal() {
        if (!this.searchModal) return;

        this.searchModal.classList.add('show');
        // Focus search input
        if (this.searchInput) {
            this.searchInput.focus();
            this.searchInput.value = '';
        }
        // Clear previous search results
        if (this.resultsContainer) {
            this.resultsContainer.innerHTML = '';
        }
    }

    closeSearchModal() {
        if (!this.searchModal) return;

        this.searchModal.classList.remove('show');
        if (this.searchInput) {
            this.searchInput.value = '';
        }
        if (this.resultsContainer) {
            this.resultsContainer.innerHTML = '';
        }
    }

    performSearch() {
        if (!this.searchInput || !this.resultsContainer) return;

        const query = this.searchInput.value.trim();

        // Clear results container
        this.resultsContainer.innerHTML = '';

        // If query is empty, exit early
        if (!query) {
            return;
        }

        // Show loading indicator
        this.resultsContainer.appendChild(this.loadingIndicator);

        // Perform client-side search
        this.performLocalSearch(query);
    }

    extractEntriesFromTable() {
        const entries = [];
        document.querySelectorAll('.table tbody tr').forEach(row => {
            // Skip the empty state row
            if (row.querySelector('.text-muted')) return;

            const cells = row.cells;
            if (cells.length < 10) return;

            // Extract edit button data attributes
            const editButton = row.querySelector('.edit-entry');
            if (!editButton) return;

            // Create entry object with data from edit button attributes
            const entry = {
                entryId: editButton.getAttribute('data-entry-id'),
                date: editButton.getAttribute('data-date'),
                omsId: editButton.getAttribute('data-oms-id'),
                productionId: editButton.getAttribute('data-production-id'),
                designerName: editButton.getAttribute('data-designer-name'),
                checkType: editButton.getAttribute('data-check-type'),
                articleNumbers: editButton.getAttribute('data-article-numbers'),
                filesNumbers: editButton.getAttribute('data-files-numbers'),
                errorDescription: editButton.getAttribute('data-error-description'),
                approvalStatus: editButton.getAttribute('data-approval-status'),
                orderValue: editButton.getAttribute('data-order-value'),
                rawRow: row
            };

            entries.push(entry);
        });
        return entries;
    }

    performLocalSearch(query) {
        // Extract entries from table
        const tableEntries = this.extractEntriesFromTable();

        // Remove loading indicator
        this.loadingIndicator.remove();

        // If query is empty or no entries, exit early
        if (!query || tableEntries.length === 0) {
            this.resultsContainer.innerHTML = '';
            return;
        }

        // Search logic: split query into terms and check if ALL terms match any field
        const searchTerms = query.toLowerCase().split(/\s+/).filter(term => term.length > 0);

        // Filter entries based on search terms
        const matchingEntries = tableEntries.filter(entry => {
            return searchTerms.every(term =>
            (entry.omsId && entry.omsId.toLowerCase().includes(term)) ||
            (entry.productionId && entry.productionId.toLowerCase().includes(term)) ||
            (entry.designerName && entry.designerName.toLowerCase().includes(term)) ||
            (entry.checkType && entry.checkType.toLowerCase().includes(term)) ||
            (entry.errorDescription && entry.errorDescription.toLowerCase().includes(term))
            );
        });

        this.displaySearchResults(matchingEntries);
    }

    displaySearchResults(entries) {
        if (!this.resultsContainer) return;

        // Clear previous results
        this.resultsContainer.innerHTML = '';

        // If no results
        if (entries.length === 0) {
            this.resultsContainer.innerHTML = `
                <div class="p-3 text-center text-muted">
                    <i class="bi bi-search"></i>
                    <p class="mt-2">No entries found matching your search.</p>
                </div>
            `;
            return;
        }

        // Create results header
        const header = document.createElement('div');
        header.className = 'search-result-header';
        header.innerHTML = `
            <div>Date</div>
            <div>OMS ID</div>
            <div>Designer</div>
            <div>Type</div>
            <div>Articles</div>
            <div>Files</div>
            <div>Status</div>
            <div>Actions</div>
        `;
        this.resultsContainer.appendChild(header);

        // Render results
        entries.forEach(entry => {
            const resultRow = document.createElement('div');
            resultRow.className = 'search-result-row';
            resultRow.innerHTML = `
                <div>${entry.date ? new Date(entry.date).toLocaleDateString() : '-'}</div>
                <div>${entry.omsId || '-'}</div>
                <div>${entry.designerName || '-'}</div>
                <div>${entry.checkType || '-'}</div>
                <div>${entry.articleNumbers || '0'}</div>
                <div>${entry.filesNumbers || '0'}</div>
                <div>${entry.approvalStatus || '-'}</div>
                <div>
                    <button class="btn btn-sm btn-outline-primary search-edit-entry">
                        <i class="bi bi-pencil"></i> Edit
                    </button>
                    <button class="btn btn-sm btn-outline-secondary search-copy-entry">
                        <i class="bi bi-copy"></i> Copy
                    </button>
                </div>
            `;

            // Add click event to edit button
            resultRow.querySelector('.search-edit-entry').addEventListener('click', () => {
                // Find the edit button in the main table for this entry
                const editButton = entry.rawRow.querySelector('.edit-entry');
                if (editButton) {
                    editButton.click();
                    this.closeSearchModal();
                }
            });

            // Add click event to copy button
            resultRow.querySelector('.search-copy-entry').addEventListener('click', () => {
                // Find the copy button in the main table for this entry
                const copyButton = entry.rawRow.querySelector('.copy-entry');
                if (copyButton) {
                    copyButton.click();
                    this.closeSearchModal();
                }
            });

            this.resultsContainer.appendChild(resultRow);
        });
    }
}

/**
 * Initialize click handlers for status badges in team view
 * Allows team leads to click badges to mark entries as TEAM_FINAL
 */
function initializeStatusBadgeClickHandlers() {
    console.log('initializeStatusBadgeClickHandlers called');
    const clickableBadges = document.querySelectorAll('.clickable-badge');
    console.log('Found clickable badges:', clickableBadges.length);

    if (clickableBadges.length === 0) {
        console.warn('No clickable badges found! Checking all status badges...');
        const allBadges = document.querySelectorAll('.status-badge');
        console.log('Total status badges found:', allBadges.length);
        allBadges.forEach(badge => {
            console.log('Badge classes:', badge.className);
            console.log('Badge data-entry-id:', badge.getAttribute('data-entry-id'));
        });
    }

    clickableBadges.forEach(badge => {
        badge.addEventListener('click', function() {
            const entryId = this.getAttribute('data-entry-id');

            if (!entryId) {
                console.warn('No entry ID found on badge');
                return;
            }

            // Confirm action
            const confirmed = confirm('Mark this entry as Team Final (TF)?');
            if (!confirmed) {
                return;
            }

            // Get values from server-provided constants (same as mark-all-checked button)
            // These are passed from Thymeleaf template to JavaScript
            const username = typeof SELECTED_USER !== 'undefined' ? SELECTED_USER : null;
            const userId = typeof SELECTED_USER_ID !== 'undefined' ? SELECTED_USER_ID : null;
            const year = typeof CURRENT_YEAR !== 'undefined' ? CURRENT_YEAR : null;
            const month = typeof CURRENT_MONTH !== 'undefined' ? CURRENT_MONTH : null;

            // Validate required parameters
            if (!username || !userId || !year || !month) {
                console.error('Missing required parameters:', { username, userId, year, month });
                alert('Missing required parameters. Page context not properly initialized.');
                return;
            }

            console.log('Submitting mark-single-entry-final with:', { entryId, username, userId, year, month });

            // Create form and submit
            const form = document.createElement('form');
            form.method = 'POST';
            form.action = '/team/check-register/mark-single-entry-final';

            // Add hidden fields - only add non-null values
            const fields = {
                'entryId': entryId,
                'username': username,
                'userId': userId,
                'year': year,
                'month': month
            };

            for (const [name, value] of Object.entries(fields)) {
                const input = document.createElement('input');
                input.type = 'hidden';
                input.name = name;
                input.value = value;
                form.appendChild(input);
            }

            document.body.appendChild(form);
            form.submit();
        });
    });

    console.log(`Initialized ${clickableBadges.length} clickable status badges`);
}

// Add CSS for clickable badges
const style = document.createElement('style');
style.textContent = `
    .clickable-badge {
        cursor: pointer;
        transition: all 0.2s ease;
    }
    .clickable-badge:hover {
        transform: scale(1.1);
        opacity: 0.9;
        box-shadow: 0 2px 4px rgba(0,0,0,0.2);
    }
    .clickable-badge:active {
        transform: scale(0.95);
    }
`;
document.head.appendChild(style);

// Initialize on DOM load
document.addEventListener('DOMContentLoaded', () => {
    console.log("Initializing Check Register Handlers...");

    window.checkRegisterHandler = new CheckRegisterFormHandler();
    window.checkRegisterSummaryHandler = new CheckRegisterSummaryHandler();

    // Check if IS_TEAM_VIEW is defined, use window.IS_TEAM_VIEW or fallback to false
    const isTeamView = typeof IS_TEAM_VIEW !== 'undefined' ? IS_TEAM_VIEW :
    (typeof window.IS_TEAM_VIEW !== 'undefined' ? window.IS_TEAM_VIEW : false);

    console.log("Team view status:", isTeamView);

    // Note: Status badge click handlers for team view are initialized
    // in team-check-register.html template script block, not here
    // This ensures they're only attached when the register content is actually shown

    // Only initialize SearchHandler in user view or if search elements exist
    if (!isTeamView || document.getElementById('searchModal')) {
        try {
            window.searchHandler = new SearchHandler();
            console.log("Search handler initialized");
        } catch(e) {
            console.error("Error initializing search handler:", e);
        }
    }

    // Function to reset the form (for external use)
    window.resetForm = function() {
        if (window.checkRegisterHandler) {
            window.checkRegisterHandler.resetForm();
        }
    };
});