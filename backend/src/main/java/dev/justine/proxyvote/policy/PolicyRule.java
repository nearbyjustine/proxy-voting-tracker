package dev.justine.proxyvote.policy;

import dev.justine.proxyvote.meeting.Proposal;
import dev.justine.proxyvote.meeting.ProposalCategory;
import dev.justine.proxyvote.meeting.VoteDecision;
import jakarta.persistence.*;

/** "IF category = X [AND condition(threshold)] THEN decision BECAUSE rationale". */
@Entity
public class PolicyRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id")
    private VotingPolicy policy;

    @Column(nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ProposalCategory category;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private ConditionType conditionType;

    private Integer threshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VoteDecision decision;

    @Column(nullable = false, length = 300)
    private String rationale;

    protected PolicyRule() {}

    public PolicyRule(int priority, ProposalCategory category, ConditionType conditionType, Integer threshold,
                      VoteDecision decision, String rationale) {
        this.priority = priority;
        this.category = category;
        this.conditionType = conditionType;
        this.threshold = threshold;
        this.decision = decision;
        this.rationale = rationale;
    }

    void attach(VotingPolicy policy, int priority) {
        this.policy = policy;
        this.priority = priority;
    }

    /** Builds the composed Specification for this rule. */
    public ProposalCondition condition() {
        ProposalCondition c = category == null ? ProposalCondition.always() : p -> p.getCategory() == category;
        if (conditionType != null) c = c.and(conditionType.with(threshold));
        return c;
    }

    public String explain(Proposal p) {
        String why = conditionType == null ? (category == null ? "default rule" : "category is " + category)
            : conditionType.describe(p, threshold);
        return rationale + " (rule " + priority + ": " + why + ")";
    }

    public Long getId() { return id; }
    public int getPriority() { return priority; }
    public ProposalCategory getCategory() { return category; }
    public ConditionType getConditionType() { return conditionType; }
    public Integer getThreshold() { return threshold; }
    public VoteDecision getDecision() { return decision; }
    public String getRationale() { return rationale; }
}
