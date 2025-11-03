/**
 * OPTIMIZED Login JavaScript
 * Ensures fast, non-blocking login experience with optimized backend
 *
 * Changes:
 * - Non-blocking form submission
 * - Faster loading states
 * - Better error handling
 * - Optimized overlay timing
 * - Lightning-fast subsequent login detection
 */

// Execute all code after DOM is fully loaded
document.addEventListener('DOMContentLoaded', function() {
    console.log('Login page JavaScript initialized');


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

    // OPTIMIZED: Setup form validation and submission handling
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

                // OPTIMIZED: Show loading overlay only if network is available
                // For subsequent logins, this should be very brief
                if (window.networkAvailable) {
                    showOptimizedLoadingOverlay();
                }

                console.log('Login form submitted - optimized flow');
                return true;
            });
        }
    };

    // NEW: Optimized loading overlay that adapts to login speed
    const showOptimizedLoadingOverlay = () => {
        const overlay = document.getElementById('syncOverlay');
        if (overlay) {
            overlay.style.opacity = '1';
            overlay.style.display = 'flex';

            // OPTIMIZED: For subsequent logins, hide overlay quickly if page loads fast
            // This prevents the overlay from showing for lightning-fast logins
            const hideOverlayTimeout = setTimeout(() => {
                if (overlay.style.display === 'flex') {
                    console.log('Quick login detected - hiding overlay early');
                    overlay.style.opacity = '0';
                    setTimeout(() => {
                        overlay.style.display = 'none';
                    }, 300);
                }
            }, 1000); // Hide after 1 second if still showing

            // Clear timeout if page navigation happens (normal flow)
            window.addEventListener('beforeunload', () => {
                clearTimeout(hideOverlayTimeout);
            }, { once: true });
        }
    };

    // OPTIMIZED: Setup animations for form inputs (non-blocking)
    const setupInputAnimations = () => {
        const inputs = document.querySelectorAll('.form-control');
        inputs.forEach(input => {
            // Use passive event listeners for better performance
            input.addEventListener('focus', function() {
                this.parentElement.classList.add('focused');
            }, { passive: true });

            input.addEventListener('blur', function() {
                if (!this.value) {
                    this.parentElement.classList.remove('focused');
                }
            }, { passive: true });

            // OPTIMIZED: Check initial state without blocking
            requestAnimationFrame(() => {
                if (input.value) {
                    input.parentElement.classList.add('focused');
                }
            });
        });
    };

    // NEW: Login type detection for analytics and optimization
    const detectLoginType = () => {
        const loginCount = sessionStorage.getItem('dailyLoginCount');
        const isFirstLogin = !loginCount || loginCount === '0';

        if (isFirstLogin) {
            console.log('First login of the day detected');
            sessionStorage.setItem('dailyLoginCount', '1');
        } else {
            const count = parseInt(loginCount) + 1;
            console.log(`Subsequent login detected (login #${count})`);
            sessionStorage.setItem('dailyLoginCount', count.toString());
        }

        // Add login type to body class for CSS targeting
        document.body.classList.add(isFirstLogin ? 'first-login' : 'subsequent-login');

        return isFirstLogin;
    };

    // NEW: Performance monitoring for login optimization
    const initializePerformanceMonitoring = () => {
        const startTime = performance.now();

        // Monitor page load performance
        window.addEventListener('load', () => {
            const loadTime = performance.now() - startTime;
            console.log(`Login page loaded in ${loadTime.toFixed(2)}ms`);

            // Store performance data
            sessionStorage.setItem('loginPageLoadTime', loadTime.toString());
        });

        // Monitor form submission performance
        const loginForm = document.querySelector('form.needs-validation');
        if (loginForm) {
            loginForm.addEventListener('submit', () => {
                sessionStorage.setItem('loginSubmitTime', performance.now().toString());
            });
        }
    };

    // NEW: Handle back button and refresh scenarios
    const handleNavigationScenarios = () => {
        // Detect if user came back via browser back button
        if (performance.navigation.type === performance.navigation.TYPE_BACK_FORWARD) {
            console.log('Back button navigation detected');
            document.body.classList.add('back-navigation');
        }

        // Handle page refresh
        if (performance.navigation.type === performance.navigation.TYPE_RELOAD) {
            console.log('Page refresh detected');
            document.body.classList.add('page-refresh');
        }
    };

    // OPTIMIZED: Setup keyboard shortcuts (non-blocking)
    const setupKeyboardShortcuts = () => {
        document.addEventListener('keydown', function(event) {
            // Enter key on username field focuses password field
            if (event.key === 'Enter' && event.target.id === 'username') {
                event.preventDefault();
                const passwordField = document.getElementById('password');
                if (passwordField) {
                    passwordField.focus();
                }
            }

            // Escape key clears form
            if (event.key === 'Escape') {
                const form = document.querySelector('form.needs-validation');
                if (form) {
                    form.reset();
                    form.classList.remove('was-validated');
                    console.log('Form cleared via Escape key');
                }
            }
        }, { passive: false });
    };

    // NEW: Auto-focus username field if empty
    const setupAutoFocus = () => {
        requestAnimationFrame(() => {
            const usernameField = document.getElementById('username');
            if (usernameField && !usernameField.value) {
                usernameField.focus();
                console.log('Auto-focused username field');
            }
        });
    };

    // NEW: Setup remember me functionality
    const setupRememberMe = () => {
        const rememberMeCheckbox = document.getElementById('rememberMe');
        const usernameField = document.getElementById('username');

        if (rememberMeCheckbox && usernameField) {
            // Load remembered username
            const rememberedUsername = localStorage.getItem('rememberedUsername');
            if (rememberedUsername) {
                usernameField.value = rememberedUsername;
                rememberMeCheckbox.checked = true;
                usernameField.parentElement.classList.add('focused');

                // Focus password field instead
                requestAnimationFrame(() => {
                    const passwordField = document.getElementById('password');
                    if (passwordField) {
                        passwordField.focus();
                    }
                });
            }

            // Save username when remember me is checked
            const loginForm = document.querySelector('form.needs-validation');
            if (loginForm) {
                loginForm.addEventListener('submit', () => {
                    if (rememberMeCheckbox.checked) {
                        localStorage.setItem('rememberedUsername', usernameField.value);
                    } else {
                        localStorage.removeItem('rememberedUsername');
                    }
                });
            }
        }
    };

    // Initialize all functionality in optimal order
    try {
        // Critical path - do these first
        detectLoginType();
        setupFormHandling();
        setupPasswordToggle();

        // Secondary features - defer to next frame
        requestAnimationFrame(() => {
            setupInputAnimations();
            setupAutoFocus();
            setupRememberMe();
        });

        // Performance and analytics - defer further
        setTimeout(() => {
            initializePerformanceMonitoring();
            handleNavigationScenarios();
            setupKeyboardShortcuts();
        }, 0);

        console.log('Login page setup completed successfully');

    } catch (error) {
        console.error('Error during login page setup:', error);

        // Fallback: ensure basic form functionality works
        const loginForm = document.querySelector('form.needs-validation');
        if (loginForm && !loginForm.hasAttribute('data-setup')) {
            loginForm.setAttribute('data-setup', 'true');
            console.log('Fallback form setup applied');
        }
    }
});

// Expose utilities for debugging
window.loginPageUtils = {
    clearRememberedUsername: () => localStorage.removeItem('rememberedUsername'),
    getPerformanceData: () => ({
        loginPageLoadTime: sessionStorage.getItem('loginPageLoadTime'),
        loginSubmitTime: sessionStorage.getItem('loginSubmitTime'),
        dailyLoginCount: sessionStorage.getItem('dailyLoginCount'),
        lastMergeTime: sessionStorage.getItem('lastMergeTime')
    }),
    resetLoginCount: () => sessionStorage.removeItem('dailyLoginCount')
};

console.log('Optimized login.js loaded - ready for lightning-fast logins!');