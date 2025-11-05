/**
 * Check Values - Entry Point
 *
 * Initializes the check values management interface for configuring
 * work unit values and check register calculation parameters per user.
 *
 * @module features/check-values
 */

import { CheckValuesHandler } from './CheckValuesHandler.js';

/**
 * Initialize check values interface
 */
function init() {
    console.log('🚀 Initializing Check Values Interface...');

    try {
        const checkValuesHandler = new CheckValuesHandler();

        // Export globally for backward compatibility
        window.checkValuesHandler = checkValuesHandler;

        console.log('✅ Check Values Interface initialized successfully');
    } catch (error) {
        console.error('❌ Error initializing Check Values:', error);
    }
}

// Auto-initialize on DOM ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}

// Export for external usage
export { CheckValuesHandler, init };
