package com.gympal.common.exceptions;

public class DuplicatePhoneException extends ConflictException {
    public DuplicatePhoneException(String message) {
        super(message);
    }
}
