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

### `api.js` ✅ COMPLETE

**Unified AJAX/fetch wrapper with CSRF handling.**

Provides consistent HTTP request handling across the application.

**Exports:**
- `API` class with static methods
- `APIError` class for error handling

**HTTP Methods:**
- `get(url, params, options)` - GET request
- `post(url, data, options)` - POST with JSON
- `postForm(url, formData, options)` - POST with form data
- `put(url, data, options)` - PUT request
- `patch(url, data, options)` - PATCH request
- `delete(url, options)` - DELETE request

**Features:**
- Automatic CSRF token injection (from meta tags)
- Request/response interceptors
- Timeout support (default: 30s)
- JSON/FormData handling
- URL parameter encoding
- Consistent error handling
- Custom APIError class with helper methods

**Usage:**
```javascript
import { API } from './core/api.js';

// GET request with params
const users = await API.get('/api/users', { page: 1, limit: 10 });

// POST with JSON
const result = await API.post('/api/users', {
    name: 'John Doe',
    email: 'john@example.com'
});

// POST with form data
const formData = new FormData(form);
const result = await API.postForm('/api/upload', formData);

// Error handling
try {
    const data = await API.get('/api/data');
} catch (error) {
    if (error.isTimeout()) {
        console.error('Request timed out');
    } else if (error.isClientError()) {
        console.error('Client error:', error.message);
    }
}

// Configure (do once on app init)
API.configure({
    baseURL: '/api',
    timeout: 60000,
    defaultHeaders: { 'X-Custom': 'value' }
});

// Add interceptors
API.addRequestInterceptor((url, options) => {
    console.log('Request:', url);
    return options;
});

API.addResponseInterceptor((response) => {
    console.log('Response:', response.status);
    return response;
});
```

**Benefits:**
- ✅ Single place for HTTP logic
- ✅ ~250 lines of inline AJAX eliminated
- ✅ Automatic CSRF handling
- ✅ Consistent error handling
- ✅ Interceptor support for logging/auth

---

### `utils.js` ✅ COMPLETE

**Common utility functions - No jQuery.**

Pure vanilla JavaScript utilities for common operations.

**Categories:**

**DOM Utilities:**
- `$(selector, context)` - Query selector wrapper
- `$$(selector, context)` - Query selector all wrapper
- `createElement(tag, attrs, content)` - Create element with attributes
- `on(element, event, selector, handler)` - Event delegation
- `remove(element)` - Remove element from DOM
- `hasClass(element, className)` - Check if has class

**Date/Time Utilities:**
- `formatDate(date)` - Format to YYYY-MM-DD
- `formatDateEU(date)` - Format to DD/MM/YYYY
- `formatTime(date)` - Format to HH:mm
- `formatDateTime(date)` - Format to YYYY-MM-DD HH:mm:ss
- `parseDate(dateString)` - Parse various date formats
- `getRelativeTime(date)` - Get relative time ("2 hours ago")

**String Utilities:**
- `capitalize(str)` - Capitalize first letter
- `titleCase(str)` - Convert to title case
- `truncate(str, maxLength, suffix)` - Truncate with suffix
- `escapeHtml(str)` - Escape HTML characters
- `stripHtml(html)` - Strip HTML tags
- `randomString(length)` - Generate random string

**Number Utilities:**
- `formatNumber(num, decimals)` - Format with thousand separators
- `formatPercentage(num, decimals, isDecimal)` - Format as percentage
- `clamp(num, min, max)` - Clamp between min/max
- `isNumeric(value)` - Check if numeric

**Array/Object Utilities:**
- `deepClone(obj)` - Deep clone object
- `isEmpty(obj)` - Check if empty
- `groupBy(array, key)` - Group array by key
- `sortBy(array, key, ascending)` - Sort array by key
- `unique(array)` - Remove duplicates

**Function Utilities:**
- `debounce(func, wait)` - Debounce function
- `throttle(func, limit)` - Throttle function
- `sleep(ms)` - Async sleep/delay

**URL Utilities:**
- `getUrlParams(url)` - Get URL parameters as object
- `updateUrlParam(key, value)` - Update URL parameter
- `removeUrlParam(key)` - Remove URL parameter

**Validation Utilities:**
- `isValidEmail(email)` - Validate email
- `isValidUrl(url)` - Validate URL
- `isValidPhone(phone)` - Validate phone number

**Usage:**
```javascript
import { formatDate, debounce, createElement, $ } from './core/utils.js';

// Date formatting
const today = formatDate(new Date()); // '2025-11-04'
const time = formatTime(new Date()); // '14:30'

// DOM manipulation (no jQuery)
const button = $('button.submit');
const inputs = $$('input.required');
const div = createElement('div', { class: 'card' }, 'Hello');

// Debounce search input
const debouncedSearch = debounce((query) => {
    console.log('Searching:', query);
}, 300);
input.addEventListener('input', (e) => debouncedSearch(e.target.value));

// String utilities
const text = truncate('Long text here...', 20); // 'Long text here...'
const safe = escapeHtml('<script>alert("xss")</script>');

// Array operations
const grouped = groupBy(users, 'role');
const sorted = sortBy(users, 'name');
const uniqueIds = unique([1, 2, 2, 3, 3, 4]);

// Number formatting
const formatted = formatNumber(1234567.89, 2); // '1,234,567.89'
const percent = formatPercentage(0.856, 1); // '85.6%'
```

**Benefits:**
- ✅ No jQuery dependency
- ✅ ~200 lines saved across files
- ✅ Consistent utilities across app
- ✅ Modern JavaScript (ES6+)
- ✅ Well-tested patterns
- ✅ Tree-shakeable imports

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
