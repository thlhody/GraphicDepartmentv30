/**
 * Session Time Management Integration Script
 * Handles the embedded time management section within the session page
 * File: /js/session-time-management-integration.js
 */

// Integration state management
let timeManagementLoaded = false;
let currentTMYear = new Date().getFullYear();
let currentTMMonth = new Date().getMonth() + 1;

document.addEventListener('DOMContentLoaded', function() {
    console.log('📄 Initializing Session-Time Management Integration...');

    // Load time management content immediately since it starts expanded
    loadTimeManagementContent();

    // Set up scroll navigation functions
    setupScrollNavigation();

    console.log('✅ Session-Time Management Integration initialized');
});

/**
 * Toggle time management section visibility
 */
function toggleTimeManagement() {
    const content = document.getElementById('timeManagementContent');
    const icon = document.getElementById('tmToggleIcon');

    if (content.classList.contains('d-none')) {
        // Expanding
        content.classList.remove('d-none');
        icon.classList.remove('bi-chevron-down');
        icon.classList.add('bi-chevron-up');

        if (!timeManagementLoaded) {
            loadTimeManagementContent();
        }

        // Scroll to time management section
        setTimeout(() => {
            scrollToTimeManagement();
        }, 300);

    } else {
        // Collapsing
        content.classList.add('d-none');
        icon.classList.remove('bi-chevron-up');
        icon.classList.add('bi-chevron-down');
    }
}

/**
 * Load time management content via AJAX
 * FIXED: Use the TimeManagementController fragment endpoint
 */
async function loadTimeManagementContent() {
    console.log(`🔥 Loading time management content for ${currentTMYear}/${currentTMMonth}...`);

    try {
        // Show loading state
        showTMLoadingState();

        // FIXED: Use the TimeManagementController fragment endpoint
        const response = await fetch(`/user/time-management/fragment?year=${currentTMYear}&month=${currentTMMonth}`);

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const html = await response.text();

        // FIXED: For fragment response, we expect just the content without layout wrapper
        // Try to parse as HTML first, but if it's just a fragment, use it directly
        const parser = new DOMParser();
        const doc = parser.parseFromString(html, 'text/html');

        // Check if we got a full HTML document or just a fragment
        const fragmentContent = doc.querySelector('[layout\\:fragment="content"]');

        if (fragmentContent) {
            // Full page response - extract the content
            document.getElementById('timeManagementContent').innerHTML = fragmentContent.innerHTML;
        } else {
            // Fragment response - use directly
            document.getElementById('timeManagementContent').innerHTML = html;
        }

        // Update period indicator
        updatePeriodIndicator();

        // Initialize time management modules for embedded context
        initializeEmbeddedTimeManagement();

        // Set up embedded navigation handlers
        // Wait for next animation frame to ensure DOM is ready
        requestAnimationFrame(() => {
            initializeEmbeddedTimeManagement();
        });

        timeManagementLoaded = true;

        console.log('✅ Time management content loaded successfully');

    } catch (error) {
        console.error('❌ Error loading time management content:', error);
        showTMError(error.message);
    }
}

/**
 * Show loading state in time management section
 */
function showTMLoadingState() {
    document.getElementById('timeManagementContent').innerHTML = `
        <div class="text-center p-4">
            <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">Loading...</span>
            </div>
            <p class="mt-2 text-muted">Loading time management data...</p>
        </div>
    `;
}

/**
 * Show error state in time management section
 */
function showTMError(errorMessage) {
    document.getElementById('timeManagementContent').innerHTML = `
        <div class="alert alert-danger m-3">
            <h6><i class="bi bi-exclamation-triangle me-2"></i>Failed to Load Time Management</h6>
            <p class="mb-2">${errorMessage}</p>
            <div class="d-flex gap-2">
                <button class="btn btn-sm btn-outline-danger" onclick="loadTimeManagementContent()">
                    <i class="bi bi-arrow-clockwise me-1"></i>Retry
                </button>
                <a href="/user/time-management" target="_blank" class="btn btn-sm btn-outline-primary">
                    <i class="bi bi-box-arrow-up-right me-1"></i>Open in New Tab
                </a>
            </div>
        </div>
    `;
}

/**
 * Update the period indicator in the tab header
 */
function updatePeriodIndicator() {
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
        'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const periodText = `${months[currentTMMonth - 1]} ${currentTMYear}`;

    const periodElement = document.getElementById('tmCurrentPeriod');
    if (periodElement) {
        periodElement.textContent = periodText;
        console.log(`📅 Updated period indicator: ${periodText}`);
    }
}

/**
 * Initialize time management modules for embedded context
 * SIMPLIFIED: Modules are already loaded by session page
 */
