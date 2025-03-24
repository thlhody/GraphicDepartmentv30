// Add this code at the beginning of your worktime-admin.js file
document.addEventListener('DOMContentLoaded', function() {
    // Check structure of a sample cell
    const sampleCell = document.querySelector('.worktime-cell');
    if (sampleCell) {
        console.log("Sample cell found:", sampleCell);
        const editor = sampleCell.querySelector('.worktime-editor');
        if (editor) {
            console.log("Editor found within cell:", editor);
            console.log("Editor HTML structure:", editor.outerHTML);
        } else {
            console.error("Editor not found within cell!");
            console.log("Cell HTML structure:", sampleCell.innerHTML);
        }
    } else {
        console.error("No worktime-cell found on page!");
    }
});
function showEditor(cell) {
    // First, hide ALL editors, not just the visible ones
    document.querySelectorAll('.worktime-editor').forEach(editor => {
        editor.classList.remove('show');
        editor.style.display = 'none';
    });

    const editor = cell.querySelector('.worktime-editor');
    if (editor) {
        // Position editor above the cell
        const rect = cell.getBoundingClientRect();

        // Use fixed positioning
        editor.style.position = 'fixed';
        editor.style.top = (rect.top - 100) + 'px'; // 100px above the cell
        editor.style.left = rect.left + 'px';

        // Show the editor
        editor.style.display = 'block';
        editor.classList.add('show');
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

function getCurrentViewPeriod() {
    // Get current view's year and month from the select elements
    const yearSelect = document.getElementById('yearSelect');
    const monthSelect = document.getElementById('monthSelect');

    return {
        year: yearSelect.value,
        month: monthSelect.value
    };
}

function submitWorktimeUpdate(cell, value) {
    const userId = cell.dataset.userId;
    const [year, month, day] = cell.dataset.date.split('-');

    // Get the current view period before submitting the update
    const currentViewPeriod = getCurrentViewPeriod();

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

            // Redirect to the same page with the current view period parameters
            window.location.href = `/admin/worktime?year=${currentViewPeriod.year}&month=${currentViewPeriod.month}`;
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