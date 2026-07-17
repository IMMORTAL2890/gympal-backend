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
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Handles all known API exceptions (BadRequest, NotFound, Conflict, Unauthorized) */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleApiException(ApiException ex) {
        log.warn("[API ERROR] {} - {}", ex.getStatus().name(), ex.getMessage());
        ApiResponseDto<Object> response = ApiResponseDto.error(ex.getStatus().value(), ex.getMessage());
        return new ResponseEntity<>(response, ex.getStatus());
    }

    /** Handles Spring @Valid validation failures */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("[VALIDATION ERROR] Fields: {}", errors);
        ApiResponseDto<Object> response = new ApiResponseDto<>(HttpStatus.BAD_REQUEST.value(), "Input validation failed", errors);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /** Handles malformed JSON body */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("[BAD REQUEST] Malformed request body: {}", ex.getMessage());
        ApiResponseDto<Object> response = ApiResponseDto.error(HttpStatus.BAD_REQUEST.value(), "Malformed request body or missing required field");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /** Handles wrong HTTP method (e.g. GET instead of POST) */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        log.warn("[METHOD NOT ALLOWED] {}", ex.getMessage());
        ApiResponseDto<Object> response = ApiResponseDto.error(HttpStatus.METHOD_NOT_ALLOWED.value(), ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    /** Handles missing query params */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("[BAD REQUEST] Missing parameter: {}", ex.getParameterName());
        ApiResponseDto<Object> response = ApiResponseDto.error(HttpStatus.BAD_REQUEST.value(), "Required parameter '" + ex.getParameterName() + "' is missing");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /** Handles wrong type for a parameter (e.g. string instead of number) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("[BAD REQUEST] Type mismatch for param '{}': {}", ex.getName(), ex.getMessage());
        ApiResponseDto<Object> response = ApiResponseDto.error(HttpStatus.BAD_REQUEST.value(), "Invalid value for parameter '" + ex.getName() + "'");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /** Handles illegal argument / state from service layer */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponseDto<Object>> handleBadRequestExceptions(RuntimeException ex) {
        log.warn("[BAD REQUEST] {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        ApiResponseDto<Object> response = ApiResponseDto.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /** Catch-all — logs full stack trace so nothing is silently swallowed */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<Object>> handleGenericException(Exception ex) {
        log.error("[UNHANDLED EXCEPTION] {} - {}", ex.getClass().getName(), ex.getMessage(), ex);
        ApiResponseDto<Object> response = ApiResponseDto.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "An unexpected error occurred. Please try again.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
