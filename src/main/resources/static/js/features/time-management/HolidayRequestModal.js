/**
 * Holiday Request Modal
 * Manages the holiday request modal UI, form handling, and validation
 *
 * @module HolidayRequestModal
 * @requires HolidayExportService
 */

import { formatDate } from '../../core/utils.js';
import { HolidayExportService } from './HolidayExportService.js';

/**
 * HolidayRequestModal class
 * Handles all holiday request modal operations
 */
export class HolidayRequestModal {
    /**
     * Create a new HolidayRequestModal instance
     */
    constructor() {
        this.modalData = {};
        this.selectedHolidayType = 'odihna'; // Default: vacation
        this.selectedRecovery = null;

        this.exportService = new HolidayExportService();

        // Bind methods
        this.open = this.open.bind(this);
        this.close = this.close.bind(this);
        this.handleOverlayClick = this.handleOverlayClick.bind(this);
        this.handleEscapeKey = this.handleEscapeKey.bind(this);
        this.exportToImage = this.exportToImage.bind(this);
    }

    /**
     * Initialize modal event handlers
     */
    init() {
        console.log('Initializing HolidayRequestModal...');

        this.setupEventHandlers();
        this.exportService.checkDependencies();

        console.log('HolidayRequestModal initialized successfully');
    }

    /**
     * Open holiday request modal with data
     *
     * @param {string} startDate - Start date for the holiday
     * @param {string} endDate - End date for the holiday
     * @param {Object} userData - User data object
     * @param {string} [timeOffType] - Optional time-off type for auto-selection (CO, CM, CR, CN, CE, D)
     */
    open(startDate, endDate, userData = {}, timeOffType = null) {
        this.modalData = { startDate, endDate, userData };

        // Populate form with data
        this.populateForm();

        // Show modal
        const modal = document.getElementById('holidayModal');
        if (modal) {
            modal.classList.add('active');
            document.body.style.overflow = 'hidden';
        }

        // Auto-select holiday type based on timeOffType parameter
        if (timeOffType) {
            console.log('Auto-selecting holiday type for:', timeOffType);
            setTimeout(() => {
                this.autoSelectHolidayType(timeOffType);
            }, 300);
        } else {
            // Set default selection if no type specified
            this.selectHolidayType('odihna');
        }
    }

    /**
     * Close holiday request modal
     */
    close() {
        const modal = document.getElementById('holidayModal');
        if (modal) {
            modal.classList.remove('active');
            document.body.style.overflow = '';

            // Refresh the time management fragment to show updated data (NO PAGE RELOAD!)
            console.log('🔄 Refreshing time management fragment after modal close...');

            // Check if we're on the session page (embedded time management)
            if (window.SessionTimeManagementInstance && typeof window.SessionTimeManagementInstance.loadContent === 'function') {
                // Session page - reload the embedded fragment via AJAX
                console.log('📄 Session page detected - using SessionTimeManagementInstance');
                setTimeout(() => {
                    window.SessionTimeManagementInstance.loadContent();
                }, 300);
            } else if (window.TimeManagementAjaxHandler && typeof window.TimeManagementAjaxHandler.reloadCurrentPeriod === 'function') {
                // Standalone time-management page - reload via AJAX (NO PAGE RELOAD!)
                console.log('📄 Standalone page detected - using TimeManagementAjaxHandler');
                setTimeout(() => {
                    window.TimeManagementAjaxHandler.reloadCurrentPeriod();
                }, 300);
            } else {
                // Fallback - reload page (shouldn't happen with new AJAX handler)
                console.warn('⚠️ No AJAX handler found - falling back to page reload');
                setTimeout(() => {
                    window.location.reload();
                }, 300);
            }
        }
    }

    /**
     * Populate form fields with data from time management system
     */
    populateForm() {
        const { startDate, endDate, userData } = this.modalData;

        // Auto-fill employee name
        const employeeNameField = document.getElementById('employeeName');
        if (employeeNameField) {
            employeeNameField.value = userData.name || '';
        }

        // Use unique IDs for holiday modal date fields
        const holidayStartField = document.getElementById('holidayStartDate');
        const holidayEndField = document.getElementById('holidayEndDate');

        if (holidayStartField) {
            holidayStartField.value = this.formatDateForDisplay(startDate) || '';
        }
        if (holidayEndField) {
            holidayEndField.value = this.formatDateForDisplay(endDate) || '';
        }
    }

