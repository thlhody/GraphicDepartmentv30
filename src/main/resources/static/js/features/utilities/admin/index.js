/**
 * Admin Utilities - Entry Point
 *
 * Main entry point for admin utility management features.
 * Initializes the utility coordinator and module manager which coordinate
 * Health, Monitor, Session, Backup, Diagnostics, Actions, and Merge utilities.
 *
 * @module features/utilities/admin
 */

import * as UtilityCoordinator from './UtilityCoordinator.js';
import UtilityModuleManager from './UtilityModuleManager.js';

// Export all coordinator and manager functionality
export {
    UtilityCoordinator,
    UtilityModuleManager
};

// Make available globally for backward compatibility
window.UtilityCoordinator = UtilityCoordinator;
window.UtilityModuleManager = UtilityModuleManager;

console.log('📦 Admin Utilities module loaded');
