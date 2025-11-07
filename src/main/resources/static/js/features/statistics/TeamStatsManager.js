/**
 * TeamStatsManager.js
 *
 * Manages team statistics interface for team leaders.
 * Handles user selection via checkboxes, search filtering, and form submission
 * for initializing and updating team member statistics.
 *
 * @module features/statistics/TeamStatsManager
 */

import { API } from '../../core/api.js';

/**
 * TeamStatsManager class
 * Manages team statistics operations with checkbox-based user selection
 */
export class TeamStatsManager {
    constructor() {
        // DOM elements
        this.checkboxes = null;
        this.searchInput = null;
        this.selectAllBtn = null;
        this.deselectAllBtn = null;
        this.initializeBtn = null;
        this.updateBtn = null;
        this.selectedCount = null;
        this.checkboxContainer = null;

        console.log('TeamStatsManager initialized');
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize team statistics manager
     */
    initialize() {
        console.log('🚀 Initializing Team Stats Manager...');

        // Get DOM elements
        this.checkboxes = document.querySelectorAll('.team-member-checkbox');
        this.searchInput = document.getElementById('userSearchInput');
        this.selectAllBtn = document.getElementById('selectAllBtn');
        this.deselectAllBtn = document.getElementById('deselectAllBtn');
        this.initializeBtn = document.getElementById('initializeBtn');
        this.updateBtn = document.getElementById('updateBtn');
        this.selectedCount = document.getElementById('selectedCount');
        this.checkboxContainer = document.getElementById('teamMemberCheckboxes');

        // Setup event listeners
        this.setupEventListeners();

        // Initialize selected count
        this.updateSelectedCount();

        console.log('✅ Team Stats Manager initialized successfully');
    }

    /**
     * Setup all event listeners
     */
    setupEventListeners() {
        // Checkbox change events
        this.checkboxes.forEach(checkbox => {
            checkbox.addEventListener('change', () => this.updateSelectedCount());
        });

        // Search input
        if (this.searchInput) {
            this.searchInput.addEventListener('input', (e) => this.filterUsers(e.target.value));
        }

        // Bulk selection buttons
        if (this.selectAllBtn) {
            this.selectAllBtn.addEventListener('click', () => this.selectAll());
        }

        if (this.deselectAllBtn) {
            this.deselectAllBtn.addEventListener('click', () => this.deselectAll());
        }

        // Action buttons
        if (this.initializeBtn) {
            this.initializeBtn.addEventListener('click', () => this.initializeMembers());
        }

        if (this.updateBtn) {
            this.updateBtn.addEventListener('click', () => this.updateStats());
        }

        console.log('✓ Event listeners attached');
    }

    // ========================================================================
    // SELECTION MANAGEMENT
    // ========================================================================

    /**
     * Get all selected user IDs
     * @returns {Array<string>} Array of selected user IDs
     */
    getSelectedUsers() {
        const selected = [];
        this.checkboxes.forEach(checkbox => {
            if (checkbox.checked) {
                selected.push(checkbox.value);
            }
        });
        return selected;
    }

    /**
     * Update the selected count badge
     */
    updateSelectedCount() {
        const count = this.getSelectedUsers().length;
        if (this.selectedCount) {
            this.selectedCount.textContent = `${count} selected`;
        }
    }

    /**
     * Select all visible checkboxes
     */
    selectAll() {
        this.checkboxes.forEach(checkbox => {
            const item = checkbox.closest('.user-checkbox-item');
            if (item && !item.classList.contains('hidden')) {
                checkbox.checked = true;
            }
        });
        this.updateSelectedCount();
    }

    /**
     * Deselect all checkboxes
     */
    deselectAll() {
        this.checkboxes.forEach(checkbox => {
            checkbox.checked = false;
        });
        this.updateSelectedCount();
    }

    /**
     * Filter users based on search input
     * @param {string} searchTerm - Search term to filter by
     */
    filterUsers(searchTerm) {
        const term = searchTerm.toLowerCase().trim();

        this.checkboxes.forEach(checkbox => {
            const item = checkbox.closest('.user-checkbox-item');
            if (!item) return;

            const userName = item.getAttribute('data-user-name') || '';
            const matches = userName.toLowerCase().includes(term);

            if (matches) {
                item.classList.remove('hidden');
            } else {
                item.classList.add('hidden');
            }
        });
    }

    // ========================================================================
    // TEAM MEMBER OPERATIONS
    // ========================================================================

    /**
     * Initialize team members
     * Submits form to create statistics for selected users
     */
    initializeMembers() {
        console.log('Initializing team members...');

        const selectedUsers = this.getSelectedUsers();

        // Validate that users are selected
        if (selectedUsers.length === 0) {
            alert('Please select at least one team member before initializing.');
            return;
        }

        console.log(`Initializing ${selectedUsers.length} team members`);

        // Create and submit form
        const form = this.createForm('/user/stats/initialize', selectedUsers);
        this.submitForm(form);
    }

    /**
     * Update statistics for current period
     * Submits form to refresh statistics data
     */
    updateStats() {
        console.log('Updating team statistics...');

        // Create and submit form
        const form = this.createForm('/user/stats/update');
        this.submitForm(form);
    }

    // ========================================================================
    // FORM OPERATIONS
    // ========================================================================

    /**
     * Create form with period selection and optional user list
     * @param {string} action - Form action URL
     * @param {Array<string>} selectedUsers - Optional array of user IDs
     * @returns {HTMLFormElement} Created form element
     */
    createForm(action, selectedUsers = null) {
        const form = document.createElement('form');
        form.method = 'post';
        form.action = action;

        // Add year
        const year = document.getElementById('yearSelect')?.value;
        if (year) {
            this.appendInput(form, 'year', year);
        }

        // Get month value (from select dropdown or button group)
        const month = this.getMonthValue();
        if (month) {
            this.appendInput(form, 'month', month);
        }

        // Add selected users if provided (for initialize action)
        if (selectedUsers && selectedUsers.length > 0) {
            selectedUsers.forEach(userId => {
                this.appendInput(form, 'selectedUsers', userId);
            });
        }

        // Add CSRF token (if available - local app mode doesn't use CSRF)
        const token = API.getCSRFToken();
        const header = API.getCSRFHeader();
        if (token && header) {
            this.appendInput(form, header, token);
        }

        return form;
    }

    /**
     * Get month value from UI
     * Checks both select dropdown and button group
     * @returns {string|null} Month number (1-12) or null
     */
    getMonthValue() {
        // Try select dropdown first
        const monthSelect = document.getElementById('monthSelect');
        if (monthSelect && monthSelect.value) {
            return monthSelect.value;
        }

        // Try button group
        const activeButton = document.querySelector('.btn-group .active');
        if (activeButton) {
            // Try data-month attribute
            const dataMonth = activeButton.getAttribute('data-month');
            if (dataMonth) {
                return dataMonth;
            }

            // Try to extract from href attribute (format: ?month=N)
            const href = activeButton.getAttribute('href');
            if (href) {
                const monthMatch = href.match(/month=(\d+)/);
                if (monthMatch && monthMatch[1]) {
                    return monthMatch[1];
                }
            }
        }

        // Fallback to current month
        const currentMonth = new Date().getMonth() + 1;
        console.warn(`Could not determine month from UI, using current month: ${currentMonth}`);
        return currentMonth.toString();
    }

    /**
     * Append hidden input to form
     * @param {HTMLFormElement} form - Form element
     * @param {string} name - Input name
     * @param {string} value - Input value
     */
    appendInput(form, name, value) {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        form.appendChild(input);
    }

    /**
     * Submit form
     * Appends form to body and submits
     * @param {HTMLFormElement} form - Form element to submit
     */
    submitForm(form) {
        document.body.appendChild(form);
        form.submit();
    }
}
