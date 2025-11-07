/**
 * Core Utilities - Common JavaScript utilities
 *
 * This module provides common utility functions that are used throughout
 * the application. No jQuery dependency - pure vanilla JavaScript.
 *
 * @module core/utils
 * @version 1.0.0
 * @since 2025-11-04
 *
 * ⚠️ IMPORTANT: No jQuery - pure JavaScript only.
 * Use these utilities instead of jQuery where possible.
 *
 * Categories:
 * - DOM utilities
 * - Date/Time formatting
 * - String utilities
 * - Number formatting
 * - Array/Object utilities
 * - Function utilities (debounce, throttle)
 * - URL utilities
 * - Validation utilities
 *
 * Usage:
 *   import { formatDate, debounce, createElement } from './core/utils.js';
 *
 *   const formattedDate = formatDate(new Date());
 *   const debouncedFn = debounce(myFunction, 300);
 */

// =============================================================================
// DOM UTILITIES
// =============================================================================

/**
 * Query selector wrapper ($ alternative)
 * @param {string} selector - CSS selector
 * @param {Element} context - Context element (default: document)
 * @returns {Element|null} First matching element
 */
export function $(selector, context = document) {
    return context.querySelector(selector);
}

/**
 * Query selector all wrapper ($$ alternative)
 * @param {string} selector - CSS selector
 * @param {Element} context - Context element (default: document)
 * @returns {NodeList} All matching elements
 */
export function $$(selector, context = document) {
    return context.querySelectorAll(selector);
}

/**
 * Convert hyphenated string to camelCase
 * @param {string} str - Hyphenated string (e.g., 'search-modal')
 * @returns {string} CamelCase string (e.g., 'searchModal')
 * @private
 */
function hyphenToCamelCase(str) {
    return str.replace(/-([a-z])/g, (match, letter) => letter.toUpperCase());
}

/**
 * Create element with attributes and content
 * @param {string} tag - HTML tag name
 * @param {Object} attrs - Attributes object
 * @param {string|Element|Array} content - Content (text, element, or array of elements)
 * @returns {Element} Created element
 *
 * @example
 *   createElement('div', { class: 'card', id: 'myCard' }, 'Hello World');
 *   createElement('button', { class: 'btn' }, [icon, text]);
 */
export function createElement(tag, attrs = {}, content = null) {
    const element = document.createElement(tag);

    // Set attributes
    for (const [key, value] of Object.entries(attrs)) {
        if (key === 'class') {
            element.className = value;
        } else if (key === 'style' && typeof value === 'object') {
            Object.assign(element.style, value);
        } else if (key.startsWith('data-')) {
            // Convert data-search-modal → searchModal for dataset
            const dataKey = hyphenToCamelCase(key.replace('data-', ''));
            element.dataset[dataKey] = value;
        } else {
            element.setAttribute(key, value);
        }
    }

    // Set content
    if (content !== null) {
        if (Array.isArray(content)) {
            content.forEach(child => {
                if (typeof child === 'string') {
                    element.appendChild(document.createTextNode(child));
                } else if (child instanceof Element) {
                    element.appendChild(child);
                }
            });
        } else if (typeof content === 'string') {
            element.innerHTML = content;  // Use innerHTML to render HTML tags
        } else if (content instanceof Element) {
            element.appendChild(content);
        }
    }

    return element;
}

/**
 * Add event listener with delegation support
 * @param {Element} element - Parent element
 * @param {string} event - Event name
 * @param {string} selector - Child selector for delegation
 * @param {Function} handler - Event handler
 */
export function on(element, event, selector, handler) {
    element.addEventListener(event, (e) => {
        const target = e.target.closest(selector);
        if (target && element.contains(target)) {
            handler.call(target, e);
        }
    });
}

/**
 * Remove element from DOM
 * @param {Element} element - Element to remove
 */
export function remove(element) {
    if (element && element.parentNode) {
        element.parentNode.removeChild(element);
    }
}

/**
 * Check if element has class
 * @param {Element} element - Element to check
 * @param {string} className - Class name
 * @returns {boolean} True if element has class
 */
export function hasClass(element, className) {
    return element && element.classList.contains(className);
}

// =============================================================================
// DATE/TIME UTILITIES
// =============================================================================

/**
 * Format date to YYYY-MM-DD
 * @param {Date|string} date - Date to format
 * @returns {string} Formatted date string
 */