function initializeEmbeddedTimeManagement() {
    console.log('🔧 Initializing embedded time management modules...');

    try {
        // All modules are already loaded and initialized by session page
        // Just need to reinitialize for the new fragment content

        if (window.TimeOffManagementModule && typeof window.TimeOffManagementModule.initialize === 'function') {
            // Reset state and reinitialize for new content
            window.TimeOffManagementModule.state.isInitialized = false;
            window.TimeOffManagementModule.initialize();
        }

        if (window.InlineEditingModule && typeof window.InlineEditingModule.initialize === 'function') {
            window.InlineEditingModule.initialize();
            setupEmbeddedInlineEditing();
        }

        if (window.setupSignatureHandler) {
            window.setupSignatureHandler();
        }
        if (window.setupModalHandlers) {
            window.setupModalHandlers();
        }
        if (window.setupCheckboxHandlers) {
            window.setupCheckboxHandlers();
        }

        console.log('✅ Embedded time management modules initialized');

    } catch (error) {
        console.error('❌ Error initializing embedded time management:', error);
    }
}

// Override inline editing form submissions to stay in embedded context
function setupEmbeddedInlineEditing() {
    if (window.InlineEditingModule) {
        // Override the handleSuccess function that triggers the refresh
        const originalHandleSuccess = window.InlineEditingModule.handleSuccess;
        window.InlineEditingModule.handleSuccess = function(cell, field, response) {
            console.log('🔄 Embedded inline edit success handler - preventing redirect');

            // Update the cell display with the new value
            this.updateCellDisplay(cell, field, response.newValue || response.value);

            // Finish the edit (remove editor, etc.)
            this.finishEdit(cell);

            // Show success message
            if (window.showToast) {
                window.showToast('Updated', `${field} updated successfully`, 'success', { duration: 2000 });
            }

            // Reload embedded content instead of redirecting
            setTimeout(() => {
                console.log('🔄 Reloading embedded content after edit success');
                timeManagementLoaded = false;
                loadTimeManagementContent();
            }, 1000);
        };
    }
}

/**
 * Set up navigation handlers for embedded context
 */
function setupEmbeddedNavigation() {
    console.log('🔧 Setting up embedded navigation handlers...');

    // Override period navigation to reload embedded content instead of full page
    const periodForm = document.querySelector('#timeManagementContent .card-header form[action*="/user/time-management"]');
    if (periodForm) {
        // Remove existing submit handler
        periodForm.removeEventListener('submit', periodForm._embeddedHandler);

        // Add new embedded handler
        periodForm._embeddedHandler = function(e) {
            e.preventDefault();
            e.stopPropagation();

            const yearSelect = this.querySelector('#yearSelect');
            const monthSelect = this.querySelector('#monthSelect');

            if (yearSelect && monthSelect) {
                const newYear = parseInt(yearSelect.value);
                const newMonth = parseInt(monthSelect.value);

                console.log(`📅 Period navigation: ${newYear}/${newMonth}`);

                // Update current period
                currentTMYear = newYear;
                currentTMMonth = newMonth;

                // Show loading message
                if (window.showToast) {
                    const monthName = monthSelect.options[monthSelect.selectedIndex].text;
                    window.showToast('Loading Period', `Loading ${monthName} ${newYear}...`, 'info', { duration: 1500 });
                }

                // Reload content with new period
                timeManagementLoaded = false;
                loadTimeManagementContent();
            }
        };

        periodForm.addEventListener('submit', periodForm._embeddedHandler);
        console.log('✅ Period form navigation handler attached');
    }

    // Override keyboard navigation shortcuts
    setupEmbeddedKeyboardNavigation();

    // Override export button to maintain context
    setupEmbeddedExportHandler();
}

/**
 * Set up keyboard navigation for embedded context
 */
function setupEmbeddedKeyboardNavigation() {
    // Remove any existing period navigation listeners to avoid conflicts
    document.removeEventListener('keydown', window.PeriodNavigationModule?._keydownHandler);

    // Add embedded-specific keyboard navigation
    document.addEventListener('keydown', function(e) {
        // Only if focus is within time management section
        const tmSection = document.getElementById('timeManagementContent');
        if (!tmSection || !tmSection.contains(document.activeElement)) {
            return;
        }

        // Check if inline editing is in progress
        if (window.InlineEditingModule && window.InlineEditingModule.getCurrentEditCell()) {
            return; // Don't interfere with editing
        }

        // Handle navigation keys
        if (e.altKey) {
            switch(e.key) {
                case 'ArrowLeft':
                    e.preventDefault();
                    navigateEmbeddedPeriod(-1, 0);
                    break;
                case 'ArrowRight':
                    e.preventDefault();
                    navigateEmbeddedPeriod(1, 0);
                    break;
                case 'ArrowUp':
                    e.preventDefault();
                    navigateEmbeddedPeriod(0, -1);
                    break;
                case 'ArrowDown':
                    e.preventDefault();
                    navigateEmbeddedPeriod(0, 1);
                    break;
            }
        }
    });
}

/**
 * Navigate to different period in embedded context
 */
