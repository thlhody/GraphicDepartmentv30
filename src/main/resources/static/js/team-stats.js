 // Initialize Select2
        document.addEventListener('DOMContentLoaded', function() {
            $('.select2-users').select2({
                theme: 'bootstrap-5',
                width: '100%',
                placeholder: 'Select team members',
                allowClear: true
            });
            // For debugging - log when selection changes
            $('.select2-users').on('change', function() {
                console.log('Selected users:', $(this).val());
            });
        });

        // Initialize Team Members
        function initializeMembers() {
            const selectedUsers = $('.select2-users').val();

            // Check if users are selected
            if (!selectedUsers || selectedUsers.length === 0) {
                // Show an alert or notification that users must be selected
                alert('Please select at least one team member before initializing.');
                return;
            }

            const form = createForm('/user/stats/initialize');
            submitForm(form);
        }

        // Update Statistics
        function updateStats() {
            const form = createForm('/user/stats/update');
            submitForm(form);
        }

        // Helper to create and submit form
        // Helper to create and submit form
        function createForm(action) {
            const form = document.createElement('form');
            form.method = 'post';
            form.action = action;

            // Add year and month
            const year = document.getElementById('yearSelect').value;

            // Get month as a number (1-12)
            let month;
            // If using a month select dropdown
            const monthSelect = document.getElementById('monthSelect');
            if (monthSelect) {
                month = monthSelect.value;
            }
            // If using button group, find the active button and extract its month number
            else {
                const activeButton = document.querySelector('.btn-group .active');
                if (activeButton) {
                    month = activeButton.getAttribute('data-month');
                    // Extract the month number from the href attribute which should contain month={number}
                    const href = activeButton.getAttribute('href');
                    const monthMatch = href.match(/month=(\d+)/);
                    if (monthMatch && monthMatch[1]) {
                        month = monthMatch[1];
                    } else {
                        // Fallback to current month
                        month = new Date().getMonth() + 1;
                    }
                } else {
                    // Fallback to current month
                    month = new Date().getMonth() + 1;
                }
            }

            appendInput(form, 'year', year);
            appendInput(form, 'month', month);

            // Add selected users if initializing
            if (action.includes('initialize')) {
                const selectedUsers = $('.select2-users').val();
                selectedUsers.forEach(userId => {
                    appendInput(form, 'selectedUsers', userId);
                });
            }

            // Add CSRF token
            const token = document.querySelector("meta[name='_csrf']")?.getAttribute("content");
            const header = document.querySelector("meta[name='_csrf_header']")?.getAttribute("content");
            if (token && header) {
                appendInput(form, header, token);
            }

            return form;
        }

        function appendInput(form, name, value) {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = name;
            input.value = value;
            form.appendChild(input);
        }

        function submitForm(form) {
            document.body.appendChild(form);
            form.submit();
        }