/**
 * SessionTimeManagement.js
 *
 * Handles embedded time management section within the session page.
 * Loads time management content via AJAX, handles period navigation,
 * keyboard shortcuts, and scroll functionality.
 *
 * @module features/session/SessionTimeManagement
 */

/**
 * SessionTimeManagement class
 * Manages time management integration in session page
 */
export class SessionTimeManagement {
    constructor() {
        this.timeManagementLoaded = false;
        this.currentYear = new Date().getFullYear();
        this.currentMonth = new Date().getMonth() + 1;

        console.log('📄 Initializing Session-Time Management Integration...');

        // Load time management content immediately since it starts expanded
        this.loadContent();

        // Set up scroll navigation functions
        this.setupScrollNavigation();

        // Make toggle function available globally
        window.toggleTimeManagement = () => this.toggle();

        console.log('✅ Session-Time Management Integration initialized');
    }

    /**
     * Toggle time management section visibility
     */
    toggle() {
        const content = document.getElementById('timeManagementContent');
        const icon = document.getElementById('tmToggleIcon');

        if (!content || !icon) return;

        if (content.classList.contains('d-none')) {
            // Expanding
            content.classList.remove('d-none');
            icon.classList.remove('bi-chevron-down');
            icon.classList.add('bi-chevron-up');

            if (!this.timeManagementLoaded) {
                this.loadContent();
            }

            // Scroll to time management section
            setTimeout(() => {
                this.scrollToTimeManagement();
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
     */
    async loadContent() {
        console.log(`🔥 Loading time management content for ${this.currentYear}/${this.currentMonth}...`);

        try {
            // Show loading state
            this.showLoadingState();

            // Fetch fragment from TimeManagementController
            const response = await fetch(`/user/time-management/fragment?year=${this.currentYear}&month=${this.currentMonth}`);

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const html = await response.text();

            // Parse HTML to check if we got a full page or just a fragment
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, 'text/html');

            // Check for layout fragment attribute
            const fragmentContent = doc.querySelector('[layout\\:fragment="content"]');

            if (fragmentContent) {
                // Full page response - extract the content
                document.getElementById('timeManagementContent').innerHTML = fragmentContent.innerHTML;
            } else {
                // Fragment response - use directly
                document.getElementById('timeManagementContent').innerHTML = html;
            }

            // Update period indicator
            this.updatePeriodIndicator();

            // Initialize time management modules for embedded context
            // Wait for next animation frame to ensure DOM is ready
            requestAnimationFrame(() => {
                this.initializeEmbeddedTimeManagement();
            });

            this.timeManagementLoaded = true;

            console.log('✅ Time management content loaded successfully');

        } catch (error) {
            console.error('❌ Error loading time management content:', error);
            this.showError(error.message);
        }
    }

    /**
     * Show loading state in time management section
     */
    showLoadingState() {
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
     * @param {string} errorMessage - Error message
     */
    showError(errorMessage) {
        document.getElementById('timeManagementContent').innerHTML = `
            <div class="alert alert-danger m-3">
                <h6><i class="bi bi-exclamation-triangle me-2"></i>Failed to Load Time Management</h6>
                <p class="mb-2">${errorMessage}</p>
                <div class="d-flex gap-2">
                    <button class="btn btn-sm btn-outline-danger" onclick="window.SessionTimeManagementInstance.loadContent()">
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
    updatePeriodIndicator() {
        const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
            'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        const periodText = `${months[this.currentMonth - 1]} ${this.currentYear}`;

        const periodElement = document.getElementById('tmCurrentPeriod');
        if (periodElement) {
            periodElement.textContent = periodText;
            console.log(`📅 Updated period indicator: ${periodText}`);
        }
    }

    /**
     * Initialize time management modules for embedded context
     */
    initializeEmbeddedTimeManagement() {
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
                this.setupEmbeddedInlineEditing();
            }

            // Initialize Holiday Request Modal if present
            this.initializeHolidayModal();

            if (window.setupSignatureHandler) {
                window.setupSignatureHandler();
            }
            if (window.setupModalHandlers) {
                window.setupModalHandlers();
            }
            if (window.setupCheckboxHandlers) {
                window.setupCheckboxHandlers();
            }

            // Set up embedded navigation
            this.setupEmbeddedNavigation();

            console.log('✅ Embedded time management modules initialized');

        } catch (error) {
            console.error('❌ Error initializing embedded time management:', error);
        }
    }

    /**
     * Initialize holiday request modal for embedded context
     */
    initializeHolidayModal() {
        const holidayModalElement = document.getElementById('holidayModal');
        if (holidayModalElement && window.HolidayRequestModalModule) {
            console.log('📋 Initializing Holiday Request Modal (embedded)...');

            const holidayModal = new window.HolidayRequestModalModule();
            holidayModal.init();

            // Make available globally
            window.holidayRequestModal = holidayModal;
            window.openHolidayRequestModal = (startDate, endDate, userData, timeOffType) => {
                return holidayModal.open(startDate, endDate, userData, timeOffType);
            };
            window.closeHolidayModal = () => holidayModal.close();
            window.exportHolidayToImage = (format) => holidayModal.exportToImage(format);

            // Add openHolidayRequestFromForm for inline onclick compatibility
            window.openHolidayRequestFromForm = () => {
                console.log('📋 Opening holiday request modal from form (session page)...');

                // Extract user data
                const userData = this.extractCurrentUserData();

                // Get dates from form inputs
                const startDateField = document.querySelector('input[name="startDate"]');
                const endDateField = document.querySelector('input[name="endDate"]');
                const startDate = startDateField ? startDateField.value : '';
                const endDate = endDateField ? endDateField.value : '';

                // Get selected time off type
                const timeOffTypeSelect = document.getElementById('timeOffType');
                const selectedType = timeOffTypeSelect ? timeOffTypeSelect.value : null;

                console.log('📊 Form data:', { startDate, endDate, userData, selectedType });

                // Open the modal
                window.openHolidayRequestModal(startDate, endDate, userData, selectedType);
            };

            console.log('✅ Holiday Request Modal initialized (embedded)');
        }
    }

    /**
     * Extract current user data for holiday modal
     * @returns {Object} User data object
     */
    extractCurrentUserData() {
        const userData = {};

        // Try to get name from user badge
        const userBadgeSpan = document.querySelector('.badge .bi-person + span');
        if (userBadgeSpan && userBadgeSpan.textContent.trim()) {
            userData.name = userBadgeSpan.textContent.trim();
            console.log('👤 Found username from badge:', userData.name);
        }

        // Fallback name
        if (!userData.name) {
            userData.name = 'User';
            console.log('👤 Using fallback username');
        }

        return userData;
    }

    /**
     * Set up navigation handlers for embedded context
     */
    setupEmbeddedNavigation() {
        console.log('🔧 Setting up embedded navigation handlers...');

        // Override period navigation to reload embedded content
        const periodForm = document.querySelector('#timeManagementContent .card-header form[action*="/user/time-management"]');
        if (periodForm) {
            // Remove existing submit handler
            periodForm.removeEventListener('submit', periodForm._embeddedHandler);

            // Add new embedded handler
            periodForm._embeddedHandler = (e) => {
                e.preventDefault();
                e.stopPropagation();

                const yearSelect = periodForm.querySelector('#yearSelect');
                const monthSelect = periodForm.querySelector('#monthSelect');

                if (yearSelect && monthSelect) {
                    const newYear = parseInt(yearSelect.value);
                    const newMonth = parseInt(monthSelect.value);

                    console.log(`📅 Period navigation: ${newYear}/${newMonth}`);

                    // Update current period
                    this.currentYear = newYear;
                    this.currentMonth = newMonth;

                    // Show loading message
                    if (window.showToast) {
                        const monthName = monthSelect.options[monthSelect.selectedIndex].text;
                        window.showToast('Loading Period', `Loading ${monthName} ${newYear}...`, 'info', { duration: 1500 });
                    }

                    // Reload content with new period
                    this.timeManagementLoaded = false;
                    this.loadContent();
                }
            };

            periodForm.addEventListener('submit', periodForm._embeddedHandler);
            console.log('✅ Period form navigation handler attached');
        }

        // Set up keyboard navigation
        this.setupEmbeddedKeyboardNavigation();

        // Set up export button
        this.setupEmbeddedExportHandler();
    }

    /**
     * Set up keyboard navigation for embedded context
     */
    setupEmbeddedKeyboardNavigation() {
        // Remove any existing period navigation listeners to avoid conflicts
        if (window.PeriodNavigationModule && window.PeriodNavigationModule._keydownHandler) {
            document.removeEventListener('keydown', window.PeriodNavigationModule._keydownHandler);
        }

        // Add embedded-specific keyboard navigation
        document.addEventListener('keydown', (e) => {
            // Only if focus is within time management section
            const tmSection = document.getElementById('timeManagementContent');
            if (!tmSection || !tmSection.contains(document.activeElement)) {
                return;
            }

            // Check if inline editing is in progress
            if (window.InlineEditingModule && window.InlineEditingModule.getCurrentEditCell()) {
                return; // Don't interfere with editing
            }

            // Handle navigation keys with Alt
            if (e.altKey) {
                switch(e.key) {
                    case 'ArrowLeft':
                        e.preventDefault();
                        this.navigatePeriod(-1, 0);
                        break;
                    case 'ArrowRight':
                        e.preventDefault();
                        this.navigatePeriod(1, 0);
                        break;
                    case 'ArrowUp':
                        e.preventDefault();
                        this.navigatePeriod(0, -1);
                        break;
                    case 'ArrowDown':
                        e.preventDefault();
                        this.navigatePeriod(0, 1);
                        break;
                }
            }
        });
    }

    /**
     * Navigate to different period
     * @param {number} monthDelta - Month change
     * @param {number} yearDelta - Year change
     */
    navigatePeriod(monthDelta, yearDelta) {
        let newMonth = this.currentMonth + monthDelta;
        let newYear = this.currentYear + yearDelta;

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
        this.currentYear = newYear;
        this.currentMonth = newMonth;

        // Show navigation toast
        if (window.showToast) {
            const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
            window.showToast('Navigation', `Loading ${months[newMonth - 1]} ${newYear}...`, 'info', { duration: 1500 });
        }

        // Reload content
        this.timeManagementLoaded = false;
        this.loadContent();
    }

    /**
     * Set up export handler for embedded context
     */
    setupEmbeddedExportHandler() {
        const exportButton = document.querySelector('#timeManagementContent a[href*="/user/time-management/export"]');
        if (exportButton) {
            // Update href to include current period
            const baseHref = exportButton.getAttribute('href').split('?')[0];
            exportButton.href = `${baseHref}?year=${this.currentYear}&month=${this.currentMonth}`;

            exportButton.addEventListener('click', () => {
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
     * Setup inline editing for embedded context
     */
    setupEmbeddedInlineEditing() {
        // Override inline editing form submissions to stay in embedded context
        // This would integrate with InlineEditingModule if needed
    }

    /**
     * Set up scroll navigation functions
     */
    setupScrollNavigation() {
        // Make scroll functions available globally
        window.scrollToTimeManagement = () => this.scrollToTimeManagement();
        window.scrollToUnresolved = () => this.scrollToUnresolved();
        window.hideUnresolvedTab = () => this.hideUnresolvedTab();
    }

    /**
     * Scroll to time management section
     */
    scrollToTimeManagement() {
        const tmTab = document.getElementById('timeManagementTab');
        if (tmTab) {
            tmTab.scrollIntoView({
                behavior: 'smooth',
                block: 'start'
            });

            // Expand if collapsed
            const content = document.getElementById('timeManagementContent');
            if (content && content.classList.contains('d-none')) {
                this.toggle();
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
    scrollToUnresolved() {
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
    hideUnresolvedTab() {
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
     * Get current time management state (for debugging)
     * @returns {Object} Current state
     */
    getState() {
        return {
            loaded: this.timeManagementLoaded,
            currentYear: this.currentYear,
            currentMonth: this.currentMonth,
            expanded: !document.getElementById('timeManagementContent').classList.contains('d-none'),
            lastUpdated: new Date().toISOString()
        };
    }

    /**
     * Force reload time management content (for debugging/manual refresh)
     */
    reload() {
        console.log('🔄 Force reloading time management content...');
        this.timeManagementLoaded = false;
        this.loadContent();
    }
}
