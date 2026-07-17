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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;

@ControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Autowired
    private ObjectMapper objectMapper;

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

        // If the body is already an ApiResponseDto, do not wrap it again
        if (body instanceof ApiResponseDto) {
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

        // Return empty body directly for HTTP 204 No Content
        if (statusCode == HttpStatus.NO_CONTENT.value()) {
            return body;
        }

        String message = "Request processed successfully.";
        if (request instanceof org.springframework.http.server.ServletServerHttpRequest) {
            String method = ((org.springframework.http.server.ServletServerHttpRequest) request).getServletRequest().getMethod();
            if ("GET".equalsIgnoreCase(method)) {
                message = "Data fetched successfully.";
            } else if ("POST".equalsIgnoreCase(method)) {
                message = "Data created successfully.";
            } else if ("PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
                message = "Data updated successfully.";
            } else if ("DELETE".equalsIgnoreCase(method)) {
                message = "Data deleted successfully.";
            }
        }

        // Handle raw String controller response wrapping
        if (body instanceof String) {
            try {
                // Return wrapped object as JSON string since Spring's StringHttpMessageConverter is selected
                return objectMapper.writeValueAsString(new ApiResponseDto<>(statusCode, message, body));
            } catch (Exception e) {
                return body;
            }
        }

        return new ApiResponseDto<>(statusCode, message, body);
    }
}