    /**
     * Format date for display (DD.MM.YYYY format)
     *
     * @param {string} dateString - Date string to format
     * @returns {string} Formatted date string
     */
    formatDateForDisplay(dateString) {
        if (!dateString) return '';

        try {
            const date = new Date(dateString);
            const day = date.getDate().toString().padStart(2, '0');
            const month = (date.getMonth() + 1).toString().padStart(2, '0');
            const year = date.getFullYear();
            return `${day}.${month}.${year}`;
        } catch (error) {
            console.error('Error formatting date:', error);
            return dateString;
        }
    }

    /**
     * Auto-select holiday type based on time-off type code
     * Used when modal is opened after form submission (from backend)
     *
     * @param {string} timeOffType - Time-off type code (CO, CM, CR, CN, CE, D)
     */
    autoSelectHolidayType(timeOffType) {
        console.log('Auto-selecting holiday type for time-off code:', timeOffType);

        const typeMapping = {
            'CO': 'odihna',           // Vacation
            'CM': 'special',          // Special events
            'CE': 'special',          // Special event leave
            'CR': 'fara_plata',       // Unpaid leave with recovery
            'CN': 'fara_plata',       // Unpaid leave without recovery
            'D': 'odihna'             // Delegation (default to vacation)
        };

        const holidayType = typeMapping[timeOffType] || 'odihna';
        this.selectHolidayType(holidayType);

        // Handle recovery options for unpaid leave
        if (timeOffType === 'CR') {
            setTimeout(() => this.toggleRecovery('cu'), 200);  // With recovery
        } else if (timeOffType === 'CN') {
            setTimeout(() => this.toggleRecovery('fara'), 200);  // Without recovery
        }
    }

    /**
     * Handle holiday type selection (square checkboxes with X)
     *
     * @param {string} type - Holiday type ('odihna', 'special', 'fara_plata')
     */
    selectHolidayType(type) {
        // Clear all selections
        document.querySelectorAll('.custom-checkbox').forEach(checkbox => {
            checkbox.classList.remove('selected');
        });

        // Select the clicked one
        const selectedCheckbox = document.querySelector(`[data-value="${type}"]`);
        if (selectedCheckbox) {
            selectedCheckbox.classList.add('selected');
        }

        this.selectedHolidayType = type;

        // Show/hide conditional sections
        this.updateConditionalSections();
    }

    /**
     * Update visibility of conditional form sections based on selected holiday type
     */
    updateConditionalSections() {
        // Hide all conditional sections first
        this.hideElement('specialEventNote');
        this.hideElement('specialEventReason');
        this.hideElement('unpaidLeaveOptions');

        // Show relevant sections based on holiday type
        switch (this.selectedHolidayType) {
            case 'special':
                this.showElement('specialEventNote');
                this.showElement('specialEventReason');
                break;
            case 'fara_plata':
                this.showElement('unpaidLeaveOptions');
                break;
            case 'odihna':
            default:
                // No additional sections for regular vacation
                break;
        }
    }

    /**
     * Handle recovery option selection (cu/fara recuperare)
     * Sends CR/CN backend codes instead of 'cu'/'fara'
     *
     * @param {string} type - Recovery type ('cu' or 'fara')
     */
    toggleRecovery(type) {
        // Clear both selections
        const cuBox = document.getElementById('cuRecuperareBox');
        const faraBox = document.getElementById('faraRecuperareBox');

        if (cuBox) cuBox.classList.remove('selected');
        if (faraBox) faraBox.classList.remove('selected');

        // Select the clicked one
        if (type === 'cu' && cuBox) {
            cuBox.classList.add('selected');
            this.selectedRecovery = 'CR';  // CR = Concediu Recuperare (paid from overtime balance)
        } else if (type === 'fara' && faraBox) {
            faraBox.classList.add('selected');
            this.selectedRecovery = 'CN';  // CN = Concediu Neplatit (unpaid leave)
        }
    }

