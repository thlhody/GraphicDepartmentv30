/**
 * Check Register - Entry Point
 *
 * Initializes all modules for check register functionality.
 * Works for both user and team views.
 *
 * @module features/check-register
 */

import { CheckRegisterForm } from './CheckRegisterForm.js';
import { CheckRegisterSummary } from './CheckRegisterSummary.js';
import { CheckRegisterSearch } from './CheckRegisterSearch.js';
import { StatusBadgeHandler } from './StatusBadgeHandler.js';

/**
 * Initialize check register interface
 */
function init() {
    console.log("Initializing Check Register...");

    // Initialize form handler
    const checkRegisterForm = new CheckRegisterForm();

    // Initialize summary handler
    const checkRegisterSummary = new CheckRegisterSummary();

    // Check if IS_TEAM_VIEW is defined
    const isTeamView = typeof IS_TEAM_VIEW !== 'undefined' ? IS_TEAM_VIEW :
        (typeof window.IS_TEAM_VIEW !== 'undefined' ? window.IS_TEAM_VIEW : false);

    console.log("Team view status:", isTeamView);

    // Initialize search handler (if not team view or if search elements exist)
    let checkRegisterSearch = null;
    if (!isTeamView || document.getElementById('searchModal')) {
        try {
            checkRegisterSearch = new CheckRegisterSearch();
            console.log("Search handler initialized");
        } catch (e) {
            console.error("Error initializing search handler:", e);
        }
    }

    // Initialize status badge handler (team view only)
    let statusBadgeHandler = null;
    if (isTeamView) {
        try {
            statusBadgeHandler = new StatusBadgeHandler();
            console.log("Status badge handler initialized");
        } catch (e) {
            console.error("Error initializing status badge handler:", e);
        }
    }

    // Make instances globally available for debugging and backward compatibility
    window.CheckRegister = {
        form: checkRegisterForm,
        summary: checkRegisterSummary,
        search: checkRegisterSearch,
        statusBadgeHandler: statusBadgeHandler
    };

    // Legacy global references for backward compatibility
    window.checkRegisterHandler = checkRegisterForm;
    window.checkRegisterSummaryHandler = checkRegisterSummary;
    window.searchHandler = checkRegisterSearch;

    // Function to reset the form (for external use)
    window.resetForm = function() {
        if (checkRegisterForm) {
            checkRegisterForm.reset();
        }
    };

    console.log("Check Register initialized successfully");
}

// Auto-initialize on DOM ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}

// Export for manual initialization if needed
export { init };
