/**
 * Holiday Export Service
 * Handles exporting holiday request forms to various formats (JPG, PNG)
 * Dynamically loads required libraries (html2canvas, jsPDF)
 *
 * @module HolidayExportService
 */

/**
 * HolidayExportService class
 * Provides export functionality for holiday request forms
 */
export class HolidayExportService {
    constructor() {
        this.config = {
            libraries: {
                jsPDF: 'https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js',
                html2canvas: 'https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js'
            },
            a4: {
                width: 794,
                height: 1123
            }
        };

        this.ensureAnimationStyles();
    }

    // ========================================================================
    // LIBRARY LOADING UTILITIES
    // ========================================================================

    /**
     * Dynamically load external JavaScript libraries
     *
     * @param {string} src - Library URL
     * @returns {Promise<void>}
     */
    async loadScript(src) {
        return new Promise((resolve, reject) => {
            // Check if already loaded
            if (document.querySelector(`script[src="${src}"]`)) {
                resolve();
                return;
            }

            const script = document.createElement('script');
            script.src = src;
            script.onload = resolve;
            script.onerror = reject;
            document.head.appendChild(script);
        });
    }

    /**
     * Load required library for specific export type
     *
     * @param {string} libraryName - Library name ('html2canvas' or 'jsPDF')
     * @returns {Promise<void>}
     */
    async loadLibrary(libraryName) {
        const url = this.config.libraries[libraryName];
        if (!url) {
            throw new Error(`Unknown library: ${libraryName}`);
        }

        console.log(`Loading ${libraryName} library...`);
        await this.loadScript(url);

        // Verify library loaded correctly
        await this.verifyLibraryLoaded(libraryName);
        console.log(`${libraryName} library loaded and verified successfully`);
    }

    /**
     * Verify that a library loaded correctly
     *
     * @param {string} libraryName - Library name
     * @returns {Promise<void>}
     */
    async verifyLibraryLoaded(libraryName) {
        const maxAttempts = 10;
        const delay = 100; // ms

        for (let attempt = 0; attempt < maxAttempts; attempt++) {
            let isLoaded = false;

            switch (libraryName) {
                case 'jsPDF':
                    isLoaded = window.jspdf && window.jspdf.jsPDF;
                    break;
                case 'html2canvas':
                    isLoaded = typeof window.html2canvas === 'function';
                    break;
                default:
                    throw new Error(`Unknown library for verification: ${libraryName}`);
            }

            if (isLoaded) {
                console.log(`✅ ${libraryName} verified loaded after ${attempt * delay}ms`);
                return;
            }

            // Wait before next attempt
            await new Promise(resolve => setTimeout(resolve, delay));
        }

        throw new Error(`${libraryName} library failed to load properly after ${maxAttempts * delay}ms`);
    }

    /**
     * Check if export dependencies are available
     */
    checkDependencies() {
        console.log('✅ HolidayExportService initialized successfully');
    }

    // ========================================================================
    // UI UTILITIES
    // ========================================================================

    /**
     * Show/hide loading state on export buttons
     *
     * @param {boolean} disabled - Whether to disable buttons
     */
    setExportButtonsState(disabled) {
        const exportButtons = document.querySelectorAll('.export-btn');
        exportButtons.forEach(btn => btn.disabled = disabled);
    }

