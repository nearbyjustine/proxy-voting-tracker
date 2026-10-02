package dev.justine.proxyvote.meeting;

import jakarta.persistence.*;

@Entity
public class Proposal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id")
    private Meeting meeting;

    @Column(nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProposalCategory category;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VoteDecision boardRecommendation;

    private Integer payScore;
    private Integer boardIndependencePct;

    @Column(columnDefinition = "TEXT")
    private String aiSummary;
    @Column(length = 20)
    private String aiSummarySource;

    protected Proposal() {}

    Proposal(Meeting meeting, int seq) {
        this.meeting = meeting;
        this.seq = seq;
    }

    /** Test/demo constructor for a detached proposal (no meeting). */
    public static Proposal of(ProposalCategory category, String title, VoteDecision board, Integer payScore, Integer independence) {
        Proposal p = new Proposal();
        p.update(category, title, null, board, payScore, independence);
        return p;
    }

    public void update(ProposalCategory category, String title, String description, VoteDecision boardRecommendation,
                       Integer payScore, Integer boardIndependencePct) {
        this.category = category;
        this.title = title;
        this.description = description;
        this.boardRecommendation = boardRecommendation;
        this.payScore = payScore;
        this.boardIndependencePct = boardIndependencePct;
    }

    public void summarized(String summary, String source) {
        this.aiSummary = summary;
        this.aiSummarySource = source;
    }

    public Long getId() { return id; }
    public Meeting getMeeting() { return meeting; }
    public int getSeq() { return seq; }
    public ProposalCategory getCategory() { return category; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public VoteDecision getBoardRecommendation() { return boardRecommendation; }
    public Integer getPayScore() { return payScore; }
    public Integer getBoardIndependencePct() { return boardIndependencePct; }
    public String getAiSummary() { return aiSummary; }
    public String getAiSummarySource() { return aiSummarySource; }
}
