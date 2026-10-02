package dev.justine.proxyvote.meeting;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Meeting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(nullable = false)
    private LocalDate meetingDate;

    /** An exact instant. Time zones are only for DISPLAY (marketTimeZone), never for storage. */
    @Column(nullable = false)
    private Instant voteDeadline;

    @Column(nullable = false, length = 40)
    private String marketTimeZone;

    @Column(nullable = false, length = 5)
    private String meetingType;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq")
    private List<Proposal> proposals = new ArrayList<>();

    protected Meeting() {}

    public Meeting(String externalId, Company company) {
        this.externalId = externalId;
        this.company = company;
    }

    public void schedule(LocalDate meetingDate, Instant voteDeadline, String marketTimeZone, String meetingType, Instant now) {
        this.meetingDate = meetingDate;
        this.voteDeadline = voteDeadline;
        this.marketTimeZone = marketTimeZone;
        this.meetingType = meetingType;
        this.updatedAt = now;
    }

    /** Upsert a proposal by sequence number, so re-importing the same meeting doesn't duplicate proposals. */
    public Proposal upsertProposal(int seq) {
        return proposals.stream().filter(p -> p.getSeq() == seq).findFirst().orElseGet(() -> {
            Proposal p = new Proposal(this, seq);
            proposals.add(p);
            return p;
        });
    }

    public Long getId() { return id; }
    public String getExternalId() { return externalId; }
    public Company getCompany() { return company; }
    public LocalDate getMeetingDate() { return meetingDate; }
    public Instant getVoteDeadline() { return voteDeadline; }
    public String getMarketTimeZone() { return marketTimeZone; }
    public String getMeetingType() { return meetingType; }
    public List<Proposal> getProposals() { return proposals; }
}
