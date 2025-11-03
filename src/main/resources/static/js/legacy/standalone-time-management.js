/**
 * Standalone Time Management Initialization
 * Handles initialization for the standalone time management page
 * File: /js/standalone-time-management.js
 */

document.addEventListener('DOMContentLoaded', function() {
    console.log('🚀 Initializing Standalone Time Management...');

    // Initialize all modules directly (content is already loaded)
    initializeStandaloneTimeManagement();

    // Set up period navigation to work with full page reloads
    setupStandalonePeriodNavigation();

    console.log('✅ Standalone Time Management initialized');
});

/**
 * Initialize time management modules for standalone context
 */
function initializeStandaloneTimeManagement() {
    console.log('🔧 Initializing standalone time management modules...');

    try {
        // Initialize modules in the same order as session page
        if (window.TimeOffManagementModule && typeof window.TimeOffManagementModule.initialize === 'function') {
            window.TimeOffManagementModule.initialize();
        }

        if (window.InlineEditingModule && typeof window.InlineEditingModule.initialize === 'function') {
            window.InlineEditingModule.initialize();
            // Use default behavior (full page refresh) for standalone
        }

        if (window.StatusDisplayModule && typeof window.StatusDisplayModule.initialize === 'function') {
            window.StatusDisplayModule.initialize();
        }

        if (window.WorkTimeDisplayModule && typeof window.WorkTimeDisplayModule.initialize === 'function') {
            window.WorkTimeDisplayModule.initialize();
        }

        // Initialize core last
        if (window.TimeManagementCore && typeof window.TimeManagementCore.initialize === 'function') {
            window.TimeManagementCore.initialize();
        }

        console.log('✅ Standalone time management modules initialized');

    } catch (error) {
        console.error('❌ Error initializing standalone time management:', error);
    }
}

/**
 * Set up period navigation for standalone context (full page reloads)
 */
function setupStandalonePeriodNavigation() {
    // Period navigation will work with default form submissions and page reloads
    // No special handling needed - the forms will submit normally
    console.log('📅 Period navigation set up for standalone mode');
}