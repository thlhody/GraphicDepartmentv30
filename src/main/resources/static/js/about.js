/**
 * About Page Script
 *
 * Handles all functionality for the About page (/about):
 * - Logo easter egg (Ctrl+Click to access logs)
 * - Notification testing buttons
 * - Modal auto-show (if modal exists)
 *
 * Note: This is a standalone script (not an ES6 module) because:
 * - Simple, self-contained functionality
 * - No dependencies needed
 * - Avoids browser cache issues with ES6 modules
 * - Perfect for this use case
 *
 * @file about.js
 * @version 1.0.0
 */

document.addEventListener('DOMContentLoaded', function() {
    console.log('🚀 About page script initialized');

    // =========================================================================
    // MODAL AUTO-SHOW (for modal mode)
    // =========================================================================
    const aboutModal = document.getElementById('aboutModal');
    if (aboutModal) {
        const modal = new bootstrap.Modal(aboutModal);
        modal.show();
        console.log('About modal auto-shown');
    }

    // =========================================================================
    // LOGO EASTER EGG - Ctrl+Click to access logs
    // =========================================================================
    const logoImage = document.getElementById('logo-image');
    if (logoImage) {
        logoImage.addEventListener('click', function(event) {
            if (event.ctrlKey) {
                console.log('Logo easter egg activated - navigating to logs');
                window.location.href = '/logs';
                event.preventDefault();
            }
        });

        // Visual hint
        logoImage.style.cursor = 'pointer';
        logoImage.title = 'Ctrl+Click to access logs';
        console.log('✅ Logo easter egg enabled (Ctrl+Click → /logs)');
    }

    // =========================================================================
    // NOTIFICATION TESTING BUTTONS
    // =========================================================================
    const notificationButtons = document.querySelectorAll('.notification-btn');
    const statusElement = document.getElementById('notification-status');

    if (notificationButtons.length > 0 && statusElement) {
        console.log(`✅ Found ${notificationButtons.length} notification test buttons`);

        notificationButtons.forEach(button => {
            button.addEventListener('click', function() {
                const type = this.getAttribute('data-type');
                console.log(`Triggering notification preview: ${type}`);

                // Show loading state
                this.classList.add('loading');
                statusElement.innerHTML = '<div class="alert alert-info">Displaying notification preview...</div>';

                // Make API call to trigger mockup notification
                fetch('/about/trigger-mockup', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                    },
                    body: `type=${type}`
                })
                    .then(response => response.json())
                    .then(data => {
                        // Remove loading state
                        this.classList.remove('loading');

                        // Show success or error message
                        if (data.success) {
                            statusElement.innerHTML = '<div class="alert alert-success">Notification preview displayed!</div>';
                            console.log(`✅ Notification preview sent: ${type}`);
                        } else {
                            statusElement.innerHTML = `<div class="alert alert-danger">Error: ${data.error || data.message}</div>`;
                            console.error(`❌ Notification error:`, data.error || data.message);
                        }

                        // Clear status after 3 seconds
                        setTimeout(() => {
                            statusElement.innerHTML = '';
                        }, 3000);
                    })
                    .catch(error => {
                        // Remove loading state
                        this.classList.remove('loading');

                        // Show error message
                        statusElement.innerHTML = `<div class="alert alert-danger">Error: ${error.message}</div>`;
                        console.error('❌ Notification request failed:', error);

                        // Clear status after 3 seconds
                        setTimeout(() => {
                            statusElement.innerHTML = '';
                        }, 3000);
                    });
            });
        });

        console.log('✅ Notification test buttons initialized');
    }

    console.log('✅ About page ready');
});
