package dev.justine.proxyvote.audit;

import dev.justine.proxyvote.config.Tenant;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    public record AuditResponse(Long id, String actor, String action, String entityType, String entityId,
                                Map<String, Object> details, Instant occurredAt) {}

    private final AuditEventRepository events;
    private final Tenant tenant;

    public AuditController(AuditEventRepository events, Tenant tenant) {
        this.events = events;
        this.tenant = tenant;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PagedModel<AuditResponse> list(@PageableDefault(size = 25) Pageable pageable) {
        return new PagedModel<>(events.findByOrganizationIdOrderByOccurredAtDesc(tenant.current().getId(), pageable)
            .map(e -> new AuditResponse(e.getId(), e.getActor(), e.getAction(), e.getEntityType(), e.getEntityId(),
                e.getDetails(), e.getOccurredAt())));
    }
}
