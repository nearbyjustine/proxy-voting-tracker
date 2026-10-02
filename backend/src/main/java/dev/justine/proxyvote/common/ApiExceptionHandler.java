package dev.justine.proxyvote.common;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns every exception into an RFC 9457 ProblemDetail JSON body, localised via Accept-Language,
 * always carrying the request's correlation ID so a user report can be matched to the logs.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final MessageSource messages;

    public ApiExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException e) {
        return problem(e.status(), e.messageKey(), e.args());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "error.validation");
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
            .forEach(f -> fields.putIfAbsent(f.getField(), messages.getMessage(f, LocaleContextHolder.getLocale())));
        p.setProperty("errors", fields);
        return p;
    }

    /** Malformed JSON or an unknown enum value: the client's mistake, so 400, not a 500 from the catch-all. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, "error.badRequest", "malformed request body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST, "error.badRequest", e.getName());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail staleVersion(ObjectOptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "error.conflict.version");
    }

    /** Last line of defence for unique constraints when two requests race past the service-level check. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        log.warn("Integrity violation: {}", e.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "error.conflict.duplicate", "value");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail denied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "error.forbidden");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unhandled exception", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal", MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    private ProblemDetail problem(HttpStatus status, String key, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        Object[] resolved = Arrays.stream(args == null ? new Object[0] : args)
            .map(a -> a instanceof MessageArg m ? messages.getMessage(m.key(), null, m.key(), locale) : a)
            .toArray();
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, messages.getMessage(key, resolved, key, locale));
        p.setProperty("code", key);
        p.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return p;
    }
}
