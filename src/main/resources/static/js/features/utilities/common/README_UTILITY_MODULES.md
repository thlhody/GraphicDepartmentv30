/**
 * Admin Utility Modules - README
 *
 * This directory contains admin utility management modules that coordinate with
 * the UtilityCoordinator for system administration tasks.
 *
 * ## Modules
 *
 * 1. **ActionsUtility** (556 lines) - Quick actions and emergency operations
 *    - Emergency cache reset, force cache refresh
 *    - Quick cache data check, cache validation
 *    - System health check, refresh all data
 *
 * 2. **BackupUtility** (525 lines) - Backup management
 *    - Full backup operations, user backups
 *    - Backup restoration, backup deletion
 *    - Backup history and status
 *
 * 3. **DiagnosticsUtility** (406 lines) - System diagnostics
 *    - Backup event diagnostics
 *    - System summary reports
 *    - File type and period-based diagnostics
 *
 * 4. **HealthUtility** (465 lines) - System health monitoring
 *    - Overall health status
 *    - Task-level health checks
 *    - Health metrics and monitoring
 *
 * 5. **MergeUtility** (621 lines) - Merge operations
 *    - User data merges
 *    - Conflict resolution
 *    - Merge status tracking
 *
 * 6. **MonitorUtility** (557 lines) - Cache and session monitoring
 *    - Cache status monitoring
 *    - Session activity tracking
 *    - Real-time updates
 *
 * ## Architecture
 *
 * These modules integrate with:
 * - **UtilityCoordinator.js** - Main coordinator for cross-utility communication
 * - **UtilityState** - Shared state across all utilities
 * - **UtilityEvents** - Event-driven communication system
 *
 * ## Current Status
 *
 * **Implementation**: jQuery-based (legacy)
 * **Reason**: These are admin-only utilities with complex DOM manipulation
 * **Future**: Can be refactored to vanilla JS when needed
 *
 * ## Usage
 *
 * Each utility module:
 * 1. Initializes on DOM ready
 * 2. Registers event handlers for UI interactions
 * 3. Makes AJAX calls to backend utility endpoints
 * 4. Formats and displays results
 * 5. Communicates status via UtilityEvents
 *
 * ## Notes
 *
 * - All modules require jQuery
 * - All modules use Bootstrap 5 for UI components
 * - All modules coordinate via UtilityCoordinator
 * - Session utility (session-utility.js) is empty/deprecated
 *
 * ## Refactoring Priority
 *
 * **Priority**: Low (admin-only, working correctly)
 * **Dependencies**: jQuery, Bootstrap 5, UtilityCoordinator
 * **Estimated effort for full ES6 rewrite**: 40-50 hours
 */

// This directory contains jQuery-based utility modules.
// They are intentionally kept in legacy format as they:
// 1. Work correctly with existing backend endpoints
// 2. Are admin-only features (not user-facing)
// 3. Coordinate properly with UtilityCoordinator
// 4. Would require significant effort to refactor without clear benefit

// Future work: These can be refactored to vanilla JS/ES6 when:
// - jQuery is being removed from the project
// - UI components are being updated
// - Backend APIs are being modernized
