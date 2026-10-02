package dev.justine.proxyvote.meeting;

import dev.justine.proxyvote.common.NotFoundException;
import dev.justine.proxyvote.config.AppProperties;
import dev.justine.proxyvote.config.Tenant;
import dev.justine.proxyvote.meeting.MeetingDtos.*;
import dev.justine.proxyvote.voting.Recommendation;
import dev.justine.proxyvote.voting.RecommendationRepository;
import dev.justine.proxyvote.voting.Vote;
import dev.justine.proxyvote.voting.VoteRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Meetings and proposals are shared market data; recommendations and votes are per-tenant.
 * This service merges the two views, always scoped to the caller's organisation.
 */
@Service
@Transactional(readOnly = true)
public class MeetingService {
    private final MeetingRepository meetings;
    private final RecommendationRepository recommendations;
    private final VoteRepository votes;
    private final Tenant tenant;
    private final Clock clock;
    private final Duration closingSoon;

    public MeetingService(MeetingRepository meetings, RecommendationRepository recommendations, VoteRepository votes,
                          Tenant tenant, Clock clock, AppProperties props) {
        this.meetings = meetings;
        this.recommendations = recommendations;
        this.votes = votes;
        this.tenant = tenant;
        this.clock = clock;
        this.closingSoon = Duration.ofHours(props.voting().closingSoonHours());
    }

    /** Upcoming meetings plus those that closed in the last 30 days. Three queries total, regardless of count (no N+1). */
    public List<MeetingSummary> list() {
        Organization org = tenant.current();
        Instant now = clock.instant();
        List<Meeting> list = meetings.findDeadlineAfter(now.minus(Duration.ofDays(30)));
        List<Long> proposalIds = list.stream().flatMap(m -> m.getProposals().stream()).map(Proposal::getId).toList();
        Map<Long, Vote> voted = proposalIds.isEmpty() ? Map.of()
            : votes.findByOrganizationIdAndProposalIdIn(org.getId(), proposalIds).stream()
                .collect(Collectors.toMap(v -> v.getProposal().getId(), Function.identity()));
        Map<Long, Recommendation> calls = proposalIds.isEmpty() ? Map.of()
            : recommendations.findByOrganizationIdAndProposalIdIn(org.getId(), proposalIds).stream()
                .collect(Collectors.toMap(r -> r.getProposal().getId(), Function.identity()));
        return list.stream().map(m -> summary(m, now, calls, voted)).toList();
    }

    public MeetingDetail detail(Long id) {
        Organization org = tenant.current();
        Meeting m = meetings.findWithProposalsById(id).orElseThrow(() -> new NotFoundException("entity.meeting", id));
        List<Long> ids = m.getProposals().stream().map(Proposal::getId).toList();
        Map<Long, Recommendation> recs = recommendations.findByOrganizationIdAndProposalIdIn(org.getId(), ids).stream()
            .collect(Collectors.toMap(r -> r.getProposal().getId(), Function.identity()));
        Map<Long, Vote> vs = votes.findByOrganizationIdAndProposalIdIn(org.getId(), ids).stream()
            .collect(Collectors.toMap(v -> v.getProposal().getId(), Function.identity()));
        List<ProposalView> proposals = m.getProposals().stream().map(p -> {
            Recommendation r = recs.get(p.getId());
            Vote v = vs.get(p.getId());
            return new ProposalView(p.getId(), p.getSeq(), p.getCategory(), p.getTitle(), p.getDescription(),
                p.getBoardRecommendation(), p.getPayScore(), p.getBoardIndependencePct(), p.getAiSummary(), p.getAiSummarySource(),
                r == null ? null : new RecommendationView(r.getDecision(), r.getRationale(), r.getRulePriority()),
                v == null ? null : new VoteView(v.getDecision(), v.getSubmittedBy(), v.getSubmittedAt(), v.getVersion()));
        }).toList();
        return new MeetingDetail(summary(m, clock.instant(), recs, vs), proposals);
    }

    private MeetingSummary summary(Meeting m, Instant now, Map<Long, Recommendation> calls, Map<Long, Vote> votes) {
        List<Segment> segments = m.getProposals().stream()
            .map(p -> new Segment(p.getId(),
                calls.containsKey(p.getId()) ? calls.get(p.getId()).getDecision() : null,
                votes.containsKey(p.getId()) ? votes.get(p.getId()).getDecision() : null))
            .toList();
        int votedCount = (int) segments.stream().filter(s -> s.vote() != null).count();
        return new MeetingSummary(m.getId(), m.getExternalId(), m.getCompany().getTicker(), m.getCompany().getName(),
            m.getCompany().getCountry(), m.getMeetingDate(), m.getVoteDeadline(), m.getMarketTimeZone(), m.getMeetingType(),
            DeadlineStatus.of(m.getVoteDeadline(), now, closingSoon), m.getProposals().size(), votedCount, segments);
    }
}
