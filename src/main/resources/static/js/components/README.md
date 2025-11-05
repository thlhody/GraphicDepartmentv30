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

### `FormHandler.js` ✅ COMPLETE

**Base class for form handling.**

Provides comprehensive form management including validation, submission, error handling, and utilities.

**Exports:**
- `FormHandler` class

**Key Features:**
- HTML5 validation support
- Custom validation rules
- Bootstrap 5 validation classes (`.is-invalid`, `.invalid-feedback`)
- AJAX submission via `core/api.js`
- Standard form POST submission
- Error display and clearing
- Loading states with spinner
- Form reset
- FormData collection
- Success/error callbacks
- Field-level validation
- Programmatic form population

**Configuration Options:**
```javascript
{
    url: '/api/submit',              // Submission URL
    method: 'POST',                  // HTTP method
    useAjax: true,                   // Use AJAX vs standard POST
    validateOnSubmit: true,          // Validate before submission
    validateOnBlur: false,           // Validate fields on blur
    clearOnSuccess: false,           // Clear form after success
    showToastOnSuccess: true,        // Show success toast
    showToastOnError: true,          // Show error toast
    successMessage: 'Form submitted successfully',
    errorMessage: 'Form submission failed',
    loadingText: 'Submitting...',
    submitButton: null,              // Submit button selector
    customValidation: (formData) => {}, // Custom validation
    onSuccess: (response, form) => {},  // Success callback
    onError: (error, form) => {},       // Error callback
    onValidationError: (errors, form) => {}, // Validation error callback
    transformData: (formData) => {}     // Transform data before submit
}
```

**Usage:**

```javascript
import { FormHandler } from './components/FormHandler.js';

// Basic usage
const form = new FormHandler('#myForm', {
    url: '/api/submit',
    onSuccess: (response) => {
        console.log('Success:', response);
    }
});

// With custom validation
const loginForm = new FormHandler('#loginForm', {
    url: '/login',
    customValidation: (formData) => {
        const errors = {};

        const email = formData.get('email');
        if (!email || !email.includes('@')) {
            errors.email = 'Invalid email address';
        }

        const password = formData.get('password');
        if (!password || password.length < 6) {
            errors.password = 'Password must be at least 6 characters';
        }

        return errors;
    },
    onSuccess: (response) => {
        window.location.href = response.redirectUrl;
    }
});

// With data transformation
const registerForm = new FormHandler('#registerForm', {
    url: '/api/register',
    transformData: (formData) => {
        // Convert FormData to JSON object
        const data = {};
        for (const [key, value] of formData.entries()) {
            data[key] = value;
        }
        // Add additional fields
        data.timestamp = Date.now();
        return data;
    }
});

// Programmatic usage
const myForm = new FormHandler('#myForm', {
    url: '/api/data',
    useAjax: true
});

// Show errors manually
myForm.showErrors({
    username: 'Username is already taken',
    email: 'Email is required'
});

// Clear errors
myForm.clearErrors();

// Get form data
const formData = myForm.getFormData();
const dataObject = myForm.getFormDataAsObject();

// Set field values
myForm.setFieldValue('username', 'john_doe');
myForm.setFieldValue('email', 'john@example.com');

// Get field values
const username = myForm.getFieldValue('username');

// Populate entire form
myForm.populate({
    username: 'john_doe',
    email: 'john@example.com',
    age: 30
});

// Reset form
myForm.reset();

// Enable/disable form
myForm.setEnabled(false); // Disable all inputs
myForm.setEnabled(true);  // Enable all inputs

// Submit programmatically
try {
    const response = await myForm.submit({ additionalField: 'value' });
    console.log('Submitted:', response);
} catch (error) {
    console.error('Submission failed:', error);
}

// Check loading state
if (myForm.isLoading()) {
    console.log('Form is submitting...');
}

// Destroy when done
myForm.destroy();
```

