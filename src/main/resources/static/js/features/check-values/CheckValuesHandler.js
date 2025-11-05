/**
 * CheckValuesHandler.js
 *
 * Manages check values configuration for users.
 * Handles loading, updating, and batch operations for work unit values,
 * layout values, and various other check register calculation values.
 *
 * @module features/check-values/CheckValuesHandler
 */

/**
 * Helper function for formatting dates
 * @param {string} dateStr - ISO date string
 * @returns {string} Formatted date string
 */
function formatDate(dateStr) {
    if (!dateStr) return 'N/A';

    try {
        const date = new Date(dateStr);
        return date.toLocaleString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch (e) {
        console.error('Error formatting date:', e);
        return dateStr;
    }
}

/**
 * CheckValuesHandler class
 * Manages check values for all users
 */
export class CheckValuesHandler {
    constructor() {
        this.originalValues = {}; // Store original values for reset
        this.modifiedValues = {}; // Track modified values
        this.selectedUsername = null; // Currently selected user for confirmation
        this.selectedUserId = null; // Currently selected user ID for confirmation

        // Initialize confirm modal
        this.confirmModal = new bootstrap.Modal(document.getElementById('confirmModal'));

        this.initializeEventListeners();
        this.loadCheckValues();
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    initializeEventListeners() {
        // Input change tracking
        document.querySelectorAll('.check-values-form input').forEach(input => {
            input.addEventListener('change', (e) => this.handleInputChange(e));
        });

        // Save buttons
        document.querySelectorAll('.save-user-btn').forEach(button => {
            button.addEventListener('click', (e) => this.openConfirmModal(e));
        });

        // Reset buttons
        document.querySelectorAll('.reset-user-btn').forEach(button => {
            button.addEventListener('click', (e) => this.resetUserValues(e));
        });

        // Confirm save button
        document.getElementById('confirmSaveBtn').addEventListener('click', () => this.saveUserValues());

        // Save all button
        document.getElementById('saveAllButton').addEventListener('click', () => this.saveAllChanges());

        // Reset all button
        document.getElementById('resetAllButton').addEventListener('click', () => this.resetAllChanges());

        // Tab change events
        document.querySelectorAll('button[data-bs-toggle="tab"]').forEach(button => {
            button.addEventListener('shown.bs.tab', (e) => {
                // Get username from tab ID (tab-username)
                const username = e.target.id.split('-')[1];
                console.log(`Switched to user tab: ${username}`);
            });
        });
    }

    // ========================================================================
    // LOADING DATA
    // ========================================================================

    loadCheckValues() {
        // Get all users
        const userTabs = document.querySelectorAll('button[data-bs-toggle="tab"]');

        userTabs.forEach(tab => {
            const username = tab.id.split('-')[1];
            const userId = tab.getAttribute('data-user-id');

            this.loadUserCheckValues(username, userId);
        });
    }

    async loadUserCheckValues(username, userId) {
        try {
            const response = await fetch(`/user/check-values/${username}/${userId}`);

            if (!response.ok) {
                throw new Error(`Error loading check values: ${response.status}`);
            }

            const data = await response.json();
            this.setUserCheckValues(username, userId, data);
        } catch (error) {
            console.error('Error loading check values:', error);
            this.showError(`Failed to load check values for ${username}: ${error.message}`);
        }
    }

    setUserCheckValues(username, userId, data) {
        // Store original values
        this.originalValues[username] = data;

        // Log the data we're setting for debugging
        console.log(`Setting check values for ${username}:`, data);

        // Get the check values entry
        const checkValuesEntry = data.checkValuesEntry;

        if (!checkValuesEntry) {
            console.warn(`No check values found for user ${username}`);
            return;
        }

        // Update last updated date
        const lastUpdatedElement = document.querySelector(`.last-updated-date[data-username="${username}"]`);
        if (lastUpdatedElement && data.latestEntry) {
            lastUpdatedElement.textContent = formatDate(data.latestEntry);
        }

        // Set values in the form
        this.setInputValue(username, 'workUnitsPerHour', checkValuesEntry.workUnitsPerHour);
        this.setInputValue(username, 'layoutValue', checkValuesEntry.layoutValue);
        this.setInputValue(username, 'kipstaLayoutValue', checkValuesEntry.kipstaLayoutValue);
        this.setInputValue(username, 'layoutChangesValue', checkValuesEntry.layoutChangesValue);
        this.setInputValue(username, 'gptArticlesValue', checkValuesEntry.gptArticlesValue);
        this.setInputValue(username, 'gptFilesValue', checkValuesEntry.gptFilesValue);
        this.setInputValue(username, 'productionValue', checkValuesEntry.productionValue);
        this.setInputValue(username, 'reorderValue', checkValuesEntry.reorderValue);
        this.setInputValue(username, 'sampleValue', checkValuesEntry.sampleValue);
        this.setInputValue(username, 'omsProductionValue', checkValuesEntry.omsProductionValue);
        this.setInputValue(username, 'kipstaProductionValue', checkValuesEntry.kipstaProductionValue);

        console.log(`Loaded check values for ${username}`);
    }

    // ========================================================================
    // FORM HANDLING
    // ========================================================================

    setInputValue(username, fieldName, value) {
        const input = document.getElementById(`${fieldName}-${username}`);
        if (input) {
            input.value = value || input.getAttribute('value') || '';
        }
    }

    handleInputChange(event) {
        const input = event.target;
        const fieldName = input.name;
        const username = input.getAttribute('data-username');
        const value = parseFloat(input.value);

        console.log(`Input changed for ${username}, field: ${fieldName}, value: ${value}`);

        // Add to modified values
        if (!this.modifiedValues[username]) {
            this.modifiedValues[username] = {};
        }

        this.modifiedValues[username][fieldName] = value;

        // Highlight modified field
        input.classList.add('field-modified');

        console.log(`Modified ${fieldName} for ${username}: ${value}`);
        console.log("Current modified values:", this.modifiedValues);
    }

    openConfirmModal(event) {
        const button = event.target.closest('button');
        this.selectedUsername = button.getAttribute('data-username');
        this.selectedUserId = button.getAttribute('data-user-id');

        // Only open modal if there are changes
        if (this.modifiedValues[this.selectedUsername]) {
            this.confirmModal.show();
        } else {
            this.showInfo(`No changes to save for ${this.selectedUsername}`);
        }
    }

    // ========================================================================
    // SAVING DATA
    // ========================================================================

    async saveUserValues() {
        if (!this.selectedUsername || !this.selectedUserId) {
            this.confirmModal.hide();
            return;
        }

        // Get the original entry
        const originalEntry = this.originalValues[this.selectedUsername];
        if (!originalEntry) {
            this.confirmModal.hide();
            this.showError(`No original values found for ${this.selectedUsername}`);
            return;
        }

        // Get modified values
        const modified = this.modifiedValues[this.selectedUsername] || {};

        // Create a new check values entry with the original values as a base
        const checkValuesEntry = {
            ...originalEntry.checkValuesEntry,
            createdAt: new Date().toISOString().replace('Z', '')
        };

        // Apply the modified values
        Object.keys(modified).forEach(key => {
            checkValuesEntry[key] = modified[key];
        });

        console.log(`Saving check values for ${this.selectedUsername}:`, checkValuesEntry);
        console.log(`Modified values:`, modified);

        // Create the payload with the updated check values
        const payload = {
            ...originalEntry,
            userId: this.selectedUserId,
            username: this.selectedUsername,
            latestEntry: new Date().toISOString().replace('Z', ''),
            checkValuesEntry: checkValuesEntry
        };

        try {
            // Send to server
            const response = await fetch(`/user/check-values/${this.selectedUsername}/${this.selectedUserId}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                throw new Error(`Error saving check values: ${response.status}`);
            }

            const data = await response.json();

            // Update last updated date
            const lastUpdatedElement = document.querySelector(`.last-updated-date[data-username="${this.selectedUsername}"]`);
            if (lastUpdatedElement) {
                lastUpdatedElement.textContent = new Date().toLocaleString();
            }

            // Clear modified state
            delete this.modifiedValues[this.selectedUsername];

            // Remove highlight from fields
            document.querySelectorAll(`#form-${this.selectedUsername} input`).forEach(input => {
                input.classList.remove('field-modified');
            });

            // Flash the form to indicate save success
            const form = document.getElementById(`form-${this.selectedUsername}`);
            form.classList.add('save-flash');
            setTimeout(() => {
                form.classList.remove('save-flash');
            }, 1500);

            // Update original values
            this.originalValues[this.selectedUsername] = payload;

            this.showSuccess(`Successfully saved check values for ${this.selectedUsername}`);
        } catch (error) {
            console.error('Error saving check values:', error);
            this.showError(`Failed to save check values for ${this.selectedUsername}: ${error.message}`);
        } finally {
            this.confirmModal.hide();
            this.selectedUsername = null;
            this.selectedUserId = null;
        }
    }

    async saveAllChanges() {
        const usersWithChanges = Object.keys(this.modifiedValues);

        if (usersWithChanges.length === 0) {
            this.showInfo('No changes to save');
            return;
        }

        const payloads = [];

        // Prepare payloads for all modified users
        usersWithChanges.forEach(username => {
            const originalEntry = this.originalValues[username];
            if (!originalEntry) {
                this.showError(`No original values found for ${username}`);
                return;
            }

            // Get user ID from original entry
            const userId = originalEntry.userId;

            // Get modified values
            const modified = this.modifiedValues[username] || {};

            // Create a new check values entry with the original values as a base
            const checkValuesEntry = {
                ...originalEntry.checkValuesEntry,
                createdAt: new Date().toISOString().replace('Z', '')
            };

            // Apply the modified values
            Object.keys(modified).forEach(key => {
                checkValuesEntry[key] = modified[key];
            });

            console.log(`Saving batch check values for ${username}:`, checkValuesEntry);

            // Create the payload with the updated check values
            const payload = {
                ...originalEntry,
                userId: userId,
                username: username,
                latestEntry: new Date().toISOString().replace('Z', ''),
                checkValuesEntry: checkValuesEntry
            };

            payloads.push(payload);
        });

        try {
            // Send batch update
            const response = await fetch('/user/check-values/batch', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payloads)
            });

            if (!response.ok) {
                throw new Error(`Error saving check values: ${response.status}`);
            }

            const data = await response.json();

            // Update last updated dates
            usersWithChanges.forEach(username => {
                const lastUpdatedElement = document.querySelector(`.last-updated-date[data-username="${username}"]`);
                if (lastUpdatedElement) {
                    lastUpdatedElement.textContent = new Date().toLocaleString();
                }

                // Remove highlight from fields
                document.querySelectorAll(`#form-${username} input`).forEach(input => {
                    input.classList.remove('field-modified');
                });

                // Update original values
                const index = payloads.findIndex(p => p.username === username);
                if (index !== -1) {
                    this.originalValues[username] = payloads[index];
                }
            });

            // Clear all modifications
            this.modifiedValues = {};

            this.showSuccess(`Successfully saved changes for ${usersWithChanges.length} users`);
        } catch (error) {
            console.error('Error saving batch check values:', error);
            this.showError(`Failed to save changes: ${error.message}`);
        }
    }

    // ========================================================================
    // RESETTING DATA
    // ========================================================================

    resetUserValues(event) {
        const button = event.target.closest('button');
        const username = button.getAttribute('data-username');

        // Check if there are modifications
        if (!this.modifiedValues[username]) {
            this.showInfo(`No changes to reset for ${username}`);
            return;
        }

        // Reset form values to original
        const originalEntry = this.originalValues[username];
        if (!originalEntry || !originalEntry.checkValuesEntry) {
            this.showError(`No original values found for ${username}`);
            return;
        }

        const checkValuesEntry = originalEntry.checkValuesEntry;

        this.setInputValue(username, 'workUnitsPerHour', checkValuesEntry.workUnitsPerHour);
        this.setInputValue(username, 'layoutValue', checkValuesEntry.layoutValue);
        this.setInputValue(username, 'kipstaLayoutValue', checkValuesEntry.kipstaLayoutValue);
        this.setInputValue(username, 'layoutChangesValue', checkValuesEntry.layoutChangesValue);
        this.setInputValue(username, 'gptArticlesValue', checkValuesEntry.gptArticlesValue);
        this.setInputValue(username, 'gptFilesValue', checkValuesEntry.gptFilesValue);
        this.setInputValue(username, 'productionValue', checkValuesEntry.productionValue);
        this.setInputValue(username, 'reorderValue', checkValuesEntry.reorderValue);
        this.setInputValue(username, 'sampleValue', checkValuesEntry.sampleValue);
        this.setInputValue(username, 'omsProductionValue', checkValuesEntry.omsProductionValue);
        this.setInputValue(username, 'kipstaProductionValue', checkValuesEntry.kipstaProductionValue);

        // Clear modified flags
        document.querySelectorAll(`#form-${username} input`).forEach(input => {
            input.classList.remove('field-modified');
        });

        // Clear modified state
        delete this.modifiedValues[username];

        this.showSuccess(`Reset check values for ${username}`);
    }

    resetAllChanges() {
        const usersWithChanges = Object.keys(this.modifiedValues);

        if (usersWithChanges.length === 0) {
            this.showInfo('No changes to reset');
            return;
        }

        // Reset each user's form to original values
        usersWithChanges.forEach(username => {
            const originalEntry = this.originalValues[username];
            if (!originalEntry || !originalEntry.checkValuesEntry) {
                this.showError(`No original values found for ${username}`);
                return;
            }

            const checkValuesEntry = originalEntry.checkValuesEntry;

            this.setInputValue(username, 'workUnitsPerHour', checkValuesEntry.workUnitsPerHour);
            this.setInputValue(username, 'layoutValue', checkValuesEntry.layoutValue);
            this.setInputValue(username, 'kipstaLayoutValue', checkValuesEntry.kipstaLayoutValue);
            this.setInputValue(username, 'layoutChangesValue', checkValuesEntry.layoutChangesValue);
            this.setInputValue(username, 'gptArticlesValue', checkValuesEntry.gptArticlesValue);
            this.setInputValue(username, 'gptFilesValue', checkValuesEntry.gptFilesValue);
            this.setInputValue(username, 'productionValue', checkValuesEntry.productionValue);
            this.setInputValue(username, 'reorderValue', checkValuesEntry.reorderValue);
            this.setInputValue(username, 'sampleValue', checkValuesEntry.sampleValue);
            this.setInputValue(username, 'omsProductionValue', checkValuesEntry.omsProductionValue);
            this.setInputValue(username, 'kipstaProductionValue', checkValuesEntry.kipstaProductionValue);

            // Clear modified flags
            document.querySelectorAll(`#form-${username} input`).forEach(input => {
                input.classList.remove('field-modified');
            });
        });

        // Clear modified state
        this.modifiedValues = {};

        this.showSuccess(`Reset changes for ${usersWithChanges.length} users`);
    }

    // ========================================================================
    // NOTIFICATIONS
    // ========================================================================

    showSuccess(message) {
        this.showToast('Success', message, 'success');
    }

    showError(message) {
        this.showToast('Error', message, 'danger');
    }

    showInfo(message) {
        this.showToast('Information', message, 'info');
    }

    showToast(title, message, type) {
        if (typeof window.showToast === 'function') {
            window.showToast(title, message, type);
        } else {
            console.log(`${title}: ${message}`);

            // Fallback if the global function is not available
            const toastContainer = document.getElementById('toastContainer');
            if (!toastContainer) return;

            const toastId = 'toast-' + new Date().getTime();
            const toastHtml = `
                <div id="${toastId}" class="toast" role="alert" aria-live="assertive" aria-atomic="true">
                    <div class="toast-header bg-${type} text-white">
                        <strong class="me-auto">${title}</strong>
                        <button type="button" class="btn-close btn-close-white" data-bs-dismiss="toast" aria-label="Close"></button>
                    </div>
                    <div class="toast-body">
                        ${message}
                    </div>
                </div>
            `;

            toastContainer.insertAdjacentHTML('beforeend', toastHtml);
            const toastElement = new bootstrap.Toast(document.getElementById(toastId));
            toastElement.show();
        }
    }
}
