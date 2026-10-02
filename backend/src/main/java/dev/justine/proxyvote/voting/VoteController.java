package dev.justine.proxyvote.voting;

import dev.justine.proxyvote.meeting.VoteDecision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/proposals")
public class VoteController {

    public record VoteRequest(@NotNull VoteDecision decision, Long version) {}

    private final VoteService votes;

    public VoteController(VoteService votes) {
        this.votes = votes;
    }

    /** PUT, not POST: casting the same vote twice leaves the same state (idempotent). */
    @PutMapping("/{id}/vote")
    public VoteService.VoteResponse vote(@PathVariable Long id, @Valid @RequestBody VoteRequest req) {
        return votes.cast(id, req.decision(), req.version());
    }
}
