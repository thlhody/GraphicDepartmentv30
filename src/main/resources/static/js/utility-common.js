/**
 * Common utility functions for the utilities page
 */

// Global utility namespace
window.UtilityCommon = {

    /**
     * Set button loading state
     * @param {jQuery} btn - Button element
     * @param {boolean} loading - Loading state
     */
    setButtonLoading: function(btn, loading) {
        if (loading) {
            btn.addClass('loading').prop('disabled', true);
            btn.find('i').addClass('spin');
        } else {
            btn.removeClass('loading').prop('disabled', false);
            btn.find('i').removeClass('spin');
        }
    },

    /**
     * Format bytes to human readable string
     * @param {number} bytes - Number of bytes
     * @returns {string} Formatted string
     */
    formatBytes: function(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    },

    /**
     * Format date to locale string
     * @param {Date|string} date - Date to format
     * @returns {string} Formatted date string
     */
    formatDate: function(date) {
        if (typeof date === 'string') {
            date = new Date(date);
        }
        return date.toLocaleString('en-US', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            hour12: false
        });
    },

    /**
     * Show confirmation dialog
     * @param {string} message - Confirmation message
     * @param {Function} callback - Callback function if confirmed
     */
    confirmAction: function(message, callback) {
        if (confirm(message)) {
            callback();
        }
    },

    /**
     * Generic AJAX error handler
     * @param {Object} xhr - XMLHttpRequest object
     * @param {string} status - Status text
     * @param {string} error - Error message
     * @param {string} operation - Operation description
     */
    handleAjaxError: function(xhr, status, error, operation) {
        let message = `Failed to ${operation}: ${error}`;

        // Try to get more specific error from response
        try {
            if (xhr.responseJSON && xhr.responseJSON.message) {
                message = xhr.responseJSON.message;
            }
        } catch (e) {
            // Use default message
        }

        window.showToast('Error', message, 'error');
    },

    /**
     * Debounce function to limit rapid calls
     * @param {Function} func - Function to debounce
     * @param {number} wait - Wait time in milliseconds
     * @returns {Function} Debounced function
     */
    debounce: function(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    },

    /**
     * Create loading indicator
     * @param {string} message - Loading message
     * @returns {jQuery} Loading element
     */
    createLoadingIndicator: function(message = 'Loading...') {
        return $(`
            <div class="loading-indicator">
                <div class="d-flex align-items-center justify-content-center">
                    <i class="bi bi-arrow-repeat spin me-2"></i>
                    <span>${message}</span>
                </div>
            </div>
        `);
    },

    /**
     * Animate element entrance
     * @param {jQuery} element - Element to animate
     */
    animateIn: function(element) {
        element.hide().fadeIn(300);
    },

    /**
     * Animate element exit
     * @param {jQuery} element - Element to animate
     * @param {Function} callback - Callback after animation
     */
    animateOut: function(element, callback) {
        element.fadeOut(300, callback);
    },

    /**
     * Update timestamp display
     * @param {jQuery} element - Element to update
     */
    updateTimestamp: function(element) {
        const now = new Date();
        const timeString = now.toLocaleTimeString('en-US', {
            hour12: false,
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
        element.text(timeString);
    },

    /**
     * Scroll to element smoothly
     * @param {jQuery} element - Element to scroll to
     */
    scrollToElement: function(element) {
        if (element.length) {
            $('html, body').animate({
                scrollTop: element.offset().top - 20
            }, 500);
        }
    },

    /**
     * Copy text to clipboard
     * @param {string} text - Text to copy
     */
    copyToClipboard: function(text) {
        if (navigator.clipboard) {
            navigator.clipboard.writeText(text).then(() => {
                window.showToast('Success', 'Copied to clipboard', 'success');
            }).catch(() => {
                this.fallbackCopyTextToClipboard(text);
            });
        } else {
            this.fallbackCopyTextToClipboard(text);
        }
    },

    /**
     * Fallback copy method for older browsers
     * @param {string} text - Text to copy
     */
    fallbackCopyTextToClipboard: function(text) {
        const textArea = document.createElement("textarea");
        textArea.value = text;
        textArea.style.position = "fixed";
        textArea.style.left = "-999999px";
        textArea.style.top = "-999999px";
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();

        try {
            document.execCommand('copy');
            window.showToast('Success', 'Copied to clipboard', 'success');
        } catch (err) {
            window.showToast('Error', 'Failed to copy to clipboard', 'error');
        }

        document.body.removeChild(textArea);
    }
};

// Initialize common functionality when document is ready
$(document).ready(function() {

    // Global utility event handlers

    // Auto-expand collapsed sections when they have content
    $('.utility-controls').on('DOMSubtreeModified', function() {
        const $this = $(this);
        if ($this.find('.results-area:visible').length > 0 && !$this.hasClass('show')) {
            $this.collapse('show');
        }
    });

    // Auto-scroll to results when they appear
    $('.results-area').on('show', function() {
        setTimeout(() => {
            UtilityCommon.scrollToElement($(this));
        }, 100);
    });

    // Keyboard shortcuts
    $(document).on('keydown', function(e) {
        // ESC key to close expanded sections
        if (e.key === 'Escape') {
            $('.utility-controls.show').collapse('hide');
        }
    });

    // Initialize tooltips if Bootstrap is available
    if (typeof bootstrap !== 'undefined') {
        $('[data-bs-toggle="tooltip"]').tooltip();
    }

    console.log('Utility Common JS initialized');
});