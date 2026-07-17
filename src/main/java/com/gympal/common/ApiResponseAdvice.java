package com.gympal.common;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Map;

@ControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    // Success response wrapper
    public static class ApiResponse<T> {
        private T data;
        private String message;
        private int status;

        public ApiResponse(T data, String message, int status) {
            this.data = data;
            this.message = message;
            this.status = status;
        }

        public T getData() { return data; }
        public String getMessage() { return message; }
        public int getStatus() { return status; }
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Intercept all endpoints
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        
        // Skip wrapping if response is not JSON
        if (selectedContentType != null && !selectedContentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            return body;
        }

        // Skip wrapping for default error path to avoid nesting errors
        if (request.getURI().getPath().equals("/error")) {
            return body;
        }

        // If the body is already an ErrorResponse or ApiResponse, do not wrap it again
        if (body instanceof com.gympal.common.exceptions.GlobalExceptionHandler.ErrorResponse) {
            return body;
        }
        if (body instanceof ApiResponse) {
            return body;
        }

        // Avoid wrapping Spring Security's / error attributes maps if they bubble through
        if (body instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) body;
            if (map.containsKey("status") && map.containsKey("error") && map.containsKey("path")) {
                return body;
            }
        }

        int statusCode = HttpStatus.OK.value();
        if (response instanceof ServletServerHttpResponse) {
            statusCode = ((ServletServerHttpResponse) response).getServletResponse().getStatus();
        }

        return new ApiResponse<>(body, "Success", statusCode);
    }
}