**Error Display:**
```javascript
// Show single field error
myForm.showFieldError('email', 'Email is invalid');

// Show multiple errors
myForm.showErrors({
    email: 'Email is required',
    password: 'Password is too short'
});

// Clear specific field error
myForm.clearFieldError('email');

// Clear all errors
myForm.clearErrors();
```

**Validation Modes:**
```javascript
// Validate on submit only (default)
const form1 = new FormHandler('#form1', {
    validateOnSubmit: true,
    validateOnBlur: false
});

// Validate on blur (real-time validation)
const form2 = new FormHandler('#form2', {
    validateOnSubmit: true,
    validateOnBlur: true
});

// Manual validation
const isValid = form1.validate();
if (!isValid) {
    console.log('Form has errors');
}
```

**Integration with ToastNotification:**
```javascript
// Automatic toast notifications
const form = new FormHandler('#myForm', {
    url: '/api/submit',
    showToastOnSuccess: true,  // Shows success toast
    showToastOnError: true,    // Shows error toast
    successMessage: 'Data saved successfully!',
    errorMessage: 'Failed to save data'
});

// Custom toast handling
const form2 = new FormHandler('#myForm2', {
    url: '/api/submit',
    showToastOnSuccess: false, // Disable automatic toast
    onSuccess: (response) => {
        // Show custom toast
        ToastNotification.success('Success', 'Custom success message');
    }
});
```

**Benefits:**
- ✅ Consolidates form handling from 10+ legacy files
- ✅ Consistent validation and error display
- ✅ Bootstrap 5 compatible
- ✅ Automatic loading states
- ✅ Integrates with `core/api.js` and `ToastNotification`
- ✅ Both AJAX and standard POST support
- ✅ Highly configurable
- ✅ Clean, reusable API

---

### `Modal.js` ✅ COMPLETE

**Bootstrap 5 modal wrapper.**

Provides a clean API for working with Bootstrap 5 modals, including programmatic creation, confirmation dialogs, and modal state management.

**Exports:**
- `Modal` class
- Static methods: `create()`, `confirm()`, `alert()`, `loading()`, `prompt()`

**Key Features:**
- Programmatic modal creation
- Confirmation dialogs (Promise-based)
- Alert dialogs
- Loading/progress modals
- Prompt dialogs for input
- Dynamic content updates
- Event callbacks (onShow, onShown, onHide, onHidden)
- Size variants (sm, lg, xl)
- Centered positioning
- Scrollable modals
- Backdrop control (true, false, 'static')
- Keyboard shortcuts
- Auto-disposal for dynamic modals

**Configuration Options:**
```javascript
{
    backdrop: true,           // true, false, or 'static'
    keyboard: true,           // Close on Escape key
    focus: true,              // Focus modal when shown
    size: null,               // null, 'sm', 'lg', 'xl'
    centered: false,          // Vertically center modal
    scrollable: false,        // Make modal body scrollable
    onShow: (modal) => {},    // Before show callback
    onShown: (modal) => {},   // After shown callback
    onHide: (modal) => {},    // Before hide callback
    onHidden: (modal) => {}   // After hidden callback
}
```

**Usage:**

