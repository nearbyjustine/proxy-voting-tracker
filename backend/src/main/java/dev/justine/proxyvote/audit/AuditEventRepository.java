package dev.justine.proxyvote.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    Page<AuditEvent> findByOrganizationIdOrderByOccurredAtDesc(Long organizationId, Pageable pageable);
}
