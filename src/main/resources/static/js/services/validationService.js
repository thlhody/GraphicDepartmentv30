/**
 * ValidationService - Form validation utilities
 *
 * This service provides common validation rules and utilities for form
 * validation. It consolidates validation patterns from multiple legacy files
 * and provides a consistent, reusable validation API.
 *
 * @module services/validationService
 * @version 1.0.0
 * @since 2025-11-05
 *
 * Features:
 * - Common validation rules (required, email, number, etc.)
 * - Custom validators
 * - Batch validation
 * - Error message customization
 * - Conditional validation
 * - Array validation
 * - Date validation
 * - Pattern matching
 *
 * Usage:
 *   import { ValidationService } from './services/validationService.js';
 *
 *   const errors = ValidationService.validate({
 *       email: { value: 'user@example.com', rules: ['required', 'email'] },
 *       age: { value: 25, rules: ['required', 'number', 'min:18'] }
 *   });
 */

/**
 * ValidationService class
 * Provides static validation methods
 */
export class ValidationService {
    /**
     * Built-in validation rules
     * @private
     */
    static #rules = {
        /**
         * Required field
         */
        required: {
            validate: (value) => {
                if (value === null || value === undefined) return false;
                if (typeof value === 'string') return value.trim().length > 0;
                if (Array.isArray(value)) return value.length > 0;
                if (typeof value === 'number') return !isNaN(value);
                return !!value;
            },
            message: 'This field is required.'
        },

        /**
         * Email validation
         */
        email: {
            validate: (value) => {
                if (!value) return true; // Use with 'required' for mandatory emails
                const regex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
                return regex.test(value);
            },
            message: 'Please enter a valid email address.'
        },

        /**
         * Number validation
         */
        number: {
            validate: (value) => {
                if (!value && value !== 0) return true;
                return !isNaN(parseFloat(value)) && isFinite(value);
            },
            message: 'Please enter a valid number.'
        },

        /**
         * Integer validation
         */
        integer: {
            validate: (value) => {
                if (!value && value !== 0) return true;
                return Number.isInteger(Number(value));
            },
            message: 'Please enter a valid integer.'
        },

        /**
         * URL validation
         */
        url: {
            validate: (value) => {
                if (!value) return true;
                try {
                    new URL(value);
                    return true;
                } catch {
                    return false;
                }
            },
            message: 'Please enter a valid URL.'
        },

        /**
         * Phone number validation (basic)
         */
        phone: {
            validate: (value) => {
                if (!value) return true;
                const regex = /^[\d\s\-\+\(\)]{10,}$/;
                return regex.test(value);
            },
            message: 'Please enter a valid phone number.'
        },

        /**
         * Date validation
         */
        date: {
            validate: (value) => {
                if (!value) return true;
                const date = new Date(value);
                return date instanceof Date && !isNaN(date);
            },
            message: 'Please enter a valid date.'
        },

        /**
         * Alphabetic characters only
         */
        alpha: {
            validate: (value) => {
                if (!value) return true;
                return /^[a-zA-Z]+$/.test(value);
            },
            message: 'Please use only alphabetic characters.'
        },

