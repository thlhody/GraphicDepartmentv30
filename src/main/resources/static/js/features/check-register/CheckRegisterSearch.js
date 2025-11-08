/**
 * CheckRegisterSearch.js
 *
 * Manages search functionality for check register entries.
 * Provides keyboard shortcuts, debounced search, and result highlighting.
 *
 * @module features/check-register/CheckRegisterSearch
 */

/**
 * CheckRegisterSearch class
 * Handles search modal and local search functionality
 */
export class CheckRegisterSearch {
    /**
     * Create a CheckRegisterSearch instance
     * @param {CheckRegisterForm} checkRegisterForm - Reference to CheckRegisterForm instance
     */
    constructor(checkRegisterForm = null) {
        this.checkRegisterForm = checkRegisterForm;
        this.searchModal = document.getElementById('searchModal');
        this.searchInput = document.getElementById('searchInput');
        this.resultsContainer = document.getElementById('searchResultsContainer');
        this.searchButton = document.getElementById('searchModalTrigger');
        this.searchTimeoutId = null;

        // Only initialize if required elements exist
        if (this.searchModal && this.searchInput && this.resultsContainer) {
            this.createLoadingIndicator();
            this.setupEventListeners();
        }
    }

    /**
     * Create loading indicator element
     */
    createLoadingIndicator() {
        this.loadingIndicator = document.createElement('div');
        this.loadingIndicator.className = 'search-loading-indicator';
        this.loadingIndicator.innerHTML = `
            <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">Loading...</span>
            </div>
        `;
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        if (!this.searchModal || !this.searchInput) return;

        // Global keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            if (e.ctrlKey && (e.key === 'f' || e.key === 'F')) {
                e.preventDefault();
                this.openModal();
            }
            if (e.key === 'Escape') {
                this.closeModal();
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
                this.closeModal();
            }
        });

        // Search modal trigger button
        if (this.searchButton) {
            this.searchButton.addEventListener('click', () => {
                this.openModal();
            });
        }
    }

    /**
     * Open search modal
     */
    openModal() {
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

    /**
     * Close search modal
     */
    closeModal() {
        if (!this.searchModal) return;

        this.searchModal.classList.remove('show');

        if (this.searchInput) {
            this.searchInput.value = '';
        }

        if (this.resultsContainer) {
            this.resultsContainer.innerHTML = '';
        }
    }

    /**
     * Perform search
     */
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

        // Perform local search
        this.performLocalSearch(query);
    }

    /**
     * Extract entries from table
     * @returns {Array} Array of entry objects
     */
    extractEntriesFromTable() {
        const entries = [];
        document.querySelectorAll('.table tbody tr').forEach(row => {
            // Skip empty state rows
            if (row.querySelector('.text-muted')) return;

            const cells = row.cells;
            if (cells.length < 10) return;

            // Extract edit button data attributes
            const editButton = row.querySelector('.edit-entry');
            if (!editButton) return;

            // Create entry object
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

    /**
     * Perform local search on table entries
     * @param {string} query - Search query
     */
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

    /**
     * Display search results
     * @param {Array} entries - Array of matching entries
     */
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
            const resultRow = this.createResultRow(entry);
            this.resultsContainer.appendChild(resultRow);
        });
    }

    /**
     * Create result row element
     * @param {Object} entry - Entry object
     * @returns {HTMLElement} Result row element
     */
    createResultRow(entry) {
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
            const editButton = entry.rawRow.querySelector('.edit-entry');
            if (editButton) {
                editButton.click();
                this.closeModal();
            }
        });

        // Add click event to copy button
        resultRow.querySelector('.search-copy-entry').addEventListener('click', () => {
            const copyButton = entry.rawRow.querySelector('.copy-entry');
            if (copyButton) {
                copyButton.click();
                this.closeModal();
            }
        });

        return resultRow;
    }
}
