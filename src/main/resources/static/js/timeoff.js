        document.addEventListener('DOMContentLoaded', function () {
            const form = document.querySelector('form');
            const startDateInput = form.querySelector('input[name="startDate"]');
            const endDateInput = form.querySelector('input[name="endDate"]');
            const singleDayCheckbox = document.getElementById('singleDayRequest');
            const endDateContainer = document.getElementById('endDateContainer');

            // Single day request handling
            singleDayCheckbox.addEventListener('change', function () {
                endDateContainer.style.display = this.checked ? 'none' : 'block';
                if (this.checked) {
                    endDateInput.value = startDateInput.value;
                }
            });

            // Start date changes
            startDateInput.addEventListener('change', function () {
                if (singleDayCheckbox.checked) {
                    endDateInput.value = this.value;
                }
            });
        });