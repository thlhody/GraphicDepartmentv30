/**
 * RegisterSearch.js
 * Unified search modal with local and full search modes
 *
 * Features:
 * - Keyboard shortcut: Ctrl+F to open
 * - Local search: Search current page entries (client-side)
 * - Full search: Backend search across all entries
 * - Debounced search input (250ms)
 * - Copy to form functionality
 *
 * @module features/register/RegisterSearch
 */

import { SearchModal } from '../../components/SearchModal.js';
import { API } from '../../core/api.js';

/**
 * RegisterSearch - Search handler for registration entries
 */
export class RegisterSearch {

    /**
     * Create a RegisterSearch instance
     * @param {RegisterForm} registerForm - Reference to RegisterForm instance
     */
    constructor(registerForm) {
        this.registerForm = registerForm;
        this.searchModal = null;
        this.localEntries = [];
        this.isFullSearchMode = false;

        this.init();
    }

    /**
     * Initialize search functionality
     * @private
     */
    init() {
        // Extract local entries from table
        this.extractEntriesFromTable();

        // Create search modal
        this.createSearchModal();

        // Setup keyboard shortcut (Ctrl+F)
        this.setupKeyboardShortcut();

        // Setup search trigger button if it exists
        this.setupTriggerButton();
    }

    /**
     * Extract entries from current table for local search
     * @private
     */
    extractEntriesFromTable() {
        this.localEntries = [];

        const rows = document.querySelectorAll('.table tbody tr');

        rows.forEach(row => {
            // Skip empty state rows
            if (row.querySelector('.text-muted') || row.cells.length < 10) {
                return;
            }

            const cells = row.cells;

            // Extract entry data from table cells
            const entry = {
                id: cells[0]?.textContent?.trim() || '',
                date: cells[1]?.textContent?.trim() || '',
                orderId: cells[2]?.textContent?.trim() || '',
                productionId: cells[3]?.textContent?.trim() || '',
                omsId: cells[4]?.textContent?.trim() || '',
                clientName: cells[5]?.textContent?.trim() || '',
                actionType: cells[6]?.textContent?.trim() || '',
                printPrepTypes: cells[7]?.textContent?.trim() || '',
                observations: cells[8]?.textContent?.trim() || '',
                articleNumbers: cells[9]?.textContent?.trim() || '',
                graphicComplexity: cells[10]?.textContent?.trim() || '',
                colors: cells[11]?.textContent?.trim() || ''
            };

            this.localEntries.push(entry);
        });

        console.log(`RegisterSearch: Extracted ${this.localEntries.length} entries`);
    }

    /**
     * Create and configure SearchModal
     * @private
     */
    createSearchModal() {
        this.searchModal = new SearchModal({
            title: 'Search Register Entries',
            placeholder: 'Search by Order ID, Client, Action Type...',
            debounceDelay: 250,
            onSearch: (query) => this.performSearch(query),
            onResultClick: (result) => this.handleResultClick(result),
            renderResult: (entry) => this.renderSearchResult(entry)
        });
    }

    /**
     * Setup Ctrl+F keyboard shortcut
     * @private
     */
    setupKeyboardShortcut() {
        document.addEventListener('keydown', (e) => {
            // Ctrl+F or Cmd+F
            if ((e.ctrlKey || e.metaKey) && e.key === 'f') {
                e.preventDefault();
                this.open();
            }
        });
    }

    /**
     * Setup manual trigger button
     * @private
     */
    setupTriggerButton() {
        const triggerButton = document.getElementById('searchModalTrigger');
        if (triggerButton) {
            triggerButton.addEventListener('click', () => this.open());
        }
    }

    /**
     * Open search modal
     * @public
     */
    open() {
        this.searchModal.open();
    }

    /**
     * Close search modal
     * @public
     */
    close() {
        this.searchModal.close();
    }

    /**
     * Perform search (local or full based on mode)
     * @param {string} query - Search query
     * @private
     */
    async performSearch(query) {
        if (!query || query.trim().length === 0) {
            return [];
        }

        if (this.isFullSearchMode) {
            return await this.performFullSearch(query);
        } else {
            return this.performLocalSearch(query);
        }
    }

    /**
     * Perform local (client-side) search
     * @param {string} query - Search query
     * @returns {Array} Matching entries
     * @private
     */
    performLocalSearch(query) {
        const terms = query.toLowerCase().split(' ').filter(t => t.length > 0);

        const results = this.localEntries.filter(entry => {
            // All terms must match at least one field (AND logic)
            return terms.every(term => {
                return (
                    entry.orderId.toLowerCase().includes(term) ||
                    entry.productionId.toLowerCase().includes(term) ||
                    entry.omsId.toLowerCase().includes(term) ||
                    entry.clientName.toLowerCase().includes(term) ||
                    entry.actionType.toLowerCase().includes(term) ||
                    entry.printPrepTypes.toLowerCase().includes(term) ||
                    entry.observations.toLowerCase().includes(term)
                );
            });
        });

        return results;
    }

    /**
     * Perform full (backend) search
     * @param {string} query - Search query
     * @returns {Promise<Array>} Matching entries from backend
     * @private
     */
    async performFullSearch(query) {
        try {
            const response = await API.get('/user/register/full-search', { query });

            if (response.ok) {
                const html = await response.text();
                return this.parseSearchResults(html);
            } else {
                console.error('Full search failed:', response.statusText);
                return [];
            }
        } catch (error) {
            console.error('Full search error:', error);
            return [];
        }
    }

