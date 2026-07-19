package com.gympal.common.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.gympal.common.ApiResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Handles all known API exceptions (BadRequest, NotFound, Conflict, Unauthorized) */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleApiException(ApiException ex, HttpServletRequest request) {
        logAndScrubError(request, "API ERROR", ex.getMessage(), ex);
        ApiResponseDto<Object> response = ApiResponseDto.error(ex.getStatus().value(), sanitizeMessage(ex.getMessage()));
        return new ResponseEntity<>(response, ex.getStatus());
    }

    /** Handles Spring @Valid validation failures */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        logAndScrubError(request, "VALIDATION ERROR", "Validation failed", ex);
        ApiResponseDto<Object> response = new ApiResponseDto<>(HttpStatus.BAD_REQUEST.value(), "Validation failed", errors);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /** Handles Spring Data Database violations (e.g. Duplicate Key / Constraint Violations) */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleDataIntegrityViolation(org.springframework.dao.DataIntegrityViolationException ex, HttpServletRequest request) {
        Throwable rootCause = getRootCause(ex);
        String message = rootCause.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = "Database integrity constraint violation";
        }
        
        HttpStatus status = HttpStatus.CONFLICT;
        // Clean up common database messages
        if (message.contains("duplicate key") || message.contains("Unique index or primary key violation")) {
            message = "Duplicate entry or unique constraint violation";
            if (message.toLowerCase().contains("email")) {
                message = "Duplicate email";
            }
        } else {
            status = HttpStatus.BAD_REQUEST;
        }

        logAndScrubError(request, "DATABASE ERROR", message, ex);
        ApiResponseDto<Object> response = ApiResponseDto.error(status.value(), sanitizeMessage(message));
        return new ResponseEntity<>(response, status);
    }

    /** Handles Duplicate Email Exception */
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleDuplicateEmail(DuplicateEmailException ex, HttpServletRequest request) {
        logAndScrubError(request, "CONFLICT", ex.getMessage(), ex);
        ApiResponseDto<Object> response = ApiResponseDto.error(HttpStatus.CONFLICT.value(), sanitizeMessage(ex.getMessage()));
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    /** Handles Duplicate Phone Exception */
    @ExceptionHandler(DuplicatePhoneException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleDuplicatePhone(DuplicatePhoneException ex, HttpServletRequest request) {
        logAndScrubError(request, "CONFLICT", ex.getMessage(), ex);
        ApiResponseDto<Object> response = ApiResponseDto.error(HttpStatus.CONFLICT.value(), sanitizeMessage(ex.getMessage()));
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    /** Handles SMTP/Email sending exceptions */
    @ExceptionHandler({org.springframework.mail.MailException.class, jakarta.mail.MessagingException.class})
    public ResponseEntity<ApiResponseDto<Object>> handleMailException(Exception ex, HttpServletRequest request) {
        Throwable rootCause = getRootCause(ex);
        String message = rootCause.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = "SMTP or Email service error";
        }

        logAndScrubError(request, "SMTP ERROR", message, ex);
        ApiResponseDto<Object> response = ApiResponseDto.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), sanitizeMessage(message));
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** Handles malformed JSON body */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        logAndScrubError(request, "BAD REQUEST", "Malformed request body or missing required field", ex);
        ApiResponseDto<Object> response = ApiResponseDto.error(HttpStatus.BAD_REQUEST.value(), "Malformed request body or missing required field");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /** Handles wrong HTTP method (e.g. GET instead of POST) */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        logAndScrubError(request, "METHOD NOT ALLOWED", ex.getMessage(), ex);
        ApiResponseDto<Object> response = ApiResponseDto.error(HttpStatus.METHOD_NOT_ALLOWED.value(), ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    /** Handles missing query params */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String msg = "Required parameter '" + ex.getParameterName() + "' is missing";
        logAndScrubError(request, "BAD REQUEST", msg, ex);
        ApiResponseDto<Object> response = ApiResponseDto.error(HttpStatus.BAD_REQUEST.value(), msg);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /** Handles wrong type for a parameter (e.g. string instead of number) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String msg = "Invalid value for parameter '" + ex.getName() + "'";
        logAndScrubError(request, "BAD REQUEST", msg, ex);
        ApiResponseDto<Object> response = ApiResponseDto.error(HttpStatus.BAD_REQUEST.value(), msg);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /** Handles illegal argument / state from service layer */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponseDto<Object>> handleBadRequestExceptions(RuntimeException ex, HttpServletRequest request) {
        logAndScrubError(request, "BAD REQUEST", ex.getMessage(), ex);
        ApiResponseDto<Object> response = ApiResponseDto.error(HttpStatus.BAD_REQUEST.value(), sanitizeMessage(ex.getMessage()));
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /** Catch-all — logs full stack trace so nothing is silently swallowed */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<Object>> handleGenericException(Exception ex, HttpServletRequest request) {
        String message = ex.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = "Internal server error";
        }
        logAndScrubError(request, "UNHANDLED EXCEPTION", message, ex);
        ApiResponseDto<Object> response = ApiResponseDto.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), sanitizeMessage(message));
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Helper method to resolve root cause
    private Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    // Helper method to log and scrub errors
    private void logAndScrubError(HttpServletRequest request, String type, String message, Throwable ex) {
        String method = request.getMethod();
        String url = request.getRequestURL().toString();
        String query = request.getQueryString();
        if (query != null) {
            url += "?" + query;
        }

        String cleanUrl = scrubUrl(url);
        String cleanMsg = sanitizeMessage(message);

        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null) {
            requestId = request.getHeader("X-Correlation-Id");
        }
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }

        log.error("[{}] RequestId: {} - {} {} - Timestamp: {} - Message: {}",
                type, requestId, method, cleanUrl, Instant.now(), cleanMsg, ex);
    }

    // Helper method to scrub credentials from logs
    private String scrubUrl(String url) {
        if (url == null) return "";
        return url.replaceAll("(?i)(password|pwd|secret|key|token|auth|credentials|smtp_pass)=[^&\\s;]+", "$1=***");
    }

    // Helper method to scrub sensitive terms from responses
    private String sanitizeMessage(String message) {
        if (message == null) return "Internal server error";
        String clean = message;
        clean = clean.replaceAll("(?i)password\\s*=\\s*[^&\\s;]+", "password=***");
        clean = clean.replaceAll("(?i)pwd\\s*=\\s*[^&\\s;]+", "pwd=***");
        clean = clean.replaceAll("(?i)secret\\s*=\\s*[^&\\s;]+", "secret=***");
        clean = clean.replaceAll("(?i)key\\s*=\\s*[^&\\s;]+", "key=***");
        clean = clean.replaceAll("(?i)token\\s*=\\s*[^&\\s;]+", "token=***");
        return clean;
    }
}
