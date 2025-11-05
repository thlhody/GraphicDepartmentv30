/**
 * Admin Worktime Management - Entry Point
 *
 * Initializes all modules for the admin worktime interface.
 * Provides global access to module instances for backward compatibility.
 *
 * @module features/worktime/admin
 */

import { WorktimeEditor } from './WorktimeEditor.js';
import { WorktimeValidator } from './WorktimeValidator.js';
import { WorktimeDataService } from './WorktimeDataService.js';
import { WorktimeFinalization } from './WorktimeFinalization.js';

/**
 * Initialize admin worktime interface
 */
function init() {
    console.log('Initializing admin worktime interface...');

    // Create instances
    const validator = new WorktimeValidator();
    const dataService = new WorktimeDataService();
    const editor = new WorktimeEditor({ validator, dataService });
    const finalization = new WorktimeFinalization();

    // Make instances globally available for debugging and inline HTML event handlers
    window.WorktimeAdmin = {
        editor,
        validator,
        dataService,
        finalization
    };

    // Expose methods for inline HTML event handlers (backward compatibility)
    window.showEditor = (cell) => editor.showEditor(cell);
    window.setWorktime = (btn, value) => {
        event.stopPropagation();
        editor.setWorktime(btn, value);
    };
    window.saveWorktime = (btn) => {
        event.stopPropagation();
        editor.saveWorktime(btn);
    };
    window.showFinalizeConfirmation = (userId) => finalization.showFinalizeConfirmation(userId);
    window.finalizeSpecificUser = () => finalization.finalizeSpecificUser();
    window.executeFinalization = () => finalization.executeFinalization();

    // Check structure of worktime cells
    const worktimeCells = document.querySelectorAll('.worktime-cell');
    console.log(`Found ${worktimeCells.length} worktime cells`);

    console.log('Admin worktime interface initialized successfully');
}

// Auto-initialize on DOM ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}

// Export for manual initialization if needed
export { init };
