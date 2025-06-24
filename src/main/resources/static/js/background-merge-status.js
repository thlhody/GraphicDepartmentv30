/**
 * Background Merge Status Indicator
 * Shows users when background merge operations are in progress and completed
 */

class BackgroundMergeStatus {
    constructor() {
        this.isFirstLogin = this.checkIfFirstLogin();
        this.mergeInProgress = false;
        this.checkInterval = null;

        if (this.isFirstLogin) {
            this.initializeStatusIndicator();
            this.startMonitoring();
        }
    }

    /**
     * Check if this is the first login of the day (merge in progress)
     */
    checkIfFirstLogin() {
        // Check URL parameters or session storage for login indicators
        const urlParams = new URLSearchParams(window.location.search);
        const firstLogin = urlParams.get('firstLogin') === 'true';

        // Or check session storage
        const loginCount = sessionStorage.getItem('dailyLoginCount');

        return firstLogin || loginCount === '1';
    }

    /**
     * Initialize the status indicator UI
     */
    initializeStatusIndicator() {
        // Create status indicator element
        const statusDiv = document.createElement('div');
        statusDiv.id = 'background-merge-status';
        statusDiv.className = 'background-merge-indicator';
        statusDiv.innerHTML = `
            <div class="merge-status-content">
                <div class="merge-spinner"></div>
                <span class="merge-text">Synchronizing data...</span>
                <div class="merge-progress">
                    <div class="merge-progress-bar"></div>
                </div>
            </div>
        `;

        // Add CSS styles
        this.addStatusStyles();

        // Add to page
        document.body.appendChild(statusDiv);

        console.log('Background merge status indicator initialized');
    }

    /**
     * Add CSS styles for the status indicator
     */
    addStatusStyles() {
        const style = document.createElement('style');
        style.textContent = `
            .background-merge-indicator {
                position: fixed;
                top: 20px;
                right: 20px;
                background: #2196F3;
                color: white;
                padding: 12px 16px;
                border-radius: 8px;
                box-shadow: 0 4px 12px rgba(0,0,0,0.2);
                z-index: 10000;
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                font-size: 14px;
                min-width: 200px;
                transition: all 0.3s ease;
            }

            .merge-status-content {
                display: flex;
                align-items: center;
                gap: 8px;
            }

            .merge-spinner {
                width: 16px;
                height: 16px;
                border: 2px solid rgba(255,255,255,0.3);
                border-radius: 50%;
                border-top-color: white;
                animation: spin 1s linear infinite;
            }

            .merge-text {
                flex: 1;
                font-weight: 500;
            }

            .merge-progress {
                width: 60px;
                height: 4px;
                background: rgba(255,255,255,0.3);
                border-radius: 2px;
                overflow: hidden;
            }

            .merge-progress-bar {
                height: 100%;
                background: white;
                width: 0%;
                border-radius: 2px;
                animation: progress 8s linear forwards;
            }

            @keyframes spin {
                to { transform: rotate(360deg); }
            }

            @keyframes progress {
                to { width: 100%; }
            }

            .background-merge-indicator.completed {
                background: #4CAF50;
                animation: slideOut 2s ease forwards 1s;
            }

            .background-merge-indicator.completed .merge-spinner {
                display: none;
            }

            .background-merge-indicator.completed .merge-text::before {
                content: '✓ ';
            }

            @keyframes slideOut {
                to {
                    transform: translateX(calc(100% + 40px));
                    opacity: 0;
                }
            }
        `;
        document.head.appendChild(style);
    }

    /**
     * Start monitoring background merge progress
     */
    startMonitoring() {
        this.mergeInProgress = true;

        // Poll server for merge status
        this.checkInterval = setInterval(() => {
            this.checkMergeStatus();
        }, 2000);

        // Fallback timeout (assume complete after 15 seconds)
        setTimeout(() => {
            if (this.mergeInProgress) {
                console.log('Background merge timeout - assuming complete');
                this.markMergeComplete();
            }
        }, 15000);
    }

    /**
     * Check merge status from server
     */
    async checkMergeStatus() {
        try {
            const response = await fetch('/api/auth/merge-status', {
                method: 'GET',
                credentials: 'same-origin'
            });

            if (response.ok) {
                const status = await response.json();

                if (status.mergeComplete) {
                    this.markMergeComplete();
                }
            }
        } catch (error) {
            console.log('Could not check merge status:', error.message);
            // Continue polling - don't fail on network errors
        }
    }

    /**
     * Mark merge as complete and update UI
     */
    markMergeComplete() {
        if (!this.mergeInProgress) return;

        this.mergeInProgress = false;

        if (this.checkInterval) {
            clearInterval(this.checkInterval);
            this.checkInterval = null;
        }

        // Update UI to show completion
        const statusElement = document.getElementById('background-merge-status');
        if (statusElement) {
            statusElement.classList.add('completed');
            statusElement.querySelector('.merge-text').textContent = 'Data synchronized';

            // Remove element after animation
            setTimeout(() => {
                statusElement.remove();
            }, 3000);
        }

        console.log('Background merge completed');
    }

    /**
     * Manual method to hide status (for testing)
     */
    hide() {
        const statusElement = document.getElementById('background-merge-status');
        if (statusElement) {
            statusElement.remove();
        }

        if (this.checkInterval) {
            clearInterval(this.checkInterval);
        }
    }
}

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    // Only initialize if this is a first login
    const urlParams = new URLSearchParams(window.location.search);
    const isFirstLogin = urlParams.get('firstLogin') === 'true';

    if (isFirstLogin) {
        window.backgroundMergeStatus = new BackgroundMergeStatus();
    }
});

// Export for manual control
window.BackgroundMergeStatus = BackgroundMergeStatus;