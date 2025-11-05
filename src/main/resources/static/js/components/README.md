# Components Module

This directory contains reusable UI components.

## Purpose

The `components/` directory provides UI components that can be used across different features. Components handle presentation and user interaction, but delegate business logic to services.

## Design Principles

1. **Reusable**: Components work in any context
2. **Self-contained**: Minimal external dependencies
3. **Well-documented**: Clear API and usage examples
4. **Static Methods**: Use static methods where appropriate (no unnecessary instantiation)
5. **Bootstrap Compatible**: Integrate with Bootstrap 5

## Modules

### `ToastNotification.js` ✅ COMPLETE

**Unified toast/alert notification system.**

Consolidates two previous systems:
- `legacy/toast-alerts.js` (321 lines) - Full toast system
- `legacy/default.js` (28 lines) - Simple alert auto-dismiss

**Exports:**
- `ToastNotification` class with static methods

**Key Features:**
- Auto-dismiss with configurable timeout
- Progress bar animation (handled by Bootstrap)
- Multiple types: success, error, warning, info
- Position control (top-right, top-center, etc.)
- Queue management (max 5 concurrent toasts)
- Persistent toasts (manual dismiss only)
- XSS protection (HTML escaping)
- Bootstrap 5 native toast component
- Server-side alert processing

**Key Methods:**

**Convenience Methods (Quick API):**
- `success(title, message, options)` - Show success toast
- `error(title, message, options)` - Show error toast
- `warning(title, message, options)` - Show warning toast
- `info(title, message, options)` - Show info toast

**Core Methods:**
- `show(title, message, type, options)` - Show any type of toast
- `hide(toastId)` - Hide specific toast
- `hideAll(includePersistent)` - Hide all toasts
- `configure(options)` - Configure toast system
- `processServerAlerts()` - Process Thymeleaf model attributes
- `getCount()` - Get number of active toasts
- `exists(toastId)` - Check if toast is active

**Options:**
```javascript
{
    persistent: false,      // Don't auto-dismiss
    duration: 5000,         // Auto-dismiss time (ms)
    icon: 'bi-...',        // Custom Bootstrap icon
    closeButton: true       // Show close button
}
```

**Configuration:**
```javascript
{
    position: 'top-end',    // Bootstrap position class
    duration: 5000,         // Default duration
    maxToasts: 5,           // Max concurrent toasts
    animation: true,        // Enable animations
    closeButton: true       // Show close button by default
}
```

**Usage:**

```javascript
import { ToastNotification } from './components/ToastNotification.js';

// Simple success toast
ToastNotification.success('Success!', 'Operation completed successfully');

// Error with custom duration
ToastNotification.error('Error!', 'Something went wrong', {
    duration: 8000
});

// Persistent warning (won't auto-dismiss)
ToastNotification.warning('Warning!', 'This requires your attention', {
    persistent: true
});

// Custom type with all options
const toastId = ToastNotification.show(
    'Custom Title',
    'Custom message here',
    'info',
    {
        persistent: false,
        duration: 10000,
        icon: 'bi-star-fill',
        closeButton: true
    }
);

// Hide specific toast later
setTimeout(() => {
    ToastNotification.hide(toastId);
}, 5000);

// Configure system (do this once on app init)
ToastNotification.configure({
    position: 'top-center',
    maxToasts: 3,
    duration: 7000
});

// Process server-side alerts (Thymeleaf integration)
document.addEventListener('DOMContentLoaded', () => {
    ToastNotification.processServerAlerts();
});
```

**Position Options (Bootstrap):**
- `top-start` - Top left
- `top-center` - Top center
- `top-end` - Top right (default)
- `middle-start` - Middle left
- `middle-center` - Middle center
- `middle-end` - Middle right
- `bottom-start` - Bottom left
- `bottom-center` - Bottom center
- `bottom-end` - Bottom right

**Benefits:**
- ✅ Single unified notification system
- ✅ ~100 lines of duplication eliminated
- ✅ Cleaner API with static methods
- ✅ Native Bootstrap 5 toast integration
- ✅ XSS protection built-in
- ✅ Queue management prevents spam
- ✅ Server-side alert processing
- ✅ No global namespace pollution
- ✅ Fully configurable

---

### `FormHandler.js` ⏳ PENDING

**Base class for form handling.**

Will provide common form functionality.

**Planned Features:**
- Form validation
- Submit handling
- Reset functionality
- Error display
- CSRF token management

---

### `Modal.js` ⏳ PENDING

**Bootstrap modal wrapper.**

Will provide easier modal management.

**Planned Features:**
- Show/hide modals
- Dynamic content
- Confirmation dialogs
- Loading states

---

### `SearchModal.js` ⏳ PENDING

**Reusable search component.**

Will consolidate search modals from multiple pages.

**Planned Features:**
- Keyboard shortcuts (Ctrl+F)
- Real-time search
- Result highlighting
- Filter options

---

## Migration Guide

### From legacy/toast-alerts.js

**Before:**
```javascript
// Global initialization in toast-alerts.js
window.showToast('Success', 'Message', 'success');
window.hideToast(toastId);
```

**After:**
```javascript
import { ToastNotification } from './components/ToastNotification.js';

ToastNotification.success('Success', 'Message');
ToastNotification.hide(toastId);
```

### From legacy/default.js (auto-dismiss alerts)

**Before:**
```javascript
// Auto-dismiss in default.js (DOMContentLoaded)
const alerts = document.querySelectorAll('.alert:not(.bg-info)');
alerts.forEach(alert => {
    setTimeout(() => alert.remove(), 3000);
});
```

**After:**
```javascript
// Use ToastNotification instead
ToastNotification.success('Alert Title', 'Alert message');

// Or process server alerts automatically
ToastNotification.processServerAlerts();
```

---

## Testing

Each component should have a corresponding test file in `tests/components/`.

Example:
```javascript
// tests/components/ToastNotification.test.js
import { ToastNotification } from '../../components/ToastNotification.js';

test('success toast shows correctly', () => {
    const toastId = ToastNotification.success('Test', 'Message');
    expect(ToastNotification.exists(toastId)).toBe(true);
});

test('toast auto-dismisses', async () => {
    const toastId = ToastNotification.info('Test', 'Message', { duration: 100 });
    await new Promise(resolve => setTimeout(resolve, 150));
    expect(ToastNotification.exists(toastId)).toBe(false);
});
```

---

_Last updated: 2025-11-04_