    /**
     * Handle signature file upload
     *
     * @param {Event} event - File input change event
     */
    handleSignatureUpload(event) {
        const file = event.target.files[0];
        if (!file) return;

        // Validate file type
        if (!file.type.startsWith('image/')) {
            alert('Vă rugăm să selectați o imagine pentru semnătură.');
            return;
        }

        // Validate file size (max 2MB)
        if (file.size > 2 * 1024 * 1024) {
            alert('Fișierul este prea mare. Vă rugăm să selectați o imagine mai mică de 2MB.');
            return;
        }

        const reader = new FileReader();
        reader.onload = (e) => {
            const preview = document.getElementById('signaturePreview');
            const text = document.getElementById('signatureText');

            if (preview && text) {
                preview.src = e.target.result;
                preview.classList.remove('hidden');
                text.style.display = 'none';
            }
        };
        reader.readAsDataURL(file);
    }

    /**
     * Get form data for export
     *
     * @returns {Object} Form data object
     */
    getFormData() {
        const signaturePreview = document.getElementById('signaturePreview');

        return {
            employeeName: this.getElementValue('employeeName'),
            jobPosition: this.getElementValue('jobPosition'),
            workplace: this.getElementValue('workplace'),
            startDate: this.getElementValue('holidayStartDate'),
            endDate: this.getElementValue('holidayEndDate'),
            holidayType: this.selectedHolidayType,
            specialReason: this.getElementValue('motivSpecial'),
            unpaidReason: this.getElementValue('motivFaraPlata'),
            recovery: this.selectedRecovery,
            signature: signaturePreview ? signaturePreview.src : '',
            hasSignature: signaturePreview ? !signaturePreview.classList.contains('hidden') : false
        };
    }

    /**
     * Get element value safely
     *
     * @param {string} elementId - Element ID
     * @returns {string} Element value or empty string
     */
    getElementValue(elementId) {
        const element = document.getElementById(elementId);
        return element ? element.value.trim() : '';
    }

    /**
     * Validate form data
     *
     * @returns {boolean} True if form is valid
     */
    validate() {
        const validation = this.exportService.validateForm(
            () => this.getFormData(),
            this.selectedHolidayType,
            this.selectedRecovery
        );

        if (!validation.isValid) {
            this.exportService.showValidationErrors(validation.errors);
            return false;
        }

        return true;
    }

    /**
     * Export to image format (JPG/PNG)
     *
     * @param {string} format - Image format ('jpg' or 'png')
     */
    async exportToImage(format) {
        if (!this.validate()) {
            return;
        }

        await this.exportService.exportToImage(
            format,
            () => this.getFormData(),
            this.selectedHolidayType,
            this.selectedRecovery
        );
    }

    /**
     * Show element by ID
     *
     * @param {string} elementId - Element ID
     */
    showElement(elementId) {
        const element = document.getElementById(elementId);
        if (element) {
            element.style.display = 'block';
        }
    }

    /**
     * Hide element by ID
     *
     * @param {string} elementId - Element ID
     */
    hideElement(elementId) {
        const element = document.getElementById(elementId);
        if (element) {
            element.style.display = 'none';
        }
    }

    /**
     * Setup all event handlers
     */
    setupEventHandlers() {
        this.setupLogoErrorHandling();
        this.setupModalHandlers();
        this.setupCheckboxHandlers();
        this.setupSignatureHandler();
        this.setupFormHandler();
    }

    /**
     * Setup logo error handling
     */
    setupLogoErrorHandling() {
        const logo = document.getElementById('companyLogo');
        const fallback = document.getElementById('logoFallback');

        if (logo && fallback) {
            logo.addEventListener('error', function() {
                this.style.display = 'none';
                fallback.style.display = 'flex';
            });

            logo.addEventListener('load', function() {
                fallback.style.display = 'none';
                this.style.display = 'block';
            });
        }
    }

    /**
     * Setup modal event handlers
     */
    setupModalHandlers() {
        const modalOverlay = document.getElementById('holidayModal');
        if (!modalOverlay) return;

        // Close modal on overlay click
        modalOverlay.addEventListener('click', this.handleOverlayClick);

        // Close modal on Escape key (global listener)
        document.addEventListener('keydown', this.handleEscapeKey);
    }

    /**
     * Handle modal overlay click
     *
     * @param {Event} e - Click event
     */
    handleOverlayClick(e) {
        if (e.target === e.currentTarget) {
            this.close();
        }
    }

