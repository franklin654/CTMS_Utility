package com.ctms.ctms_backend.security.exception;

import com.ctms.ctms_backend.rules.RuleCompilationException;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Object> handleInvalidCredentials(InvalidCredentialsException e) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Object> handleAccountLocked(AccountLockedException e) {
        return error(HttpStatus.LOCKED, e.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Object> handleInvalidToken(InvalidTokenException e) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(PasswordPolicyViolationException.class)
    public ResponseEntity<Object> handlePasswordPolicyViolation(PasswordPolicyViolationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("timestamp", Instant.now(), "message", e.getMessage(), "violations", e.getViolations()));
    }

    @ExceptionHandler(RuleCompilationException.class)
    public ResponseEntity<Object> handleRuleCompilation(RuleCompilationException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException e) {
        return error(HttpStatus.BAD_REQUEST, "Validation failed: " + e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneric(Exception e) {
        log.error("Unhandled exception", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<Object> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("timestamp", Instant.now(), "message", message));
    }
}
