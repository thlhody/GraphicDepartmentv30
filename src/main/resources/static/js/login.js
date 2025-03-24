// Execute all code after DOM is fully loaded
document.addEventListener('DOMContentLoaded', function() {
    // Setup password toggle functionality
    const setupPasswordToggle = () => {
        const togglePasswordBtn = document.getElementById('togglePasswordBtn');
        const passwordInput = document.getElementById('password');

        if (togglePasswordBtn && passwordInput) {
            togglePasswordBtn.addEventListener('click', function() {
                const toggleIcon = togglePasswordBtn.querySelector('i');

                if (passwordInput.type === 'password') {
                    passwordInput.type = 'text';
                    toggleIcon.classList.remove('bi-eye');
                    toggleIcon.classList.add('bi-eye-slash');
                } else {
                    passwordInput.type = 'password';
                    toggleIcon.classList.remove('bi-eye-slash');
                    toggleIcon.classList.add('bi-eye');
                }

                console.log('Password visibility toggled');
            });
        }
    };

    // Setup form validation and submission handling
    const setupFormHandling = () => {
        const loginForm = document.querySelector('form.needs-validation');
        if (loginForm) {
            loginForm.addEventListener('submit', function(event) {
                if (!this.checkValidity()) {
                    event.preventDefault();
                    event.stopPropagation();
                    this.classList.add('was-validated');

                    // Shake invalid inputs
                    const invalidInputs = document.querySelectorAll('.form-control:invalid');
                    invalidInputs.forEach(input => {
                        input.parentElement.classList.add('shake-animation');
                        setTimeout(() => {
                            input.parentElement.classList.remove('shake-animation');
                        }, 500);
                    });

                    return false;
                }

                if (window.networkAvailable) {
                    const overlay = document.getElementById('syncOverlay');
                    if (overlay) {
                        overlay.style.opacity = '1';
                        overlay.style.display = 'flex';
                    }
                }

                return true;
            });
        }
    };

    // Setup animations for form inputs
    const setupInputAnimations = () => {
        const inputs = document.querySelectorAll('.form-control');
        inputs.forEach(input => {
            input.addEventListener('focus', function() {
                this.parentElement.classList.add('input-focus');
            });

            input.addEventListener('blur', function() {
                this.parentElement.classList.remove('input-focus');
            });
        });
    };

    // Setup parallax effect for background shapes
    const setupParallaxEffect = () => {
        document.addEventListener('mousemove', function(e) {
            const shapes = document.querySelectorAll('.shape');
            const mouseX = e.clientX / window.innerWidth;
            const mouseY = e.clientY / window.innerHeight;

            shapes.forEach((shape, index) => {
                const speed = (index + 1) * 20;
                const x = mouseX * speed;
                const y = mouseY * speed;

                shape.style.transform = `translate(${x}px, ${y}px)`;
            });
        });
    };

    // Initialize all login page functionality
    const initLoginPage = () => {
        setupPasswordToggle();
        setupFormHandling();
        setupInputAnimations();
        setupParallaxEffect();

        console.log('Login page initialized');
    };

    // Execute initialization
    initLoginPage();
});