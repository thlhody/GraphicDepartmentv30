/**
 * SearchModal - Reusable search modal component
 *
 * This component provides a keyboard-accessible search modal with debounced
 * input, loading states, and customizable search logic. It consolidates
 * search modal patterns from multiple legacy files.
 *
 * @module components/SearchModal
 * @version 1.0.0
 * @since 2025-11-05
 *
 * Features:
 * - Keyboard shortcuts (Ctrl+F to open, Escape to close)
 * - Debounced search input
 * - Loading states with spinner
 * - Custom search functions (client-side or AJAX)
 * - Result highlighting
 * - Empty state handling
 * - Click outside to close
 * - Auto-focus on open
 * - Result templates
 *
 * Usage:
 *   import { SearchModal } from './components/SearchModal.js';
 *
 *   const modal = new SearchModal({
 *       onSearch: (query) => {
 *           return myData.filter(item => item.name.includes(query));
 *       },
 *       renderResult: (result) => {
 *           return `<div class="result-item">${result.name}</div>`;
 *       }
 *   });
 */

import { debounce } from '../core/utils.js';
import { createElement } from '../core/utils.js';

/**
 * SearchModal class
 * Manages search modal functionality
 */
export class SearchModal {
    /**
     * Default configuration
     * @private
     */
    static #defaultConfig = {
        trigger: null,                  // Trigger button selector
        placeholder: 'Search...',       // Input placeholder
        debounceDelay: 250,            // Debounce delay in ms
        minQueryLength: 1,             // Minimum query length to search
        showEmptyState: true,          // Show message when no results
        emptyStateMessage: 'No results found',
        loadingMessage: 'Searching...',
        enableKeyboardShortcut: true,  // Enable Ctrl+F shortcut
        keyboardShortcut: 'f',         // Keyboard shortcut key
        closeOnEscape: true,           // Close on Escape key
        closeOnOutsideClick: true,     // Close when clicking backdrop
        focusOnOpen: true,             // Auto-focus input on open
        clearOnClose: true,            // Clear input when closing
        onSearch: null,                // Search function: async (query) => results[]
        onResultClick: null,           // Result click handler: (result, index) => {}
        renderResult: null,            // Render function: (result, index, query) => HTML string
        renderHeader: null,            // Header render function: () => HTMLElement (optional)
        onOpen: null,                  // Open callback: () => {}
        onClose: null,                 // Close callback: () => {}
        customClass: ''                // Additional CSS class for modal
    };

    /**
     * Create SearchModal instance
     * @param {Object} config - Configuration options
     */
    constructor(config = {}) {
        // Merge config
        this.config = { ...SearchModal.#defaultConfig, ...config };

        // State
        this.isOpen = false;
        this.currentResults = [];
        this.currentQuery = '';

        // Create modal elements
        this.#createModal();

        // Set up event listeners
        this.#setupEventListeners();

        // Set up trigger button if provided
        if (this.config.trigger) {
            this.#setupTrigger();
        }
    }

    // =========================================================================
    // MODAL CREATION
    // =========================================================================

    /**
     * Create modal HTML structure
     * @private
     */
    #createModal() {
        // Create modal container
        this.modal = createElement('div', {
            class: `search-modal ${this.config.customClass}`
        });

        // Create modal dialog
        const dialog = createElement('div', { class: 'search-modal-dialog' });

        // Create modal content
        const content = createElement('div', { class: 'search-modal-content' });

        // Create header
        const header = createElement('div', { class: 'search-modal-header' });

        // Create search input container
        const inputContainer = createElement('div', { class: 'search-input-container' });

        // Create search icon
        const searchIcon = createElement('i', { class: 'bi bi-search search-icon' });

        // Create input
        this.input = createElement('input', {
            type: 'text',
            class: 'search-modal-input',
            placeholder: this.config.placeholder,
            autocomplete: 'off'
        });

        // Create close button
        const closeButton = createElement('button', {
            type: 'button',
            class: 'search-modal-close',
            'aria-label': 'Close'
        }, createElement('i', { class: 'bi bi-x-lg' }));

        closeButton.addEventListener('click', () => this.close());

        // Assemble input container
        inputContainer.appendChild(searchIcon);
        inputContainer.appendChild(this.input);

        // Assemble header
        header.appendChild(inputContainer);
        header.appendChild(closeButton);

        // Create body
        const body = createElement('div', { class: 'search-modal-body' });

        // Create action buttons container (optional)
        this.actionsContainer = createElement('div', {
            class: 'search-modal-actions',
            style: { display: 'none' }
        });

        // Create results container
        this.resultsContainer = createElement('div', { class: 'search-results-container' });

        // Create loading indicator
        this.loadingIndicator = createElement('div', {
            class: 'search-loading-indicator',
            style: { display: 'none' }
        }, `
            <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">${this.config.loadingMessage}</span>
            </div>
            <p class="mt-2 mb-0">${this.config.loadingMessage}</p>
        `);

        // Create empty state
        this.emptyState = createElement('div', {
            class: 'search-empty-state',
            style: { display: 'none' }
        }, `
            <i class="bi bi-search text-muted" style="font-size: 3rem;"></i>
            <p class="mt-3 mb-0 text-muted">${this.config.emptyStateMessage}</p>
        `);

        // Assemble body
        body.appendChild(this.actionsContainer);
        body.appendChild(this.loadingIndicator);
        body.appendChild(this.resultsContainer);
        body.appendChild(this.emptyState);

        // Assemble content
        content.appendChild(header);
        content.appendChild(body);

        // Assemble dialog
        dialog.appendChild(content);

        // Assemble modal
        this.modal.appendChild(dialog);

        // Append to body
        document.body.appendChild(this.modal);

        // Add default styles if not already present
        this.#addDefaultStyles();
    }

    /**
     * Add default styles for search modal
     * @private
     */
    #addDefaultStyles() {
        // Check if styles already exist
        if (document.getElementById('search-modal-styles')) {
            return;
        }

        const styles = document.createElement('style');
        styles.id = 'search-modal-styles';
        styles.textContent = `
            .search-modal {
                display: none;
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background-color: rgba(0, 0, 0, 0.5);
                z-index: 1050;
                animation: fadeIn 0.2s ease-in-out;
            }

            .search-modal.show {
                display: flex;
                justify-content: center;
                align-items: flex-start;
                padding-top: 10vh;
            }

            .search-modal-dialog {
                background: white;
                border-radius: 8px;
                width: 95%;
                max-width: 1200px;
                max-height: 80vh;
                display: flex;
                flex-direction: column;
                box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
                animation: slideDown 0.3s ease-out;
            }

            .search-modal-content {
                display: flex;
                flex-direction: column;
                width: 100%;
                height: 100%;
            }

            .search-modal-header {
                padding: 1rem;
                border-bottom: 1px solid #dee2e6;
                display: flex;
                gap: 0.5rem;
            }

            .search-input-container {
                flex: 1;
                display: flex;
                align-items: center;
                gap: 0.5rem;
                background: #f8f9fa;
                border-radius: 6px;
                padding: 0.5rem 1rem;
            }

            .search-icon {
                color: #6c757d;
                font-size: 1.25rem;
            }

            .search-modal-input {
                flex: 1;
                border: none;
                background: transparent;
                font-size: 1rem;
                outline: none;
            }

            .search-modal-close {
                background: none;
                border: none;
                font-size: 1.5rem;
                color: #6c757d;
                cursor: pointer;
                padding: 0.5rem;
                line-height: 1;
                transition: color 0.2s;
            }

            .search-modal-close:hover {
                color: #212529;
            }

            .search-modal-body {
                flex: 1;
                overflow-y: auto;
                padding: 1rem;
                min-height: 200px;
            }

            .search-modal-actions {
                display: flex;
                gap: 0.5rem;
                padding: 0.5rem;
                border-bottom: 1px solid #dee2e6;
                background-color: #f8f9fa;
                margin: -1rem -1rem 1rem -1rem;
            }

            .search-results-container {
                display: flex;
                flex-direction: column;
                gap: 0.5rem;
            }

            .search-loading-indicator,
            .search-empty-state {
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                padding: 3rem 1rem;
                text-align: center;
            }

            .search-result-item {
                padding: 0.75rem 1rem;
                border: 1px solid #dee2e6;
                border-radius: 6px;
                cursor: pointer;
                transition: all 0.2s;
            }

            .search-result-item:hover {
                background-color: #f8f9fa;
                border-color: #0d6efd;
                transform: translateX(4px);
            }

            .search-highlight {
                background-color: #fff3cd;
                font-weight: 600;
            }

            /* Grid layout styles for search results */
            .search-result-header,
            .search-result-row {
                display: grid;
                grid-template-columns: 100px 110px 100px 80px 1fr 120px 100px 80px;
                gap: 0.5rem;
                padding: 0.5rem;
                align-items: center;
                font-size: 0.875rem;
            }

            .search-result-header {
                font-weight: 600;
                background-color: #f8f9fa;
                border-bottom: 2px solid #dee2e6;
                position: sticky;
                top: 0;
                z-index: 10;
            }

            .search-result-row {
                border-bottom: 1px solid #dee2e6;
                transition: background-color 0.2s;
            }

            .search-result-row:hover {
                background-color: #f8f9fa;
            }

            .search-result-row > div {
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
            }

            @keyframes fadeIn {
                from { opacity: 0; }
                to { opacity: 1; }
            }

            @keyframes slideDown {
                from {
                    opacity: 0;
                    transform: translateY(-20px);
                }
                to {
                    opacity: 1;
                    transform: translateY(0);
                }
            }
        `;

        document.head.appendChild(styles);
    }

    // =========================================================================
    // EVENT LISTENERS
    // =========================================================================

    /**
     * Set up event listeners
     * @private
     */
    #setupEventListeners() {
        // Debounced search on input
        const debouncedSearch = debounce(() => this.#performSearch(), this.config.debounceDelay);
        this.input.addEventListener('input', debouncedSearch);

        // Keyboard shortcuts
        if (this.config.enableKeyboardShortcut) {
            document.addEventListener('keydown', (e) => {
                if (e.ctrlKey && e.key.toLowerCase() === this.config.keyboardShortcut) {
                    e.preventDefault();
                    this.open();
                }
            });
        }

        // Close on Escape
        if (this.config.closeOnEscape) {
            document.addEventListener('keydown', (e) => {
                if (e.key === 'Escape' && this.isOpen) {
                    this.close();
                }
            });
        }

        // Close on outside click
        if (this.config.closeOnOutsideClick) {
            this.modal.addEventListener('click', (e) => {
                if (e.target === this.modal) {
                    this.close();
                }
            });
        }
    }

    /**
     * Set up trigger button
     * @private
     */
    #setupTrigger() {
        const trigger = typeof this.config.trigger === 'string'
            ? document.querySelector(this.config.trigger)
            : this.config.trigger;

        if (trigger) {
            trigger.addEventListener('click', () => this.open());
        }
    }

    // =========================================================================
    // MODAL CONTROL
    // =========================================================================

    /**
     * Open search modal
     */
    open() {
        this.isOpen = true;
        this.modal.classList.add('show');

        // Focus input
        if (this.config.focusOnOpen) {
            setTimeout(() => this.input.focus(), 100);
        }

        // Call open callback
        if (this.config.onOpen) {
            this.config.onOpen();
        }
    }

    /**
     * Close search modal
     */
    close() {
        this.isOpen = false;
        this.modal.classList.remove('show');

        // Clear input
        if (this.config.clearOnClose) {
            this.input.value = '';
            this.clear();
        }

        // Call close callback
        if (this.config.onClose) {
            this.config.onClose();
        }
    }

    /**
     * Toggle modal open/close
     */
    toggle() {
        if (this.isOpen) {
            this.close();
        } else {
            this.open();
        }
    }

    /**
     * Add action button to modal
     * @param {HTMLElement|string} button - Button element or HTML string
     */
    addActionButton(button) {
        if (typeof button === 'string') {
            this.actionsContainer.insertAdjacentHTML('beforeend', button);
        } else if (button instanceof HTMLElement) {
            this.actionsContainer.appendChild(button);
        }
        this.actionsContainer.style.display = 'flex';
    }

    /**
     * Clear all action buttons
     */
    clearActionButtons() {
        this.actionsContainer.innerHTML = '';
        this.actionsContainer.style.display = 'none';
    }

    // =========================================================================
    // SEARCH
    // =========================================================================

    /**
     * Perform search
     * @private
     */
    async #performSearch() {
        const query = this.input.value.trim();
        this.currentQuery = query;

        // Clear if query is too short
        if (query.length < this.config.minQueryLength) {
            this.clear();
            return;
        }

        // Show loading
        this.#setLoadingState(true);

        try {
            // Call search function
            if (this.config.onSearch) {
                const results = await this.config.onSearch(query);
                this.#displayResults(results, query);
            } else {
                throw new Error('No search function provided');
            }
        } catch (error) {
            console.error('Search error:', error);
            this.#showError('An error occurred while searching');
        } finally {
            this.#setLoadingState(false);
        }
    }

    /**
     * Display search results
     * @private
     */
    #displayResults(results, query) {
        this.currentResults = results;

        // Clear previous results
        this.resultsContainer.innerHTML = '';

        // Show empty state if no results
        if (results.length === 0) {
            this.emptyState.style.display = this.config.showEmptyState ? 'flex' : 'none';
            return;
        }

        this.emptyState.style.display = 'none';

        // Add header if renderHeader function is provided
        if (this.config.renderHeader && typeof this.config.renderHeader === 'function') {
            const header = this.config.renderHeader();
            if (header instanceof HTMLElement) {
                this.resultsContainer.appendChild(header);
            }
        }

        // Render results
        results.forEach((result, index) => {
            const resultElement = this.#renderResultItem(result, index, query);
            this.resultsContainer.appendChild(resultElement);
        });
    }

    /**
     * Render a single result item
     * @private
     */
    #renderResultItem(result, index, query) {
        let renderedContent;

        // Use custom render function if provided
        if (this.config.renderResult) {
            renderedContent = this.config.renderResult(result, index, query);
        } else {
            // Default render
            renderedContent = `<div class="result-content">${JSON.stringify(result)}</div>`;
        }

        // Create result element
        const element = createElement('div', {
            class: 'search-result-item',
            'data-index': index
        });

        // Check if rendered content is a DOM element or HTML string
        if (renderedContent instanceof HTMLElement) {
            // If it's already a DOM element, append it directly
            element.appendChild(renderedContent);
        } else {
            // If it's an HTML string, set as innerHTML
            element.innerHTML = renderedContent;
        }

        // Add click handler
        element.addEventListener('click', () => {
            if (this.config.onResultClick) {
                this.config.onResultClick(result, index);
            }
        });

        return element;
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    /**
     * Set loading state
     * @private
     */
    #setLoadingState(loading) {
        if (loading) {
            this.loadingIndicator.style.display = 'flex';
            this.resultsContainer.style.display = 'none';
            this.emptyState.style.display = 'none';
        } else {
            this.loadingIndicator.style.display = 'none';
            this.resultsContainer.style.display = 'flex';
        }
    }

    /**
     * Show error message
     * @private
     */
    #showError(message) {
        this.resultsContainer.innerHTML = `
            <div class="alert alert-danger" role="alert">
                <i class="bi bi-exclamation-triangle me-2"></i>${message}
            </div>
        `;
        this.resultsContainer.style.display = 'flex';
    }

    /**
     * Clear results and input
     */
    clear() {
        this.resultsContainer.innerHTML = '';
        this.emptyState.style.display = 'none';
        this.currentResults = [];
        this.currentQuery = '';
    }

    /**
     * Highlight text in search results
     * @param {string} text - Text to highlight in
     * @param {string} query - Query to highlight
     * @returns {string} HTML with highlighted text
     */
    static highlightText(text, query) {
        if (!query || !text) return text;

        const regex = new RegExp(`(${query})`, 'gi');
        return text.replace(regex, '<span class="search-highlight">$1</span>');
    }

    /**
     * Get current results
     * @returns {Array} Current search results
     */
    getResults() {
        return this.currentResults;
    }

    /**
     * Get current query
     * @returns {string} Current search query
     */
    getQuery() {
        return this.currentQuery;
    }

    /**
     * Set placeholder text
     * @param {string} placeholder - Placeholder text
     */
    setPlaceholder(placeholder) {
        this.input.placeholder = placeholder;
    }

    /**
     * Check if modal is open
     * @returns {boolean} True if open
     */
    isModalOpen() {
        return this.isOpen;
    }

    // =========================================================================
    // DESTROY
    // =========================================================================

    /**
     * Destroy modal and clean up
     */
    destroy() {
        // Close if open
        if (this.isOpen) {
            this.close();
        }

        // Remove from DOM
        if (this.modal && this.modal.parentNode) {
            this.modal.parentNode.removeChild(this.modal);
        }

        // Clear references
        this.modal = null;
        this.input = null;
        this.resultsContainer = null;
        this.loadingIndicator = null;
        this.emptyState = null;
        this.currentResults = [];
    }
}

/**
 * Export as default for convenience
 */
export default SearchModal;
