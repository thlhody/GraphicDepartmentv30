/**
 * Simplified OMS Connection JavaScript
 * Handles manual token paste functionality only
 */

document.addEventListener('DOMContentLoaded', function() {
    // Initialize event listeners
    initializeOmsConnection();
});

function initializeOmsConnection() {
    const connectBtn = document.getElementById('connectToOmsBtn');
    const disconnectBtn = document.getElementById('confirmDisconnectBtn');
    const testConnectionBtn = document.getElementById('testConnectionBtn');

    if (connectBtn) {
        connectBtn.addEventListener('click', handleManualTokenSubmit);
    }

    if (disconnectBtn) {
        disconnectBtn.addEventListener('click', handleDisconnect);
    }

    if (testConnectionBtn) {
        testConnectionBtn.addEventListener('click', testConnection);
    }
}

function handleManualTokenSubmit() {
    const tokenInput = document.getElementById('omsToken');
    const usernameInput = document.getElementById('omsUsername');
    const token = tokenInput ? tokenInput.value.trim() : '';
    const omsUsername = usernameInput ? usernameInput.value.trim() : '';

    if (!token) {
        showAlert('Please paste your OMS session token', 'warning');
        return;
    }

    if (!omsUsername) {
        showAlert('Please enter your OMS username', 'warning');
        return;
    }

    const connectBtn = document.getElementById('connectToOmsBtn');
    const spinner = document.getElementById('connectBtnSpinner');
    const btnText = document.getElementById('connectBtnText');

    // Show loading state
    if (connectBtn) connectBtn.disabled = true;
    if (spinner) spinner.classList.remove('d-none');
    if (btnText) btnText.textContent = 'Connecting...';

    // Send token to backend
    fetch('/oms/import-token', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Requested-With': 'XMLHttpRequest'
        },
        body: JSON.stringify({
            extractedData: {
                cookies: token,
                authMethod: 'manual_paste',
                timestamp: new Date().toISOString(),
                isLoggedIn: true,
                omsUsername: omsUsername
            },
            extractedAt: new Date().toISOString(),
            sourceUrl: 'manual_input'
        }),
        credentials: 'include'
    })
    .then(response => response.json())
    .then(result => {
        if (result.success) {
            showAlert('✅ Successfully connected to OMS!', 'success');
            // Clear the token input for security
            if (tokenInput) tokenInput.value = '';

            // Close modal and refresh page
            setTimeout(() => {
                const modal = bootstrap.Modal.getInstance(document.getElementById('omsConnectionModal'));
                if (modal) modal.hide();
                location.reload();
            }, 1500);
        } else {
            showAlert('❌ Connection failed: ' + (result.message || 'Invalid token'), 'danger');
        }
    })
    .catch(error => {
        console.error('Connection error:', error);
        showAlert('❌ Connection failed. Please check your network.', 'danger');
    })
    .finally(() => {
        // Reset button state
        if (connectBtn) connectBtn.disabled = false;
        if (spinner) spinner.classList.add('d-none');
        if (btnText) btnText.textContent = 'Connect';
    });
}

function handleDisconnect() {
    const disconnectBtn = document.getElementById('confirmDisconnectBtn');
    const spinner = document.getElementById('disconnectBtnSpinner');
    const btnText = document.getElementById('disconnectBtnText');

    // Show loading state
    if (disconnectBtn) disconnectBtn.disabled = true;
    if (spinner) spinner.classList.remove('d-none');
    if (btnText) btnText.textContent = 'Disconnecting...';

    fetch('/oms/disconnect', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Requested-With': 'XMLHttpRequest'
        },
        credentials: 'include'
    })
    .then(response => response.json())
    .then(result => {
        if (result.success) {
            // Close modal and refresh page
            const modal = bootstrap.Modal.getInstance(document.getElementById('omsDisconnectModal'));
            if (modal) modal.hide();
            location.reload();
        } else {
            alert('Failed to disconnect: ' + (result.message || 'Unknown error'));
        }
    })
    .catch(error => {
        console.error('Disconnect error:', error);
        alert('Failed to disconnect. Please try again.');
    })
    .finally(() => {
        // Reset button state
        if (disconnectBtn) disconnectBtn.disabled = false;
        if (spinner) spinner.classList.add('d-none');
        if (btnText) btnText.textContent = 'Disconnect';
    });
}

function testConnection() {
    const testBtn = document.getElementById('testConnectionBtn');
    const originalText = testBtn ? testBtn.textContent : '';

    if (testBtn) {
        testBtn.disabled = true;
        testBtn.innerHTML = '<i class="fas fa-spinner fa-spin me-1"></i>Testing...';
    }

    fetch('/oms/test-connection', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Requested-With': 'XMLHttpRequest'
        },
        credentials: 'include'
    })
    .then(response => response.json())
    .then(result => {
        if (result.success) {
            showAlert('✅ OMS connection is working!', 'success');
        } else {
            showAlert('❌ ' + (result.message || 'Connection test failed'), 'warning');
        }
    })
    .catch(error => {
        console.error('Test error:', error);
        showAlert('❌ Connection test failed', 'danger');
    })
    .finally(() => {
        if (testBtn) {
            testBtn.disabled = false;
            testBtn.innerHTML = originalText;
        }
    });
}

function showAlert(message, type) {
    const alertDiv = document.getElementById('omsConnectionAlert');
    if (alertDiv) {
        alertDiv.className = `alert alert-${type}`;
        alertDiv.innerHTML = message;
        alertDiv.classList.remove('d-none');

        // Auto-hide success alerts after 3 seconds
        if (type === 'success') {
            setTimeout(() => {
                alertDiv.classList.add('d-none');
            }, 3000);
        }
    }
}

function copyTokenExtractionCode() {
    const code = `// Paste this in the OMS website console (F12 > Console)
console.log("=== OMS Session Token ===");
console.log(document.cookie);
console.log("=== Copy the text above ===");`;

    navigator.clipboard.writeText(code).then(() => {
        alert('Code copied to clipboard! Paste it in the OMS console.');
    }).catch(() => {
        // Fallback for older browsers
        const textarea = document.createElement('textarea');
        textarea.value = code;
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
        alert('Code copied to clipboard! Paste it in the OMS console.');
    });
}