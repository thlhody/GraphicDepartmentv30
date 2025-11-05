/**
 * Standalone Time Management Initializer
 *
 * Handles initialization for the standalone time management page.
 * This is a separate entry point from the session-embedded version.
 *
 * @module features/time-management/StandaloneInitializer
 */

import * as TimeManagement from './index.js';

/**
 * Initialize time management modules for standalone context
 */
function initializeStandaloneTimeManagement() {
    console.log('🚀 Initializing Standalone Time Management...');
    console.log('🔧 Using standalone context (full page, not embedded)');

    try {
        // Initialize the main time management system
        // The index.js already handles auto-initialization,
        // but we call it explicitly for standalone context
        TimeManagement.init();

        // Set up period navigation to work with full page reloads
        setupStandalonePeriodNavigation();

        console.log('✅ Standalone Time Management initialized');

    } catch (error) {
        console.error('❌ Error initializing standalone time management:', error);
    }
}

/**
 * Set up period navigation for standalone context (full page reloads)
 */
function setupStandalonePeriodNavigation() {
    // Period navigation will work with default form submissions and page reloads
    // No special AJAX handling needed - the forms will submit normally
    console.log('📅 Period navigation set up for standalone mode (full page reloads)');

    // Note: The PeriodNavigation module already handles keyboard shortcuts
    // (Ctrl+Left/Right arrows) which work in standalone mode
}

// ============================================================================
// AUTO-INITIALIZATION
// ============================================================================

// Auto-initialize on DOM ready
document.addEventListener('DOMContentLoaded', function() {
    initializeStandaloneTimeManagement();
});

// ============================================================================
// EXPORTS
// ============================================================================

export {
    initializeStandaloneTimeManagement,
    setupStandalonePeriodNavigation
};

// Make available globally for debugging
window.StandaloneTimeManagement = {
    initialize: initializeStandaloneTimeManagement,
    setupNavigation: setupStandalonePeriodNavigation
};
