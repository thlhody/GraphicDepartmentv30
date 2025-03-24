document.addEventListener('DOMContentLoaded', function() {
    // Auto refresh for metrics
    if (document.querySelector('[data-refresh="true"]')) {
        setInterval(function() {
            location.reload();
        }, 300000); // 5 minutes
    }
});