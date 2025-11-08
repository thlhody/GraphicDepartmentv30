/**
 * index.js
 * Entry point for admin register feature
 *
 * Initializes:
 * - AdminRegisterState: State management and data processing
 * - AdminRegisterView: UI management and event handling
 * - BonusCalculator: Bonus calculation and display
 *
 * @module features/register/admin
 */

import { AdminRegisterState } from './AdminRegisterState.js';
import { AdminRegisterView } from './AdminRegisterView.js';
import { BonusCalculator } from './BonusCalculator.js';

/**
 * Initialize admin register feature
 */
function initializeAdminRegister() {
    console.log('Initializing Admin Register Feature...');

    // Get server data (provided by Thymeleaf in template)
    const serverData = window.serverData || {};

    // Initialize state management
    const state = new AdminRegisterState(serverData);

    // Initialize view
    const view = new AdminRegisterView(state);
    view.initialize();

    // Initialize bonus calculator
    const bonusCalculator = new BonusCalculator(state, view);
    bonusCalculator.initialize();

    // Make instances globally available for debugging
    window.adminRegisterState = state;
    window.adminRegisterView = view;
    window.bonusCalculator = bonusCalculator;

    console.log('Admin Register Feature initialized successfully');
    console.log('Current context:', state.getContext());
}

// Initialize based on DOM state
// If DOM is still loading, wait for DOMContentLoaded
// If DOM is already loaded (module loaded late), initialize immediately
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        initializeAdminRegister();
    });
} else {
    // DOM already loaded, initialize immediately
    initializeAdminRegister();
}

// Export for testing or external access
export { initializeAdminRegister };
