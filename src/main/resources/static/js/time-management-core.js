/**
 * Time Management Core - Main coordinator for all time management modules
 * Handles page initialization, module coordination, and global event management
 */

const TimeManagementCore = {

    // ========================================================================
    // INITIALIZATION STATE
    // ========================================================================

    state: {
        isInitialized: false,
        modules: [],
        perfStart: performance.now()
    },

    // ========================================================================
    // MAIN INITIALIZATION
    // ========================================================================

    /**
     * Initialize the complete time management system
     */
    initialize() {
        console.log('🔍 DEBUG: Current URL when page loads:', window.location.href);
        console.log('🔍 DEBUG: URL parameters:', new URLSearchParams(window.location.search).toString());
        // Prevent multiple initializations
        if (this.state.isInitialized) {
            console.warn('Time Management already initialized, skipping...');
            return;
        }

        console.log('🚀 Initializing Time Management System...');

        // Initialize time input features first
        this.initializeTimeInputFeatures();

        // Initialize all modules in dependency order
        this.initializeModules();

        // Set up global error handling
        this.setupGlobalErrorHandling();

        // Handle server messages with toast system
        this.handleServerMessages();

        // Show keyboard shortcut help
        this.showKeyboardShortcuts();

        // Mark as initialized
        this.state.isInitialized = true;

        // Log performance
        this.logInitializationPerformance();

        console.log('✅ Time Management System fully initialized');

        // Add this to the end of TimeManagementCore.initialize()
        const savedScrollPosition = sessionStorage.getItem('timeManagementScrollPosition');
        if (savedScrollPosition) {
            const scrollY = parseInt(savedScrollPosition);
            console.log('📜 Restoring scroll position:', scrollY);

            // Wait for page to fully load, then scroll
            setTimeout(() => {
                window.scrollTo(0, scrollY);
                sessionStorage.removeItem('timeManagementScrollPosition');
            }, 100);
        }
    },

    /**
     * Initialize time input features
     */
    initializeTimeInputFeatures() {
        // Add time input validation listeners
        document.addEventListener('input', function(e) {
            if (e.target.hasAttribute('data-time-input')) {
                const value = e.target.value;
                const isValid = window.TimeInputModule?.validateTime(value);

                // Update validation classes
                if (value.length === 5) {
                    e.target.classList.toggle('is-valid', isValid);
                    e.target.classList.toggle('is-invalid', !isValid);
                } else {
                    e.target.classList.remove('is-valid', 'is-invalid');
                }
            }
        });

        // Log module availability
        if (window.TimeInputModule) {
            console.log('✅ TimeInputModule loaded successfully');
        } else {
            console.warn('⚠️ TimeInputModule not found - time inputs may not work properly');
        }
    },

    /**
     * Initialize all modules in the correct order
     */
    initializeModules() {
        const moduleInitializers = [
            // Utility modules first
            { name: 'UtilitiesModule', module: window.UtilitiesModule },

            // Display and status modules
            { name: 'StatusDisplayModule', module: window.StatusDisplayModule },
            { name: 'WorkTimeDisplayModule', module: window.WorkTimeDisplayModule },

            // Feature modules that depend on display modules
            { name: 'InlineEditingModule', module: window.InlineEditingModule },
            { name: 'TimeOffManagementModule', module: window.TimeOffManagementModule },
            { name: 'PeriodNavigationModule', module: window.PeriodNavigationModule }
        ];

        moduleInitializers.forEach(({ name, module }) => {
            if (module && typeof module.initialize === 'function') {
                try {
                    module.initialize();
                    this.state.modules.push(name);
                    console.log(`✅ ${name} initialized successfully`);
                } catch (error) {
                    console.error(`❌ Failed to initialize ${name}:`, error);
                }
            } else {
                console.warn(`⚠️ ${name} not found or missing initialize method`);
            }
        });

        console.log(`📦 Initialized ${this.state.modules.length} modules:`, this.state.modules.join(', '));
    },

    // ========================================================================
    // SERVER MESSAGE HANDLING
    // ========================================================================

    /**
     * ENHANCED: Handle server-side messages with toast system AND holiday modal integration
     */
    handleServerMessages() {
        console.log('🔍 Checking server messages and holiday modal triggers...');

        // Check for success messages and provide additional feedback
        const successAlert = document.querySelector('.alert-success');
        if (successAlert) {
            // Hide the original alert since toast system handles it
            successAlert.style.display = 'none';

            // Show success toast
            if (window.showToast) {
                const message = successAlert.textContent.trim();
                window.showToast('Success', message, 'success');
            }
            window.addEventListener('beforeunload', function(e) {
                console.log('🔍 DEBUG: Page is being unloaded/refreshed. Current URL:', window.location.href);
            });
            setTimeout(() => {
                console.log('🔄 Auto-refreshing page after successful operation...');
                window.location.reload();
            }, 3000); // 3 second delay
        }

        // Check for error messages
        const errorAlert = document.querySelector('.alert-danger');
        if (errorAlert) {
            errorAlert.style.display = 'none';

            if (window.showToast) {
                const message = errorAlert.textContent.trim();
                window.showToast('Error', message, 'error');
            }
        }

        // NEW: Check for holiday modal trigger from server
        this.checkForHolidayModalTrigger();
    },

    /**
     * NEW: Check if server indicated we should open holiday modal after time-off submission
     */
    checkForHolidayModalTrigger() {
        const holidayDataElement = document.getElementById('timeOffResultData');
        if (!holidayDataElement) {
            console.log('📄 No holiday modal trigger data found');
            return;
        }

        try {
            const holidayData = JSON.parse(holidayDataElement.textContent);
            console.log('📊 Holiday modal data:', holidayData);

            // Check if we should auto-open holiday modal
            if (holidayData.openHolidayModal === 'true') {
                console.log('🎯 Holiday modal trigger detected!');

                // Ensure holiday modal functions are available
                if (typeof window.openHolidayModal !== 'function') {
                    console.error('❌ Holiday modal function not available');
                    return;
                }

                // Show success toast first (if we have a message)
                if (holidayData.successMessage && window.showToast) {
                    window.showToast('Time Off Added', holidayData.successMessage, 'success', {
                        duration: 2000
                    });
                }

                // Schedule holiday modal opening after success message
                setTimeout(() => {
                    console.log('🚀 Opening holiday modal with dates:', {
                        start: holidayData.holidayStartDate,
                        end: holidayData.holidayEndDate
                    });

                    // Extract user data (reuse existing function if available)
                    const userData = this.extractCurrentUserData();

                    // Open the holiday modal
                    window.openHolidayModal(
                        holidayData.holidayStartDate,
                        holidayData.holidayEndDate,
                        userData
                    );

                    console.log('✅ Holiday modal opened successfully');

                }, 1500); // 1.5 second delay to let user see the success message
            }

        } catch (error) {
            console.error('❌ Error processing holiday modal trigger data:', error);
        }
    },

    /**
     * NEW: Extract current user data for holiday modal
     */
    extractCurrentUserData() {
        const userData = {};

        // Method 1: Try to get name from user badge (most reliable)
        const userBadgeSpan = document.querySelector('.badge .bi-person + span');
        if (userBadgeSpan && userBadgeSpan.textContent.trim()) {
            userData.name = userBadgeSpan.textContent.trim();
            console.log('👤 Found username from badge:', userData.name);
        }

        // Method 2: Try page title or header if badge method failed
        if (!userData.name) {
            const pageHeaders = document.querySelectorAll('h1, h2, h3, .header-title');
            pageHeaders.forEach(header => {
                const text = header.textContent;
                if (text.includes('Time Management') && text.includes('-')) {
                    const parts = text.split('-');
                    if (parts.length > 1) {
                        userData.name = parts[1].trim();
                        console.log('👤 Found username from header:', userData.name);
                    }
                }
            });
        }

        // Fallback name for safety
        if (!userData.name) {
            userData.name = 'User';
            console.log('👤 Using fallback username');
        }

        return userData;
    },

    // ========================================================================
    // GLOBAL ERROR HANDLING
    // ========================================================================

    /**
     * Set up global error handling
     */
    setupGlobalErrorHandling() {
        // Handle JavaScript errors
        window.addEventListener('error', (e) => {
            console.error('❌ JavaScript error:', e.error);

            if (window.UtilitiesModule) {
                window.UtilitiesModule.hideLoadingOverlay();
            }

            // Use toast system for error reporting
            if (window.showToast) {
                window.showToast('System Error',
                    'An unexpected error occurred. Please refresh the page.',
                    'error',
                    { persistent: true });
            }
        });

        // Handle unhandled promise rejections
        window.addEventListener('unhandledrejection', (e) => {
            console.error('❌ Unhandled promise rejection:', e.reason);

            if (window.UtilitiesModule) {
                window.UtilitiesModule.hideLoadingOverlay();
            }

            if (window.showToast) {
                window.showToast('Network Error',
                    'A network error occurred. Please check your connection.',
                    'error',
                    { persistent: true });
            }
        });
    },

    // ========================================================================
    // USER GUIDANCE
    // ========================================================================

    /**
     * Show keyboard shortcuts help
     */
    showKeyboardShortcuts() {
        console.log('⌨️ Keyboard shortcuts available:');
        console.log('  📅 Ctrl+← (previous month), Ctrl+→ (next month)');
        console.log('  ✏️ Double-click cells to edit');
        console.log('  ⏎ Enter to save, Escape to cancel editing');
    },

    // ========================================================================
    // PERFORMANCE MONITORING
    // ========================================================================

    /**
     * Log initialization performance
     */
    logInitializationPerformance() {
        const loadTime = performance.now() - this.state.perfStart;
        console.log(`⏱️ Time Management System loaded in ${loadTime.toFixed(2)}ms`);

        // Store performance data for debugging
        window.timeManagementPerformance = {
            initializationTime: loadTime,
            modulesLoaded: this.state.modules.length,
            timestamp: new Date().toISOString()
        };
    },

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Get system status
     * @returns {Object} Current system status
     */
    getSystemStatus() {
        return {
            isInitialized: this.state.isInitialized,
            modulesLoaded: this.state.modules,
            moduleCount: this.state.modules.length,
            performance: window.timeManagementPerformance
        };
    },

    /**
     * Reinitialize the system
     */
    reinitialize() {
        console.log('🔄 Reinitializing Time Management System...');
        this.state.isInitialized = false;
        this.state.modules = [];
        this.initialize();
    },

    /**
     * Get module instance
     * @param {string} moduleName - Name of the module
     * @returns {Object|null} Module instance
     */
    getModule(moduleName) {
        return window[moduleName] || null;
    },

    /**
     * Check if module is loaded
     * @param {string} moduleName - Name of the module
     * @returns {boolean} True if module is loaded
     */
    isModuleLoaded(moduleName) {
        return this.state.modules.includes(moduleName);
    },

    // ========================================================================
    // DEBUGGING UTILITIES
    // ========================================================================

    /**
     * Enable debug mode
     */
    enableDebugMode() {
        window.DEBUG_MODE = true;
        localStorage.setItem('timeManagementDebug', 'true');
        console.log('🐛 Debug mode enabled');
    },

    /**
     * Disable debug mode
     */
    disableDebugMode() {
        window.DEBUG_MODE = false;
        localStorage.removeItem('timeManagementDebug');
        console.log('🐛 Debug mode disabled');
    },

    /**
     * Get debug information
     * @returns {Object} Debug information
     */
    getDebugInfo() {

        return {
            system: this.getSystemStatus(),
            modules: {
                UtilitiesModule: !!window.UtilitiesModule,
                TimeInputModule: !!window.TimeInputModule,
                StatusDisplayModule: !!window.StatusDisplayModule,
                WorkTimeDisplayModule: !!window.WorkTimeDisplayModule,
                InlineEditingModule: !!window.InlineEditingModule,
                TimeOffManagementModule: !!window.TimeOffManagementModule,
                PeriodNavigationModule: !!window.PeriodNavigationModule
            },

            editing: window.InlineEditingModule?.getCurrentState() || null,
            url: window.location.href,
            userAgent: navigator.userAgent,
            timestamp: new Date().toISOString()
        };
    },

    /**
     * Log debug information to console
     */
    logDebugInfo() {
        console.log('🐛 Time Management Debug Info:', this.getDebugInfo());
    }
};

// ========================================================================
// DOM READY INITIALIZATION
// ========================================================================

document.addEventListener('DOMContentLoaded', function () {
    console.log('📄 Time Management page loaded - modular architecture');

    // Initialize the core system
    TimeManagementCore.initialize();
});

// ========================================================================
// PAGE LOAD PERFORMANCE
// ========================================================================

window.addEventListener('load', function() {
    const perfData = window.timeManagementPerformance;
    if (perfData) {
        console.log(`📊 Complete page load performance:`);
        console.log(`  - System init: ${perfData.initializationTime.toFixed(2)}ms`);
        console.log(`  - Modules loaded: ${perfData.modulesLoaded}`);
        console.log(`  - Total time: ${(performance.now() - TimeManagementCore.state.perfStart).toFixed(2)}ms`);
    }
});

// ========================================================================
// GLOBAL EXPORTS
// ========================================================================

// Make TimeManagementCore available globally
window.TimeManagementCore = TimeManagementCore;

// Export for CommonJS if available
if (typeof module !== 'undefined' && module.exports) {
    module.exports = TimeManagementCore;
}