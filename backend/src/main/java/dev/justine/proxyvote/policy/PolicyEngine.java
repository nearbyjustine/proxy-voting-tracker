package dev.justine.proxyvote.policy;

import dev.justine.proxyvote.meeting.Proposal;
import dev.justine.proxyvote.meeting.VoteDecision;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Evaluates a policy against a proposal: rules in priority order, FIRST MATCH WINS (like a firewall).
 * If nothing matches, follow the board, so every proposal always gets an explained recommendation.
 * Pure logic, no database: the whole engine is unit-testable in milliseconds.
 */
@Component
public class PolicyEngine {

    public record Evaluation(VoteDecision decision, String rationale, Integer rulePriority) {}

    public Evaluation evaluate(List<PolicyRule> rulesInPriorityOrder, Proposal proposal) {
        for (PolicyRule rule : rulesInPriorityOrder) {
            if (rule.condition().isSatisfiedBy(proposal)) {
                return new Evaluation(rule.getDecision(), rule.explain(proposal), rule.getPriority());
            }
        }
        return new Evaluation(proposal.getBoardRecommendation(),
            "No policy rule matched; following the board recommendation", null);
    }
}