    /**
     * Handle Escape key press
     *
     * @param {KeyboardEvent} e - Keyboard event
     */
    handleEscapeKey(e) {
        if (e.key === 'Escape') {
            const modal = document.getElementById('holidayModal');
            if (modal && modal.classList.contains('active')) {
                this.close();
            }
        }
    }

    /**
     * Setup checkbox interaction handlers
     */
    setupCheckboxHandlers() {
        // Handle checkbox clicks on labels
        document.querySelectorAll('.checkbox-label').forEach(label => {
            label.addEventListener('click', () => {
                const checkbox = label.parentElement.querySelector('.custom-checkbox');
                if (checkbox) {
                    const value = checkbox.getAttribute('data-value');
                    if (value) {
                        this.selectHolidayType(value);
                    }
                }
            });
        });

        // Handle recovery option labels
        document.querySelectorAll('.recovery-label').forEach(label => {
            label.addEventListener('click', () => {
                const text = label.textContent.trim();
                if (text === 'cu recuperare') {
                    this.toggleRecovery('cu');
                } else if (text === 'fără recuperare') {
                    this.toggleRecovery('fara');
                }
            });
        });
    }

    /**
     * Setup signature upload handler
     */
    setupSignatureHandler() {
        const signatureFile = document.getElementById('signatureFile');
        if (signatureFile) {
            signatureFile.addEventListener('change', (e) => this.handleSignatureUpload(e));
        }
    }

    /**
     * Setup form submission prevention
     */
    setupFormHandler() {
        const form = document.getElementById('holidayRequestForm');
        if (form) {
            form.addEventListener('submit', (e) => {
                e.preventDefault();
                console.log('Form submission prevented - use export buttons instead');
            });
        }
    }

    /**
     * Extract user data from current page
     *
     * @returns {Object} User data object
     */
    extractUserDataFromPage() {
        const userData = {};

        // Method 1: Try to get name from user badge (most reliable)
        const userBadgeSpan = document.querySelector('.badge .bi-person + span');
        if (userBadgeSpan && userBadgeSpan.textContent.trim()) {
            userData.name = userBadgeSpan.textContent.trim();
        }

        // Method 2: Try Thymeleaf rendered spans
        if (!userData.name) {
            const thymeleafSpans = document.querySelectorAll('span[th\\:text*="user.name"]');
            thymeleafSpans.forEach(span => {
                if (span.textContent.trim()) {
                    userData.name = span.textContent.trim();
                }
            });
        }

        // Method 3: Look for any badge with person icon
        if (!userData.name) {
            const badges = document.querySelectorAll('.badge');
            badges.forEach(badge => {
                const personIcon = badge.querySelector('.bi-person');
                if (personIcon && badge.textContent.trim()) {
                    const fullText = badge.textContent.trim();
                    const nameMatch = fullText.match(/[A-Za-z].*/);
                    if (nameMatch) {
                        userData.name = nameMatch[0].trim();
                    }
                }
            });
        }

        // Method 4: Try page title or header
        if (!userData.name) {
            const pageHeaders = document.querySelectorAll('h1, h2, h3, .header-title');
            pageHeaders.forEach(header => {
                const text = header.textContent;
                if (text.includes('Time Management') && text.includes('-')) {
                    const parts = text.split('-');
                    if (parts.length > 1) {
                        userData.name = parts[1].trim();
                    }
                }
            });
        }

        // Fallback name for testing
        if (!userData.name) {
            userData.name = 'Test User';
        }

        console.log('Extracted user data:', userData);
        return userData;
    }

    /**
     * Open holiday request from time management form
     * Integration point with time management page
     */
    openFromTimeManagementForm() {
        // Extract user data from current page
        const userData = this.extractUserDataFromPage();

        // Get dates from original form using name selectors to avoid ID conflicts
        const startDateField = document.querySelector('input[name="startDate"]');
        const endDateField = document.querySelector('input[name="endDate"]');

        const startDate = startDateField ? startDateField.value : '';
        const endDate = endDateField ? endDateField.value : '';

        // Check what type is selected in the main form
        const timeOffTypeSelect = document.getElementById('timeOffType');
        const selectedType = timeOffTypeSelect ? timeOffTypeSelect.value : null;

        console.log('Opening holiday modal with:', { startDate, endDate, userData, selectedType });

        // Open the modal
        this.open(startDate, endDate, userData, selectedType);
    }
}

// Export as default for easier importing
export default HolidayRequestModal;
