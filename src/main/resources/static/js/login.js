        function togglePassword() {
            const passwordInput = document.getElementById('password');
            const toggleButton = passwordInput.nextElementSibling;
            const toggleIcon = toggleButton.querySelector('i');

            if (passwordInput.type === 'password') {
                passwordInput.type = 'text';
                toggleIcon.classList.replace('bi-eye', 'bi-eye-slash');
            } else {
                passwordInput.type = 'password';
                toggleIcon.classList.replace('bi-eye-slash', 'bi-eye');
            }
        }

        const networkAvailable = /*[[${networkAvailable}]]*/ false;

        function handleLogin(event) {
            const form = event.target;
            if (!form.checkValidity()) {
                event.preventDefault();
                event.stopPropagation();
                form.classList.add('was-validated');
                return false;
            }

            if (networkAvailable) {
                const overlay = document.getElementById('syncOverlay');
                overlay.style.opacity = '1';
                overlay.style.display = 'flex';
            }

            return true;
        }