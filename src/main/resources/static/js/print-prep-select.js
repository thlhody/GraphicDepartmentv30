/**
 * PrintPrepTypes Select2 Keyboard Enhancement
 * This script specifically handles keyboard navigation for the Print Prep Types dropdown
 */

(function() {
    // Wait until DOM is fully loaded
    document.addEventListener('DOMContentLoaded', function() {
        // Wait a bit to make sure Select2 is initialized
        setTimeout(initializePrintPrepSelect, 300);
    });

    function initializePrintPrepSelect() {
        // Target specifically the print prep types dropdown
        const printPrepSelect = document.getElementById('printPrepTypeSelect');

        if (!printPrepSelect) {
            console.log('Print Prep Types select not found');
            return;
        }

        console.log('Initializing Print Prep Types Select2 enhancement');

        // Initialize Select2 with optimized keyboard navigation
        $(printPrepSelect).select2({
            theme: 'bootstrap-5',
            width: '100%',
            placeholder: 'Select',
            multiple: true,
            maximumSelectionLength: 10,
            dropdownParent: $('body'),
            minimumResultsForSearch: 0, // Always show search
            tags: false,
            selectOnClose: false, // Don't auto-select on close
            closeOnSelect: false, // Don't close after selection

            // Custom formatting of selection with first letters
            templateSelection: function(data) {
                // Get all selected items
                const selectedItems = $(printPrepSelect).select2('data');

                if (!selectedItems || selectedItems.length === 0) {
                    return $('<span class="select2-placeholder">Select</span>');
                }

                if (selectedItems.length === 1) {
                    // If only 1 item, show it normally but shortened if needed
                    const text = data.text.length > 7 ? data.text.substring(0, 7) + '...' : data.text;
                    return $(`<span class="select2-single-selection">${text}</span>`);
                }

                // For multiple selections, show first letters
                const initials = selectedItems.map(item => item.text.charAt(0).toUpperCase()).join('');

                // Limit to 7 characters plus counter
                const displayInitials = initials.length > 7 ? initials.substring(0, 7) + '...' : initials;

                return $(`<span class="select2-initials">${displayInitials} <span class="select2-selection__pill-count">${selectedItems.length}</span></span>`);
            },

            // Clean dropdown formatting
            templateResult: function(data) {
                if (!data.id) return data.text;
                return $(`<span>${data.text}</span>`);
            }
        });

        // Fix dropdown positioning
        $(printPrepSelect).on('select2:opening', function() {
            $(this).closest('.print-prep-container').css('z-index', 1055);
        });

        $(printPrepSelect).on('select2:closing', function() {
            setTimeout(function() {
                $('.print-prep-container').css('z-index', '');
            }, 100);
        });

        // Direct keyboard event handling for the search field
        document.addEventListener('keydown', function(event) {
            // Only target the search field in the print prep dropdown
            if (!$(event.target).hasClass('select2-search__field')) {
                return;
            }

            console.log('Key pressed in Print Prep search field:', event.key);

            // For letter keys, let default filtering behavior happen
            if (event.key.length === 1 || event.key === 'Backspace' || event.key === 'Delete') {
                return;
            }

            // Handle Enter key to select highlighted option
            if (event.key === 'Enter') {
                const highlighted = document.querySelector('.select2-results__option--highlighted');
                if (highlighted) {
                    console.log('Enter pressed - selecting option');
                    event.preventDefault();
                    event.stopPropagation();
                    highlighted.click();
                }
            }

            // Let arrow keys work normally
            if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(event.key)) {
                return true;
            }
        }, true); // Use capture phase to ensure our handler runs first

        // Focus search field when dropdown opens
        $(document).on('select2:open', function() {
            setTimeout(function() {
                const searchField = document.querySelector('.select2-search__field');
                if (searchField) {
                    searchField.focus();
                    console.log('Search field focused');
                }
            }, 50);
        });

        console.log('Print Prep Types Select2 enhancement initialized');
    }
})();