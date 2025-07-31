// ========================================================================
// UPDATED HOLIDAY REQUEST MODAL (holiday-request-modal.js)
// Now uses HolidayExportUtils for clean separation of concerns
// ========================================================================

// Global variables
let holidayModalData = {};
let selectedHolidayType = 'odihna'; // Default selection
let selectedRecovery = null;

/**
 * Open holiday request modal with data from time management form
 */
function openHolidayModal(startDate, endDate, userData = {}) {
    holidayModalData = { startDate, endDate, userData };

    // Populate form with data
    populateHolidayForm();

    // Show modal
    const modal = document.getElementById('holidayModal');
    if (modal) {
        modal.classList.add('active');
        document.body.style.overflow = 'hidden';
    }

    // Set default selection
    selectHolidayType('odihna');
}

/**
 * Close holiday request modal
 */
function closeHolidayModal() {
    const modal = document.getElementById('holidayModal');
    if (modal) {
        modal.classList.remove('active');
        document.body.style.overflow = '';
    }
}

/**
 * Populate form fields with data from time management system
 * FIXED: Uses unique IDs for holiday modal
 */
function populateHolidayForm() {
    const { startDate, endDate, userData } = holidayModalData;

    // Auto-fill employee name
    const employeeNameField = document.getElementById('employeeName');
    if (employeeNameField) {
        employeeNameField.value = userData.name || '';
    }

    // FIXED: Use unique IDs for holiday modal date fields
    const holidayStartField = document.getElementById('holidayStartDate');
    const holidayEndField = document.getElementById('holidayEndDate');

    if (holidayStartField) {
        holidayStartField.value = formatDateForDisplay(startDate) || '';
    }
    if (holidayEndField) {
        holidayEndField.value = formatDateForDisplay(endDate) || '';
    }
}

/**
 * Format date for display (DD.MM.YYYY format)
 */
