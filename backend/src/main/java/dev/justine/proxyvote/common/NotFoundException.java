package dev.justine.proxyvote.common;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {
    /** @param entityKey message key of the entity name, e.g. "entity.employee" */
    public NotFoundException(String entityKey, Object id) {
        super(HttpStatus.NOT_FOUND, "error.notFound", new MessageArg(entityKey), id);
    }
}
