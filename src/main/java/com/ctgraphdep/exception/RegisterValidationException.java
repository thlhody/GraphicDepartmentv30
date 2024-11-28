// First, create custom exceptions for validation
package com.ctgraphdep.exception;

public class RegisterValidationException extends RuntimeException {
    private final String errorCode;

    public RegisterValidationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}