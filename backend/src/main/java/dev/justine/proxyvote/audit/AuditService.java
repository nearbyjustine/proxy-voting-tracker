package dev.justine.proxyvote.audit;

import dev.justine.proxyvote.common.CurrentUser;
import dev.justine.proxyvote.meeting.Organization;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit rows are written in the SAME transaction as the change they describe (Propagation.MANDATORY):
 * if the vote rolls back, so does its audit row, and there is never a vote without its audit trail.
 */
@Service
public class AuditService {
    private final AuditEventRepository events;
    private final Clock clock;

    public AuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Organization org, String action, String entityType, Object entityId, Map<String, ?> details) {
        Map<String, Object> copy = new HashMap<>();
        details.forEach((k, v) -> copy.put(k, v == null ? null : (v instanceof Number || v instanceof Boolean) ? v : v.toString()));
        events.save(new AuditEvent(org, CurrentUser.username().orElse("system"), action, entityType,
            String.valueOf(entityId), copy, clock.instant()));
    }
}
