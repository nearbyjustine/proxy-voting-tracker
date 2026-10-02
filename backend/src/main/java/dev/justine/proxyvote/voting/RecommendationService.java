package dev.justine.proxyvote.voting;

import dev.justine.proxyvote.meeting.*;
import dev.justine.proxyvote.policy.PolicyEngine;
import dev.justine.proxyvote.policy.VotingPolicyRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Generates recommendations = policy engine output, upserted per (organization, proposal). Idempotent. */
@Service
@Transactional(propagation = Propagation.MANDATORY)   // always called inside a caller's transaction
public class RecommendationService {
    private final VotingPolicyRepository policies;
    private final OrganizationRepository organizations;
    private final MeetingRepository meetings;
    private final RecommendationRepository recommendations;
    private final PolicyEngine engine;
    private final Clock clock;

    public RecommendationService(VotingPolicyRepository policies, OrganizationRepository organizations,
                                 MeetingRepository meetings, RecommendationRepository recommendations,
                                 PolicyEngine engine, Clock clock) {
        this.policies = policies;
        this.organizations = organizations;
        this.meetings = meetings;
        this.recommendations = recommendations;
        this.engine = engine;
        this.clock = clock;
    }

    /** After a policy change: every proposal of every meeting that is still open for voting. */
    public int regenerateForOrganization(Organization org) {
        var policy = policies.findByOrganizationId(org.getId()).orElse(null);
        if (policy == null) return 0;
        int n = 0;
        for (Meeting m : meetings.findDeadlineAfter(clock.instant())) {
            for (Proposal p : m.getProposals()) {
                upsert(org, p, engine.evaluate(policy.getRules(), p));
                n++;
            }
        }
        return n;
    }

    /** After a meeting is imported or changed: that meeting, for every organization with a policy. */
    public void regenerateForMeeting(Meeting meeting) {
        for (Organization org : organizations.findAll()) {
            policies.findByOrganizationId(org.getId()).ifPresent(policy ->
                meeting.getProposals().forEach(p -> upsert(org, p, engine.evaluate(policy.getRules(), p))));
        }
    }

    private void upsert(Organization org, Proposal p, PolicyEngine.Evaluation e) {
        Recommendation r = (p.getId() == null ? null
            : recommendations.findByOrganizationIdAndProposalId(org.getId(), p.getId()).orElse(null));
        if (r == null) r = new Recommendation(org, p);
        r.set(e.decision(), e.rationale(), e.rulePriority(), clock.instant());
        recommendations.save(r);
    }

    public List<Recommendation> forProposals(Organization org, List<Long> proposalIds) {
        return recommendations.findByOrganizationIdAndProposalIdIn(org.getId(), proposalIds);
    }
}
