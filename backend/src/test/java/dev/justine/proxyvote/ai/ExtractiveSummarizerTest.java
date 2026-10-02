package dev.justine.proxyvote.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.justine.proxyvote.meeting.Proposal;
import dev.justine.proxyvote.meeting.ProposalCategory;
import dev.justine.proxyvote.meeting.VoteDecision;
import org.junit.jupiter.api.Test;

class ExtractiveSummarizerTest {

    @Test
    void usesFirstSentenceAndKeyFacts() {
        Proposal p = Proposal.of(ProposalCategory.SAY_ON_PAY, "Pay", VoteDecision.FOR, 31, null);
        p.update(ProposalCategory.SAY_ON_PAY, "Pay", "Retention award of 3x salary. Dividend was cut.", VoteDecision.FOR, 31, null);

        var s = new ExtractiveSummarizer().summarize(p);

        assertThat(s.source()).isEqualTo("extractive");
        assertThat(s.text()).startsWith("Retention award of 3x salary.").contains("31/100").doesNotContain("Dividend");
    }
}