    /**
     * Show export success message with animation
     *
     * @param {string} format - Export format
     * @param {string} fileName - Generated file name
     */
    showExportSuccess(format, fileName) {
        const successMsg = document.createElement('div');
        successMsg.className = 'export-success-notification';
        successMsg.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: #28a745;
            color: white;
            padding: 15px 20px;
            border-radius: 8px;
            z-index: 10000;
            box-shadow: 0 4px 20px rgba(0,0,0,0.3);
            animation: slideInRight 0.3s ease;
            max-width: 300px;
        `;

        successMsg.innerHTML = `
            <div style="display: flex; align-items: center; gap: 10px;">
                <div style="font-size: 20px;">✅</div>
                <div>
                    <strong>Export ${format} Reușit!</strong><br>
                    <small style="opacity: 0.9;">${fileName}</small>
                </div>
            </div>
        `;

        document.body.appendChild(successMsg);

        // Remove after 4 seconds with animation
        setTimeout(() => {
            successMsg.style.animation = 'slideOutRight 0.3s ease';
            setTimeout(() => {
                if (successMsg.parentNode) {
                    successMsg.parentNode.removeChild(successMsg);
                }
            }, 300);
        }, 4000);
    }

    /**
     * Show validation errors in a modal-style overlay
     *
     * @param {string[]} errors - Array of error messages
     */
    showValidationErrors(errors) {
        const errorOverlay = document.createElement('div');
        errorOverlay.className = 'validation-error-overlay';
        errorOverlay.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0,0,0,0.5);
            z-index: 10001;
            display: flex;
            align-items: center;
            justify-content: center;
        `;

        const errorModal = document.createElement('div');
        errorModal.style.cssText = `
            background: white;
            padding: 30px;
            border-radius: 12px;
            box-shadow: 0 10px 40px rgba(0,0,0,0.3);
            max-width: 500px;
            width: 90%;
            max-height: 80vh;
            overflow-y: auto;
        `;

        errorModal.innerHTML = `
            <div style="display: flex; align-items: center; gap: 15px; margin-bottom: 20px; color: #dc3545;">
                <div style="font-size: 30px;">⚠️</div>
                <h3 style="margin: 0; color: #dc3545;">Erori de validare</h3>
            </div>
            <div style="background: #f8f9fa; padding: 20px; border-radius: 8px; margin-bottom: 20px;">
                <div style="color: #495057; line-height: 1.8; font-size: 14px;">
                    ${errors.join('<br>')}
                </div>
            </div>
            <div style="text-align: right;">
                <button onclick="this.closest('.validation-error-overlay').remove()" style="
                    padding: 10px 20px;
                    background: #dc3545;
                    color: white;
                    border: none;
                    border-radius: 6px;
                    cursor: pointer;
                    font-weight: 600;
                    transition: background-color 0.2s;
                " onmouseover="this.style.backgroundColor='#c82333'"
                   onmouseout="this.style.backgroundColor='#dc3545'">
                    Închide
                </button>
            </div>
        `;

        errorOverlay.appendChild(errorModal);
        document.body.appendChild(errorOverlay);

        // Close on overlay click
        errorOverlay.addEventListener('click', (e) => {
            if (e.target === errorOverlay) {
                errorOverlay.remove();
            }
        });

        // Auto-remove after 15 seconds
        setTimeout(() => {
            if (errorOverlay.parentNode) {
                errorOverlay.remove();
            }
        }, 15000);
    }

    /**
     * Ensure animation styles are present
     */
    ensureAnimationStyles() {
        if (!document.querySelector('#holidayExportAnimations')) {
            const style = document.createElement('style');
            style.id = 'holidayExportAnimations';
            style.textContent = `
                @keyframes slideInRight {
                    from { transform: translateX(100%); opacity: 0; }
                    to { transform: translateX(0); opacity: 1; }
                }
                @keyframes slideOutRight {
                    from { transform: translateX(0); opacity: 1; }
                    to { transform: translateX(100%); opacity: 0; }
                }
            `;
            document.head.appendChild(style);
        }
    }

    // ========================================================================
    // VALIDATION UTILITIES
    // ========================================================================

    /**
     * Enhanced form validation with detailed error messages
     *
     * @param {Function} formDataGetter - Function that returns form data
     * @param {string} selectedHolidayType - Selected holiday type
     * @param {string} selectedRecovery - Selected recovery option
     * @returns {Object} Validation result {isValid, errors}
     */
    validateForm(formDataGetter, selectedHolidayType, selectedRecovery) {
        const errors = [];
        const formData = formDataGetter();

        // Required fields validation
        if (!formData.employeeName?.trim()) {
            errors.push('• Numele angajatului este obligatoriu');
        }

        if (!formData.jobPosition?.trim()) {
            errors.push('• Postul este obligatoriu');
        }

        if (!formData.workplace?.trim()) {
            errors.push('• Locul de muncă este obligatoriu');
        }

        // Dates validation
        if (!formData.startDate?.trim()) {
            errors.push('• Data de început este obligatorie');
        }

        if (!formData.endDate?.trim()) {
            errors.push('• Data de sfârșit este obligatorie');
        }

        // Holiday type validation
        if (!selectedHolidayType) {
            errors.push('• Vă rugăm să selectați tipul de concediu');
        }

        // Conditional validation based on holiday type
        if (selectedHolidayType === 'special') {
            if (!formData.specialReason?.trim()) {
                errors.push('• Motivul pentru evenimente speciale este obligatoriu');
            }
        }

        if (selectedHolidayType === 'fara_plata') {
            if (!formData.unpaidReason?.trim()) {
                errors.push('• Motivul pentru concediul fără plată este obligatoriu');
            }

            if (!selectedRecovery) {
                errors.push('• Vă rugăm să selectați opțiunea de recuperare (cu/fără)');
            }
        }

        return {
            isValid: errors.length === 0,
            errors: errors
        };
    }