        /**
         * Alphanumeric characters only
         */
        alphanumeric: {
            validate: (value) => {
                if (!value) return true;
                return /^[a-zA-Z0-9]+$/.test(value);
            },
            message: 'Please use only alphanumeric characters.'
        }
    };

    /**
     * Custom validators registered by user
     * @private
     */
    static #customRules = {};

    // =========================================================================
    // VALIDATION METHODS
    // =========================================================================

    /**
     * Validate a single field
     * @param {any} value - Field value
     * @param {Array|string} rules - Validation rules
     * @param {string} fieldName - Field name (for error messages)
     * @returns {string|null} Error message or null if valid
     *
     * @example
     *   const error = ValidationService.validateField('john@example.com', ['required', 'email']);
     *   const error = ValidationService.validateField(15, ['required', 'number', 'min:18']);
     */
    static validateField(value, rules, fieldName = 'Field') {
        if (typeof rules === 'string') {
            rules = [rules];
        }

        for (const rule of rules) {
            const error = this.#applyRule(value, rule, fieldName);
            if (error) return error;
        }

        return null;
    }

    /**
     * Validate multiple fields
     * @param {Object} fields - Object with field definitions
     * @returns {Object} Object with field names as keys and error messages as values
     *
     * @example
     *   const errors = ValidationService.validate({
     *       email: { value: 'user@example.com', rules: ['required', 'email'] },
     *       age: { value: 25, rules: ['required', 'number', 'min:18'] },
     *       password: { value: 'secret', rules: ['required', 'minLength:8'] }
     *   });
     *   // Returns: { email: 'Error message', age: null, ... }
     */
    static validate(fields) {
        const errors = {};

        for (const [fieldName, config] of Object.entries(fields)) {
            const { value, rules, label } = config;
            const displayName = label || fieldName;

            const error = this.validateField(value, rules, displayName);
            if (error) {
                errors[fieldName] = error;
            }
        }

        return errors;
    }

    /**
     * Check if validation has errors
     * @param {Object} errors - Errors object from validate()
     * @returns {boolean} True if there are errors
     */
    static hasErrors(errors) {
        return Object.keys(errors).length > 0;
    }

    // =========================================================================
    // RULE APPLICATION
    // =========================================================================

    /**
     * Apply a single validation rule
     * @private
     */
    static #applyRule(value, rule, fieldName) {
        // Parse rule (e.g., "min:5" -> { name: "min", param: "5" })
        const { name, param } = this.#parseRule(rule);

        // Get rule definition
        const ruleDefinition = this.#rules[name] || this.#customRules[name];

        if (!ruleDefinition) {
            // Try parametric rules
            return this.#applyParametricRule(value, name, param, fieldName);
        }

        // Apply rule
        const isValid = ruleDefinition.validate(value, param);

        if (!isValid) {
            // Get error message
            let message = ruleDefinition.message;

            // Replace placeholders
            message = message.replace(':field', fieldName);
            message = message.replace(':param', param);

            return message;
        }

        return null;
    }

    /**
     * Parse rule string
     * @private
     */
    static #parseRule(rule) {
        if (typeof rule !== 'string') {
            return { name: rule, param: null };
        }

        const parts = rule.split(':');
        return {
            name: parts[0],
            param: parts[1] || null
        };
    }

    /**
     * Apply parametric rules (min, max, minLength, etc.)
     * @private
     */
    static #applyParametricRule(value, name, param, fieldName) {
        switch (name) {
            case 'min':
                if (value !== null && value !== undefined && Number(value) < Number(param)) {
                    return `${fieldName} must be at least ${param}.`;
                }
                break;

            case 'max':
                if (value !== null && value !== undefined && Number(value) > Number(param)) {
                    return `${fieldName} must be no more than ${param}.`;
                }
                break;

            case 'minLength':
                if (value && value.length < Number(param)) {
                    return `${fieldName} must be at least ${param} characters.`;
                }
                break;

            case 'maxLength':
                if (value && value.length > Number(param)) {
                    return `${fieldName} must be no more than ${param} characters.`;
                }
                break;

            case 'length':
                if (value && value.length !== Number(param)) {
                    return `${fieldName} must be exactly ${param} characters.`;
                }
                break;

            case 'pattern':
                if (value && !new RegExp(param).test(value)) {
                    return `${fieldName} format is invalid.`;
                }
                break;

            case 'in':
                const options = param.split(',');
                if (value && !options.includes(value)) {
                    return `${fieldName} must be one of: ${options.join(', ')}.`;
                }
                break;

            case 'notIn':
                const forbidden = param.split(',');
                if (value && forbidden.includes(value)) {
                    return `${fieldName} cannot be one of: ${forbidden.join(', ')}.`;
                }
                break;

            case 'between':
                const [min, max] = param.split(',').map(Number);
                const numValue = Number(value);
                if (value !== null && value !== undefined && (numValue < min || numValue > max)) {
                    return `${fieldName} must be between ${min} and ${max}.`;
                }
                break;

            default:
                console.warn(`Unknown validation rule: ${name}`);
        }

        return null;
    }

    // =========================================================================
    // CUSTOM RULES
    // =========================================================================

    /**
     * Register a custom validation rule
     * @param {string} name - Rule name
     * @param {Function} validate - Validation function (value, param) => boolean
     * @param {string} message - Error message
     *
     * @example
     *   ValidationService.addRule('strongPassword', (value) => {
     *       return /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/.test(value);
     *   }, 'Password must contain uppercase, lowercase, and numbers.');
     */
    static addRule(name, validate, message) {
        this.#customRules[name] = { validate, message };
    }

    /**
     * Remove a custom validation rule
     * @param {string} name - Rule name
     */
    static removeRule(name) {
        delete this.#customRules[name];
    }

    /**
     * Check if rule exists
     * @param {string} name - Rule name
     * @returns {boolean} True if rule exists
     */
    static hasRule(name) {
        return !!(this.#rules[name] || this.#customRules[name]);
    }

    // =========================================================================
    // CONDITIONAL VALIDATION
    // =========================================================================

    /**
     * Validate field conditionally
     * @param {any} value - Field value
     * @param {Array|string} rules - Validation rules
     * @param {Function} condition - Condition function () => boolean
     * @param {string} fieldName - Field name
     * @returns {string|null} Error message or null
     *
     * @example
     *   const error = ValidationService.validateIf(
     *       phoneValue,
     *       ['required', 'phone'],
     *       () => contactMethod === 'phone',
     *       'Phone'
     *   );
     */
    static validateIf(value, rules, condition, fieldName = 'Field') {
        if (condition()) {
            return this.validateField(value, rules, fieldName);
        }
        return null;
    }

    // =========================================================================
    // BATCH VALIDATION HELPERS
    // =========================================================================

    /**
     * Validate required fields
     * @param {Object} fields - Object with field names and values
     * @returns {Object} Errors object
     *
     * @example
     *   const errors = ValidationService.validateRequired({
     *       name: document.getElementById('name').value,
     *       email: document.getElementById('email').value
     *   });
     */
    static validateRequired(fields) {
        const config = {};

        for (const [fieldName, value] of Object.entries(fields)) {
            config[fieldName] = { value, rules: ['required'] };
        }

        return this.validate(config);
    }

    /**
     * Validate form element
     * @param {HTMLFormElement} form - Form element
     * @param {Object} rules - Validation rules for each field
     * @returns {Object} Errors object
     *
     * @example
     *   const errors = ValidationService.validateForm(form, {
     *       email: ['required', 'email'],
     *       password: ['required', 'minLength:8']
     *   });
     */
    static validateForm(form, rules) {
        const config = {};

        for (const [fieldName, fieldRules] of Object.entries(rules)) {
            const field = form.elements[fieldName];
            if (field) {
                config[fieldName] = {
                    value: field.value,
                    rules: fieldRules,
                    label: field.getAttribute('data-label') || fieldName
                };
            }
        }

        return this.validate(config);
    }

    // =========================================================================
    // SPECIFIC VALIDATORS
    // =========================================================================

    /**
     * Validate date range
     * @param {string} startDate - Start date
     * @param {string} endDate - End date
     * @returns {string|null} Error message or null
     */
    static validateDateRange(startDate, endDate) {
        if (!startDate || !endDate) {
            return 'Both start and end dates are required.';
        }

        const start = new Date(startDate);
        const end = new Date(endDate);

        if (isNaN(start) || isNaN(end)) {
            return 'Invalid date format.';
        }

        if (start > end) {
            return 'Start date must be before end date.';
        }

        return null;
    }

    /**
     * Validate password strength
     * @param {string} password - Password
     * @param {Object} requirements - Requirements object
     * @returns {string|null} Error message or null
     */
    static validatePasswordStrength(password, requirements = {}) {
        const {
            minLength = 8,
            requireUppercase = true,
            requireLowercase = true,
            requireNumbers = true,
            requireSpecial = false
        } = requirements;

        if (!password) {
            return 'Password is required.';
        }

        if (password.length < minLength) {
            return `Password must be at least ${minLength} characters.`;
        }

        if (requireUppercase && !/[A-Z]/.test(password)) {
            return 'Password must contain at least one uppercase letter.';
        }

        if (requireLowercase && !/[a-z]/.test(password)) {
            return 'Password must contain at least one lowercase letter.';
        }

        if (requireNumbers && !/\d/.test(password)) {
            return 'Password must contain at least one number.';
        }

        if (requireSpecial && !/[!@#$%^&*(),.?":{}|<>]/.test(password)) {
            return 'Password must contain at least one special character.';
        }

        return null;
    }

    /**
     * Validate password match
     * @param {string} password - Password
     * @param {string} confirmPassword - Confirmation password
     * @returns {string|null} Error message or null
     */
    static validatePasswordMatch(password, confirmPassword) {
        if (password !== confirmPassword) {
            return 'Passwords do not match.';
        }
        return null;
    }

    /**
     * Validate array (e.g., multi-select)
     * @param {Array} array - Array to validate
     * @param {Object} options - Validation options
     * @returns {string|null} Error message or null
     */
    static validateArray(array, options = {}) {
        const {
            minItems = null,
            maxItems = null,
            unique = false,
            fieldName = 'Items'
        } = options;

        if (!Array.isArray(array)) {
            return `${fieldName} must be an array.`;
        }

        if (minItems !== null && array.length < minItems) {
            return `Please select at least ${minItems} ${fieldName.toLowerCase()}.`;
        }

        if (maxItems !== null && array.length > maxItems) {
            return `Please select no more than ${maxItems} ${fieldName.toLowerCase()}.`;
        }

        if (unique && new Set(array).size !== array.length) {
            return `${fieldName} must contain unique values.`;
        }

        return null;
    }
}

/**
 * Export as default for convenience
 */
export default ValidationService;
