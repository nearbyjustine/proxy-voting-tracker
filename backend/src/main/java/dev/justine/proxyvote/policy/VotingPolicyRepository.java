package dev.justine.proxyvote.policy;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VotingPolicyRepository extends JpaRepository<VotingPolicy, Long> {
    @EntityGraph(attributePaths = {"rules", "organization"})
    Optional<VotingPolicy> findByOrganizationId(Long organizationId);
}
