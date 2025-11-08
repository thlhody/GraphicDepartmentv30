/**
 * WorktimeFinalization.js
 *
 * Manages the worktime finalization workflow for admin.
 * Handles confirmation dialogs, progress tracking, and execution of finalization.
 *
 * @module features/worktime/admin/WorktimeFinalization
 */

import { API } from '../../../core/api.js';

/**
 * WorktimeFinalization class
 * Manages finalization workflow for marking entries as ADMIN_FINAL
 */
export class WorktimeFinalization {
    constructor() {
        this.currentFinalizationData = null;
    }

    /**
     * Show finalization confirmation dialog
     * @param {number|null} userId - User ID to finalize, or null for all users
     */
    showFinalizeConfirmation(userId) {
        const modal = new bootstrap.Modal(document.getElementById('finalizeConfirmationModal'));
        const modalText = document.getElementById('finalizeModalText');
        const detailsDiv = document.getElementById('finalizeDetails');

        // Store finalization data
        this.currentFinalizationData = {
            userId: userId,
            year: this.getCurrentYear(),
            month: this.getCurrentMonth()
        };

        // Update modal content based on scope
        if (userId === null) {
            modalText.innerHTML = `
                <strong>Finalize ALL users</strong> for ${this.getMonthName(this.currentFinalizationData.month)} ${this.currentFinalizationData.year}?
                <br><br>
                This will mark all worktime entries as <strong>ADMIN_FINAL</strong> and prevent any further modifications.
            `;

            detailsDiv.innerHTML = `
                <strong>Scope:</strong> All users in this period<br>
                <strong>Period:</strong> ${this.getMonthName(this.currentFinalizationData.month)} ${this.currentFinalizationData.year}<br>
                <strong>Estimated entries:</strong> ~${this.estimateEntryCount()} entries<br>
                <strong>Status change:</strong> → ADMIN_FINAL
            `;
        } else {
            const userName = this.getUserName(userId);
            modalText.innerHTML = `
                <strong>Finalize user "${userName}"</strong> for ${this.getMonthName(this.currentFinalizationData.month)} ${this.currentFinalizationData.year}?
                <br><br>
                This will mark all worktime entries for this user as <strong>ADMIN_FINAL</strong> and prevent any further modifications.
            `;

            detailsDiv.innerHTML = `
                <strong>Scope:</strong> ${userName} only<br>
                <strong>Period:</strong> ${this.getMonthName(this.currentFinalizationData.month)} ${this.currentFinalizationData.year}<br>
                <strong>Estimated entries:</strong> ~${this.getDaysInMonth(this.currentFinalizationData.year, this.currentFinalizationData.month)} entries<br>
                <strong>Status change:</strong> → ADMIN_FINAL
            `;
        }

        modal.show();
    }

    /**
     * Finalize specific user (called from button)
     */
    finalizeSpecificUser() {
        const userSelect = document.getElementById('finalizeUserSelect');
        const userId = userSelect.value;

        if (!userId) {
            alert('Please select a user to finalize');
            return;
        }

        this.showFinalizeConfirmation(parseInt(userId));
    }

    /**
     * Execute the finalization after confirmation
     */
    async executeFinalization() {
        if (!this.currentFinalizationData) {
            console.error('No finalization data available');
            return;
        }

        // Hide confirmation modal safely (prevent ARIA warnings)
        const confirmModalElement = document.getElementById('finalizeConfirmationModal');
        const confirmModal = bootstrap.Modal.getInstance(confirmModalElement);

        // Blur any focused elements within the modal before hiding
        const focusedElement = confirmModalElement.querySelector(':focus');
        if (focusedElement) {
            focusedElement.blur();
        }

        confirmModal.hide();

        // Show progress modal
        const progressModal = new bootstrap.Modal(document.getElementById('finalizationProgressModal'));
        progressModal.show();

        try {
            console.log('Executing finalization:', this.currentFinalizationData);

            // Build form data
            const formData = {
                year: this.currentFinalizationData.year,
                month: this.currentFinalizationData.month
            };

            if (this.currentFinalizationData.userId) {
                formData.userId = this.currentFinalizationData.userId;
            }

            // Submit finalization request using API wrapper
            await API.postForm('/admin/worktime/finalize', formData);

            // Hide progress modal safely
            this.hideModalSafely(progressModal, document.getElementById('finalizationProgressModal'));

            console.log('Finalization successful, redirecting...');
            // Reload to show updated status and success message
            window.location.reload();

        } catch (error) {
            // Hide progress modal safely
            this.hideModalSafely(progressModal, document.getElementById('finalizationProgressModal'));

            console.error('Finalization error:', error);
            alert('Finalization failed: ' + (error.message || 'Network error'));
        } finally {
            this.currentFinalizationData = null;
        }
    }

    /**
     * Hide modal safely without ARIA warnings
     * @private
     */
    hideModalSafely(modalInstance, modalElement) {
        if (modalElement) {
            const focusedElement = modalElement.querySelector(':focus');
            if (focusedElement) {
                focusedElement.blur();
            }
        }
        if (modalInstance) {
            modalInstance.hide();
        }
    }

    /**
     * Helper methods
     */

    getCurrentYear() {
        const yearSelect = document.getElementById('yearSelect');
        return yearSelect ? parseInt(yearSelect.value) : new Date().getFullYear();
    }

    getCurrentMonth() {
        const monthSelect = document.getElementById('monthSelect');
        return monthSelect ? parseInt(monthSelect.value) : new Date().getMonth() + 1;
    }

    getMonthName(monthNumber) {
        const months = [
            'January', 'February', 'March', 'April', 'May', 'June',
            'July', 'August', 'September', 'October', 'November', 'December'
        ];
        return months[monthNumber - 1] || 'Unknown';
    }

    getUserName(userId) {
        const userSelect = document.getElementById('finalizeUserSelect');
        const option = userSelect.querySelector(`option[value="${userId}"]`);
        return option ? option.textContent : `User ${userId}`;
    }

    estimateEntryCount() {
        const userSelect = document.getElementById('finalizeUserSelect');
        const userCount = userSelect.options.length - 1; // Exclude "Select user..." option
        const daysInMonth = this.getDaysInMonth(this.getCurrentYear(), this.getCurrentMonth());
        return userCount * daysInMonth;
    }

    getDaysInMonth(year, month) {
        return new Date(year, month, 0).getDate();
    }
}