    /**
     * Parse HTML response from backend search
     * @param {string} html - HTML response
     * @returns {Array} Parsed entries
     * @private
     */
    parseSearchResults(html) {
        const parser = new DOMParser();
        const doc = parser.parseFromString(html, 'text/html');
        const rows = doc.querySelectorAll('.table tbody tr');

        const results = [];

        rows.forEach(row => {
            if (row.querySelector('.text-muted') || row.cells.length < 10) {
                return;
            }

            const cells = row.cells;

            results.push({
                id: cells[0]?.textContent?.trim() || '',
                date: cells[1]?.textContent?.trim() || '',
                orderId: cells[2]?.textContent?.trim() || '',
                productionId: cells[3]?.textContent?.trim() || '',
                omsId: cells[4]?.textContent?.trim() || '',
                clientName: cells[5]?.textContent?.trim() || '',
                actionType: cells[6]?.textContent?.trim() || '',
                printPrepTypes: cells[7]?.textContent?.trim() || '',
                observations: cells[8]?.textContent?.trim() || '',
                articleNumbers: cells[9]?.textContent?.trim() || '',
                graphicComplexity: cells[10]?.textContent?.trim() || '',
                colors: cells[11]?.textContent?.trim() || ''
            });
        });

        return results;
    }

    /**
     * Render a search result row
     * @param {Object} entry - Entry data
     * @returns {HTMLElement} Result row element
     * @private
     */
    renderSearchResult(entry) {
        const row = document.createElement('div');
        row.className = 'search-result-row p-2 border-bottom';
        row.style.cursor = 'pointer';

        const badgeClass = this.getActionTypeBadgeClass(entry.actionType);

        row.innerHTML = `
            <div class="d-flex justify-content-between align-items-start">
                <div class="flex-grow-1">
                    <div class="mb-1">
                        <strong>${entry.orderId}</strong>
                        <span class="text-muted ms-2">${entry.date}</span>
                        <span class="badge ${badgeClass} ms-2">${entry.actionType}</span>
                    </div>
                    <div class="text-muted small">
                        ${entry.clientName || 'No client'}
                        ${entry.productionId ? `• Prod: ${entry.productionId}` : ''}
                        ${entry.omsId ? `• OMS: ${entry.omsId}` : ''}
                    </div>
                    <div class="text-muted small">
                        Articles: ${entry.articleNumbers} • Complexity: ${entry.graphicComplexity}
                        ${entry.printPrepTypes ? `• ${entry.printPrepTypes}` : ''}
                    </div>
                </div>
                <button class="btn btn-sm btn-outline-primary copy-btn" data-id="${entry.id}">
                    <i class="bi bi-files"></i> Copy
                </button>
            </div>
        `;

        // Attach data to element
        row.dataset.entry = JSON.stringify(entry);

        // Click handler for copy button
        const copyBtn = row.querySelector('.copy-btn');
        copyBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            this.copyEntryToForm(entry);
        });

        // Click handler for row
        row.addEventListener('click', () => {
            this.copyEntryToForm(entry);
        });

        return row;
    }

    /**
     * Get Bootstrap badge class for action type
     * @param {string} actionType - Action type
     * @returns {string} Badge class name
     * @private
     */
    getActionTypeBadgeClass(actionType) {
        const upperType = actionType.toUpperCase();

        if (upperType.includes('ORDIN')) return 'bg-primary';
        if (upperType.includes('CAMPION')) return 'bg-info';
        if (upperType.includes('PROBA')) return 'bg-warning';
        if (upperType.includes('DESIGN')) return 'bg-success';
        if (upperType.includes('CHECKING')) return 'bg-danger';
        if (upperType.includes('IMPOSTARE')) return 'bg-secondary';

        return 'bg-dark';
    }

    /**
     * Handle result click
     * @param {Object} result - Search result
     * @private
     */
    handleResultClick(result) {
        this.copyEntryToForm(result);
    }

    /**
     * Copy entry to main form
     * @param {Object} entry - Entry data
     * @private
     */
    copyEntryToForm(entry) {
        // Create a synthetic button element with data attributes
        const button = document.createElement('button');
        button.dataset.date = entry.date;
        button.dataset.orderId = entry.orderId;
        button.dataset.productionId = entry.productionId;
        button.dataset.omsId = entry.omsId;
        button.dataset.clientName = entry.clientName;
        button.dataset.actionType = entry.actionType;
        button.dataset.printPrepTypes = entry.printPrepTypes;
        button.dataset.articleNumbers = entry.articleNumbers;
        button.dataset.graphicComplexity = entry.graphicComplexity;
        button.dataset.colors = entry.colors;
        button.dataset.observations = entry.observations;

        // Use RegisterForm's copyEntry method
        if (this.registerForm && typeof this.registerForm.copyEntry === 'function') {
            this.registerForm.copyEntry(button);
            this.close();

            // Show success toast
            if (window.toastNotification) {
                window.toastNotification.success('Entry copied to form', 'You can now modify and submit.');
            }
        }
    }

    /**
     * Toggle search mode between local and full
     * @public
     */
    toggleSearchMode() {
        this.isFullSearchMode = !this.isFullSearchMode;
        console.log(`Search mode: ${this.isFullSearchMode ? 'Full (Backend)' : 'Local (Current Page)'}`);
    }

    /**
     * Set search mode
     * @param {boolean} fullMode - True for full search, false for local
     * @public
     */
    setSearchMode(fullMode) {
        this.isFullSearchMode = fullMode;
    }

    /**
     * Refresh local entries from table
     * @public
     */
    refresh() {
        this.extractEntriesFromTable();
    }
}
