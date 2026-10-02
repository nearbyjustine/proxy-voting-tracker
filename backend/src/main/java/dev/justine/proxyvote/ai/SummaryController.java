package dev.justine.proxyvote.ai;

import dev.justine.proxyvote.common.NotFoundException;
import dev.justine.proxyvote.meeting.Proposal;
import dev.justine.proxyvote.meeting.ProposalRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/proposals")
public class SummaryController {

    public record SummaryResponse(Long proposalId, String summary, String source) {}

    private final ProposalRepository proposals;
    private final ProposalSummarizer summarizer;

    public SummaryController(ProposalRepository proposals, ProposalSummarizer summarizer) {
        this.proposals = proposals;
        this.summarizer = summarizer;
    }

    /** Generated once and cached on the proposal; ?refresh=true regenerates. */
    @PostMapping("/{id}/summary")
    @Transactional
    public SummaryResponse summarize(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean refresh) {
        Proposal p = proposals.findById(id).orElseThrow(() -> new NotFoundException("entity.proposal", id));
        if (refresh || p.getAiSummary() == null) {
            var s = summarizer.summarize(p);
            p.summarized(s.text(), s.source());
        }
        return new SummaryResponse(id, p.getAiSummary(), p.getAiSummarySource());
    }
}
