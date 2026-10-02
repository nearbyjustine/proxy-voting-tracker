package dev.justine.proxyvote.voting;

import dev.justine.proxyvote.meeting.Organization;
import dev.justine.proxyvote.meeting.Proposal;
import dev.justine.proxyvote.meeting.VoteDecision;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Recommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id")
    private Proposal proposal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VoteDecision decision;

    @Column(nullable = false, length = 500)
    private String rationale;

    private Integer rulePriority;

    @Column(nullable = false)
    private Instant generatedAt;

    protected Recommendation() {}

    public Recommendation(Organization organization, Proposal proposal) {
        this.organization = organization;
        this.proposal = proposal;
    }

    public void set(VoteDecision decision, String rationale, Integer rulePriority, Instant at) {
        this.decision = decision;
        this.rationale = rationale.length() > 500 ? rationale.substring(0, 500) : rationale;
        this.rulePriority = rulePriority;
        this.generatedAt = at;
    }

    public Long getId() { return id; }
    public Proposal getProposal() { return proposal; }
    public VoteDecision getDecision() { return decision; }
    public String getRationale() { return rationale; }
    public Integer getRulePriority() { return rulePriority; }
    public Instant getGeneratedAt() { return generatedAt; }
}