export function formatDate(date) {
    const d = new Date(date);
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

/**
 * Format date to DD/MM/YYYY
 * @param {Date|string} date - Date to format
 * @returns {string} Formatted date string
 */
export function formatDateEU(date) {
    const d = new Date(date);
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${day}/${month}/${year}`;
}

/**
 * Format time to HH:mm
 * @param {Date|string} date - Date to format
 * @returns {string} Formatted time string
 */
export function formatTime(date) {
    const d = new Date(date);
    const hours = String(d.getHours()).padStart(2, '0');
    const minutes = String(d.getMinutes()).padStart(2, '0');
    return `${hours}:${minutes}`;
}

/**
 * Format datetime to YYYY-MM-DD HH:mm:ss
 * @param {Date|string} date - Date to format
 * @returns {string} Formatted datetime string
 */
export function formatDateTime(date) {
    const d = new Date(date);
    return `${formatDate(d)} ${formatTime(d)}:${String(d.getSeconds()).padStart(2, '0')}`;
}

/**
 * Parse date from various formats
 * @param {string} dateString - Date string
 * @returns {Date|null} Parsed date or null
 */
export function parseDate(dateString) {
    if (!dateString) return null;

    // Try ISO format
    const isoDate = new Date(dateString);
    if (!isNaN(isoDate.getTime())) {
        return isoDate;
    }

    // Try DD/MM/YYYY format
    const parts = dateString.split(/[/\-\.]/);
    if (parts.length === 3) {
        const day = parseInt(parts[0]);
        const month = parseInt(parts[1]) - 1;
        const year = parseInt(parts[2]);
        const date = new Date(year, month, day);
        if (!isNaN(date.getTime())) {
            return date;
        }
    }

    return null;
}

/**
 * Get relative time string (e.g., "2 hours ago")
 * @param {Date|string} date - Date
 * @returns {string} Relative time string
 */
export function getRelativeTime(date) {
    const d = new Date(date);
    const now = new Date();
    const diffMs = now - d;
    const diffSec = Math.floor(diffMs / 1000);
    const diffMin = Math.floor(diffSec / 60);
    const diffHour = Math.floor(diffMin / 60);
    const diffDay = Math.floor(diffHour / 24);

    if (diffSec < 60) return 'just now';
    if (diffMin < 60) return `${diffMin} minute${diffMin > 1 ? 's' : ''} ago`;
    if (diffHour < 24) return `${diffHour} hour${diffHour > 1 ? 's' : ''} ago`;
    if (diffDay < 7) return `${diffDay} day${diffDay > 1 ? 's' : ''} ago`;
    return formatDate(d);
}

/**
 * Format minutes to hours and minutes (e.g., "2h 30m")
 * @param {number} minutes - Total minutes
 * @returns {string} Formatted string
 */
export function formatMinutesToHours(minutes) {
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    if (mins === 0) {
        return `${hours}h`;
    }
    return `${hours}h ${mins}m`;
}

// =============================================================================
// STRING UTILITIES
// =============================================================================

/**
 * Capitalize first letter
 * @param {string} str - String to capitalize
 * @returns {string} Capitalized string
 */
export function capitalize(str) {
    if (!str) return '';
    return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
}

/**
 * Convert string to title case
 * @param {string} str - String to convert
 * @returns {string} Title case string
 */
export function titleCase(str) {
    if (!str) return '';
    return str.split(' ').map(capitalize).join(' ');
}

/**
 * Truncate string to max length
 * @param {string} str - String to truncate
 * @param {number} maxLength - Maximum length
 * @param {string} suffix - Suffix to add (default: '...')
 * @returns {string} Truncated string
 */
export function truncate(str, maxLength, suffix = '...') {
    if (!str || str.length <= maxLength) return str;
    return str.substring(0, maxLength - suffix.length) + suffix;
}

/**
 * Escape HTML characters
 * @param {string} str - String to escape
 * @returns {string} Escaped string
 */
export function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

/**
 * Strip HTML tags from string
 * @param {string} html - HTML string
 * @returns {string} Plain text
 */
export function stripHtml(html) {
    const div = document.createElement('div');
    div.innerHTML = html;
    return div.textContent || div.innerText || '';
}

/**
 * Generate random string
 * @param {number} length - Length of string
 * @returns {string} Random string
 */
export function randomString(length = 10) {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    let result = '';
    for (let i = 0; i < length; i++) {
        result += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return result;
}

// =============================================================================
// NUMBER UTILITIES
// =============================================================================

/**
 * Format number with thousand separators
 * @param {number} num - Number to format
 * @param {number} decimals - Number of decimal places
 * @returns {string} Formatted number
 */
export function formatNumber(num, decimals = 0) {
    return Number(num).toLocaleString('en-US', {
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals
    });
}

/**
 * Format number as percentage
 * @param {number} num - Number to format (0-1 or 0-100)
 * @param {number} decimals - Number of decimal places
 * @param {boolean} isDecimal - True if num is 0-1, false if 0-100
 * @returns {string} Formatted percentage
 */
export function formatPercentage(num, decimals = 1, isDecimal = true) {
    const value = isDecimal ? num * 100 : num;
    return `${value.toFixed(decimals)}%`;
}

/**
 * Clamp number between min and max
 * @param {number} num - Number to clamp
 * @param {number} min - Minimum value
 * @param {number} max - Maximum value
 * @returns {number} Clamped number
 */
export function clamp(num, min, max) {
    return Math.min(Math.max(num, min), max);
}

/**
 * Check if value is numeric
 * @param {any} value - Value to check
 * @returns {boolean} True if numeric
 */
export function isNumeric(value) {
    return !isNaN(parseFloat(value)) && isFinite(value);
}

// =============================================================================
// ARRAY/OBJECT UTILITIES
// =============================================================================

/**
 * Deep clone object/array
 * @param {any} obj - Object to clone
 * @returns {any} Cloned object
 */
export function deepClone(obj) {
    return JSON.parse(JSON.stringify(obj));
}

/**
 * Check if object is empty
 * @param {Object} obj - Object to check
 * @returns {boolean} True if empty
 */
export function isEmpty(obj) {
    if (obj === null || obj === undefined) return true;
    if (typeof obj === 'string' || Array.isArray(obj)) return obj.length === 0;
    if (typeof obj === 'object') return Object.keys(obj).length === 0;
    return false;
}

/**
 * Group array by key
 * @param {Array} array - Array to group
 * @param {string|Function} key - Property name or getter function
 * @returns {Object} Grouped object
 */
export function groupBy(array, key) {
    const getKey = typeof key === 'function' ? key : item => item[key];
    return array.reduce((result, item) => {
        const groupKey = getKey(item);
        (result[groupKey] = result[groupKey] || []).push(item);
        return result;
    }, {});
}

/**
 * Sort array by key
 * @param {Array} array - Array to sort
 * @param {string} key - Property name
 * @param {boolean} ascending - Sort order
 * @returns {Array} Sorted array
 */
export function sortBy(array, key, ascending = true) {
    return [...array].sort((a, b) => {
        const aVal = a[key];
        const bVal = b[key];
        const comparison = aVal < bVal ? -1 : aVal > bVal ? 1 : 0;
        return ascending ? comparison : -comparison;
    });
}

/**
 * Remove duplicates from array
 * @param {Array} array - Array with duplicates
 * @returns {Array} Array without duplicates
 */
export function unique(array) {
    return [...new Set(array)];
}

// =============================================================================
// FUNCTION UTILITIES
// =============================================================================

/**
 * Debounce function execution
 * @param {Function} func - Function to debounce
 * @param {number} wait - Wait time in ms
 * @returns {Function} Debounced function
 */
export function debounce(func, wait = 300) {
    let timeoutId;
    return function (...args) {
        clearTimeout(timeoutId);
        timeoutId = setTimeout(() => func.apply(this, args), wait);
    };
}

/**
 * Throttle function execution
 * @param {Function} func - Function to throttle
 * @param {number} limit - Limit in ms
 * @returns {Function} Throttled function
 */
export function throttle(func, limit = 300) {
    let inThrottle;
    return function (...args) {
        if (!inThrottle) {
            func.apply(this, args);
            inThrottle = true;
            setTimeout(() => inThrottle = false, limit);
        }
    };
}

/**
 * Sleep/delay execution
 * @param {number} ms - Milliseconds to sleep
 * @returns {Promise} Promise that resolves after ms
 */
export function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

// =============================================================================
// URL UTILITIES
// =============================================================================

/**
 * Get URL parameters as object
 * @param {string} url - URL (default: window.location.href)
 * @returns {Object} URL parameters
 */
export function getUrlParams(url = window.location.href) {
    const params = {};
    const urlObj = new URL(url);
    urlObj.searchParams.forEach((value, key) => {
        params[key] = value;
    });
    return params;
}

/**
 * Update URL parameter without reload
 * @param {string} key - Parameter key
 * @param {string} value - Parameter value
 */
export function updateUrlParam(key, value) {
    const url = new URL(window.location.href);
    url.searchParams.set(key, value);
    window.history.replaceState({}, '', url.toString());
}

/**
 * Remove URL parameter without reload
 * @param {string} key - Parameter key
 */
export function removeUrlParam(key) {
    const url = new URL(window.location.href);
    url.searchParams.delete(key);
    window.history.replaceState({}, '', url.toString());
}

// =============================================================================
// VALIDATION UTILITIES
// =============================================================================

/**
 * Validate email format
 * @param {string} email - Email to validate
 * @returns {boolean} True if valid
 */
export function isValidEmail(email) {
    const regex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return regex.test(email);
}

/**
 * Validate URL format
 * @param {string} url - URL to validate
 * @returns {boolean} True if valid
 */
export function isValidUrl(url) {
    try {
        new URL(url);
        return true;
    } catch {
        return false;
    }
}

/**
 * Validate phone number (basic)
 * @param {string} phone - Phone to validate
 * @returns {boolean} True if valid
 */
export function isValidPhone(phone) {
    const regex = /^[\d\s\-\+\(\)]{10,}$/;
    return regex.test(phone);
}

// =============================================================================
// EXPORT ALL
// =============================================================================

export default {
    // DOM
    $, $$, createElement, on, remove, hasClass,
    // Date/Time
    formatDate, formatDateEU, formatTime, formatDateTime, parseDate, getRelativeTime, formatMinutesToHours,
    // String
    capitalize, titleCase, truncate, escapeHtml, stripHtml, randomString,
    // Number
    formatNumber, formatPercentage, clamp, isNumeric,
    // Array/Object
    deepClone, isEmpty, groupBy, sortBy, unique,
    // Function
    debounce, throttle, sleep,
    // URL
    getUrlParams, updateUrlParam, removeUrlParam,
    // Validation
    isValidEmail, isValidUrl, isValidPhone
};
