/**
 * Status Display Module - Handles status indicators and editability checking
 * Manages status tooltips, modals, and row-level status information
 */

const StatusDisplayModule = {

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize status display functionality
     */
    initialize() {
        console.log('Initializing Status Display Module...');

        this.initializeStatusTooltips();
        this.setupStatusModalHandlers();

        console.log('✅ Status Display Module initialized');
    },

    /**
     * Initialize Bootstrap tooltips for status indicators
     */
    initializeStatusTooltips() {
        const statusIndicators = document.querySelectorAll('.status-indicator[data-bs-toggle="tooltip"]');
        statusIndicators.forEach(indicator => {
            if (typeof bootstrap !== 'undefined' && bootstrap.Tooltip) {
                new bootstrap.Tooltip(indicator, {
                    html: true,
                    placement: 'top',
                    trigger: 'hover'
                });
            }
        });
    },

    /**
     * Set up status details modal event handlers
     */
    setupStatusModalHandlers() {
        const statusModal = document.getElementById('statusDetailsModal');
        if (!statusModal) return;

        // Store the element that opened the modal
        let modalTrigger = null;

        // Capture what opened the modal
        statusModal.addEventListener('show.bs.modal', function(event) {
            modalTrigger = event.relatedTarget || document.activeElement;
        });

        // Before hiding the modal, remove focus from ALL elements inside
        statusModal.addEventListener('hide.bs.modal', function() {
            // Remove focus from the modal itself
            if (statusModal === document.activeElement) {
                statusModal.blur();
            }

            // Remove focus from any child elements
            const allFocusable = statusModal.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
            allFocusable.forEach(element => {
                if (element === document.activeElement) {
                    element.blur();
                }
            });

            // Force blur on the modal container
            setTimeout(() => {
                statusModal.blur();
            }, 0);
        });

        // After modal is completely hidden, ensure clean focus state
        statusModal.addEventListener('hidden.bs.modal', function() {
            // Final cleanup - blur modal and all children
            statusModal.blur();
            const allElements = statusModal.querySelectorAll('*');
            allElements.forEach(element => {
                if (element === document.activeElement) {
                    element.blur();
                }
            });

            // Return focus to the original trigger if it exists and is still in DOM
            if (modalTrigger && document.contains(modalTrigger)) {
                modalTrigger.focus();
            }

            modalTrigger = null;
        });

        // Handle clicks outside the modal more gracefully
        statusModal.addEventListener('click', function(event) {
            // If clicking on the backdrop (outside the modal content)
            if (event.target === statusModal) {
                // Remove focus before Bootstrap handles the close
                statusModal.blur();
                const focusedElement = statusModal.querySelector(':focus');
                if (focusedElement) {
                    focusedElement.blur();
                }
            }
        });
    },

    // ========================================================================
    // STATUS INFORMATION EXTRACTION
    // ========================================================================

    /**
     * Get comprehensive status information for a row
     * @param {HTMLElement} row - Table row element
     * @returns {Object|null} Status information object
     */
    getRowStatusInfo(row) {
        if (!row) return null;

        // First try to get status from data attributes (new approach)
        const statusRole = row.dataset.statusRole;
        const statusAction = row.dataset.statusAction;
        const statusDescription = row.dataset.statusDescription;
        const statusModifiable = row.dataset.statusModifiable === 'true';
        const statusFinal = row.dataset.statusFinal === 'true';

        if (statusDescription) {
            console.log('Using data attributes for status:', {
                role: statusRole,
                action: statusAction,
                description: statusDescription,
                modifiable: statusModifiable,
                final: statusFinal
            });

            return {
                roleName: statusRole,
                actionType: statusAction,
                fullDescription: statusDescription,
                isModifiable: statusModifiable,
                isFinal: statusFinal,
                isUserInProcess: statusRole === 'User' && statusAction === 'InProcess',
                tooltipText: statusDescription,
                rawStatus: row.dataset.statusRole + '_' + row.dataset.statusAction
            };
        }

        // Fallback: Extract from status cell DOM (existing code)
        return this._extractStatusFromDOM(row);
    },

    /**
     * Extract status information from DOM elements (fallback method)
     * @private
     */
    _extractStatusFromDOM(row) {
        const statusCell = row.querySelector('.status-cell .status-indicator');
        if (!statusCell) return null;

        const isModifiable = statusCell.querySelector('.modifiable-indicator') !== null;
        const isLocked = statusCell.querySelector('.locked-indicator') !== null;
        const badgeText = statusCell.querySelector('.status-badge')?.textContent?.trim();
        const tooltipText = statusCell.getAttribute('title');

        console.log('Using DOM fallback for status:', {
            badgeText,
            tooltipText,
            isModifiable,
            isLocked
        });

        return {
            isModifiable: isModifiable && !isLocked,
            isFinal: badgeText?.includes('F'),
            isUserInProcess: badgeText?.includes('Active'),
            fullDescription: tooltipText || 'Unknown status',
            tooltipText: tooltipText,
            rawStatus: badgeText
        };
    },

    // ========================================================================
    // STATUS-BASED EDITABILITY
    // ========================================================================

    /**
     * Check if a cell is editable based on status
     * @param {HTMLElement} cell - Cell element to check
     */
    checkStatusBasedEditability(cell) {
        const row = cell.closest('tr');
        if (!row) return;

        // Get status information from the row's data or status cell
        const statusInfo = this.getRowStatusInfo(row);

        if (!statusInfo) {
            console.warn('No status info found for row, allowing edit');
            return;
        }

        console.log(`Checking editability for row: isModifiable=${statusInfo.isModifiable}, status=${statusInfo.rawStatus}`);

        if (!statusInfo.isModifiable) {
            // Mark cell as locked
            cell.classList.add('status-locked');
            cell.setAttribute('title', statusInfo.tooltipText || 'Cannot edit this field');

            // Add row-level styling
            if (statusInfo.isFinal) {
                row.classList.add('status-final');
            } else if (statusInfo.isUserInProcess) {
                row.classList.add('status-in-process');
            }

            console.log(`Cell locked: ${statusInfo.fullDescription}`);
        } else {
            // Remove any previous locks
            cell.classList.remove('status-locked');
            console.log(`Cell editable: ${statusInfo.fullDescription}`);
        }
    },

    /**
     * Validate if field can be edited on special days (SN, CO, CM, W)
     * @param {string} field - Field name
     * @param {string} value - Field value
     * @param {Object} rowData - Row data object
     * @returns {string|null} Error message or null if editable
     */
    validateFieldForSpecialDay(field, value, rowData) {
        if (!rowData || !rowData.timeOffType) {
            return null; // Not a special day, normal validation applies
        }

        switch (field) {
            case 'timeOff':
                if (rowData.timeOffType === 'SN') {
                    return 'Cannot modify time off type for national holidays. Contact admin if changes needed.';
                }
                break;
            case 'startTime':
            case 'endTime':
                if (rowData.timeOffType === 'SN') {
                    // Allow time editing on SN days but warn user
                    if (window.showToast) {
                        window.showToast('Holiday Work Notice',
                            'Editing work time on national holiday. All work time will be counted as overtime.',
                            'warning', { duration: 5000 });
                    }
                }
                return null;
            default:
                return null;
        }

        return null;
    },

    // ========================================================================
    // STATUS DETAILS MODAL
    // ========================================================================

    /**
     * Show detailed status information in modal
     * @param {HTMLElement} statusElement - Status indicator element
     * @param {Event} event - Click event
     */
    showStatusDetails(statusElement, event) {
        event.stopPropagation();

        const modal = new bootstrap.Modal(document.getElementById('statusDetailsModal'));
        const contentDiv = document.getElementById('statusDetailsContent');

        if (!contentDiv) {
            console.error('Status details content div not found');
            return;
        }

        // Extract status information
        const row = statusElement.closest('tr');
        const statusInfo = this.getRowStatusInfo(row);
        const date = row.querySelector('.date-cell')?.textContent?.trim();

        if (!statusInfo) {
            contentDiv.innerHTML = '<p class="text-muted">No status information available</p>';
            modal.show();
            return;
        }

        // Build detailed status content
        let content = this._buildStatusDetailsContent(statusInfo, date);
        contentDiv.innerHTML = content;

        // Show modal with proper focus handling
        this._showModalWithFocusHandling(modal, statusElement);
    },

    /**
     * Build status details content HTML
     * @private
     */
    _buildStatusDetailsContent(statusInfo, date) {
        let content = `
            <div class="status-details-section">
                <div class="status-details-label">
                    <i class="${statusInfo.iconClass || 'bi-info-circle'} status-icon-large"></i>
                    Entry Status
                </div>
                <div class="status-details-value">
                    ${statusInfo.fullDescription || 'Unknown status'}
                </div>
            </div>
        `;

        if (date) {
            content += `
                <div class="status-details-section">
                    <div class="status-details-label">Date</div>
                    <div class="status-details-value">${date}</div>
                </div>
            `;
        }

        content += `
            <div class="status-details-section">
                <div class="status-details-label">Editability</div>
                <div class="status-details-value">
                    ${statusInfo.isModifiable ?
                        '<i class="bi bi-check-circle text-success me-1"></i>Can be modified' :
                        '<i class="bi bi-lock-fill text-danger me-1"></i>Cannot be modified'}
                </div>
            </div>
        `;

        // Add role and action info
        if (statusInfo.roleName) {
            content += `
                <div class="status-details-section">
                    <div class="status-details-label">Role</div>
                    <div class="status-details-value">${statusInfo.roleName}</div>
                </div>
            `;
        }

        if (statusInfo.actionType) {
            content += `
                <div class="status-details-section">
                    <div class="status-details-label">Action</div>
                    <div class="status-details-value">${statusInfo.actionType}</div>
                </div>
            `;
        }

        return content;
    },

    /**
     * Show modal with proper focus handling
     * @private
     */
    _showModalWithFocusHandling(modal, originalElement) {
        modal.show();

        const modalElement = document.getElementById('statusDetailsModal');

        // Remove focus from close button when modal is shown
        modalElement.addEventListener('shown.bs.modal', function() {
            const focusedElement = modalElement.querySelector(':focus');
            if (focusedElement) {
                focusedElement.blur();
            }
        }, { once: true });

        // Handle modal hiding properly
        modalElement.addEventListener('hidden.bs.modal', function() {
            const focusedElement = modalElement.querySelector(':focus');
            if (focusedElement) {
                focusedElement.blur();
            }
            // Return focus to the original status element
            if (originalElement) {
                originalElement.focus();
            }
        }, { once: true });
    },

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Refresh editability status for a cell
     * @param {HTMLElement} cell - Cell to refresh
     */
    refreshCellEditability(cell) {
        // Reset classes
        cell.classList.remove('status-locked');
        cell.removeAttribute('title');

        // Re-check editability
        this.checkStatusBasedEditability(cell);
    },

    /**
     * Refresh editability for all cells in a row
     * @param {HTMLElement} row - Row to refresh
     */
    refreshRowEditability(row) {
        const editableCells = row.querySelectorAll('.editable-cell');
        editableCells.forEach(cell => {
            this.refreshCellEditability(cell);
        });
    },

    /**
     * Get row data for status checking
     * @param {HTMLElement} row - Table row element
     * @returns {Object|null} Row data object
     */
    getRowData(row) {
        if (!row) return null;

        const timeOffCell = row.querySelector('.timeoff-cell');

        // Detect time off type from cell classes or content
        let timeOffType = null;
        if (timeOffCell) {
            if (timeOffCell.querySelector('.sn-work-display, .holiday')) timeOffType = 'SN';
            else if (timeOffCell.querySelector('.co-work-display, .vacation')) timeOffType = 'CO';
            else if (timeOffCell.querySelector('.cm-work-display, .medical')) timeOffType = 'CM';
            else if (timeOffCell.querySelector('.w-work-display, .weekend')) timeOffType = 'W';
        }

        // Get overtime information
        const overtimeMinutes = row.getAttribute('data-overtime-minutes') ||
        (row.querySelector('.overtime-cell') ?
        window.UtilitiesModule?.extractOvertimeMinutes(row.querySelector('.overtime-cell').textContent) : 0);

        return {
            date: row.getAttribute('data-date'),
            timeOffType: timeOffType,
            totalOvertimeMinutes: parseInt(overtimeMinutes) || 0,
            hasWork: overtimeMinutes > 0,
            isSpecialDay: ['SN', 'CO', 'CM', 'W'].includes(timeOffType)
        };
    }
};

// Make showStatusDetails available globally for onclick handlers
window.showStatusDetails = function(statusElement, event) {
    StatusDisplayModule.showStatusDetails(statusElement, event);
};

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = StatusDisplayModule;
}

// Make available globally
window.StatusDisplayModule = StatusDisplayModule;