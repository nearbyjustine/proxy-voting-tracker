package dev.justine.proxyvote.ai;

import dev.justine.proxyvote.meeting.Proposal;
import org.springframework.stereotype.Component;

/**
 * Deterministic, offline fallback: the first sentence of the description plus the key numbers.
 * Always available (no key, no network), which makes the feature testable and keeps the UI working
 * when the AI provider is down.
 */
@Component
public class ExtractiveSummarizer implements ProposalSummarizer {

    @Override
    public Summary summarize(Proposal p) {
        StringBuilder sb = new StringBuilder();
        String description = p.getDescription() == null ? "" : p.getDescription().trim();
        int end = description.indexOf(". ");
        sb.append(end > 0 ? description.substring(0, end + 1) : description);
        if (p.getPayScore() != null) sb.append(" Pay-for-performance score: ").append(p.getPayScore()).append("/100.");
        if (p.getBoardIndependencePct() != null) sb.append(" Board independence: ").append(p.getBoardIndependencePct()).append("%.");
        sb.append(" The board recommends ").append(p.getBoardRecommendation()).append('.');
        String text = sb.toString().trim();
        return new Summary(text.length() > 400 ? text.substring(0, 397) + "..." : text, "extractive");
    }
}
