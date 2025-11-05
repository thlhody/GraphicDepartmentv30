/**
 * StatusBadgeHandler.js
 *
 * Manages clickable status badges in team view.
 * Allows team leads to mark entries as TEAM_FINAL by clicking badges.
 *
 * @module features/check-register/StatusBadgeHandler
 */

/**
 * StatusBadgeHandler class
 * Handles status badge click functionality for team view
 */
export class StatusBadgeHandler {
    constructor() {
        this.initializeBadgeClickHandlers();
        this.injectCSS();
    }

    /**
     * Initialize click handlers for status badges
     */
    initializeBadgeClickHandlers() {
        console.log('Initializing status badge click handlers...');

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
            badge.addEventListener('click', () => this.handleBadgeClick(badge));
        });

        console.log(`Initialized ${clickableBadges.length} clickable status badges`);
    }

    /**
     * Handle badge click event
     * @param {HTMLElement} badge - Badge element
     */
    handleBadgeClick(badge) {
        const entryId = badge.getAttribute('data-entry-id');

        if (!entryId) {
            console.warn('No entry ID found on badge');
            return;
        }

        // Confirm action
        const confirmed = confirm('Mark this entry as Team Final (TF)?');
        if (!confirmed) {
            return;
        }

        // Get values from server-provided constants
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

        // Submit form
        this.submitMarkFinalForm(entryId, username, userId, year, month);
    }

    /**
     * Submit mark final form
     * @param {string} entryId - Entry ID
     * @param {string} username - Username
     * @param {string} userId - User ID
     * @param {string} year - Year
     * @param {string} month - Month
     */
    submitMarkFinalForm(entryId, username, userId, year, month) {
        // Create form
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = '/team/check-register/mark-single-entry-final';

        // Add hidden fields
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
    }

    /**
     * Inject CSS for clickable badges
     */
    injectCSS() {
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
    }
}
