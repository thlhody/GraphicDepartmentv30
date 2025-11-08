/**
 * Core API - Unified AJAX/Fetch wrapper
 *
 * This module provides a consistent API for making HTTP requests throughout
 * the application. Previously, AJAX calls were duplicated with inline CSRF
 * handling across 8+ files.
 *
 * @module core/api
 * @version 1.0.0
 * @since 2025-11-04
 *
 * ⚠️ IMPORTANT: This is the ONLY place for HTTP request logic.
 * Do NOT create inline fetch/AJAX calls. Use this API wrapper.
 *
 * Features:
 * - Automatic CSRF token injection
 * - Consistent error handling
 * - Request/response interceptors
 * - Timeout support
 * - JSON handling
 * - Form data support
 * - URL parameter encoding
 *
 * Usage:
 *   import { API } from './core/api.js';
 *
 *   // GET request
 *   const data = await API.get('/api/users');
 *
 *   // POST with JSON
 *   const result = await API.post('/api/users', { name: 'John' });
 *
 *   // POST with form data
 *   const result = await API.postForm('/api/upload', formData);
 */

/**
 * API Service Class
 * All methods are static - no instantiation needed
 */
export class API {
    // Configuration
    static #config = {
        timeout: 30000,              // Default timeout: 30 seconds
        baseURL: '',                 // Base URL for all requests
        csrfToken: null,             // CSRF token
        csrfHeader: null,            // CSRF header name
        defaultHeaders: {            // Default headers for all requests
            'Content-Type': 'application/json'
        }
    };

    // Request/response interceptors
    static #requestInterceptors = [];
    static #responseInterceptors = [];

    // =========================================================================
    // INITIALIZATION
    // =========================================================================

    /**
     * Initialize API with CSRF token from meta tags
     * Call this once on application startup
     */
    static init() {
        // Get CSRF token from meta tags
        const csrfToken = document.querySelector('meta[name="_csrf"]');
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]');

        if (csrfToken) {
            this.#config.csrfToken = csrfToken.getAttribute('content');
        }

        if (csrfHeader) {
            this.#config.csrfHeader = csrfHeader.getAttribute('content');
        }

        if (this.#config.csrfToken && this.#config.csrfHeader) {
            console.log('✅ API initialized with CSRF protection');
        } else {
            console.log('ℹ️ API initialized without CSRF protection (local app mode)');
        }
    }

    /**
     * Configure API
     * @param {Object} options - Configuration options
     */
    static configure(options = {}) {
        this.#config = { ...this.#config, ...options };
    }

    // =========================================================================
    // HTTP METHODS
    // =========================================================================

    /**
     * GET request
     * @param {string} url - Request URL
     * @param {Object} params - URL parameters
     * @param {Object} options - Request options
     * @returns {Promise<any>} Response data
     */
    static async get(url, params = {}, options = {}) {
        const urlWithParams = this.#buildURL(url, params);
        return this.#request(urlWithParams, {
            method: 'GET',
            ...options
        });
    }

    /**
     * POST request with JSON body
     * @param {string} url - Request URL
     * @param {Object} data - Request body
     * @param {Object} options - Request options
     * @returns {Promise<any>} Response data
     */
    static async post(url, data = {}, options = {}) {
        return this.#request(url, {
            method: 'POST',
            body: JSON.stringify(data),
            ...options
        });
    }

    /**
     * POST request with form data
     * @param {string} url - Request URL
     * @param {FormData|Object} data - Form data or object
     * @param {Object} options - Request options
     * @returns {Promise<any>} Response data
     */
    static async postForm(url, data, options = {}) {
        // If already FormData, use it (for file uploads - multipart/form-data)
        if (data instanceof FormData) {
            return this.#request(url, {
                method: 'POST',
                body: data,
                headers: {
                    // Don't set Content-Type for FormData (browser sets it with boundary)
                    ...options.headers
                },
                ...options
            });
        }

        // Otherwise, use URLSearchParams for standard form encoding (application/x-www-form-urlencoded)
        // This is what Spring Boot @RequestParam expects
        const formBody = new URLSearchParams();
        for (const [key, value] of Object.entries(data)) {
            if (value !== null && value !== undefined) {
                formBody.append(key, value);
            }
        }

        return this.#request(url, {
            method: 'POST',
            body: formBody,
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                ...options.headers
            },
            ...options
        });
    }

    /**
     * PUT request with JSON body
     * @param {string} url - Request URL
     * @param {Object} data - Request body
     * @param {Object} options - Request options
     * @returns {Promise<any>} Response data
     */
    static async put(url, data = {}, options = {}) {
        return this.#request(url, {
            method: 'PUT',
            body: JSON.stringify(data),
            ...options
        });
    }

    /**
     * PATCH request with JSON body
     * @param {string} url - Request URL
     * @param {Object} data - Request body
     * @param {Object} options - Request options
     * @returns {Promise<any>} Response data
     */
    static async patch(url, data = {}, options = {}) {
        return this.#request(url, {
            method: 'PATCH',
            body: JSON.stringify(data),
            ...options
        });
    }

    /**
     * DELETE request
     * @param {string} url - Request URL
     * @param {Object} options - Request options
     * @returns {Promise<any>} Response data
     */
    static async delete(url, options = {}) {
        return this.#request(url, {
            method: 'DELETE',
            ...options
        });
    }

    // =========================================================================
    // CORE REQUEST METHOD
    // =========================================================================

    /**
     * Core request method
     * @private
     */
    static async #request(url, options = {}) {
        // Build full URL
        const fullURL = this.#config.baseURL + url;

        // Merge headers
        const headers = {
            ...this.#config.defaultHeaders,
            ...options.headers
        };

        // Add CSRF token for non-GET requests
        if (options.method && options.method !== 'GET') {
            if (this.#config.csrfToken && this.#config.csrfHeader) {
                headers[this.#config.csrfHeader] = this.#config.csrfToken;
            }
        }

        // Build fetch options
        const fetchOptions = {
            ...options,
            headers
        };

        // Apply request interceptors
        let interceptedOptions = fetchOptions;
        for (const interceptor of this.#requestInterceptors) {
            interceptedOptions = await interceptor(fullURL, interceptedOptions);
        }

        // Create abort controller for timeout
        const controller = new AbortController();
        const timeout = options.timeout || this.#config.timeout;
        const timeoutId = setTimeout(() => controller.abort(), timeout);

        try {
            // Make request
            const response = await fetch(fullURL, {
                ...interceptedOptions,
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            // Apply response interceptors
            let interceptedResponse = response;
            for (const interceptor of this.#responseInterceptors) {
                interceptedResponse = await interceptor(interceptedResponse);
            }

            // Handle response
            return await this.#handleResponse(interceptedResponse);

        } catch (error) {
            clearTimeout(timeoutId);

            // Handle errors
            if (error.name === 'AbortError') {
                throw new APIError('Request timeout', 'TIMEOUT', 408);
            }

            throw new APIError(
                error.message || 'Network error',
                'NETWORK_ERROR',
                0,
                error
            );
        }
    }

    /**
     * Handle fetch response
     * @private
     */
    static async #handleResponse(response) {
        // Get content type
        const contentType = response.headers.get('content-type');
        const isJSON = contentType && contentType.includes('application/json');

        // Parse body
        let data;
        try {
            if (isJSON) {
                data = await response.json();
            } else {
                data = await response.text();
            }
        } catch (error) {
            data = null;
        }

        // Check if response is OK
        if (!response.ok) {
            const errorMessage = this.#extractErrorMessage(data);
            throw new APIError(
                errorMessage,
                'HTTP_ERROR',
                response.status,
                data
            );
        }

        return data;
    }

    /**
     * Extract error message from response
     * @private
     */
    static #extractErrorMessage(data) {
        // Try different error message formats
        if (typeof data === 'string') {
            return data;
        }

        if (data && typeof data === 'object') {
            return data.message
                || data.error
                || data.errorMessage
                || 'Request failed';
        }

        return 'Request failed';
    }

    // =========================================================================
    // INTERCEPTORS
    // =========================================================================

    /**
     * Add request interceptor
     * @param {Function} interceptor - Function(url, options) => options
     */
    static addRequestInterceptor(interceptor) {
        if (typeof interceptor !== 'function') {
            throw new Error('Interceptor must be a function');
        }
        this.#requestInterceptors.push(interceptor);
    }

    /**
     * Add response interceptor
     * @param {Function} interceptor - Function(response) => response
     */
    static addResponseInterceptor(interceptor) {
        if (typeof interceptor !== 'function') {
            throw new Error('Interceptor must be a function');
        }
        this.#responseInterceptors.push(interceptor);
    }

    /**
     * Clear all interceptors
     */
    static clearInterceptors() {
        this.#requestInterceptors = [];
        this.#responseInterceptors = [];
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Build URL with query parameters
     * @private
     */
    static #buildURL(url, params) {
        if (!params || Object.keys(params).length === 0) {
            return url;
        }

        const queryString = new URLSearchParams(params).toString();
        const separator = url.includes('?') ? '&' : '?';
        return `${url}${separator}${queryString}`;
    }

    /**
     * Convert object to FormData
     * @private
     */
    static #objectToFormData(obj) {
        const formData = new FormData();

        for (const [key, value] of Object.entries(obj)) {
            if (value !== null && value !== undefined) {
                if (Array.isArray(value)) {
                    value.forEach(item => formData.append(key, item));
                } else {
                    formData.append(key, value);
                }
            }
        }

        return formData;
    }

    /**
     * Get CSRF token
     * @returns {string|null} CSRF token
     */
    static getCSRFToken() {
        return this.#config.csrfToken;
    }

    /**
     * Get CSRF header name
     * @returns {string|null} CSRF header name
     */
    static getCSRFHeader() {
        return this.#config.csrfHeader;
    }
}

/**
 * Custom API Error class
 */
export class APIError extends Error {
    constructor(message, code, status, data = null) {
        super(message);
        this.name = 'APIError';
        this.code = code;
        this.status = status;
        this.data = data;
    }

    /**
     * Check if error is a timeout
     */
    isTimeout() {
        return this.code === 'TIMEOUT';
    }

    /**
     * Check if error is a network error
     */
    isNetworkError() {
        return this.code === 'NETWORK_ERROR';
    }

    /**
     * Check if error is a client error (4xx)
     */
    isClientError() {
        return this.status >= 400 && this.status < 500;
    }

    /**
     * Check if error is a server error (5xx)
     */
    isServerError() {
        return this.status >= 500;
    }
}

// Auto-initialize on module load
API.init();

// Export as default for convenience
export default API;
