/**
 * LoginManager.js
 *
 * OPTIMIZED Login JavaScript
 * Ensures fast, non-blocking login experience with optimized backend.
 * Features: password toggle, form validation, remember me, keyboard shortcuts,
 * performance monitoring, and optimized loading overlay.
 *
 * @module features/login/LoginManager
 */

/**
 * LoginManager class
 * Manages login page functionality
 */
export class LoginManager {
    constructor() {
        this.loginForm = null;
        this.passwordInput = null;
        this.usernameInput = null;
        this.isFirstLogin = false;

        console.log('LoginManager initialized');
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize login manager
     */
    initialize() {
        console.log('🚀 Initializing Login Manager...');

        try {
            // Critical path - do these first
            this.isFirstLogin = this.detectLoginType();
            this.setupFormHandling();
            this.setupPasswordToggle();

            // Secondary features - defer to next frame
            requestAnimationFrame(() => {
                this.setupInputAnimations();
                this.setupAutoFocus();
                this.setupRememberMe();
            });

            // Performance and analytics - defer further
            setTimeout(() => {
                this.initializePerformanceMonitoring();
                this.handleNavigationScenarios();
                this.setupKeyboardShortcuts();
            }, 0);

            console.log('✅ Login Manager initialized successfully');

        } catch (error) {
            console.error('❌ Error during login page setup:', error);

            // Fallback: ensure basic form functionality works
            const fallbackForm = document.querySelector('form.needs-validation');
            if (fallbackForm && !fallbackForm.hasAttribute('data-setup')) {
                fallbackForm.setAttribute('data-setup', 'true');
                console.log('Fallback form setup applied');
            }
        }
    }

    // ========================================================================
    // PASSWORD TOGGLE
    // ========================================================================

    /**
     * Setup password toggle functionality
     */
    setupPasswordToggle() {
        const togglePasswordBtn = document.getElementById('togglePasswordBtn');
        this.passwordInput = document.getElementById('password');

        if (togglePasswordBtn && this.passwordInput) {
            togglePasswordBtn.addEventListener('click', () => {
                const toggleIcon = togglePasswordBtn.querySelector('i');

                if (this.passwordInput.type === 'password') {
                    this.passwordInput.type = 'text';
                    toggleIcon.classList.remove('bi-eye');
                    toggleIcon.classList.add('bi-eye-slash');
                } else {
                    this.passwordInput.type = 'password';
                    toggleIcon.classList.remove('bi-eye-slash');
                    toggleIcon.classList.add('bi-eye');
                }

                console.log('Password visibility toggled');
            });
        }
    }

    // ========================================================================
    // FORM HANDLING
    // ========================================================================

    /**
     * Setup form validation and submission handling
     */
    setupFormHandling() {
        this.loginForm = document.querySelector('form.needs-validation');
        if (this.loginForm) {
            this.loginForm.addEventListener('submit', (event) => {
                if (!this.loginForm.checkValidity()) {
                    event.preventDefault();
                    event.stopPropagation();
                    this.loginForm.classList.add('was-validated');

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

                // Show loading overlay only if network is available
                // For subsequent logins, this should be very brief
                if (window.networkAvailable) {
                    this.showOptimizedLoadingOverlay();
                }

                console.log('Login form submitted - optimized flow');
                return true;
            });
        }
    }

    /**
     * Show optimized loading overlay that adapts to login speed
     */
    showOptimizedLoadingOverlay() {
        const overlay = document.getElementById('syncOverlay');
        if (overlay) {
            overlay.style.opacity = '1';
            overlay.style.display = 'flex';

            // For subsequent logins, hide overlay quickly if page loads fast
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
    }

    // ========================================================================
    // INPUT ANIMATIONS
    // ========================================================================

    /**
     * Setup animations for form inputs
     */
    setupInputAnimations() {
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

            // Check initial state without blocking
            requestAnimationFrame(() => {
                if (input.value) {
                    input.parentElement.classList.add('focused');
                }
            });
        });
    }

    // ========================================================================
    // LOGIN TYPE DETECTION
    // ========================================================================

    /**
     * Detect login type for analytics and optimization
     * @returns {boolean} True if first login of the day
     */
    detectLoginType() {
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
    }

    // ========================================================================
    // PERFORMANCE MONITORING
    // ========================================================================

    /**
     * Initialize performance monitoring
     */
    initializePerformanceMonitoring() {
        const startTime = performance.now();

        // Monitor page load performance
        window.addEventListener('load', () => {
            const loadTime = performance.now() - startTime;
            console.log(`Login page loaded in ${loadTime.toFixed(2)}ms`);

            // Store performance data
            sessionStorage.setItem('loginPageLoadTime', loadTime.toString());
        });

        // Monitor form submission performance
        if (this.loginForm) {
            this.loginForm.addEventListener('submit', () => {
                sessionStorage.setItem('loginSubmitTime', performance.now().toString());
            });
        }
    }

    // ========================================================================
    // NAVIGATION SCENARIOS
    // ========================================================================

    /**
     * Handle back button and refresh scenarios
     */
    handleNavigationScenarios() {
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
    }

    // ========================================================================
    // KEYBOARD SHORTCUTS
    // ========================================================================

    /**
     * Setup keyboard shortcuts
     */
    setupKeyboardShortcuts() {
        document.addEventListener('keydown', (event) => {
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
    }

    // ========================================================================
    // AUTO-FOCUS
    // ========================================================================

    /**
     * Auto-focus username field if empty
     */
    setupAutoFocus() {
        requestAnimationFrame(() => {
            this.usernameInput = document.getElementById('username');
            if (this.usernameInput && !this.usernameInput.value) {
                this.usernameInput.focus();
                console.log('Auto-focused username field');
            }
        });
    }

    // ========================================================================
    // REMEMBER ME
    // ========================================================================

    /**
     * Setup remember me functionality
     */
    setupRememberMe() {
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
            if (this.loginForm) {
                this.loginForm.addEventListener('submit', () => {
                    if (rememberMeCheckbox.checked) {
                        localStorage.setItem('rememberedUsername', usernameField.value);
                    } else {
                        localStorage.removeItem('rememberedUsername');
                    }
                });
            }
        }
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Get performance data
     * @returns {Object} Performance data
     */
    static getPerformanceData() {
        return {
            loginPageLoadTime: sessionStorage.getItem('loginPageLoadTime'),
            loginSubmitTime: sessionStorage.getItem('loginSubmitTime'),
            dailyLoginCount: sessionStorage.getItem('dailyLoginCount'),
            lastMergeTime: sessionStorage.getItem('lastMergeTime')
        };
    }

    /**
     * Clear remembered username
     */
    static clearRememberedUsername() {
        localStorage.removeItem('rememberedUsername');
        console.log('Remembered username cleared');
    }

    /**
     * Reset login count
     */
    static resetLoginCount() {
        sessionStorage.removeItem('dailyLoginCount');
        console.log('Login count reset');
    }
}
