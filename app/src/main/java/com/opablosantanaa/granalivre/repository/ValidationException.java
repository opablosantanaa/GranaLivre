package com.opablosantanaa.granalivre.repository;

public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new ValidationException(message);
        }
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
