/**
 * Login - Entry Point
 *
 * Initializes optimized login page with fast, non-blocking experience.
 * Features: password toggle, validation, remember me, keyboard shortcuts,
 * performance monitoring, and adaptive loading overlay.
 *
 * @module features/login
 */

import { LoginManager } from './LoginManager.js';

/**
 * Initialize login manager
 */
function init() {
    console.log('🚀 Initializing Login System...');

    try {
        // Create login manager instance
        const loginManager = new LoginManager();
        loginManager.initialize();

        // Make globally accessible for debugging
        window.loginManager = loginManager;

        // Expose utility methods
        window.loginPageUtils = {
            clearRememberedUsername: () => LoginManager.clearRememberedUsername(),
            getPerformanceData: () => LoginManager.getPerformanceData(),
            resetLoginCount: () => LoginManager.resetLoginCount()
        };

        console.log('✅ Login System initialized successfully');
        console.log('Optimized login.js loaded - ready for lightning-fast logins!');

    } catch (error) {
        console.error('❌ Error initializing Login System:', error);
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
    LoginManager,
    init
};

// Make available globally for backward compatibility
window.LoginManager = LoginManager;
