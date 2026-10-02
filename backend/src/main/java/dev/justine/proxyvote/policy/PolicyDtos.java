package dev.justine.proxyvote.policy;

import dev.justine.proxyvote.meeting.ProposalCategory;
import dev.justine.proxyvote.meeting.VoteDecision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class PolicyDtos {
    private PolicyDtos() {}

    public record RuleDto(int priority, ProposalCategory category, ConditionType conditionType,
                          @Min(0) @Max(100) Integer threshold, @NotNull VoteDecision decision,
                          @NotBlank @Size(max = 300) String rationale) {
        static RuleDto from(PolicyRule r) {
            return new RuleDto(r.getPriority(), r.getCategory(), r.getConditionType(), r.getThreshold(), r.getDecision(), r.getRationale());
        }

        @AssertTrue(message = "threshold is required for this condition")
        public boolean isThresholdPresentWhenNeeded() {
            return conditionType == null || !conditionType.needsThreshold() || threshold != null;
        }
    }

    public record PolicyResponse(String name, long version, Instant updatedAt, String updatedBy, List<RuleDto> rules) {}

    public record UpdatePolicyRequest(@NotBlank @Size(max = 100) String name, @NotNull Long version,
                                      @NotNull @Size(max = 50) List<@Valid RuleDto> rules) {}
}
