/**
 * index.js
 * Entry point for user register feature
 *
 * Initializes:
 * - RegisterForm: Form handling and validation
 * - RegisterSummary: Statistics calculation and display
 * - RegisterSearch: Search functionality (local + full)
 * - AjaxHandler: AJAX submissions without page reload
 *
 * @module features/register
 */

import { RegisterForm } from './RegisterForm.js';
import { RegisterSummary } from './RegisterSummary.js';
import { RegisterSearch } from './RegisterSearch.js';
import { AjaxHandler } from './AjaxHandler.js';

/**
 * Initialize user register feature
 */
function initializeRegister() {
    console.log('Initializing User Register Feature...');

    try {
        // Initialize modules in dependency order
        console.log('1️⃣ Creating RegisterForm...');
        const registerForm = new RegisterForm();
        console.log('✓ RegisterForm created');

        console.log('2️⃣ Creating RegisterSummary...');
        const registerSummary = new RegisterSummary();
        console.log('✓ RegisterSummary created');

        console.log('3️⃣ Creating RegisterSearch...');
        const registerSearch = new RegisterSearch(registerForm);
        console.log('✓ RegisterSearch created');

        // Setup tab navigation for RegisterForm
        console.log('4️⃣ Setting up tab navigation...');
        registerForm.setupTabNavigation();

        // Setup initial edit/copy button handlers
        console.log('5️⃣ Setting up edit/copy handlers...');
        setupEditCopyHandlers(registerForm);

        // Initialize AJAX handler IMMEDIATELY (no delay) to ensure validation runs
        console.log('6️⃣ Creating AjaxHandler...');
        const ajaxHandler = new AjaxHandler(registerForm, registerSummary);
        console.log('✓ AjaxHandler created');

        // Fix any existing validation states after a small delay
        setTimeout(() => {
            ajaxHandler.fixFormValidationStates();
        }, 300);

        // Setup Select2 global keyboard handling
        console.log('7️⃣ Setting up Select2 keyboard handling...');
        setupSelect2KeyboardHandling();

        // Make instances globally available
        window.registerForm = registerForm;
        window.registerSummary = registerSummary;
        window.registerSearch = registerSearch;
        window.ajaxHandler = ajaxHandler;

        // Force initial statistics calculation
        setTimeout(() => {
            console.log('8️⃣ Calculating initial statistics...');
            if (registerSummary) {
                registerSummary.calculateStats();
            }
        }, 500);

        console.log('✅ User Register Feature initialized successfully');
        console.log('✓ Form validation is active');
    } catch (error) {
        console.error('💥 FATAL ERROR during initialization:', error);
        console.error('💥 Error message:', error.message);
        console.error('💥 Error stack:', error.stack);
        alert('Failed to initialize register page. Check console for details.');
    }
}

/**
 * Setup initial edit and copy button handlers
 * @param {RegisterForm} registerForm - RegisterForm instance
 * @private
 */
function setupEditCopyHandlers(registerForm) {
    // Edit buttons
    document.querySelectorAll('.edit-entry').forEach(button => {
        button.addEventListener('click', (e) => {
            e.preventDefault();
            registerForm.populateForm(button);
        });
    });

    // Copy buttons
    document.querySelectorAll('.copy-entry').forEach(button => {
        button.addEventListener('click', (e) => {
            e.preventDefault();
            registerForm.copyEntry(button);
        });
    });

    console.log('Edit/Copy handlers attached to initial buttons');
}

/**
 * Setup global Select2 keyboard handling
 * @private
 */
function setupSelect2KeyboardHandling() {
    // Open Select2 when dropdown opens
    $(document).on('select2:open', () => {
        setTimeout(() => {
            const searchField = document.querySelector('.select2-search__field');
            if (searchField) {
                searchField.focus();
                console.log('Search field focused');
            }
        }, 100);
    });

    // Improve keyboard navigation in search field
    $(document).off('keydown.select2nav').on('keydown.select2nav', '.select2-search__field', function(e) {
        console.log('Key pressed in search field:', e.key);

        // Allow normal typing for filtering
        if (e.key.length === 1) {
            return true;
        }

        // Handle Enter key
        if (e.key === 'Enter') {
            const highlighted = $('.select2-results__option--highlighted');
            if (highlighted.length) {
                console.log('Enter pressed on highlighted option');
                e.preventDefault();
                e.stopPropagation();
                highlighted.trigger('mouseup');
                return false;
            }
        }
    });

    // Fix for direct tab to select2
    $('.select2-selection').on('focus', function() {
        console.log('Selection focused, opening dropdown');
        const container = $(this).closest('.select2-container');
        const selectId = container.attr('data-select2-id');
        if (selectId) {
            const selectElement = document.querySelector(`[data-select2-id="${selectId}"]`);
            if (selectElement) {
                $(selectElement).select2('open');
            }
        }
    });
}

/**
 * Setup action button toggles
 * @private
 */
function initializeActionToggles() {
    // Toggle action buttons visibility
    $(document).on('click', '.action-toggle', function(e) {
        e.stopPropagation();
        const container = $(this).closest('.action-container');
        const buttons = container.find('.action-buttons');

        // Close all other open action menus
        $('.action-buttons').not(buttons).removeClass('show');

        // Toggle this menu
        buttons.toggleClass('show');
    });

    // Close action menus when clicking outside
    $(document).on('click', function(e) {
        if (!$(e.target).closest('.action-container').length) {
            $('.action-buttons').removeClass('show');
        }
    });
}

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    initializeRegister();
    initializeActionToggles();
});

// Export for testing or external access
export { initializeRegister };
