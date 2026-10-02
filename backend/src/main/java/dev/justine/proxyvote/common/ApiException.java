package dev.justine.proxyvote.common;

import org.springframework.http.HttpStatus;

/**
 * Base for business errors. Carries an HTTP status plus a message key (resolved per locale
 * by {@link ApiExceptionHandler}) so services never build user-facing text themselves.
 */
public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String messageKey;
    private final transient Object[] args;

    protected ApiException(HttpStatus status, String messageKey, Object... args) {
        super(messageKey);
        this.status = status;
        this.messageKey = messageKey;
        this.args = args;
    }

    public HttpStatus status() { return status; }
    public String messageKey() { return messageKey; }
    public Object[] args() { return args; }
}
