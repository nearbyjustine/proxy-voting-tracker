package dev.justine.proxyvote.policy;

import dev.justine.proxyvote.meeting.Proposal;

/**
 * SPECIFICATION pattern: a business rule as a small object that answers "does this proposal match?".
 * Conditions compose with and/or/not, so complex rules are built from tested pieces
 * instead of one growing if/else block.
 */
@FunctionalInterface
public interface ProposalCondition {

    boolean isSatisfiedBy(Proposal proposal);

    default ProposalCondition and(ProposalCondition other) {
        return p -> isSatisfiedBy(p) && other.isSatisfiedBy(p);
    }

    default ProposalCondition or(ProposalCondition other) {
        return p -> isSatisfiedBy(p) || other.isSatisfiedBy(p);
    }

    default ProposalCondition not() {
        return p -> !isSatisfiedBy(p);
    }

    static ProposalCondition always() {
        return p -> true;
    }
}
