/**
 * Standalone Time Management Initializer
 *
 * Handles initialization for the standalone time management page.
 * This is a separate entry point from the session-embedded version.
 * NOW WITH AJAX - NO PAGE RELOADS!
 *
 * @module features/time-management/StandaloneInitializer
 */

import * as TimeManagement from './index.js';
import { TimeManagementAjaxHandler } from './AjaxHandler.js';

// Store AJAX handler instance
let ajaxHandler = null;

/**
 * Initialize time management modules for standalone context
 */
function initializeStandaloneTimeManagement() {
    console.log('🚀 Initializing Standalone Time Management...');
    console.log('🔧 Using standalone context with AJAX (NO PAGE RELOADS!)');

    try {
        // Initialize the main time management system
        // The index.js already handles auto-initialization,
        // but we call it explicitly for standalone context
        TimeManagement.init();

        // Set up AJAX period navigation (NO PAGE RELOADS!)
        setupStandaloneAjaxNavigation();

        console.log('✅ Standalone Time Management initialized with AJAX');

    } catch (error) {
        console.error('❌ Error initializing standalone time management:', error);
    }
}

/**
 * Set up period navigation for standalone context (AJAX - NO PAGE RELOADS!)
 */
function setupStandaloneAjaxNavigation() {
    console.log('📅 Setting up AJAX period navigation (NO PAGE RELOADS!)');

    // Create AJAX handler instance
    ajaxHandler = new TimeManagementAjaxHandler();

    // Make it globally accessible for debugging and other modules
    window.TimeManagementAjaxHandler = ajaxHandler;

    console.log('✅ AJAX navigation enabled');
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