```javascript
import { Modal } from './components/Modal.js';

// ===== Existing Modal (from HTML) =====
const existingModal = new Modal('#myModal');
existingModal.show();
existingModal.hide();

// Update content
existingModal.setTitle('New Title');
existingModal.setBody('<p>New content</p>');
existingModal.setFooter('<button class="btn btn-primary">Save</button>');

// Check state
if (existingModal.isVisible()) {
    console.log('Modal is visible');
}

// ===== Create Dynamic Modal =====
const dynamicModal = Modal.create({
    title: 'Dynamic Modal',
    body: '<p>This modal was created programmatically</p>',
    size: 'lg',
    centered: true,
    buttons: [
        {
            text: 'Close',
            className: 'btn-secondary',
            dismiss: true
        },
        {
            text: 'Save',
            className: 'btn-primary',
            onClick: (modal) => {
                console.log('Save clicked');
                modal.hide();
            }
        }
    ]
});

dynamicModal.show();

// ===== Confirmation Dialog (Promise-based) =====
const confirmed = await Modal.confirm({
    title: 'Delete Item',
    message: 'Are you sure you want to delete this item? This action cannot be undone.',
    confirmText: 'Delete',
    cancelText: 'Cancel',
    confirmClass: 'btn-danger',
    icon: 'bi-exclamation-triangle-fill text-warning'
});

if (confirmed) {
    console.log('User confirmed');
    // Delete item
} else {
    console.log('User cancelled');
}

// With callbacks
await Modal.confirm({
    title: 'Finalize Data',
    message: 'This will mark all entries as final. Continue?',
    onConfirm: () => {
        console.log('Finalizing...');
    },
    onCancel: () => {
        console.log('Cancelled finalization');
    }
});

// ===== Alert Dialog =====
await Modal.alert({
    title: 'Success',
    message: 'Operation completed successfully!',
    buttonText: 'OK',
    buttonClass: 'btn-success',
    icon: 'bi-check-circle-fill text-success'
});

await Modal.alert({
    title: 'Error',
    message: 'An error occurred while processing your request.',
    icon: 'bi-x-circle-fill text-danger'
});

// ===== Loading Modal =====
const loader = Modal.loading({
    title: 'Processing',
    message: 'Please wait while we process your request...'
});

// Do async work
await someAsyncOperation();

// Update message
loader.updateMessage('Almost done...');

// Hide when done
loader.hide();

// Without spinner
const loaderNoSpinner = Modal.loading({
    title: 'Saving',
    message: 'Saving changes...',
    spinner: false
});

// ===== Prompt Dialog =====
const name = await Modal.prompt({
    title: 'Enter Name',
    message: 'Please enter your name:',
    defaultValue: 'John Doe',
    placeholder: 'Your name...'
});

if (name !== null) {
    console.log('User entered:', name);
} else {
    console.log('User cancelled');
}

// With validation
const email = await Modal.prompt({
    title: 'Enter Email',
    message: 'Please enter your email address:',
    inputType: 'email',
    placeholder: 'email@example.com'
});

// ===== Event Callbacks =====
const callbackModal = Modal.create({
    title: 'Modal with Callbacks',
    body: '<p>This modal has event callbacks</p>',
    onShow: (modal) => {
        console.log('Modal is about to show');
    },
    onShown: (modal) => {
        console.log('Modal is now visible');
    },
    onHide: (modal) => {
        console.log('Modal is about to hide');
    },
    onHidden: (modal) => {
        console.log('Modal is now hidden');
    }
});

// ===== Advanced Usage =====

// Scrollable modal with long content
const scrollableModal = Modal.create({
    title: 'Long Content',
    body: '<p>'.repeat(100).replace(/^/gm, 'This is a long paragraph. ') + '</p>',
    size: 'lg',
    scrollable: true,
    centered: true
});

// Modal with static backdrop (cannot close by clicking outside)
const staticModal = Modal.create({
    title: 'Important',
    body: '<p>You must respond to this dialog.</p>',
    backdrop: 'static',
    keyboard: false,  // Cannot close with Escape
    buttons: [
        {
            text: 'I Understand',
            className: 'btn-primary',
            onClick: (modal) => modal.hide()
        }
    ]
});

// Modal with custom HTML content
const customContent = document.createElement('div');
customContent.innerHTML = `
    <div class="mb-3">
        <label class="form-label">Username</label>
        <input type="text" class="form-control" id="username">
    </div>
    <div class="mb-3">
        <label class="form-label">Password</label>
        <input type="password" class="form-control" id="password">
    </div>
`;

const formModal = Modal.create({
    title: 'Login',
    body: customContent,
    buttons: [
        {
            text: 'Cancel',
            className: 'btn-secondary',
            dismiss: true
        },
        {
            text: 'Login',
            className: 'btn-primary',
            onClick: (modal) => {
                const username = document.getElementById('username').value;
                const password = document.getElementById('password').value;
                console.log('Login:', username, password);
                modal.hide();
            }
        }
    ]
});

// ===== Dispose Modal =====
// Clean up when done
dynamicModal.dispose();
```

