/**
 * RegisterSummary.js
 * Calculates and displays registration entry statistics
 *
 * Tracks:
 * - Count by action type (Ordin, Reordin, Campion, etc.)
 * - Total entries and non-IMPOSTARE entries
 * - Average articles per entry
 * - Average complexity per entry
 *
 * Uses MutationObserver to automatically recalculate when table changes
 *
 * @module features/register/RegisterSummary
 */

/**
 * RegisterSummary - Statistics calculator and display handler
 */
export class RegisterSummary {

    /**
     * Create a RegisterSummary instance
     */
    constructor() {
        // Initialize counters
        this.actionCounts = {
            ordin: 0,
            reordin: 0,
            campion: 0,
            probe: 0,
            design: 0,
            checking: 0,
            spizedOrdin: 0,
            spizedCampion: 0,
            spizedProba: 0,
            impostare: 0,
            others: 0
        };

        // Initialize metrics
        this.metrics = {
            totalEntries: 0,
            totalNoImpostare: 0,
            avgArticles: 0,
            avgComplexity: 0
        };

        this.setupObserver();
        this.calculateStats();

        // Recalculate after form submissions
        this.setupFormListener();
    }

    /**
     * Setup MutationObserver to watch table changes
     * @private
     */
    setupObserver() {
        try {
            const tableBody = document.querySelector('.table tbody');
            if (!tableBody) {
                console.warn('RegisterSummary: Table body not found');
                return;
            }

            const observer = new MutationObserver((mutations) => {
                // Check if mutations actually affect our data
                const hasRelevantChanges = mutations.some(mutation =>
                    mutation.type === 'childList' ||
                    (mutation.type === 'characterData' &&
                     mutation.target.parentElement?.tagName === 'TD')
                );

                if (hasRelevantChanges) {
                    this.calculateStats();
                }
            });

            observer.observe(tableBody, {
                childList: true,
                subtree: true,
                characterData: true
            });

            console.log('RegisterSummary: MutationObserver initialized');
        } catch (error) {
            console.error('RegisterSummary: Error setting up observer:', error);
        }
    }

    /**
     * Setup form submission listener
     * @private
     */
    setupFormListener() {
        const registerForm = document.getElementById('registerForm');
        if (registerForm) {
            registerForm.addEventListener('submit', () => {
                // Allow time for DOM to update
                setTimeout(() => this.calculateStats(), 100);
            });
        }
    }

    /**
     * Calculate statistics from table data
     * @public
     */
    calculateStats() {
        try {
            // Reset counters
            this.resetStats();

            // Get all valid entries (excluding empty state row)
            const entries = Array.from(document.querySelectorAll('.table tbody tr'))
                .filter(row => !row.querySelector('.text-muted') && row.cells.length > 10);

            let totalArticles = 0;
            let totalComplexity = 0;
            let nonImpostareCount = 0;

            // Process each entry
            entries.forEach(row => {
                const cells = row.cells;
                const actionType = cells[6]?.textContent?.trim().toUpperCase();
                const articles = parseInt(cells[9]?.textContent || '0');
                const complexity = parseFloat(cells[10]?.textContent || '0');

                // Count action types
                this.countActionType(actionType);

                // Accumulate totals (excluding IMPOSTARE)
                if (actionType !== 'IMPOSTARE') {
                    totalArticles += articles;
                    totalComplexity += complexity;
                    nonImpostareCount++;
                }
            });

            // Calculate metrics
            this.metrics.totalEntries = entries.length;
            this.metrics.totalNoImpostare = nonImpostareCount;
            this.metrics.avgArticles = nonImpostareCount > 0
                ? (totalArticles / nonImpostareCount).toFixed(2)
                : '0.00';
            this.metrics.avgComplexity = nonImpostareCount > 0
                ? (totalComplexity / nonImpostareCount).toFixed(2)
                : '0.00';

            // Update UI
            this.updateUI();

        } catch (error) {
            console.error('RegisterSummary: Error calculating stats:', error);
        }
    }

    /**
     * Count action type
     * @param {string} actionType - Action type to count
     * @private
     */
    countActionType(actionType) {
        switch (actionType) {
            case 'ORDIN':
                this.actionCounts.ordin++;
                break;
            case 'REORDIN':
                this.actionCounts.reordin++;
                break;
            case 'CAMPION':
                this.actionCounts.campion++;
                break;
            case 'PROBA STAMPA':
            case 'PROBA CULOARE':
            case 'CARTELA CULORI':
                this.actionCounts.probe++;
                break;
            case 'DESIGN':
            case 'DESIGN 3D':
                this.actionCounts.design++;
                break;
            case 'CHECKING':
                this.actionCounts.checking++;
                break;
            case 'ORDIN SPIZED':
                this.actionCounts.spizedOrdin++;
                break;
            case 'CAMPION SPIZED':
                this.actionCounts.spizedCampion++;
                break;
            case 'PROBA S SPIZED':
                this.actionCounts.spizedProba++;
                break;
            case 'IMPOSTARE':
                this.actionCounts.impostare++;
                break;
            case 'PATTERN PREP':
            case 'OTHER':
            default:
                this.actionCounts.others++;
                break;
        }
    }

    /**
     * Reset all statistics to zero
     * @private
     */
    resetStats() {
        Object.keys(this.actionCounts).forEach(key => {
            this.actionCounts[key] = 0;
        });

        this.metrics.totalEntries = 0;
        this.metrics.totalNoImpostare = 0;
        this.metrics.avgArticles = 0;
        this.metrics.avgComplexity = 0;
    }

    /**
     * Update UI elements with calculated statistics
     * @private
     */
    updateUI() {
        // Update action type counts
        this.updateElement('#count-ordin', this.actionCounts.ordin);
        this.updateElement('#count-reordin', this.actionCounts.reordin);
        this.updateElement('#count-campion', this.actionCounts.campion);
        this.updateElement('#count-probe', this.actionCounts.probe);
        this.updateElement('#count-design', this.actionCounts.design);
        this.updateElement('#count-checking', this.actionCounts.checking);
        this.updateElement('#count-spized-ordin', this.actionCounts.spizedOrdin);
        this.updateElement('#count-spized-campion', this.actionCounts.spizedCampion);
        this.updateElement('#count-spized-proba', this.actionCounts.spizedProba);
        this.updateElement('#count-impostare', this.actionCounts.impostare);
        this.updateElement('#count-others', this.actionCounts.others);

        // Update metrics
        this.updateElement('#total-entries', this.metrics.totalEntries);
        this.updateElement('#total-entries-no-impostare', this.metrics.totalNoImpostare);
        this.updateElement('#avg-articles', this.metrics.avgArticles);
        this.updateElement('#avg-complexity', this.metrics.avgComplexity);
    }

    /**
     * Update a single DOM element with text content
     * @param {string} selector - CSS selector
     * @param {string|number} value - Value to display
     * @private
     */
    updateElement(selector, value) {
        const element = document.querySelector(selector);
        if (element) {
            element.textContent = value;
        }
    }

    /**
     * Get current statistics
     * @returns {Object} Current statistics object
     * @public
     */
    getStats() {
        return {
            actionCounts: { ...this.actionCounts },
            metrics: { ...this.metrics }
        };
    }

    /**
     * Force recalculation of statistics
     * @public
     */
    refresh() {
        this.calculateStats();
    }
}