function navigateEmbeddedPeriod(monthDelta, yearDelta) {
    let newMonth = currentTMMonth + monthDelta;
    let newYear = currentTMYear + yearDelta;

    // Handle month overflow/underflow
    if (newMonth > 12) {
        newMonth = 1;
        newYear++;
    } else if (newMonth < 1) {
        newMonth = 12;
        newYear--;
    }

    console.log(`📅 Keyboard navigation: ${newYear}/${newMonth}`);

    // Update current period
    currentTMYear = newYear;
    currentTMMonth = newMonth;

    // Show navigation toast
    if (window.showToast) {
        const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
            'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        window.showToast('Navigation', `Loading ${months[newMonth - 1]} ${newYear}...`, 'info', { duration: 1500 });
    }

    // Reload content
    timeManagementLoaded = false;
    loadTimeManagementContent();
}

/**
 * Set up export handler for embedded context
 */
function setupEmbeddedExportHandler() {
    const exportButton = document.querySelector('#timeManagementContent a[href*="/user/time-management/export"]');
    if (exportButton) {
        // Update href to include current period
        const baseHref = exportButton.getAttribute('href').split('?')[0];
        exportButton.href = `${baseHref}?year=${currentTMYear}&month=${currentTMMonth}`;

        exportButton.addEventListener('click', function(e) {
            console.log('📊 Initiating Excel export from embedded section...');

            if (window.showToast) {
                window.showToast('Exporting Data',
                    'Generating Excel file for download...',
                    'info',
                    { duration: 3000 });
            }
        });

        console.log('✅ Export button handler setup');
    }
}

/**
 * Set up scroll navigation functions
 */
function setupScrollNavigation() {
    // Make scroll functions available globally
    window.scrollToTimeManagement = scrollToTimeManagement;
    window.scrollToUnresolved = scrollToUnresolved;
    window.hideUnresolvedTab = hideUnresolvedTab;

    // Update existing scroll function for resolution
    window.scrollToResolution = scrollToUnresolved; // Backward compatibility
}

/**
 * Scroll to time management section
 */
function scrollToTimeManagement() {
    const tmTab = document.getElementById('timeManagementTab');
    if (tmTab) {
        tmTab.scrollIntoView({
            behavior: 'smooth',
            block: 'start'
        });

        // Expand if collapsed
        const content = document.getElementById('timeManagementContent');
        if (content && content.classList.contains('d-none')) {
            toggleTimeManagement();
        }

        // Show toast notification
        if (window.showToast) {
            window.showToast('Navigation', 'Scrolled to Time Management section', 'info', {
                duration: 2000
            });
        }
    }
}

/**
 * Scroll to unresolved sessions section
 */
function scrollToUnresolved() {
    const unresolvedTab = document.getElementById('unresolvedTab');
    if (unresolvedTab) {
        unresolvedTab.scrollIntoView({
            behavior: 'smooth',
            block: 'center'
        });

        // Add highlight effect
        unresolvedTab.classList.add('highlight-container');
        setTimeout(() => {
            unresolvedTab.classList.remove('highlight-container');
        }, 2000);

        if (window.showToast) {
            window.showToast('Navigation', 'Scrolled to unresolved entries section', 'info', {
                duration: 2000
            });
        }
    } else {
        console.warn('Unresolved tab not found');
        if (window.showToast) {
            window.showToast('Info', 'No unresolved entries found', 'info');
        }
    }
}

/**
 * Hide unresolved tab
 */
function hideUnresolvedTab() {
    const unresolvedTab = document.getElementById('unresolvedTab');
    if (unresolvedTab) {
        unresolvedTab.style.display = 'none';

        if (window.showToast) {
            window.showToast('Dismissed', 'Unresolved entries section hidden', 'info', {
                duration: 2000
            });
        }
    }
}

/**
 * Enhanced dismiss card function for floating notification
 */
function dismissCard() {
    const card = document.getElementById('unresolvedCard');
    if (card) {
        // Add slide-out animation to the left
        card.style.animation = 'slideOutLeft 0.5s ease-in forwards';

        // Remove from DOM after animation
        setTimeout(() => {
            card.remove();
        }, 500);

        // Store dismissal in session storage to prevent showing again
        sessionStorage.setItem('unresolvedCardDismissed', 'true');

        // Show toast notification
        if (window.showToast) {
            window.showToast('Dismissed', 'Reminder dismissed. You can still resolve entries in the Unresolved section.', 'info', {
                duration: 3000
            });
        }
    }
}

/**
 * Get current time management state (for debugging)
 */
function getTimeManagementState() {
    return {
        loaded: timeManagementLoaded,
        currentYear: currentTMYear,
        currentMonth: currentTMMonth,
        expanded: !document.getElementById('timeManagementContent').classList.contains('d-none'),
        lastUpdated: new Date().toISOString()
    };
}

/**
 * Force reload time management content (for debugging/manual refresh)
 */
function reloadTimeManagement() {
    console.log('🔄 Force reloading time management content...');
    timeManagementLoaded = false;
    loadTimeManagementContent();
}

// Export functions for debugging
window.timeManagementDebug = {
    getState: getTimeManagementState,
    reload: reloadTimeManagement,
    navigate: navigateEmbeddedPeriod
};

console.log('📋 Session-Time Management Integration script loaded');