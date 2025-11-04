# Services Module

This directory contains business logic services that are reused across features.

## Purpose

The `services/` directory provides centralized business logic that was previously duplicated across multiple files. Services handle validation, calculations, data transformation, and other non-UI logic.

## Design Principles

1. **Single Responsibility**: Each service handles one domain
2. **Stateless**: Services use static methods (no instantiation needed)
3. **Pure Functions**: Deterministic, testable behavior
4. **Use Core Constants**: All constants imported from `core/constants.js`
5. **Well Documented**: JSDoc for all public methods

## Modules

### `timeOffService.js` ✅ COMPLETE

**Consolidated time-off type management.**

Previously duplicated across 4+ files, now unified here.

**Exports:**
- `TimeOffService` class with static methods

**Key Methods:**
- `getLabel(type)` - Get display label ('SN' → 'National Holiday')
- `getIcon(type)` - Get Bootstrap icon class
- `getDescription(type)` - Get detailed description
- `getOvertimeTypeLabel(date, type)` - Determine overtime type
- `validate(value)` - Main validation (handles all formats)
- `validateZSFormat(value)` - Validate ZS-5 format
- `validateSpecialDayWorktime(value)` - Validate SN:7.5 format
- `allowsWork(type)` - Check if type allows work hours
- `parse(value)` - Parse string into components
- `format(parsed)` - Format parsed data for display
- `isValidType(type)` - Check if type code is valid

**Supported Formats:**
- Plain types: `SN`, `CO`, `CM`, `W`, `CR`, `CN`, `D`, `CE`
- Special day work: `SN:7.5`, `CO:6`, `CM:4`, `W:8`, `CE:6`
- Short day: `ZS-5` (missing hours)
- Regular work: `8h`, `7.5h`, or just `8`

**Usage:**
```javascript
import { TimeOffService } from './services/timeOffService.js';

// Get display info
const label = TimeOffService.getLabel('SN'); // 'National Holiday'
const icon = TimeOffService.getIcon('CO'); // 'bi bi-airplane text-info'
const desc = TimeOffService.getDescription('CR'); // 'Recovery Leave - Paid...'

// Validate input
const isValid = TimeOffService.validate('SN:7.5'); // true
const isValid2 = TimeOffService.validate('invalid'); // false (shows alert)

// Parse and format
const parsed = TimeOffService.parse('SN:7.5');
// { type: 'SN', hours: 7.5, format: 'special_day_work' }

const display = TimeOffService.format(parsed);
// 'National Holiday (7.5h)'

// Check capabilities
const canWork = TimeOffService.allowsWork('SN'); // true
const canWork2 = TimeOffService.allowsWork('CR'); // false

// Determine overtime type
const overtimeType = TimeOffService.getOvertimeTypeLabel('2025-01-01', 'SN');
// 'Holiday Overtime'
```

**Benefits:**
- ✅ Single place for all time-off logic
- ✅ ~300 lines of duplication eliminated
- ✅ Comprehensive validation with user-friendly alerts
- ✅ Parse/format utilities for data transformation
- ✅ Uses constants from `core/constants.js`

---

### `statusService.js` ⏳ PENDING

**Status badge and label management.**

Will consolidate status-related helpers from multiple files.

**Planned Methods:**
- `getLabel(status)` - Get display label
- `getClass(status)` - Get CSS class
- `getBadgeClass(status)` - Get badge class
- `isEditable(status)` - Check if entry can be edited
- `isFinal(status)` - Check if status is final

---

### `validationService.js` ⏳ PENDING

**Form validation logic.**

Will provide reusable validation for forms across the app.

**Planned Methods:**
- `validateForm(form, rules)` - Validate entire form
- `validateField(field, rules)` - Validate single field
- `showError(field, message)` - Display error message
- `clearErrors(form)` - Clear all errors

---

### `calculationService.js` ⏳ PENDING

**Complexity and bonus calculations.**

Will consolidate calculation logic from register and check modules.

**Planned Methods:**
- `calculateComplexity(entry)` - Calculate action complexity
- `calculateCheckValue(entry)` - Calculate check value
- `calculateBonus(entries)` - Calculate bonus
- `calculateOrderValue(checkType, articles, files)` - Calculate order value

---

## Migration Guide

When migrating legacy code that uses time-off functions:

### Before (Duplicated in multiple files)
```javascript
// In worktime-admin.js, constants.js, register-admin.js, etc.
function getTimeOffLabel(timeOffType) {
    if (!timeOffType) return timeOffType;
    if (timeOffType.startsWith('ZS-')) {
        // ... 60 lines of code
    }
    // ... switch statement
}
```

### After (Single import)
```javascript
import { TimeOffService } from './services/timeOffService.js';

const label = TimeOffService.getLabel('SN');
```

### Migration Steps

1. Add import at top of file:
   ```javascript
   import { TimeOffService } from '../../services/timeOffService.js';
   ```

2. Replace function calls:
   ```javascript
   // Before
   const label = getTimeOffLabel(type);
   const icon = getTimeOffIcon(type);
   const isValid = validateWorktimeValue(value);

   // After
   const label = TimeOffService.getLabel(type);
   const icon = TimeOffService.getIcon(type);
   const isValid = TimeOffService.validate(value);
   ```

3. Remove local function definitions (they're now in the service)

---

## Testing

Each service should have a corresponding test file in `tests/services/`.

Example:
```javascript
// tests/services/timeOffService.test.js
import { TimeOffService } from '../../services/timeOffService.js';

test('getLabel returns correct label', () => {
    expect(TimeOffService.getLabel('SN')).toBe('National Holiday');
    expect(TimeOffService.getLabel('CO')).toBe('Vacation');
});

test('validate accepts valid formats', () => {
    expect(TimeOffService.validate('8h')).toBe(true);
    expect(TimeOffService.validate('SN:7.5')).toBe(true);
    expect(TimeOffService.validate('ZS-5')).toBe(true);
});
```

---

_Last updated: 2025-11-04_
