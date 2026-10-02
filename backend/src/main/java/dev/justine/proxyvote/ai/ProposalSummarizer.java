package dev.justine.proxyvote.ai;

import dev.justine.proxyvote.meeting.Proposal;

/** Port for summarization; the app depends on this interface, not on a specific AI vendor. */
public interface ProposalSummarizer {

    record Summary(String text, String source) {}

    Summary summarize(Proposal proposal);
}