    // ========================================================================
    // IMAGE EXPORT
    // ========================================================================

    /**
     * Export holiday request form to image (JPG/PNG) using hidden DOCX template
     *
     * @param {string} format - Image format ('jpg' or 'png')
     * @param {Function} formDataGetter - Function that returns form data
     * @param {string} selectedHolidayType - Selected holiday type
     * @param {string} selectedRecovery - Selected recovery option
     * @returns {Promise<void>}
     */
    async exportToImage(format, formDataGetter, selectedHolidayType, selectedRecovery) {
        console.log(`Starting ${format.toUpperCase()} export with DOCX template...`);

        try {
            this.setExportButtonsState(true);

            // Validate form
            const validation = this.validateForm(formDataGetter, selectedHolidayType, selectedRecovery);
            if (!validation.isValid) {
                this.showValidationErrors(validation.errors);
                return;
            }

            // Load html2canvas library
            await this.loadLibrary('html2canvas');

            const formData = formDataGetter();

            // Create hidden DOCX-format template
            const template = this.createDOCXTemplate(formData, selectedHolidayType, selectedRecovery);
            document.body.appendChild(template);

            try {
                // Configure capture options for A4 size
                const options = {
                    backgroundColor: '#ffffff',
                    scale: 2,
                    useCORS: true,
                    allowTaint: true,
                    width: 794,  // A4 width in pixels at 96 DPI
                    height: 1123, // A4 height in pixels at 96 DPI
                    scrollX: 0,
                    scrollY: 0
                };

                // Capture the hidden template
                const canvas = await window.html2canvas(template, options);

                // Convert to blob and download
                const quality = format === 'jpg' ? 0.95 : undefined;
                const blob = await new Promise(resolve => {
                    canvas.toBlob(resolve, `image/${format}`, quality);
                });

                const fileName = this.generateFileName(formData.employeeName, formData.startDate, format);
                this.downloadBlob(blob, fileName);

                console.log(`${format.toUpperCase()} export completed successfully`);
                this.showExportSuccess(format.toUpperCase(), fileName);

            } finally {
                // Always remove the hidden template
                if (template.parentNode) {
                    template.parentNode.removeChild(template);
                }
            }

        } catch (error) {
            console.error(`${format.toUpperCase()} export error:`, error);
            alert(`Eroare la exportul ${format.toUpperCase()}: ` + error.message);
        } finally {
            this.setExportButtonsState(false);
        }
    }

