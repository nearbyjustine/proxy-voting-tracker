package dev.justine.proxyvote.audit;

import dev.justine.proxyvote.meeting.Organization;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only: @Immutable makes Hibernate ignore any attempted update; there are no setters. */
@Entity
@Immutable
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(nullable = false, length = 50)
    private String actor;
    @Column(nullable = false, length = 50)
    private String action;
    @Column(nullable = false, length = 30)
    private String entityType;
    @Column(nullable = false, length = 50)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(nullable = false)
    private Instant occurredAt;

    protected AuditEvent() {}

    public AuditEvent(Organization organization, String actor, String action, String entityType, String entityId,
                      Map<String, Object> details, Instant occurredAt) {
        this.organization = organization;
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public Map<String, Object> getDetails() { return details; }
    public Instant getOccurredAt() { return occurredAt; }
}
