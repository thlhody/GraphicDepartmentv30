/**
 * Utility Module Manager
 *
 * Manages loading and coordination of admin utility modules.
 * Acts as a bridge between ES6 module system and jQuery-based utility modules.
 *
 * NOTE: Individual utility modules (actions, backup, diagnostics, health, merge, monitor)
 * remain jQuery-based in legacy/um/ directory. They are loaded and coordinated through
 * this manager, which integrates them with the UtilityCoordinator.
 *
 * @module features/utilities/admin/UtilityModuleManager
 */

/**
 * Utility Module Manager class
 * Coordinates loading and initialization of all admin utility modules
 */
class UtilityModuleManager {
    constructor() {
        this.modules = {
            actions: null,
            backup: null,
            diagnostics: null,
            health: null,
            merge: null,
            monitor: null
        };

        this.loadStatus = {
            loaded: 0,
            total: 6,
            errors: []
        };
    }

    /**
     * Initialize all utility modules
     */
    async initialize() {
        console.log('🔧 Initializing Utility Module Manager...');

        // Check if utility modules are loaded (from legacy/um/ scripts)
        this.checkModuleAvailability();

        // Log load status
        this.logLoadStatus();

        // Register with UtilityCoordinator if available
        this.registerWithCoordinator();

        console.log('✅ Utility Module Manager initialized');
    }

    /**
     * Check which utility modules are available
     */
    checkModuleAvailability() {
        // Check if global utility objects exist (loaded from legacy/um/ scripts)
        const availableModules = {
            actions: typeof window.ActionsUtility !== 'undefined',
            backup: typeof window.BackupUtility !== 'undefined',
            diagnostics: typeof window.DiagnosticsUtility !== 'undefined',
            health: typeof window.HealthUtility !== 'undefined',
            merge: typeof window.MergeUtility !== 'undefined',
            monitor: typeof window.MonitorUtility !== 'undefined'
        };

        // Store references
        for (const [name, available] of Object.entries(availableModules)) {
            if (available) {
                this.modules[name] = window[this.getGlobalName(name)];
                this.loadStatus.loaded++;
            } else {
                this.loadStatus.errors.push(`${name} utility not loaded`);
            }
        }
    }

    /**
     * Get global object name for a utility module
     * @param {string} moduleName - Module name
     * @returns {string} Global object name
     */
    getGlobalName(moduleName) {
        const nameMap = {
            actions: 'ActionsUtility',
            backup: 'BackupUtility',
            diagnostics: 'DiagnosticsUtility',
            health: 'HealthUtility',
            merge: 'MergeUtility',
            monitor: 'MonitorUtility'
        };
        return nameMap[moduleName];
    }

    /**
     * Log module load status
     */
    logLoadStatus() {
        console.log(`📊 Utility Modules: ${this.loadStatus.loaded}/${this.loadStatus.total} loaded`);

        if (this.loadStatus.errors.length > 0) {
            console.warn('⚠️ Some utility modules failed to load:', this.loadStatus.errors);
        } else {
            console.log('🎉 All utility modules loaded successfully!');
        }
    }

    /**
     * Register with UtilityCoordinator
     */
    registerWithCoordinator() {
        if (typeof window.UtilityCoordinator !== 'undefined') {
            console.log('🔗 Registering utility modules with coordinator...');
            // Modules are already registered via global objects
            // UtilityCoordinator can access them via window.ActionsUtility, etc.
        }
    }

    /**
     * Get a specific utility module
     * @param {string} moduleName - Name of the module
     * @returns {Object|null} Module instance or null
     */
    getModule(moduleName) {
        return this.modules[moduleName] || null;
    }

    /**
     * Check if a module is loaded
     * @param {string} moduleName - Name of the module
     * @returns {boolean} True if module is loaded
     */
    isModuleLoaded(moduleName) {
        return this.modules[moduleName] !== null;
    }

    /**
     * Get all loaded modules
     * @returns {Object} Object with all loaded modules
     */
    getAllModules() {
        return this.modules;
    }

    /**
     * Get load status
     * @returns {Object} Load status object
     */
    getLoadStatus() {
        return {
            loaded: this.loadStatus.loaded,
            total: this.loadStatus.total,
            percentage: Math.round((this.loadStatus.loaded / this.loadStatus.total) * 100),
            errors: this.loadStatus.errors,
            modules: Object.keys(this.modules).reduce((acc, name) => {
                acc[name] = this.isModuleLoaded(name);
                return acc;
            }, {})
        };
    }

    /**
     * Refresh a specific utility module
     * @param {string} moduleName - Name of the module to refresh
     */
    refreshModule(moduleName) {
        const module = this.getModule(moduleName);
        if (module && typeof module.refresh === 'function') {
            console.log(`🔄 Refreshing ${moduleName} utility...`);
            module.refresh();
        } else if (module && typeof module.refreshOverview === 'function') {
            console.log(`🔄 Refreshing ${moduleName} utility overview...`);
            module.refreshOverview();
        } else {
            console.warn(`⚠️ ${moduleName} utility does not have a refresh method`);
        }
    }

    /**
     * Refresh all utility modules
     */
    refreshAll() {
        console.log('🔄 Refreshing all utility modules...');
        Object.keys(this.modules).forEach(name => {
            this.refreshModule(name);
        });
    }
}

// ============================================================================
// SINGLETON INSTANCE
// ============================================================================

// Create singleton instance
const utilityModuleManager = new UtilityModuleManager();

// ============================================================================
// INITIALIZATION
// ============================================================================

// Auto-initialize when DOM is ready
// Wait a bit to ensure legacy utility scripts are loaded first
document.addEventListener('DOMContentLoaded', function() {
    setTimeout(() => {
        utilityModuleManager.initialize();
    }, 1000); // 1 second delay to let legacy scripts initialize
});

// ============================================================================
// EXPORTS
// ============================================================================

export {
    UtilityModuleManager,
    utilityModuleManager as default
};

// Make available globally for backward compatibility
window.UtilityModuleManager = utilityModuleManager;
