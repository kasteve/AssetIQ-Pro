package com.stevecodes.AssetIQPro.exception;

public class PasswordReuseException extends RuntimeException {
    public PasswordReuseException(String message) {
        super(message);
    }
}