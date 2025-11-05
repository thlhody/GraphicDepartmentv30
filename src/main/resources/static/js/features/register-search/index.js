/**
 * Register Search - Entry Point
 *
 * Initializes register search interface with Select2 multi-select,
 * advanced filtering, statistics calculation, and filter management.
 *
 * @module features/register-search
 */

import { RegisterSearchManager } from './RegisterSearchManager.js';

/**
 * Initialize register search manager
 */
function init() {
    console.log('🚀 Initializing Register Search System...');

    try {
        // Only initialize if search form exists
        const searchForm = document.getElementById('searchForm');
        if (!searchForm) {
            console.log('Search form not found, skipping initialization');
            return;
        }

        // Create register search manager instance
        const registerSearchManager = new RegisterSearchManager();
        registerSearchManager.initialize();

        // Make globally accessible for debugging
        window.registerSearchManager = registerSearchManager;

        // Expose utility methods for external access
        window.registerSearchUtils = {
            calculateStats: () => registerSearchManager.calculateStats(),
            resetFilters: () => registerSearchManager.resetFilters(),
            hasAdvancedFilters: () => registerSearchManager.hasAdvancedFilters(),
            getFilterValues: () => registerSearchManager.getFilterValues()
        };

        console.log('✅ Register Search System initialized successfully');

    } catch (error) {
        console.error('❌ Error initializing Register Search System:', error);
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
    RegisterSearchManager,
    init
};

// Make available globally for backward compatibility
window.RegisterSearchManager = RegisterSearchManager;
