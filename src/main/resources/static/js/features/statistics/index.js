/**
 * Statistics - Entry Point
 *
 * Initializes statistics system based on page context.
 * Handles user statistics (Chart.js visualizations) and
 * team statistics (member selection and management).
 *
 * @module features/statistics
 */

import { StatisticsCharts } from './StatisticsCharts.js';
import { TeamStatsManager } from './TeamStatsManager.js';

/**
 * Detect which statistics page we're on and initialize appropriate manager
 */
function init() {
    console.log('🚀 Initializing Statistics System...');

    try {
        // Detect page type by checking for specific elements
        const hasCharts = document.querySelector('canvas[id*="Chart"]') !== null;
        const hasTeamSelect = document.querySelector('.team-member-checkbox') !== null;

        // Initialize User Statistics Charts
        if (hasCharts) {
            console.log('Detected user statistics page (charts)');
            const statisticsCharts = new StatisticsCharts();
            statisticsCharts.initializeCharts();
            window.statisticsCharts = statisticsCharts;
        }

        // Initialize Team Statistics Manager
        if (hasTeamSelect) {
            console.log('Detected team statistics page (management)');
            const teamStatsManager = new TeamStatsManager();
            teamStatsManager.initialize();

            // Expose globally for debugging/console access
            window.teamStatsManager = teamStatsManager;
        }

        console.log('✅ Statistics System initialized successfully');

    } catch (error) {
        console.error('❌ Error initializing Statistics System:', error);
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
    StatisticsCharts,
    TeamStatsManager,
    init
};

// Make available globally for backward compatibility
window.StatisticsCharts = StatisticsCharts;
window.TeamStatsManager = TeamStatsManager;
