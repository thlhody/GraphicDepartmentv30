package com.ctgraphdep.exception;

import lombok.Getter;

@Getter
public class RegisterValidationException extends RuntimeException {
    private final String errorCode;

    public RegisterValidationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

}