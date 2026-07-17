package com.gympal.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponseDto<T> {
    private int status;
    private String message;
    private T data;

    public ApiResponseDto() {}

    public ApiResponseDto(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponseDto<T> success(int status, String message, T data) {
        return new ApiResponseDto<>(status, message, data);
    }

    public static <T> ApiResponseDto<T> success(String message, T data) {
        return new ApiResponseDto<>(200, message, data);
    }

    public static <T> ApiResponseDto<T> error(int status, String message) {
        return new ApiResponseDto<>(status, message, null);
    }

    // Getters & Setters
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
