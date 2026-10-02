package dev.justine.proxyvote.policy;

import static dev.justine.proxyvote.meeting.ProposalCategory.*;
import static dev.justine.proxyvote.meeting.VoteDecision.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.justine.proxyvote.meeting.Proposal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyEngineTest {

    private final PolicyEngine engine = new PolicyEngine();

    private final List<PolicyRule> stewardship = List.of(
        new PolicyRule(1, SAY_ON_PAY, ConditionType.PAY_SCORE_BELOW, 50, AGAINST, "Pay misaligned"),
        new PolicyRule(2, DIRECTOR_ELECTION, ConditionType.BOARD_INDEPENDENCE_BELOW, 50, AGAINST, "Board not independent"),
        new PolicyRule(3, SHAREHOLDER_ENV, null, null, FOR, "Supports climate disclosure"));

    @Test
    void firstMatchingRuleWinsAndExplainsWhy() {
        var e = engine.evaluate(stewardship, Proposal.of(SAY_ON_PAY, "Pay", FOR, 31, null));
        assertThat(e.decision()).isEqualTo(AGAINST);
        assertThat(e.rulePriority()).isEqualTo(1);
        assertThat(e.rationale()).contains("pay score 31 < 50");
    }

    @Test
    void conditionMustHoldNotJustCategory() {
        var e = engine.evaluate(stewardship, Proposal.of(SAY_ON_PAY, "Pay", FOR, 78, null));
        assertThat(e.decision()).isEqualTo(FOR);
        assertThat(e.rulePriority()).isNull();
        assertThat(e.rationale()).contains("following the board");
    }

    @Test
    void missingDataNeverMatchesAThreshold() {
        // No independence figure: "below 50%" must not be assumed.
        var e = engine.evaluate(stewardship, Proposal.of(DIRECTOR_ELECTION, "Elect", FOR, null, null));
        assertThat(e.decision()).isEqualTo(FOR);
    }

    @Test
    void categoryOnlyRuleCanOverrideTheBoard() {
        var e = engine.evaluate(stewardship, Proposal.of(SHAREHOLDER_ENV, "Climate", AGAINST, null, null));
        assertThat(e.decision()).isEqualTo(FOR);
        assertThat(e.rulePriority()).isEqualTo(3);
    }

    @Test
    void priorityOrderMatters() {
        List<PolicyRule> rules = List.of(
            new PolicyRule(1, null, ConditionType.BOARD_RECOMMENDS_AGAINST, null, ABSTAIN, "Review manually"),
            new PolicyRule(2, SHAREHOLDER_ENV, null, null, FOR, "Supports climate"));
        var e = engine.evaluate(rules, Proposal.of(SHAREHOLDER_ENV, "Climate", AGAINST, null, null));
        assertThat(e.decision()).isEqualTo(ABSTAIN);
    }

    @Test
    void specificationsCompose() {
        ProposalCondition lowPay = ConditionType.PAY_SCORE_BELOW.with(50);
        ProposalCondition isPay = p -> p.getCategory() == SAY_ON_PAY;
        Proposal p = Proposal.of(SAY_ON_PAY, "Pay", FOR, 40, null);
        assertThat(isPay.and(lowPay).isSatisfiedBy(p)).isTrue();
        assertThat(isPay.and(lowPay.not()).isSatisfiedBy(p)).isFalse();
        assertThat(lowPay.not().or(isPay).isSatisfiedBy(p)).isTrue();
    }
}
