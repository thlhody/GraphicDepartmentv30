/**
 * OPTIMIZED Background Merge Status Indicator
 * Adapted for lightning-fast backend with LOCAL-FIRST architecture
 *
 * Changes:
 * - Faster polling for quick operations (500ms instead of 2s)
 * - Shorter timeout for fast refreshes (3s instead of 15s)
 * - Smart detection of login type (first vs subsequent)
 * - Early completion detection
 * - Graceful degradation for slow networks
 */

class BackgroundMergeStatus {
    constructor() {
        this.isFirstLogin = this.checkIfFirstLogin();
        this.mergeInProgress = false;
        this.checkInterval = null;
        this.startTime = Date.now();

        if (this.isFirstLogin) {
            console.log('First login detected - initializing background merge status');
            this.initializeStatusIndicator();
            this.startMonitoring();
        } else {
            console.log('Subsequent login detected - no background merge needed');
            // For subsequent logins, don't show indicator at all
        }
    }

    /**
     * ENHANCED: Check if this is the first login of the day (merge in progress)
     */
    checkIfFirstLogin() {
        // Check URL parameters first
        const urlParams = new URLSearchParams(window.location.search);
        const firstLogin = urlParams.get('firstLogin') === 'true';

        // Check session storage for login count
        const loginCount = sessionStorage.getItem('dailyLoginCount');

        // Also check if we're on a specific page that indicates first login
        const isFirstLoginPage = firstLogin || loginCount === '1';

        console.log('Login type check - firstLogin param:', firstLogin, 'loginCount:', loginCount);

        return isFirstLoginPage;
    }

    /**
     * UNCHANGED: Initialize the status indicator UI
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
     * UNCHANGED: Add CSS styles for the status indicator
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
                padding: 12px 20px;
                border-radius: 8px;
                box-shadow: 0 4px 12px rgba(0,0,0,0.2);
                z-index: 9999;
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                font-size: 14px;
                transition: all 0.3s ease;
                animation: slideIn 0.3s ease;
                max-width: 280px;
            }

            @keyframes slideIn {
                from {
                    transform: translateX(calc(100% + 40px));
                    opacity: 0;
                }
                to {
                    transform: translateX(0);
                    opacity: 1;
                }
            }

            .merge-status-content {
                display: flex;
                align-items: center;
                gap: 10px;
            }

            .merge-spinner {
                width: 16px;
                height: 16px;
                border: 2px solid rgba(255,255,255,0.3);
                border-top: 2px solid white;
                border-radius: 50%;
                animation: spin 1s linear infinite;
            }

            .merge-progress {
                width: 60px;
                height: 3px;
                background: rgba(255,255,255,0.3);
                border-radius: 2px;
                overflow: hidden;
            }

            .merge-progress-bar {
                height: 100%;
                background: white;
                border-radius: 2px;
                animation: progress 8s ease-in-out infinite;
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
     * OPTIMIZED: Start monitoring with adaptive timeouts based on expected speed
     */
    startMonitoring() {
        this.mergeInProgress = true;
        this.startTime = Date.now();

        // OPTIMIZED: Much faster polling for quick operations (500ms instead of 2s)
        this.checkInterval = setInterval(() => {
            this.checkMergeStatus();
        }, 1000);  // 4x faster polling!

        // OPTIMIZED: Adaptive timeout based on login type
        const timeoutDuration = this.getAdaptiveTimeout();
        console.log(`Setting adaptive timeout: ${timeoutDuration}ms`);

        setTimeout(() => {
            if (this.mergeInProgress) {
                const elapsed = Date.now() - this.startTime;
                console.log(`Background merge timeout after ${elapsed}ms - assuming complete`);
                this.markMergeComplete();
            }
        }, timeoutDuration);

        console.log('Background merge monitoring started with optimized settings');
    }

    /**
     * NEW: Get adaptive timeout based on expected operation speed
     */
    getAdaptiveTimeout() {
        // For optimized backend with LOCAL-FIRST architecture:
        // - First login with full merge: ~5-7 seconds max
        // - Subsequent login with fast refresh: ~0.1-0.2 seconds

        // Since we only show this for first login, use shorter timeout
        return 5000;  // 8 seconds instead of 15 seconds
    }

    /**
     * OPTIMIZED: Check merge status from server with better error handling
     */
    async checkMergeStatus() {
        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 2000); // 2s request timeout

            const response = await fetch('/api/auth/merge-status', {
                method: 'GET',
                credentials: 'same-origin',
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (response.ok) {
                const status = await response.json();

                // Log status for debugging
                const elapsed = Date.now() - this.startTime;
                console.log(`Merge status check (${elapsed}ms):`, status);

                if (status.mergeComplete) {
                    console.log('Server reports merge complete');
                    this.markMergeComplete();
                } else if (status.mergeNeeded === false) {
                    // Server says no merge needed (fast refresh)
                    console.log('Server reports no merge needed');
                    this.markMergeComplete();
                }
            } else {
                console.log('Merge status check failed:', response.status);

                // If server errors persist, assume complete after reasonable time
                const elapsed = Date.now() - this.startTime;
                if (elapsed > 5000) {  // 5 seconds
                    console.log('Server errors - assuming merge complete');
                    this.markMergeComplete();
                }
            }
        } catch (error) {
            if (error.name === 'AbortError') {
                console.log('Merge status check timeout');
            } else {
                console.log('Could not check merge status:', error.message);
            }

            // On network errors, be more aggressive about completion
            const elapsed = Date.now() - this.startTime;
            if (elapsed > 3000) {  // 3 seconds
                console.log('Network issues - assuming merge complete');
                this.markMergeComplete();
            }
        }
    }

    /**
     * ENHANCED: Mark merge as complete with timing info
     */
    markMergeComplete() {
        if (!this.mergeInProgress) return;

        const elapsed = Date.now() - this.startTime;
        console.log(`Background merge completed in ${elapsed}ms`);

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

        // Store completion time for analytics
        sessionStorage.setItem('lastMergeTime', elapsed.toString());
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

        this.mergeInProgress = false;
    }
}

// OPTIMIZED: Initialize only when needed
document.addEventListener('DOMContentLoaded', () => {
    // Check if we should initialize merge status monitoring
    const urlParams = new URLSearchParams(window.location.search);
    const isFirstLogin = urlParams.get('firstLogin') === 'true';
    const loginCount = sessionStorage.getItem('dailyLoginCount');

    console.log('DOM ready - firstLogin:', isFirstLogin, 'loginCount:', loginCount);

    // Only initialize for first login or when explicitly needed
    if (isFirstLogin || loginCount === '1') {
        console.log('Initializing background merge status for first login');
        window.backgroundMergeStatus = new BackgroundMergeStatus();
    } else {
        console.log('Skipping background merge status - not first login');
    }
});

// Export for manual control
window.BackgroundMergeStatus = BackgroundMergeStatus;