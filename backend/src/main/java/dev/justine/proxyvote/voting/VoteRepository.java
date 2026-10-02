package dev.justine.proxyvote.voting;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteRepository extends JpaRepository<Vote, Long> {
    Optional<Vote> findByOrganizationIdAndProposalId(Long organizationId, Long proposalId);
    List<Vote> findByOrganizationIdAndProposalIdIn(Long organizationId, Collection<Long> proposalIds);
}
