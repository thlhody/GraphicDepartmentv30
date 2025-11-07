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

// Use dynamic imports with cache busting
const cacheBuster = new Date().getTime();
const SearchModalModule = await import(`../../components/SearchModal.js?v=${cacheBuster}`);
const APIModule = await import(`../../core/api.js?v=${cacheBuster}`);

const { SearchModal } = SearchModalModule;
const { API } = APIModule;

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
            renderResult: (entry) => this.renderSearchResult(entry),
            renderHeader: () => this.createResultsHeader()
        });

        // Add full search toggle button
        this.createFullSearchToggle();
    }

    /**
     * Create full search toggle button
     * @private
     */
    createFullSearchToggle() {
        const toggleButton = document.createElement('button');
        toggleButton.className = 'btn btn-sm btn-outline-secondary';
        toggleButton.innerHTML = `
            <i class="bi bi-globe"></i>
            <span class="full-search-label">Full Search: OFF</span>
        `;

        toggleButton.addEventListener('click', () => {
            this.toggleSearchMode();
            const label = toggleButton.querySelector('.full-search-label');
            if (this.isFullSearchMode) {
                label.textContent = 'Full Search: ON';
                toggleButton.classList.remove('btn-outline-secondary');
                toggleButton.classList.add('btn-outline-success');
            } else {
                label.textContent = 'Full Search: OFF';
                toggleButton.classList.remove('btn-outline-success');
                toggleButton.classList.add('btn-outline-secondary');
            }
        });

        this.searchModal.addActionButton(toggleButton);
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
            console.log('🔍 Full search starting for query:', query);

            // API.get() returns parsed data directly, not Response object!
            const json = await API.get('/user/register/full-search', { query });

            console.log('📦 Full search received JSON:', json);
            console.log('📦 JSON is array?', Array.isArray(json));
            console.log('📦 JSON length:', json ? json.length : 'null/undefined');

            if (!json || !Array.isArray(json)) {
                console.error('❌ Invalid response format:', json);
                return [];
            }

            // Backend returns array of RegisterSearchResultDTO objects
            // DTO structure: date (LocalDate), orderId, productionId, omsId, clientName,
            //                actionType, printPrepTypes (List<String>), colorsProfile,
            //                articleNumbers (Integer), graphicComplexity (Double), observations
            const mapped = json.map(dto => ({
                id: dto.orderId || '',  // Use orderId as id since there's no entryId
                date: dto.date ? new Date(dto.date).toLocaleDateString() : '',  // Format date
                orderId: dto.orderId || '',
                productionId: dto.productionId || '',
                omsId: dto.omsId || '',
                clientName: dto.clientName || '',
                actionType: dto.actionType || '',
                printPrepTypes: Array.isArray(dto.printPrepTypes) ? dto.printPrepTypes.join(', ') : (dto.printPrepTypes || ''),  // Join array
                observations: dto.observations || '',
                articleNumbers: dto.articleNumbers != null ? String(dto.articleNumbers) : '',
                graphicComplexity: dto.graphicComplexity != null ? String(dto.graphicComplexity) : '',
                colors: dto.colorsProfile || ''  // Map colorsProfile to colors
            }));

            console.log('✅ Mapped results:', mapped);
            console.log('✅ Returning', mapped.length, 'results');
            return mapped;
        } catch (error) {
            console.error('❌ Full search error:', error);
            console.error('❌ Error type:', error.constructor.name);
            console.error('❌ Error message:', error.message);
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
     * Create results header for grid layout
     * @returns {HTMLElement} Header element
     * @private
     */
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

    /**
     * Render a search result row in grid layout
     * @param {Object} entry - Entry data
     * @returns {HTMLElement} Result row element
     * @private
     */
    renderSearchResult(entry) {
        const row = document.createElement('div');
        row.className = 'search-result-row';

        const badgeClass = this.getActionTypeBadgeClass(entry.actionType);

        row.innerHTML = `
            <div>${entry.date}</div>
            <div>${entry.orderId}</div>
            <div>${entry.productionId || ''}</div>
            <div>${entry.omsId}</div>
            <div>${entry.clientName}</div>
            <div><span class="badge ${badgeClass}">${entry.actionType || ''}</span></div>
            <div>${entry.printPrepTypes || ''}</div>
            <div><button class="btn btn-sm btn-outline-secondary copy-search-entry" data-entry-id="${entry.id}">Copy</button></div>
        `;

        // Attach data to element for copy functionality
        row.dataset.entry = JSON.stringify(entry);

        // Click handler for copy button
        const copyBtn = row.querySelector('.copy-search-entry');
        copyBtn.addEventListener('click', (e) => {
            e.stopPropagation();
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
