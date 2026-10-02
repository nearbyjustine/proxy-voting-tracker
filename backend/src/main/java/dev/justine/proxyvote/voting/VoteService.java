package dev.justine.proxyvote.voting;

import dev.justine.proxyvote.audit.AuditService;
import dev.justine.proxyvote.common.ConflictException;
import dev.justine.proxyvote.common.CurrentUser;
import dev.justine.proxyvote.common.NotFoundException;
import dev.justine.proxyvote.config.Tenant;
import dev.justine.proxyvote.meeting.*;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoteService {

    public record VoteResponse(Long proposalId, VoteDecision decision, String submittedBy, Instant submittedAt, long version) {}

    private final ProposalRepository proposals;
    private final VoteRepository votes;
    private final RecommendationRepository recommendations;
    private final Tenant tenant;
    private final AuditService audit;
    private final Clock clock;

    public VoteService(ProposalRepository proposals, VoteRepository votes, RecommendationRepository recommendations,
                       Tenant tenant, AuditService audit, Clock clock) {
        this.proposals = proposals;
        this.votes = votes;
        this.recommendations = recommendations;
        this.tenant = tenant;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Casts or changes this organisation's vote. Rules: only before the deadline (server clock, never the
     * client's), and if the client sends the version it saw, a concurrent change by a colleague is a 409.
     */
    @Transactional
    public VoteResponse cast(Long proposalId, VoteDecision decision, Long expectedVersion) {
        Organization org = tenant.current();
        Proposal proposal = proposals.findWithMeetingById(proposalId)
            .orElseThrow(() -> new NotFoundException("entity.proposal", proposalId));
        Instant now = clock.instant();
        Instant deadline = proposal.getMeeting().getVoteDeadline();
        if (!now.isBefore(deadline)) throw new ConflictException("error.vote.closed", deadline);

        Vote vote = votes.findByOrganizationIdAndProposalId(org.getId(), proposalId).orElse(null);
        VoteDecision previous = vote == null ? null : vote.getDecision();
        if (vote != null && expectedVersion != null && vote.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(Vote.class, vote.getId());
        }
        if (vote == null) vote = new Vote(org, proposal);
        String user = CurrentUser.username().orElse("system");
        vote.cast(decision, user, now);
        votes.saveAndFlush(vote);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("decision", decision);
        details.put("previous", previous);
        details.put("proposal", proposal.getMeeting().getExternalId() + "#" + proposal.getSeq());
        recommendations.findByOrganizationIdAndProposalId(org.getId(), proposalId)
            .ifPresent(r -> details.put("againstRecommendation", r.getDecision() != decision));
        audit.record(org, previous == null ? "VOTE_CAST" : "VOTE_CHANGED", "Proposal", proposalId, details);

        return new VoteResponse(proposalId, vote.getDecision(), vote.getSubmittedBy(), vote.getSubmittedAt(), vote.getVersion());
    }
}
