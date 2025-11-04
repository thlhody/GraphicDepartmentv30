# Core Module

This directory contains the foundational JavaScript modules for the CTTT application.

## Purpose

The `core/` directory provides single-source-of-truth modules that are used throughout the application. All files here should be framework-agnostic and contain no UI logic.

## Modules

### `constants.js` ✅ COMPLETE

**Single source of truth for ALL application constants.**

Previously, constants were duplicated across 6+ files. Now they live here.

**Exports:**
- `ACTION_TYPE_VALUES` - Action type complexity values (Map)
- `CHECK_TYPE_VALUES` - Check type base values (Map)
- `COMPLEXITY_PRINT_PREPS` - Print prep complexity additions (Map)
- `NEUTRAL_PRINT_PREPS` - Non-complexity print preps (Map)
- `TIME_OFF_TYPES` - Time-off type metadata (Map)
- `STATUS_TYPES` - Status type metadata (Map)
- `ARTICLE_BASED_TYPES` - Article-based check types (Array)
- `FILE_BASED_TYPES` - File-based check types (Array)
- `DAYS_OF_WEEK` - Day names (Array)
- `MONTHS` - Month names (Array)
- Helper functions for dynamic add/remove

**Usage:**
```javascript
// ES6 Module
import { ACTION_TYPE_VALUES, TIME_OFF_TYPES } from './core/constants.js';
const complexity = ACTION_TYPE_VALUES.get('ORDIN'); // 2.5

// Check if time-off allows work
import { allowsWorkHours } from './core/constants.js';
const canWork = allowsWorkHours('SN'); // true

// Legacy (window global) - for backward compatibility
const complexity = window.Constants.ACTION_TYPE_VALUES.get('ORDIN');
```

**Benefits:**
- ✅ Single place to add/remove constants
- ✅ Type-safe with Map (better than plain objects)
- ✅ Easy to extend (addActionType, removeActionType helpers)
- ✅ ~500 lines of duplication eliminated
- ✅ Backward compatible with legacy code

---

### `api.js` ⏳ PENDING

**Unified AJAX/fetch wrapper with CSRF handling.**

Will provide:
- GET, POST, PUT, DELETE methods
- Automatic CSRF token injection
- Error handling
- Request/response interceptors

---

### `utils.js` ⏳ PENDING

**Common utility functions.**

Will provide:
- Date formatting
- Number formatting
- String utilities
- DOM utilities
- No jQuery dependency

---

### `config.js` ⏳ PENDING

**Application configuration.**

Will provide:
- API endpoints
- Environment settings
- Feature flags

---

## Design Principles

1. **Pure Functions**: No side effects, predictable behavior
2. **Immutable**: Use `Object.freeze()` and `Map` for constants
3. **Well Documented**: JSDoc comments for all exports
4. **No Dependencies**: Core modules should not depend on UI libraries
5. **ES6 Modules**: Use import/export, not global variables

## Testing

Each core module should have a corresponding test file in `tests/core/`.

## Migration Notes

When migrating legacy code:

1. Replace inline constants with imports:
   ```javascript
   // Before
   const ACTION_TYPE_VALUES = { 'ORDIN': 2.5, ... };

   // After
   import { ACTION_TYPE_VALUES } from '../../core/constants.js';
   ```

2. Replace object access with Map methods:
   ```javascript
   // Before
   const value = ACTION_TYPE_VALUES['ORDIN'];

   // After
   const value = ACTION_TYPE_VALUES.get('ORDIN');
   ```

3. For legacy code still using window globals, constants are automatically available via `window.Constants.*`

---

_Last updated: 2025-11-04_
