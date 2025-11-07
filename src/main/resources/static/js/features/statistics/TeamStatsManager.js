/**
 * TeamStatsManager.js
 *
 * Manages team statistics interface for team leaders.
 * Handles user selection (Select2), form submission for initializing
 * and updating team member statistics.
 *
 * @module features/statistics/TeamStatsManager
 */

import { API } from '../../core/api.js';

/**
 * TeamStatsManager class
 * Manages team statistics operations
 */
export class TeamStatsManager {
    constructor() {
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

        this.initializeSelect2();

        console.log('✅ Team Stats Manager initialized successfully');
    }

    /**
     * Initialize Select2 for user selection
     */
    initializeSelect2() {
        // Check if jQuery and Select2 are available
        if (typeof $ === 'undefined') {
            console.error('jQuery not loaded - cannot initialize Select2');
            return;
        }

        if (typeof $.fn.select2 === 'undefined') {
            console.error('Select2 not loaded');
            return;
        }

        // Initialize Select2 on user selection dropdown
        const $select = $('.select2-users');

        if ($select.length === 0) {
            console.warn('Select2 users element not found');
            return;
        }

        $select.select2({
            theme: 'bootstrap-5',
            width: '100%',
            placeholder: 'Select team members',
            allowClear: true
        });

        // Debug logging on selection change
        $select.on('change', () => {
            const selectedUsers = $select.val();
            console.log('Selected users:', selectedUsers);
        });

        console.log('✓ Select2 initialized for user selection');
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

        // Check if jQuery is available
        if (typeof $ === 'undefined') {
            console.error('jQuery not loaded');
            alert('Unable to initialize: jQuery not loaded');
            return;
        }

        const selectedUsers = $('.select2-users').val();

        // Validate that users are selected
        if (!selectedUsers || selectedUsers.length === 0) {
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