**Migration from Legacy Code:**

**Before (legacy pattern)**:
```javascript
// Legacy: Manually create Bootstrap modal
const modalElement = document.getElementById('confirmModal');
const modal = new bootstrap.Modal(modalElement);
modal.show();

// Update content
document.getElementById('confirmText').textContent = 'Are you sure?';

// Manual event handling
modalElement.addEventListener('hidden.bs.modal', () => {
    console.log('Modal hidden');
});
```

**After (using Modal component)**:
```javascript
// Modern: Use Modal wrapper
const confirmed = await Modal.confirm({
    title: 'Confirm',
    message: 'Are you sure?',
    onConfirm: () => console.log('Confirmed')
});

// Or wrap existing modal
const modal = new Modal('#confirmModal');
modal.setBody('Are you sure?');
modal.show();
```

**Benefits:**
- ✅ Consolidates modal patterns from 8+ legacy files
- ✅ Promise-based confirmations for cleaner async code
- ✅ Dynamic modal creation without HTML templates
- ✅ Consistent API across all modal types
- ✅ Built-in confirmation, alert, loading, and prompt dialogs
- ✅ Auto-disposal for dynamic modals
- ✅ Event callbacks for lifecycle management
- ✅ Bootstrap 5 compatible
- ✅ ~80 lines of duplicated modal code eliminated

---

### `SearchModal.js` ✅ COMPLETE

**Reusable search modal component.**

Provides keyboard-accessible search functionality with debounced input, loading states, and customizable search logic.

**Exports:**
- `SearchModal` class
- Static method: `highlightText(text, query)`

**Key Features:**
- Keyboard shortcuts (Ctrl+F to open, Escape to close)
- Debounced search input (configurable delay)
- Loading states with spinner
- Custom search functions (client-side or AJAX)
- Result highlighting
- Empty state handling
- Click outside to close
- Auto-focus on open
- Customizable result templates
- Built-in styles (no external CSS needed)

**Configuration Options:**
```javascript
{
    trigger: '#searchButton',          // Trigger button selector
    placeholder: 'Search...',           // Input placeholder
    debounceDelay: 250,                // Debounce delay in ms
    minQueryLength: 1,                 // Min query length to search
    showEmptyState: true,              // Show message when no results
    emptyStateMessage: 'No results found',
    loadingMessage: 'Searching...',
    enableKeyboardShortcut: true,      // Enable Ctrl+F shortcut
    keyboardShortcut: 'f',             // Keyboard shortcut key
    closeOnEscape: true,               // Close on Escape key
    closeOnOutsideClick: true,         // Close when clicking backdrop
    focusOnOpen: true,                 // Auto-focus input on open
    clearOnClose: true,                // Clear input when closing
    onSearch: async (query) => {},     // Search function
    onResultClick: (result, index) => {}, // Result click handler
    renderResult: (result, index, query) => {}, // Render function
    onOpen: () => {},                  // Open callback
    onClose: () => {},                 // Close callback
    customClass: ''                    // Additional CSS class
}
```

**Usage:**

