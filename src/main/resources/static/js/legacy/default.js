document.addEventListener('DOMContentLoaded', function() {
    // Remove URL parameters
    if (window.history.replaceState) {
        window.history.replaceState({}, document.title, window.location.pathname);
    }

    // Auto-dismiss alerts
    const alerts = document.querySelectorAll('.alert:not(.bg-info)');
    alerts.forEach(alert => {
        setTimeout(() => {
            alert.classList.remove('show');
            setTimeout(() => {
                alert.remove();
            }, 150);
        }, 3000);
    });

    // Form validation
    const forms = document.querySelectorAll('.needs-validation');
    Array.from(forms).forEach(form => {
        form.addEventListener('submit', event => {
            if (!form.checkValidity()) {
                event.preventDefault();
                event.stopPropagation();
            }
            form.classList.add('was-validated');
        }, false);
    });
});