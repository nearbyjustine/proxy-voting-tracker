package dev.justine.proxyvote.policy;

import dev.justine.proxyvote.meeting.Proposal;

/**
 * The building blocks a policy admin can choose from. Each one turns a threshold into a ProposalCondition.
 * Missing data never matches (a proposal with no pay score is not "below 50").
 */
public enum ConditionType {
    PAY_SCORE_BELOW {
        ProposalCondition with(Integer threshold) {
            return p -> p.getPayScore() != null && p.getPayScore() < threshold;
        }
        String describe(Proposal p, Integer t) { return "pay score " + p.getPayScore() + " < " + t; }
    },
    BOARD_INDEPENDENCE_BELOW {
        ProposalCondition with(Integer threshold) {
            return p -> p.getBoardIndependencePct() != null && p.getBoardIndependencePct() < threshold;
        }
        String describe(Proposal p, Integer t) { return "board independence " + p.getBoardIndependencePct() + "% < " + t + "%"; }
    },
    BOARD_RECOMMENDS_AGAINST {
        ProposalCondition with(Integer threshold) {
            return p -> p.getBoardRecommendation() == dev.justine.proxyvote.meeting.VoteDecision.AGAINST;
        }
        String describe(Proposal p, Integer t) { return "board recommends against"; }
    };

    abstract ProposalCondition with(Integer threshold);

    abstract String describe(Proposal proposal, Integer threshold);

    public boolean needsThreshold() {
        return this != BOARD_RECOMMENDS_AGAINST;
    }
}
