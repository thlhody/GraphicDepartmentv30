// Toggle icon animation
$('#monitor-controls').on('show.bs.collapse', function() {
    $('#monitor-utility .toggle-icon').removeClass('bi-chevron-down').addClass('bi-chevron-up');
}).on('hide.bs.collapse', function() {
    $('#monitor-utility .toggle-icon').removeClass('bi-chevron-up').addClass('bi-chevron-down');
});

// View Cache Status
$('#view-cache-status-btn').click(function() {
    const btn = $(this);
    setButtonLoading(btn, true);

    $.ajax({
        url: '/utility/cache/status',
        method: 'GET',
        success: function(response) {
            if (response.success) {
                displayCacheStatus(response.cacheStatus);
                updateQuickStats('Cache Status', 'Loaded');
                updateLastCheck();
                $('#monitor-results').show();
                window.showToast('Success', 'Cache status retrieved', 'success');
            } else {
                window.showToast('Error', response.message || 'Failed to get cache status', 'error');
            }
        },
        error: function(xhr, status, error) {
            window.showToast('Error', 'Failed to get cache status: ' + error, 'error');
            updateQuickStats('Cache Status', 'Error');
        },
        complete: function() {
            setButtonLoading(btn, false);
        }
    });
});

// Validate Cache
$('#validate-cache-btn').click(function() {
    const btn = $(this);
    setButtonLoading(btn, true);

    $.ajax({
        url: '/utility/cache/validate',
        method: 'POST',
        success: function(response) {
            if (response.success) {
                displayValidationResult(response);
                updateQuickStats('Cache Status', 'Valid');
                updateLastCheck();
                $('#monitor-results').show();
                window.showToast('Success', 'Cache validation completed', 'success');
            } else {
                window.showToast('Error', response.message || 'Cache validation failed', 'error');
                updateQuickStats('Cache Status', 'Invalid');
            }
        },
        error: function(xhr, status, error) {
            window.showToast('Error', 'Failed to validate cache: ' + error, 'error');
            updateQuickStats('Cache Status', 'Error');
        },
        complete: function() {
            setButtonLoading(btn, false);
        }
    });
});

// Clear Results
$('#clear-monitor-results').click(function() {
    $('#monitor-results').hide();
    $('#monitor-content').empty();
});

// Display cache status
function displayCacheStatus(cacheStatus) {
    const content = $('#monitor-content');
    content.html(`
            <div class="monitor-section">
                <h5>Cache Status Report</h5>
                <div class="status-display">
                    <pre class="status-text">${cacheStatus}</pre>
                </div>
            </div>
        `);
}

// Display validation result
function displayValidationResult(result) {
    const content = $('#monitor-content');
    content.html(`
            <div class="monitor-section">
                <h5>Cache Validation Result</h5>
                <div class="validation-result">
                    <div class="result-item success">
                        <i class="bi bi-check-circle"></i>
                        <span>${result.message}</span>
                    </div>
                    <div class="result-meta">
                        <small class="text-muted">Validated at: ${result.timestamp}</small>
                    </div>
                </div>
            </div>
        `);
}

// Update quick stats
function updateQuickStats(label, value) {
    if (label === 'Cache Status') {
        $('#cache-status-indicator').text(value);
    }
}

// Update last check time
function updateLastCheck() {
    const now = new Date();
    const timeString = now.toLocaleTimeString('en-US', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit'
    });
    $('#last-check-time').text(timeString);
}

// Button loading state
function setButtonLoading(btn, loading) {
    if (loading) {
        btn.addClass('loading').prop('disabled', true);
        btn.find('i').addClass('spin');
    } else {
        btn.removeClass('loading').prop('disabled', false);
        btn.find('i').removeClass('spin');
    }
}