```javascript
import { SearchModal } from './components/SearchModal.js';

// Client-side search
const searchModal = new SearchModal({
    trigger: '#searchButton',
    placeholder: 'Search users...',
    onSearch: (query) => {
        // Filter local data
        return users.filter(user =>
            user.name.toLowerCase().includes(query.toLowerCase())
        );
    },
    renderResult: (user, index, query) => {
        // Highlight matching text
        const name = SearchModal.highlightText(user.name, query);
        return `
            <div class="d-flex align-items-center gap-3">
                <div>
                    <h6 class="mb-0">${name}</h6>
                    <small class="text-muted">${user.email}</small>
                </div>
            </div>
        `;
    },
    onResultClick: (user, index) => {
        console.log('Selected user:', user);
        window.location.href = `/user/${user.id}`;
    }
});

// AJAX search
const ajaxSearchModal = new SearchModal({
    placeholder: 'Search products...',
    debounceDelay: 500,
    minQueryLength: 3,
    onSearch: async (query) => {
        // Fetch from API
        const response = await API.get('/api/products/search', { q: query });
        return response.products;
    },
    renderResult: (product, index, query) => {
        const name = SearchModal.highlightText(product.name, query);
        return `
            <div class="search-product-result">
                <img src="${product.image}" alt="${product.name}" width="50">
                <div>
                    <h6>${name}</h6>
                    <p class="mb-0 text-muted">$${product.price}</p>
                </div>
            </div>
        `;
    }
});

// With custom keyboard shortcut
const customModal = new SearchModal({
    enableKeyboardShortcut: true,
    keyboardShortcut: 'k',  // Ctrl+K instead of Ctrl+F
    onSearch: async (query) => {
        // Your search logic
    }
});

// Programmatic control
searchModal.open();          // Open modal
searchModal.close();         // Close modal
searchModal.toggle();        // Toggle open/close
searchModal.clear();         // Clear results and input

// Get current state
const results = searchModal.getResults();
const query = searchModal.getQuery();
const isOpen = searchModal.isModalOpen();

// Update placeholder
searchModal.setPlaceholder('Search for anything...');

// Destroy when done
searchModal.destroy();
```

**Search with Table Data:**
```javascript
// Extract and search table data
const tableSearchModal = new SearchModal({
    trigger: '#searchTable',
    onSearch: (query) => {
        const entries = [];

        // Extract data from table
        document.querySelectorAll('.data-table tbody tr').forEach(row => {
            const cells = row.cells;
            entries.push({
                name: cells[0].textContent,
                date: cells[1].textContent,
                status: cells[2].textContent,
                row: row  // Keep reference to row
            });
        });

        // Filter based on query
        return entries.filter(entry =>
            entry.name.toLowerCase().includes(query.toLowerCase()) ||
            entry.status.toLowerCase().includes(query.toLowerCase())
        );
    },
    renderResult: (entry, index, query) => {
        const name = SearchModal.highlightText(entry.name, query);
        const status = SearchModal.highlightText(entry.status, query);

        return `
            <div>
                <strong>${name}</strong>
                <div class="text-muted">
                    Date: ${entry.date} | Status: ${status}
                </div>
            </div>
        `;
    },
    onResultClick: (entry) => {
        // Scroll to and highlight the table row
        entry.row.scrollIntoView({ behavior: 'smooth', block: 'center' });
        entry.row.classList.add('table-active');
    }
});
```

**With Callbacks:**
```javascript
const modal = new SearchModal({
    onSearch: async (query) => {
        // Search logic
    },
    onOpen: () => {
        console.log('Search modal opened');
        // Track analytics, etc.
    },
    onClose: () => {
        console.log('Search modal closed');
    },
    onResultClick: (result, index) => {
        console.log(`Clicked result ${index}:`, result);
    }
});
```

**Text Highlighting:**
```javascript
// Highlight matching text in results
const highlighted = SearchModal.highlightText(
    'John Doe works at Company Inc.',
    'doe'
);
// Result: 'John <span class="search-highlight">Doe</span> works at Company Inc.'
```

**Custom Styling:**
```javascript
const styledModal = new SearchModal({
    customClass: 'my-custom-search-modal',
    onSearch: (query) => {
        // Search logic
    }
});

// Add custom CSS
/*
.my-custom-search-modal .search-modal-dialog {
    max-width: 800px;
}

.my-custom-search-modal .search-result-item {
    background-color: #f0f8ff;
}
*/
```

**Benefits:**
- ✅ Consolidates search modal patterns from register-user.js, check-register.js
- ✅ Keyboard accessible (Ctrl+F shortcut, Escape to close)
- ✅ Debounced input prevents excessive searches
- ✅ Works with both client-side and AJAX searches
- ✅ Built-in styles, no external CSS required
- ✅ Highly customizable rendering
- ✅ Text highlighting utility
- ✅ Loading and empty states
- ✅ ~100 lines of duplicated search code eliminated

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
