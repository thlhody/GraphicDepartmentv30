/**
 * Bonus - Entry Point
 *
 * Initializes bonus management system based on page context.
 * Handles admin bonus, check bonus dashboard, and check bonus fragment.
 *
 * @module features/bonus
 */

import { AdminBonusManager } from './AdminBonusManager.js';
import { CheckBonusDashboard } from './CheckBonusDashboard.js';
import { CheckBonusFragment } from './CheckBonusFragment.js';

/**
 * Detect which bonus page we're on and initialize appropriate manager
 */
function init() {
    console.log('🚀 Initializing Bonus Management System...');

    try {
        // Detect page type by checking for specific elements
        const isAdminBonusPage = document.querySelector('#bonusTableBody') !== null;
        const isCheckBonusPage = document.querySelector('#checkBonusTableBody') !== null;
        const isBonusFragmentPresent = document.querySelector('#calculateBonusBtn') !== null;

        // Initialize Admin Bonus Manager (admin bonus page)
        if (isAdminBonusPage && !isCheckBonusPage) {
            console.log('Detected admin bonus page');
            const adminBonusManager = new AdminBonusManager();
            adminBonusManager.initialize();
            window.adminBonusManager = adminBonusManager;
        }

        // Initialize Check Bonus Dashboard (team bonus page)
        if (isCheckBonusPage) {
            console.log('Detected check bonus dashboard page');
            const checkBonusDashboard = new CheckBonusDashboard();
            checkBonusDashboard.initialize();
            window.checkBonusDashboard = checkBonusDashboard;
        }

        // Initialize Check Bonus Fragment (individual user bonus calculation)
        if (isBonusFragmentPresent) {
            console.log('Detected bonus fragment (individual user calculation)');
            const checkBonusFragment = new CheckBonusFragment();
            checkBonusFragment.initialize();
            window.checkBonusFragment = checkBonusFragment;
        }

        console.log('✅ Bonus Management System initialized successfully');

    } catch (error) {
        console.error('❌ Error initializing Bonus Management System:', error);
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
    AdminBonusManager,
    CheckBonusDashboard,
    CheckBonusFragment,
    init
};

// Make available globally for backward compatibility
window.AdminBonusManager = AdminBonusManager;
window.CheckBonusDashboard = CheckBonusDashboard;
window.CheckBonusFragment = CheckBonusFragment;
