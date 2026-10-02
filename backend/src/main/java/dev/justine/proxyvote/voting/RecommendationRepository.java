package dev.justine.proxyvote.voting;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    Optional<Recommendation> findByOrganizationIdAndProposalId(Long organizationId, Long proposalId);
    List<Recommendation> findByOrganizationIdAndProposalIdIn(Long organizationId, Collection<Long> proposalIds);
}
