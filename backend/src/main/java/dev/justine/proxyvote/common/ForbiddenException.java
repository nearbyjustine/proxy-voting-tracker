package dev.justine.proxyvote.common;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiException {
    public ForbiddenException() {
        super(HttpStatus.FORBIDDEN, "error.forbidden");
    }

    public ForbiddenException(String messageKey) {
        super(HttpStatus.FORBIDDEN, messageKey);
    }
}