function formatDateForDisplay(dateString) {
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
 * Handle holiday type selection (square checkboxes with X)
 */
function selectHolidayType(type) {
    // Clear all selections
    document.querySelectorAll('.custom-checkbox').forEach(checkbox => {
        checkbox.classList.remove('selected');
    });

    // Select the clicked one
    const selectedCheckbox = document.querySelector(`[data-value="${type}"]`);
    if (selectedCheckbox) {
        selectedCheckbox.classList.add('selected');
    }

    selectedHolidayType = type;

    // Show/hide conditional sections
    hideAllConditionalSections();

    switch (type) {
        case 'special':
            showElement('specialEventNote');
            showElement('specialEventReason');
            break;
        case 'fara_plata':
            showElement('unpaidLeaveOptions');
            break;
        case 'odihna':
        default:
            // No additional sections for regular holiday
            break;
    }
}

/**
 * Hide all conditional sections
 */
function hideAllConditionalSections() {
    hideElement('specialEventNote');
    hideElement('specialEventReason');
    hideElement('unpaidLeaveOptions');
}

/**
 * Show element by ID
 */
function showElement(elementId) {
    const element = document.getElementById(elementId);
    if (element) {
        element.style.display = 'block';
    }
}

/**
 * Hide element by ID
 */
function hideElement(elementId) {
    const element = document.getElementById(elementId);
    if (element) {
        element.style.display = 'none';
    }
}

/**
 * Handle recovery option selection (cu/fara recuperare)
 */
function toggleRecovery(type) {
    // Clear both selections
    const cuBox = document.getElementById('cuRecuperareBox');
    const faraBox = document.getElementById('faraRecuperareBox');

    if (cuBox) cuBox.classList.remove('selected');
    if (faraBox) faraBox.classList.remove('selected');

    // Select the clicked one
    if (type === 'cu' && cuBox) {
        cuBox.classList.add('selected');
        selectedRecovery = 'cu';
    } else if (type === 'fara' && faraBox) {
        faraBox.classList.add('selected');
        selectedRecovery = 'fara';
    }
}

/**
 * Handle signature file upload
 */
function handleSignatureUpload(event) {
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
    reader.onload = function(e) {
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
 * Helper function to get element value safely
 */
function getElementValue(elementId) {
    const element = document.getElementById(elementId);
    return element ? element.value.trim() : '';
}

/**
 * Get form data for export
 * FIXED: Uses correct unique IDs
 */
function getFormData() {
    const signaturePreview = document.getElementById('signaturePreview');

    return {
        employeeName: getElementValue('employeeName'),
        jobPosition: getElementValue('jobPosition'),
        workplace: getElementValue('workplace'),
        // FIXED: Use unique holiday modal IDs
        startDate: getElementValue('holidayStartDate'),
        endDate: getElementValue('holidayEndDate'),
        holidayType: selectedHolidayType,
        specialReason: getElementValue('motivSpecial'),
        unpaidReason: getElementValue('motivFaraPlata'),
        recovery: selectedRecovery,
        signature: signaturePreview ? signaturePreview.src : '',
        hasSignature: signaturePreview ? !signaturePreview.classList.contains('hidden') : false
    };
}

// ========================================================================
// EXPORT FUNCTIONS - NOW USING HolidayExportUtils
// ========================================================================

/**
 * Export to PDF using utility
 */
function exportToPDF() {
    if (!window.HolidayExportUtils) {
        console.error('HolidayExportUtils not found. Make sure holiday-export-utils.js is loaded.');
        alert('Export utilities not loaded. Please refresh the page.');
        return;
    }

    window.HolidayExportUtils.exportToPDF(
        getFormData,
        selectedHolidayType,
        selectedRecovery
    );
}

/**
 * Export to Image using utility
 */
function exportToImage(format) {
    if (!window.HolidayExportUtils) {
        console.error('HolidayExportUtils not found. Make sure holiday-export-utils.js is loaded.');
        alert('Export utilities not loaded. Please refresh the page.');
        return;
    }

    window.HolidayExportUtils.exportToImage(
        format,
        getFormData,
        selectedHolidayType,
        selectedRecovery
    );
}

// ========================================================================
// VALIDATION - NOW USING HolidayExportUtils
// ========================================================================

/**
 * Validate form using utility (backward compatibility)
 */
function validateForm() {
    if (!window.HolidayExportUtils) {
        console.warn('HolidayExportUtils not found. Using basic validation.');
        return validateFormBasic();
    }

    const validation = window.HolidayExportUtils.validateHolidayForm(
        getFormData,
        selectedHolidayType,
        selectedRecovery
    );

    if (!validation.isValid) {
        window.HolidayExportUtils.showValidationErrors(validation.errors);
        return false;
    }

    return true;
}

/**
 * Basic validation fallback (if utility not loaded)
 */
function validateFormBasic() {
    const errors = [];
    const formData = getFormData();

    if (!formData.employeeName) errors.push('Numele angajatului este obligatoriu.');
    if (!selectedHolidayType) errors.push('Vă rugăm să selectați tipul de concediu.');

    if (selectedHolidayType === 'special' && !formData.specialReason) {
        errors.push('Motivul pentru evenimente speciale este obligatoriu.');
    }

    if (selectedHolidayType === 'fara_plata') {
        if (!formData.unpaidReason) errors.push('Motivul pentru concediul fără plată este obligatoriu.');
        if (!selectedRecovery) errors.push('Vă rugăm să selectați opțiunea de recuperare.');
    }

    if (errors.length > 0) {
        alert('Erori de validare:\n\n' + errors.join('\n'));
        return false;
    }

    return true;
}

// ========================================================================
// INTEGRATION FUNCTIONS
// ========================================================================

/**
 * Demo function to test the modal
 */
function testModal() {
    openHolidayModal('2025-08-10', '2025-08-20', {
        name: 'Popescu Ion'
    });
}

/**
 * FIXED: Test function for integration with time management page
 * Uses name attributes to avoid ID conflicts
 */
function testHolidayModal() {
    // Extract user data from current page
    const userData = extractUserDataFromCurrentPage();

    // FIXED: Get dates from original form using name selectors to avoid ID conflicts
    const startDateField = document.querySelector('input[name="startDate"]');
    const endDateField = document.querySelector('input[name="endDate"]');

    const startDate = startDateField ? startDateField.value : '2025-08-10';
    const endDate = endDateField ? endDateField.value : '2025-08-20';

    console.log('Opening holiday modal with:', { startDate, endDate, userData });

    // Open the modal
    openHolidayModal(startDate, endDate, userData);
}

/**
 * IMPROVED: Extract user data from current page with multiple fallback methods
 */
function extractUserDataFromCurrentPage() {
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
                // Extract text after the icon
                const fullText = badge.textContent.trim();
                // Remove any leading icons or symbols
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

// ========================================================================
// SETUP AND INITIALIZATION
// ========================================================================

/**
 * Setup logo error handling
 */
function setupLogoErrorHandling() {
    const logo = document.getElementById('companyLogo');
    const fallback = document.getElementById('logoFallback');

    if (logo && fallback) {
        logo.addEventListener('error', function() {
            this.style.display = 'none';
            fallback.style.display = 'flex';
        });

        // Also handle successful load to hide fallback
        logo.addEventListener('load', function() {
            fallback.style.display = 'none';
            this.style.display = 'block';
        });
    }
}

/**
 * Setup modal event handlers
 */
function setupModalHandlers() {
    const modalOverlay = document.getElementById('holidayModal');
    if (!modalOverlay) return;

    // Close modal on overlay click
    modalOverlay.addEventListener('click', function(e) {
        if (e.target === this) {
            closeHolidayModal();
        }
    });

    // Close modal on Escape key (global listener)
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') {
            const modal = document.getElementById('holidayModal');
            if (modal && modal.classList.contains('active')) {
                closeHolidayModal();
            }
        }
    });
}

/**
 * Setup checkbox interaction handlers
 */
function setupCheckboxHandlers() {
    // Handle checkbox clicks on labels
    document.querySelectorAll('.checkbox-label').forEach(label => {
        label.addEventListener('click', function() {
            const checkbox = this.parentElement.querySelector('.custom-checkbox');
            if (checkbox) {
                const value = checkbox.getAttribute('data-value');
                if (value) {
                    selectHolidayType(value);
                }
            }
        });
    });

    // Handle recovery option labels
    document.querySelectorAll('.recovery-label').forEach(label => {
        label.addEventListener('click', function() {
            const text = this.textContent.trim();
            if (text === 'cu recuperare') {
                toggleRecovery('cu');
            } else if (text === 'fără recuperare') {
                toggleRecovery('fara');
            }
        });
    });
}

/**
 * Setup signature upload handler
 */
function setupSignatureHandler() {
    const signatureFile = document.getElementById('signatureFile');
    if (signatureFile) {
        signatureFile.addEventListener('change', handleSignatureUpload);
    }
}

/**
 * Setup form submission prevention
 */
function setupFormHandler() {
    const form = document.getElementById('holidayRequestForm');
    if (form) {
        form.addEventListener('submit', function(e) {
            e.preventDefault();
            console.log('Form submission prevented - use export buttons instead');
        });
    }
}

/**
 * Check if export utilities are loaded
 */
function checkExportUtilities() {
    if (!window.HolidayExportUtils) {
        console.warn('⚠️ HolidayExportUtils not found. Export functions may not work properly.');
        console.log('💡 Make sure to include holiday-export-utils.js before holiday-request-modal.js');

        // Show a subtle warning to developers
        if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
            const warningDiv = document.createElement('div');
            warningDiv.style.cssText = `
                position: fixed;
                bottom: 20px;
                left: 20px;
                background: #ffc107;
                color: #212529;
                padding: 10px 15px;
                border-radius: 5px;
                font-size: 12px;
                z-index: 9999;
                max-width: 300px;
                box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            `;
            warningDiv.innerHTML = `
                <strong>Dev Warning:</strong><br>
                HolidayExportUtils not loaded.<br>
                Include holiday-export-utils.js
            `;
            document.body.appendChild(warningDiv);

            // Remove after 10 seconds
            setTimeout(() => {
                if (warningDiv.parentNode) {
                    warningDiv.parentNode.removeChild(warningDiv);
                }
            }, 10000);
        }
    } else {
        console.log('✅ HolidayExportUtils loaded successfully');
    }
}

/**
 * Initialize all event listeners when DOM is loaded
 */
document.addEventListener('DOMContentLoaded', function() {
    console.log('Initializing holiday request modal...');

    // Check if export utilities are available
    checkExportUtilities();

    // Setup all handlers
    setupLogoErrorHandling();
    setupModalHandlers();
    setupCheckboxHandlers();
    setupSignatureHandler();
    setupFormHandler();

    console.log('Holiday request modal initialized successfully');
});

// ========================================================================
// GLOBAL INTEGRATION FUNCTIONS
// ========================================================================

/**
 * Global function to be called from time-management.js
 * This is the main integration point
 */
window.openHolidayRequestModal = openHolidayModal;

/**
 * DEPRECATED: Legacy function for backward compatibility
 * Use extractUserDataFromCurrentPage() instead
 */
function extractUserDataFromPage() {
    console.warn('extractUserDataFromPage() is deprecated, use extractUserDataFromCurrentPage()');
    return extractUserDataFromCurrentPage();
}