    /**
     * Create hidden HTML template for image export - exact layout with proper spacing
     *
     * @param {Object} formData - Form data object
     * @param {string} selectedHolidayType - Selected holiday type
     * @param {string} selectedRecovery - Selected recovery option
     * @returns {HTMLElement} Hidden template element
     */
    createDOCXTemplate(formData, selectedHolidayType, selectedRecovery) {
        const template = document.createElement('div');
        template.style.cssText = `
            position: fixed;
            top: -10000px;
            left: -10000px;
            width: 794px;
            height: 1123px;
            background: white;
            font-family: Arial, sans-serif;
            font-size: 10pt;
            line-height: 1.4;
            padding: 0;
            margin: 0;
            box-sizing: border-box;
        `;

        // Calculate left margin - 40% to the right
        const leftMargin = 120;

        // Handle checkbox display
        const checkbox1 = selectedHolidayType === 'odihna' ? '☑' : '☐';
        const checkbox2 = selectedHolidayType === 'special' ? '☑' : '☐';
        const checkbox3 = selectedHolidayType === 'fara_plata' ? '☑' : '☐';
        const cuRecuperare = selectedRecovery === 'cu' || selectedRecovery === 'CR' ? '☑' : '☐';
        const faraRecuperare = selectedRecovery === 'fara' || selectedRecovery === 'CN' ? '☑' : '☐';

        // Handle reason fields - show dots if empty, show text if filled
        const specialReasonDisplay = (selectedHolidayType === 'special' && formData.specialReason && formData.specialReason.trim())
            ? `<strong>${formData.specialReason}</strong>`
            : '..............................................................';

        const unpaidReasonDisplay = (selectedHolidayType === 'fara_plata' && formData.unpaidReason && formData.unpaidReason.trim())
            ? `<strong>${formData.unpaidReason}</strong>`
            : '..............................................................';

        template.innerHTML = `
<div style="font-family: 'Times New Roman', serif; font-size: 10pt; line-height: 1.4; margin: 0; padding: 0; font-weight: bold;">

    <!-- Logo section -->
    <div style="margin-left: ${leftMargin}px; margin-bottom: 5px;">
        <img src="/images/cerere_concediu_logo.png" alt="LOGO" style="max-height: 50px;"
             onerror="this.style.display='none'; this.nextElementSibling.style.display='block';">
        <div style="display: none; font-weight: bold; font-size: 12pt;">LOGO</div>
    </div>

    <!-- Header line -->
    <div style="margin: 2px ${leftMargin - 20}px 15px ${leftMargin - 20}px; border-bottom: 1px solid black;"></div>

    <div style="height: 60px;"></div>

    <!-- Title -->
    <div style="text-align: center; font-weight: bold; font-size: 12pt; margin-bottom: 24px;">
        Cerere de concediu
    </div>

    <div style="height: 24px;"></div>

    <div style="margin-left: ${leftMargin}px;">

        <!-- Employee name -->
        <div style="margin-bottom: 15px; width: 480px; display: flex; align-items: end; position: relative;">
            <span style="background: white; padding-right: 5px; z-index: 2; position: relative;">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Subsemnatul(a)</span>
            <div style="flex: 1; height: 1px; border-bottom: 1px dotted #000; margin-bottom: 2px;"></div>
            <span style="background: white; padding: 0 5px; z-index: 2; position: relative; font-weight: bold;">${formData.employeeName || ''}</span>
            <div style="flex: 0 0 80px; height: 1px; border-bottom: 1px dotted #000; margin-bottom: 2px;"></div>
        </div>

        <!-- Job position -->
        <div style="margin-bottom: 15px; width: 480px; display: flex; align-items: end; position: relative;">
            <span style="background: white; padding-right: 5px; z-index: 2; position: relative;">angajat/a la societatea dvs. pe postul</span>
            <div style="flex: 1; height: 1px; border-bottom: 1px dotted #000; margin-bottom: 2px;"></div>
            <span style="background: white; padding: 0 5px; z-index: 2; position: relative; font-weight: bold;">${formData.jobPosition || ''}</span>
            <div style="flex: 0 0 80px; height: 1px; border-bottom: 1px dotted #000; margin-bottom: 2px;"></div>
        </div>

        <!-- Workplace -->
        <div style="margin-bottom: 15px; width: 480px; display: flex; align-items: end; position: relative;">
            <span style="background: white; padding-right: 5px; z-index: 2; position: relative;">loc de muncă</span>
            <div style="flex: 1; height: 1px; border-bottom: 1px dotted #000; margin-bottom: 2px;"></div>
            <span style="background: white; padding: 0 5px; z-index: 2; position: relative; font-weight: bold;">${formData.workplace || ''}</span>
            <div style="flex: 1; height: 1px; border-bottom: 1px dotted #000; margin-bottom: 2px;"></div>
            <span style="background: white; padding-left: 5px; z-index: 2; position: relative;">va rog sa-mi aprobati</span>
        </div>

        <!-- Holiday period -->
        <div style="margin-bottom: 15px; width: 480px; display: flex; align-items: end; position: relative;">
            <span style="background: white; padding-right: 5px; z-index: 2; position: relative;">zile de concediu*, în perioada</span>
            <div style="flex: 0 0 30px; height: 1px; border-bottom: 1px dotted #000; margin-bottom: 2px;"></div>
            <span style="background: white; padding: 0 3px; z-index: 2; position: relative; font-weight: bold;">${formData.startDate || ''}</span>
            <span style="background: white; padding: 0 3px; z-index: 2; position: relative;"> --- </span>
            <span style="background: white; padding: 0 3px; z-index: 2; position: relative; font-weight: bold;">${formData.endDate || ''}</span>
            <div style="flex: 1; height: 1px; border-bottom: 1px dotted #000; margin-bottom: 2px;"></div>
        </div>

        <div style="margin-bottom: 15px;">reprezentând**.</div>
        <div style="margin-bottom: 16px;"></div>

        <div style="margin-bottom: 15px;">1. Concediu de odihna&nbsp;&nbsp;&nbsp;&nbsp;${checkbox1}</div>
        <div style="margin-bottom: 20px;"></div>

        <div>2. Concediu pentru evenimente speciale ***&nbsp;&nbsp;&nbsp;&nbsp;${checkbox2}</div>
        <div>&nbsp;&nbsp;&nbsp;(la intoarcerea din concediu se vor anexa actele doveditoare)</div>
        <div style="margin-bottom: 16px;"></div>
        <div style="margin-bottom: 12px;">&nbsp;&nbsp;&nbsp;Motivul:  ${specialReasonDisplay}</div>
        <div style="margin-bottom: 16px;"></div>

        <div>***</div>
        <div>Conf. Regulamentului Intern pentru:</div>
        <div>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;căsătoria salariatului:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;5 zile</div>
        <div>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;căsătoria unui copil:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2 zile</div>
        <div>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;nașterea unui copil:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;10 zile</div>
        <div>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;decesul soțului, copilului, părinți, socri:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;3 zile</div>
        <div>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;decesul bunicilor, frați, surori:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;1 zi</div>
        <div style="margin-bottom: 20px;"></div>

        <div style="margin-bottom: 12px;">3. Concediu fără plată / Învoire&nbsp;&nbsp;&nbsp;&nbsp;${checkbox3}</div>
        <div style="margin-bottom: 16px;"></div>
        <div>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Motivul ${unpaidReasonDisplay}</div>
        <div>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(se va specifica: rezolvarea unor probleme personale)</div>
        <div style="margin-bottom: 16px;"></div>
        <div style="margin-bottom: 12px;">&nbsp;&nbsp;&nbsp;${cuRecuperare} cu recuperare&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;${faraRecuperare} fără recuperare&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Aprobat _____________</div>

        <div style="margin-bottom: 12px;"></div>
        <div style="margin-bottom: 16px;"></div>
        <div style="margin-bottom: 16px;"></div>

        <div>Semnatura,&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Director,</div>
        <div>Angajat,</div>
        <div style="position: relative;">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            ${formData.hasSignature ? `<img src="${formData.signature}" style="margin-left: 20px; max-width:100px; max-height: 50px; vertical-align: top;" alt="Signature">` : ''}
        </div>
        <div style="margin-bottom: 30px;"></div>

        <div style="margin-bottom: 20px;">Sef compartiment,</div>
        <div style="margin-bottom: 60px;"></div>

        <div style="margin-bottom: 5px;">
            <div style="position: left; bottom: 40px; font-size: 7pt;">MB30.01/29.01.04</div>
        </div>
    </div>
</div>
        `;

        return template;
    }

    // ========================================================================
    // UTILITY FUNCTIONS
    // ========================================================================

    /**
     * Generate filename based on employee name and date
     *
     * @param {string} employeeName - Employee name
     * @param {string} startDate - Start date
     * @param {string} extension - File extension
     * @returns {string} Generated filename
     */
    generateFileName(employeeName, startDate, extension) {
        const cleanName = employeeName.replace(/\s+/g, '_').replace(/[^a-zA-Z0-9_]/g, '');
        const cleanDate = startDate.replace(/[^\d]/g, '');
        return `Cerere_Concediu_${cleanName}_${cleanDate}.${extension}`;
    }

    /**
     * Download blob as file
     *
     * @param {Blob} blob - Blob to download
     * @param {string} fileName - File name
     */
    downloadBlob(blob, fileName) {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }
}

// Export as default for easier importing
export default HolidayExportService;
