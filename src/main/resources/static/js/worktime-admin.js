  function showEditor(cell) {
                // Hide any other open editors
                document.querySelectorAll('.worktime-editor.show').forEach(editor => {
                    editor.classList.remove('show');
                });

                const editor = cell.querySelector('.worktime-editor');
                if (editor) {
                    // Get cell position
                    const rect = cell.getBoundingClientRect();
                    const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
                    const scrollLeft = window.pageXOffset || document.documentElement.scrollLeft;

                    // Position editor below the cell
                    editor.style.top = (rect.bottom + scrollTop) + 'px';
                    editor.style.left = (rect.left + scrollLeft) + 'px';

                    // Show the editor
                    editor.classList.add('show');

                    // Ensure editor stays in viewport
                    const editorRect = editor.getBoundingClientRect();
                    const viewportWidth = window.innerWidth;
                    const viewportHeight = window.innerHeight;

                    // Adjust horizontal position if needed
                    if (editorRect.right > viewportWidth) {
                        editor.style.left = (viewportWidth - editorRect.width - 10) + 'px';
                    }

                    // Adjust vertical position if needed
                    if (editorRect.bottom > viewportHeight) {
                        editor.style.top = (rect.top + scrollTop - editorRect.height) + 'px';
                    }
                }
            }

            function setWorktime(btn, value) {
                event.stopPropagation(); // Prevent event bubbling
                const cell = btn.closest('.worktime-cell');
                submitWorktimeUpdate(cell, value);
            }

            function saveWorktime(btn) {
                event.stopPropagation(); // Prevent event bubbling
                const input = btn.previousElementSibling;
                const value = input.value.trim();
                if (value) {
                    const cell = btn.closest('.worktime-cell');
                    submitWorktimeUpdate(cell, value);
                }
            }

            function submitWorktimeUpdate(cell, value) {
                const userId = cell.dataset.userId;
                const [year, month, day] = cell.dataset.date.split('-');

                console.log('Submitting update:', { userId, year, month, day, value });

                fetch('/admin/worktime/update', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded'
                    },
                    body: new URLSearchParams({
                        userId: userId,
                        year: year,
                        month: month,
                        day: day,
                        value: value
                    })
                })
                    .then(response => {
                    if (response.ok) {
                        console.log('Update successful');
                        location.reload();
                    } else {
                        console.error('Update failed');
                        response.text().then(text => {
                            alert('Failed to update worktime: ' + text);
                        });
                    }
                })
                    .catch(error => {
                    console.error('Error:', error);
                    alert('Failed to update worktime');
                });
            }
            // Close editor when clicking outside
            document.addEventListener('click', function(event) {
                if (!event.target.closest('.worktime-cell')) {
                    document.querySelectorAll('.worktime-editor.show').forEach(editor => {
                        editor.classList.remove('show');
                    });
                }
            });