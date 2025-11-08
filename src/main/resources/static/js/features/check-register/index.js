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
 * @param {HTMLFormElement} formElement - Pre-validated form element
 */
function initializeCheckRegister(formElement) {
    console.log('Initializing Check Register Feature...');
    console.log('Using pre-validated form element:', formElement);

    try {
        // Initialize form handler with pre-validated form element
        console.log('1️⃣ Creating CheckRegisterForm with pre-validated form element...');
        const checkRegisterForm = new CheckRegisterForm(formElement);
        console.log('✓ CheckRegisterForm created');

        // Initialize summary handler
        console.log('2️⃣ Creating CheckRegisterSummary...');
        const checkRegisterSummary = new CheckRegisterSummary();
        console.log('✓ CheckRegisterSummary created');

        // Check if IS_TEAM_VIEW is defined
        const isTeamView = typeof IS_TEAM_VIEW !== 'undefined' ? IS_TEAM_VIEW :
            (typeof window.IS_TEAM_VIEW !== 'undefined' ? window.IS_TEAM_VIEW : false);

        console.log('Team view status:', isTeamView);

        // Initialize search handler (if not team view or if search elements exist)
        let checkRegisterSearch = null;
        if (!isTeamView || document.getElementById('searchModal')) {
            try {
                console.log('3️⃣ Creating CheckRegisterSearch...');
                checkRegisterSearch = new CheckRegisterSearch(checkRegisterForm);
                console.log('✓ CheckRegisterSearch created');
            } catch (e) {
                console.error('❌ Error initializing search handler:', e);
            }
        }

        // Initialize status badge handler (team view only)
        let statusBadgeHandler = null;
        if (isTeamView) {
            try {
                console.log('4️⃣ Creating StatusBadgeHandler...');
                statusBadgeHandler = new StatusBadgeHandler();
                console.log('✓ StatusBadgeHandler created');
            } catch (e) {
                console.error('❌ Error initializing status badge handler:', e);
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

        // Force initial statistics calculation
        setTimeout(() => {
            console.log('5️⃣ Calculating initial statistics...');
            if (checkRegisterSummary) {
                checkRegisterSummary.calculateStats();
            }
        }, 500);

        console.log('✅ Check Register Feature initialized successfully');
    } catch (error) {
        console.error('💥 FATAL ERROR during initialization:', error);
        console.error('💥 Error message:', error.message);
        console.error('💥 Error stack:', error.stack);
        alert('Failed to initialize check register page. Check console for details.');
    }
}

/**
 * Wait for form element to be ready before initializing
 */
function waitForFormAndInitialize() {
    const form = document.getElementById('checkRegisterForm');

    if (form && form.tagName === 'FORM') {
        console.log('✓ Form element ready (tagName: FORM)');
        console.log('✓ Form ID:', form.id, 'Tag:', form.tagName);
        // Add small delay to ensure page is fully rendered
        console.log('⏳ Waiting 100ms for page to stabilize...');
        setTimeout(() => {
            console.log('✓ Starting initialization with validated form element...');
            initializeCheckRegister(form);  // Pass the actual form element
        }, 100);
    } else {
        if (form) {
            console.log(`⏳ Form element exists but tagName is '${form.tagName}', not 'FORM'. Retrying in 50ms...`);
        } else {
            console.log('⏳ Form element not found yet, retrying in 50ms...');
        }
        setTimeout(waitForFormAndInitialize, 50);
    }
}

// Initialize when DOM is ready OR immediately if already loaded
if (document.readyState === 'loading') {
    console.log('⏳ DOM still loading, waiting for DOMContentLoaded...');
    document.addEventListener('DOMContentLoaded', waitForFormAndInitialize);
} else {
    console.log('✓ DOM already loaded, checking for form...');
    waitForFormAndInitialize();
}

// Export for manual initialization if needed
export { initializeCheckRegister };
