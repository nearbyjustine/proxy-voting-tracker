package dev.justine.proxyvote.common;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {
    public ConflictException(String messageKey, Object... args) {
        super(HttpStatus.CONFLICT, messageKey, args);
    }
}
