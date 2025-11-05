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

### `statusService.js` ✅ COMPLETE

**Status badge and label management.**

Previously duplicated across 3+ files, now unified here.

**Exports:**
- `StatusService` class with static methods

**Key Methods:**

**Display Helpers:**
- `getLabel(status)` - Get display label ('USER_DONE' → 'User Completed')
- `getClass(status)` - Get CSS text class ('text-success', 'text-warning')
- `getBadgeClass(status)` - Get badge class ('bg-success', 'bg-warning')
- `getBadgeHtml(status, classes)` - Generate complete badge HTML
- `formatWithIcon(status)` - Format with icon (✓, 🔒, etc.)

**Status Checks:**
- `isFinal(status)` - Check if status is final (ADMIN_FINAL, TEAM_FINAL)
- `isInProcess(status)` - Check if active session
- `isUserStatus(status)` - Check if user-created
- `isAdminStatus(status)` - Check if admin-modified
- `isTeamStatus(status)` - Check if team-modified

**Permission Checks:**
- `isEditable(status, userRole)` - Check if user can edit entry
- `canOverride(currentStatus, newStatus)` - Check override permissions
- `getPriority(status)` - Get priority level (0-5)

**Utilities:**
- `getAllStatuses()` - Get all valid status codes
- `isValidStatus(status)` - Validate status code
- `getStatusForAction(userRole, isFinal)` - Get recommended status for action

**Usage:**
```javascript
import { StatusService } from './services/statusService.js';

// Display helpers
const label = StatusService.getLabel('USER_DONE'); // 'User Completed'
const cssClass = StatusService.getClass('ADMIN_EDITED'); // 'text-warning'
const badge = StatusService.getBadgeClass('ADMIN_FINAL'); // 'bg-dark'
const html = StatusService.getBadgeHtml('USER_DONE');
// '<span class="badge bg-success">User Completed</span>'

// Status checks
const isFinal = StatusService.isFinal('ADMIN_FINAL'); // true
const isActive = StatusService.isInProcess('USER_IN_PROCESS'); // true

// Permission checks
const canEdit = StatusService.isEditable('USER_DONE', 'ROLE_ADMIN'); // true
const canEdit2 = StatusService.isEditable('ADMIN_FINAL', 'ROLE_ADMIN'); // false
const canEdit3 = StatusService.isEditable('USER_IN_PROCESS', 'ROLE_ADMIN'); // false (active)

const canOverride = StatusService.canOverride('TEAM_EDITED', 'ADMIN_EDITED'); // true
const canOverride2 = StatusService.canOverride('ADMIN_FINAL', 'TEAM_EDITED'); // false

// Utilities
const priority = StatusService.getPriority('ADMIN_FINAL'); // 5 (highest)
const status = StatusService.getStatusForAction('ROLE_ADMIN', true); // 'ADMIN_FINAL'
```

**Permission Rules:**
- **ADMIN_FINAL**: Cannot be edited by anyone (locked)
- **USER_IN_PROCESS**: Cannot be edited (active session)
- **ROLE_ADMIN**: Can edit all except ADMIN_FINAL and active sessions
- **ROLE_TEAM_LEADER**: Can edit user and team entries, not admin entries
- **ROLE_USER**: Can only edit own non-final entries

**Priority Hierarchy:**
1. ADMIN_FINAL (5) - Highest
2. TEAM_FINAL (4)
3. ADMIN_EDITED (3)
4. TEAM_EDITED (2)
5. USER_IN_PROCESS (2)
6. USER_DONE, USER_INPUT, ADMIN_BLANK (1) - Lowest

**Benefits:**
- ✅ Single place for all status logic
- ✅ ~80 lines of duplication eliminated
- ✅ Comprehensive permission checking
- ✅ Role-based edit validation
- ✅ Priority-based conflict resolution
- ✅ Uses STATUS_TYPES from `core/constants.js`

---

### `validationService.js` ✅ COMPLETE

**Form validation utilities.**

Provides common validation rules and utilities for form validation across the app.

**Exports:**
- `ValidationService` class with static methods

**Built-in Validation Rules:**
- `required` - Field must have a value
- `email` - Valid email format
- `number` - Valid number
- `integer` - Valid integer
- `url` - Valid URL
- `phone` - Valid phone number (basic)
- `date` - Valid date
- `alpha` - Alphabetic characters only
- `alphanumeric` - Alphanumeric characters only

**Parametric Rules:**
- `min:value` - Minimum numeric value
- `max:value` - Maximum numeric value
- `minLength:n` - Minimum string length
- `maxLength:n` - Maximum string length
- `length:n` - Exact string length
- `pattern:regex` - Match regex pattern
- `in:opt1,opt2` - Value must be one of options
- `notIn:opt1,opt2` - Value cannot be one of options
- `between:min,max` - Value must be between min and max

**Key Methods:**

**Basic Validation:**
- `validateField(value, rules, fieldName)` - Validate single field
- `validate(fields)` - Validate multiple fields
- `hasErrors(errors)` - Check if validation has errors

**Custom Rules:**
- `addRule(name, validate, message)` - Register custom validation rule
- `removeRule(name)` - Remove custom rule
- `hasRule(name)` - Check if rule exists

