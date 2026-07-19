package com.gympal.common.exceptions;

public class DuplicateEmailException extends ConflictException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}
