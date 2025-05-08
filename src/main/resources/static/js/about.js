document.addEventListener('DOMContentLoaded', function() {
    // Show modal automatically on page load (existing functionality)
    const aboutModal = document.getElementById('aboutModal');
    if (aboutModal) {
        const modal = new bootstrap.Modal(aboutModal);
        modal.show();
    }

    // Add Ctrl+Click handler on logo to access logs (existing functionality)
    const logoImage = document.getElementById('logo-image');
    if (logoImage) {
        logoImage.addEventListener('click', function(event) {
            if (event.ctrlKey) {
                window.location.href = '/logs';
                event.preventDefault();
            }
        });
    }

    // Notification preview functionality
    const notificationButtons = document.querySelectorAll('.notification-btn');
    const statusElement = document.getElementById('notification-status');

    if (notificationButtons.length > 0 && statusElement) {
        notificationButtons.forEach(button => {
            button.addEventListener('click', function() {
                const type = this.getAttribute('data-type');

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
                    } else {
                        statusElement.innerHTML = `<div class="alert alert-danger">Error: ${data.error || data.message}</div>`;
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

                    // Clear status after 3 seconds
                    setTimeout(() => {
                        statusElement.innerHTML = '';
                    }, 3000);
                });
            });
        });
    }
});