**Conditional Validation:**
- `validateIf(value, rules, condition, fieldName)` - Validate conditionally

**Batch Helpers:**
- `validateRequired(fields)` - Validate required fields
- `validateForm(form, rules)` - Validate HTML form element

**Specific Validators:**
- `validateDateRange(startDate, endDate)` - Validate date range
- `validatePasswordStrength(password, requirements)` - Password strength
- `validatePasswordMatch(password, confirmPassword)` - Password match
- `validateArray(array, options)` - Array validation

**Usage:**

```javascript
import { ValidationService } from './services/validationService.js';

// ===== Single Field Validation =====
const error = ValidationService.validateField(
    'john@example.com',
    ['required', 'email'],
    'Email'
);

if (error) {
    console.log('Error:', error);
} else {
    console.log('Valid');
}

// With parametric rules
const ageError = ValidationService.validateField(15, ['required', 'number', 'min:18'], 'Age');
// Error: "Age must be at least 18."

// ===== Multiple Fields Validation =====
const errors = ValidationService.validate({
    email: {
        value: 'user@example.com',
        rules: ['required', 'email'],
        label: 'Email Address'
    },
    password: {
        value: 'secret123',
        rules: ['required', 'minLength:8'],
        label: 'Password'
    },
    age: {
        value: 25,
        rules: ['required', 'number', 'between:18,100']
    }
});

if (ValidationService.hasErrors(errors)) {
    console.log('Validation errors:', errors);
    // { email: null, password: "Password must be at least 8 characters.", age: null }
}

// ===== Form Validation =====
const formErrors = ValidationService.validateForm(form, {
    username: ['required', 'minLength:3', 'maxLength:20'],
    email: ['required', 'email'],
    phone: ['phone'],  // Optional phone
    age: ['required', 'integer', 'min:18']
});

// ===== Custom Validation Rules =====
ValidationService.addRule('strongPassword', (value) => {
    return /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/.test(value);
}, 'Password must contain uppercase, lowercase, number, and special character.');

const pwdError = ValidationService.validateField(
    'weakpass',
    ['required', 'strongPassword'],
    'Password'
);

// ===== Conditional Validation =====
const phoneError = ValidationService.validateIf(
    phoneValue,
    ['required', 'phone'],
    () => contactMethod === 'phone',  // Only validate if phone is selected
    'Phone Number'
);

// ===== Required Fields Only =====
const requiredErrors = ValidationService.validateRequired({
    name: document.getElementById('name').value,
    email: document.getElementById('email').value,
    message: document.getElementById('message').value
});

// ===== Date Range Validation =====
const dateError = ValidationService.validateDateRange(
    '2025-01-01',
    '2024-12-31'
);
// Error: "Start date must be before end date."

// ===== Password Validation =====
const passwordError = ValidationService.validatePasswordStrength('weak', {
    minLength: 10,
    requireUppercase: true,
    requireLowercase: true,
    requireNumbers: true,
    requireSpecial: true
});

const matchError = ValidationService.validatePasswordMatch(
    'password123',
    'password456'
);
// Error: "Passwords do not match."

// ===== Array Validation =====
const selectedItems = ['item1', 'item2'];
const arrayError = ValidationService.validateArray(selectedItems, {
    minItems: 1,
    maxItems: 5,
    unique: true,
    fieldName: 'Items'
});
```

**Integration with FormHandler:**

```javascript
import { FormHandler } from './components/FormHandler.js';
import { ValidationService } from './services/validationService.js';

const form = new FormHandler('#myForm', {
    url: '/api/submit',
    customValidation: (formData) => {
        // Use ValidationService for custom validation
        const errors = ValidationService.validate({
            email: {
                value: formData.get('email'),
                rules: ['required', 'email']
            },
            age: {
                value: formData.get('age'),
                rules: ['required', 'number', 'min:18', 'max:120']
            }
        });

        // Return errors object
        return errors;
    }
});
```

**Custom Rule Examples:**

```javascript
// Strong password rule
ValidationService.addRule('strongPassword', (value) => {
    return /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/.test(value);
}, 'Password must contain uppercase, lowercase, and numbers.');

// No whitespace rule
ValidationService.addRule('noSpaces', (value) => {
    return !/\s/.test(value);
}, 'This field cannot contain spaces.');

// Valid username rule
ValidationService.addRule('username', (value) => {
    return /^[a-zA-Z0-9_]{3,20}$/.test(value);
}, 'Username must be 3-20 characters (letters, numbers, underscore only).');

// Custom date rule (must be in future)
ValidationService.addRule('futureDate', (value) => {
    const inputDate = new Date(value);
    const now = new Date();
    return inputDate > now;
}, 'Date must be in the future.');
```

**Benefits:**
- ✅ Single place for all validation logic
- ✅ ~70 lines of duplicated validation code eliminated
- ✅ Reusable rules across all forms
- ✅ Custom rule support
- ✅ Built-in common validators
- ✅ Parametric rules (min, max, length, etc.)
- ✅ Date range and password validation
- ✅ Array/multi-select validation
- ✅ Integrates with FormHandler component

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
