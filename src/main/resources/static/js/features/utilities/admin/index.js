/**
 * Admin Utilities - Entry Point
 *
 * Main entry point for admin utility management features.
 * Initializes the utility coordinator which manages Health, Monitor, Session,
 * Backup, and Diagnostics utilities.
 *
 * @module features/utilities/admin
 */

import * as UtilityCoordinator from './UtilityCoordinator.js';

// Export all coordinator functionality
export {
    UtilityCoordinator
};

// Make available globally for backward compatibility
window.UtilityCoordinator = UtilityCoordinator;

console.log('📦 Admin Utilities module loaded');
