# Deprecated Legacy Files

This document tracks legacy JavaScript files that have been replaced by ES6 modules.

## Fully Deprecated Files

### 1. constants.js (259 lines)
**Status**: ✅ DEPRECATED
**Replaced by**: `src/main/resources/static/js/core/constants.js`
**Date deprecated**: 2025-11-04
**Reason**: All constants consolidated into ES6 module with Maps for better structure

**Migration**: Import from `core/constants.js` instead:
```javascript
import { ACTION_TYPE_VALUES, CHECK_TYPE_VALUES, COMPLEXITY_PRINT_PREPS } from '../core/constants.js';
```

### 2. toast-alerts.js (321 lines)
**Status**: ✅ DEPRECATED
**Replaced by**: `src/main/resources/static/js/components/ToastNotification.js`
**Date deprecated**: 2025-11-04
**Reason**: Unified toast system with better features (XSS protection, queue management, server alerts)

**Migration**: Import ToastNotification component:
```javascript
import { ToastNotification } from '../components/ToastNotification.js';
const toast = new ToastNotification();
toast.success('Title', 'Message');
```

### 3. session-utility.js (0 lines)
**Status**: ✅ DEPRECATED
**Replaced by**: N/A (file is empty)
**Date deprecated**: N/A
**Reason**: File was never implemented

## Partially Deprecated Files

### 4. default.js (29 lines)
**Status**: ⚠️ PARTIALLY DEPRECATED
**Replaced by**: Partially replaced by `components/ToastNotification.js`
**Remaining functionality**:
- URL parameter cleanup (lines 2-5)
- Form validation setup (lines 19-28)

**Note**: Alert auto-dismiss (lines 8-16) is replaced by ToastNotification.
Consider migrating remaining utilities to `core/utils.js` in future refactoring.

## Files Integrated into ES6 Module System

### 5. utility-core.js (625 lines)
**Status**: ✅ REFACTORED
**Replaced by**: `features/utilities/admin/UtilityCoordinator.js`
**Date refactored**: 2025-11-05
**Reason**: Converted to ES6 module for better organization

### 6. Utility Management Modules (um/*.js - 3,130 lines)
**Status**: ✅ INTEGRATED
**Coordinated by**:
- `features/utilities/admin/UtilityCoordinator.js`
- `features/utilities/admin/UtilityModuleManager.js`
**Date integrated**: 2025-11-05
**Reason**: Admin-only jQuery utilities integrated into ES6 module system via coordinators

**Files**:
- actions-utility.js (556 lines) - Quick actions and emergency operations
- backup-utility.js (525 lines) - Backup management
- diagnostics-utility.js (406 lines) - System diagnostics
- health-utility.js (465 lines) - System health monitoring
- merge-utility.js (621 lines) - Merge operations
- monitor-utility.js (557 lines) - Cache and session monitoring

**Note**: Individual utility files remain jQuery-based but are now coordinated through ES6 module system. Can be refactored to vanilla JS in future phases.

## Removal Timeline

**Phase 4** (HTML Template Updates):
- Update all HTML templates to use new ES6 modules
- Verify no remaining references to deprecated files

**Phase 5** (Final Cleanup):
- Remove or archive deprecated files
- Update documentation
- Final testing

## Usage Check

Before removing any deprecated file, verify no active usage:
```bash
# Search for imports or script tags
grep -r "constants.js" src/main/resources/templates/
grep -r "toast-alerts.js" src/main/resources/templates/
grep -r "default.js" src/main/resources/templates/
```

## Summary

- **Fully deprecated**: 3 files (constants.js, toast-alerts.js, session-utility.js)
- **Partially deprecated**: 1 file (default.js - 2 utilities remain)
- **Refactored to ES6**: 7 files (utility-core.js + 6 utility modules)
- **Total legacy code addressed**: 11 files, ~4,285 lines

**Next step**: Update HTML templates in Phase 4 to use new ES6 modules, then remove deprecated files in Phase 5.
