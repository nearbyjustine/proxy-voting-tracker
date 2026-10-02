package dev.justine.proxyvote.meeting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class MeetingDtos {
    private MeetingDtos() {}

    public record MeetingSummary(Long id, String externalId, String ticker, String companyName, String country,
                                 LocalDate meetingDate, Instant voteDeadline, String marketTimeZone, String meetingType,
                                 DeadlineStatus deadlineStatus, int proposalCount, int votedCount, List<Segment> segments) {}

    /** One proposal on the board's result bar: the policy's call and this organisation's vote (either may be null). */
    public record Segment(Long proposalId, VoteDecision call, VoteDecision vote) {}

    public record RecommendationView(VoteDecision decision, String rationale, Integer rulePriority) {}

    public record VoteView(VoteDecision decision, String submittedBy, Instant submittedAt, long version) {}

    public record ProposalView(Long id, int seq, ProposalCategory category, String title, String description,
                               VoteDecision boardRecommendation, Integer payScore, Integer boardIndependencePct,
                               String aiSummary, String aiSummarySource,
                               RecommendationView recommendation, VoteView vote) {}

    public record MeetingDetail(MeetingSummary meeting, List<ProposalView> proposals) {}
}
