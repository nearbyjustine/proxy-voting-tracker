package dev.justine.proxyvote.meeting;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    @EntityGraph(attributePaths = {"company", "proposals"})
    Optional<Meeting> findByExternalId(String externalId);

    @EntityGraph(attributePaths = {"company", "proposals"})
    Optional<Meeting> findWithProposalsById(Long id);

    /** Meetings whose deadline is after the cutoff, soonest first, with company + proposals in one query. */
    @EntityGraph(attributePaths = {"company", "proposals"})
    @Query("select distinct m from Meeting m where m.voteDeadline >= :since order by m.voteDeadline")
    List<Meeting> findDeadlineAfter(Instant since);
}
