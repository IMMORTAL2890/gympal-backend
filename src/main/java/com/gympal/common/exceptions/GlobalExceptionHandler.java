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

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Standard API response envelope requested by the user: { data, message, status }
    public static class ErrorResponse {
        private Object data;
        private String message;
        private int status;

        public ErrorResponse(Object data, String message, int status) {
            this.data = data;
            this.message = message;
            this.status = status;
        }

        public Object getData() { return data; }
        public String getMessage() { return message; }
        public int getStatus() { return status; }
    }

    /** Handles all known API exceptions (BadRequest, NotFound, Conflict, Unauthorized) */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        log.warn("[API ERROR] {} - {}", ex.getStatus().name(), ex.getMessage());
        ErrorResponse response = new ErrorResponse(null, ex.getMessage(), ex.getStatus().value());
        return new ResponseEntity<>(response, ex.getStatus());
    }

    /** Handles Spring @Valid validation failures */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("[VALIDATION ERROR] Fields: {}", errors);
        ErrorResponse response = new ErrorResponse(errors, "Input validation failed", HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /** Handles malformed JSON body */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("[BAD REQUEST] Malformed request body: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(null, "Malformed request body or missing required field", HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /** Handles wrong HTTP method (e.g. GET instead of POST) */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        log.warn("[METHOD NOT ALLOWED] {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(null, ex.getMessage(), HttpStatus.METHOD_NOT_ALLOWED.value());
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    /** Handles missing query params */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("[BAD REQUEST] Missing parameter: {}", ex.getParameterName());
        ErrorResponse response = new ErrorResponse(null, "Required parameter '" + ex.getParameterName() + "' is missing", HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /** Handles wrong type for a parameter (e.g. string instead of number) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("[BAD REQUEST] Type mismatch for param '{}': {}", ex.getName(), ex.getMessage());
        ErrorResponse response = new ErrorResponse(null, "Invalid value for parameter '" + ex.getName() + "'", HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /** Handles illegal argument / state from service layer */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleBadRequestExceptions(RuntimeException ex) {
        log.warn("[BAD REQUEST] {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        ErrorResponse response = new ErrorResponse(null, ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /** Catch-all — logs full stack trace so nothing is silently swallowed */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("[UNHANDLED EXCEPTION] {} - {}", ex.getClass().getName(), ex.getMessage(), ex);
        ErrorResponse response = new ErrorResponse(null, "An unexpected error occurred. Please try again.", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
