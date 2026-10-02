package dev.justine.proxyvote.voting;

import dev.justine.proxyvote.meeting.Organization;
import dev.justine.proxyvote.meeting.Proposal;
import dev.justine.proxyvote.meeting.VoteDecision;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Vote {
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

    @Column(nullable = false, length = 50)
    private String submittedBy;
    @Column(nullable = false)
    private Instant submittedAt;

    @Version
    private long version;

    protected Vote() {}

    public Vote(Organization organization, Proposal proposal) {
        this.organization = organization;
        this.proposal = proposal;
    }

    public void cast(VoteDecision decision, String by, Instant at) {
        this.decision = decision;
        this.submittedBy = by;
        this.submittedAt = at;
    }

    public Long getId() { return id; }
    public Proposal getProposal() { return proposal; }
    public VoteDecision getDecision() { return decision; }
    public String getSubmittedBy() { return submittedBy; }
    public Instant getSubmittedAt() { return submittedAt; }
    public long getVersion() { return version; }
}
