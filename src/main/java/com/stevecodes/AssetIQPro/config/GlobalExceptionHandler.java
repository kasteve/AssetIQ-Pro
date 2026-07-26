package com.stevecodes.AssetIQPro.config;

import com.stevecodes.AssetIQPro.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    // ============================================
    // Access Denied - Shows popup on frontend
    // ============================================
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", "access_denied");
        error.put("message", "You don't have permission to perform this action.");
        error.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    // ============================================
    // Account Locked
    // ============================================
    @ExceptionHandler(AccountLockedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<Map<String, Object>> handleAccountLocked(AccountLockedException e) {
        log.warn("Account locked: {}", e.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", "account_locked");
        error.put("message", e.getMessage());
        error.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    // ============================================
    // Password Reuse
    // ============================================
    @ExceptionHandler(PasswordReuseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, Object>> handlePasswordReuse(PasswordReuseException e) {
        log.warn("Password reuse: {}", e.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", "password_reuse");
        error.put("message", e.getMessage());
        error.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // ============================================
    // Invalid Password
    // ============================================
    @ExceptionHandler(InvalidPasswordException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, Object>> handleInvalidPassword(InvalidPasswordException e) {
        log.warn("Invalid password: {}", e.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", "invalid_password");
        error.put("message", e.getMessage());
        error.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // ============================================
    // Resource Not Found
    // ============================================
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException e) {
        log.warn("Resource not found: {}", e.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", "not_found");
        error.put("message", e.getMessage());
        error.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // ============================================
    // User Already Exists
    // ============================================
    @ExceptionHandler(UserAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<Map<String, Object>> handleUserAlreadyExists(UserAlreadyExistsException e) {
        log.warn("User already exists: {}", e.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", "user_exists");
        error.put("message", e.getMessage());
        error.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // ============================================
    // Illegal Argument / Bad Request
    // ============================================
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Bad request: {}", e.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", "bad_request");
        error.put("message", e.getMessage());
        error.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.badRequest().body(error);
    }

    // ============================================
    // Illegal State
    // ============================================
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        log.warn("Illegal state: {}", e.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", "illegal_state");
        error.put("message", e.getMessage());
        error.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // ============================================
    // Generic Exception - Fallback
    // ============================================
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", "internal_error");
        error.put("message", "An unexpected error occurred. Please try again later.");
        error.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}