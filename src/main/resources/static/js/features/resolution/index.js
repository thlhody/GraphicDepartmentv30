/**
 * Resolution - Entry Point
 *
 * Initializes work time resolution interface for unfinished sessions.
 * Features: backend time calculation, form validation, and breakdown display.
 *
 * @module features/resolution
 */

import { ResolutionManager } from './ResolutionManager.js';

/**
 * Initialize resolution manager
 */
function init() {
    console.log('🚀 Initializing Resolution System...');

    try {
        // Only initialize if resolution forms exist
        const calculationForms = document.querySelectorAll('.calculation-form');
        if (calculationForms.length === 0) {
            console.log('No resolution forms found, skipping initialization');
            return;
        }

        // Create resolution manager instance
        const resolutionManager = new ResolutionManager();
        resolutionManager.initialize();

        // Make globally accessible for debugging
        window.resolutionManager = resolutionManager;

        console.log('✅ Resolution System initialized successfully');

    } catch (error) {
        console.error('❌ Error initializing Resolution System:', error);
    }
}

// Auto-initialize on DOM ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}

// Export for external access
export {
    ResolutionManager,
    init
};

// Make available globally for backward compatibility
window.ResolutionManager = ResolutionManager;
