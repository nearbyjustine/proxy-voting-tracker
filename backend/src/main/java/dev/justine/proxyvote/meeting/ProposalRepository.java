package dev.justine.proxyvote.meeting;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposalRepository extends JpaRepository<Proposal, Long> {
    @EntityGraph(attributePaths = {"meeting", "meeting.company"})
    Optional<Proposal> findWithMeetingById(Long id);
}
