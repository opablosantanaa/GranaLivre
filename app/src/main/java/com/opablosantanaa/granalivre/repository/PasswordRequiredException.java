package com.opablosantanaa.granalivre.repository;

public class PasswordRequiredException extends ValidationException {
    public PasswordRequiredException() {
        super("Este backup é protegido por senha");
    }

    public PasswordRequiredException(String message) {
        super(message);
    }
}
