package dev.justine.proxyvote.policy;

import dev.justine.proxyvote.audit.AuditService;
import dev.justine.proxyvote.common.CurrentUser;
import dev.justine.proxyvote.common.NotFoundException;
import dev.justine.proxyvote.config.Tenant;
import dev.justine.proxyvote.meeting.Organization;
import dev.justine.proxyvote.policy.PolicyDtos.*;
import dev.justine.proxyvote.voting.RecommendationService;
import java.time.Clock;
import java.util.Map;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyService {
    private final VotingPolicyRepository policies;
    private final Tenant tenant;
    private final RecommendationService recommendations;
    private final AuditService audit;
    private final Clock clock;

    public PolicyService(VotingPolicyRepository policies, Tenant tenant, RecommendationService recommendations,
                         AuditService audit, Clock clock) {
        this.policies = policies;
        this.tenant = tenant;
        this.recommendations = recommendations;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PolicyResponse current() {
        return toResponse(load(tenant.current()));
    }

    /** Saving a policy regenerates this org's recommendations in the SAME transaction: never half-updated. */
    @Transactional
    public PolicyResponse update(UpdatePolicyRequest req) {
        Organization org = tenant.current();
        VotingPolicy policy = load(org);
        if (policy.getVersion() != req.version()) throw new ObjectOptimisticLockingFailureException(VotingPolicy.class, policy.getId());
        policy.replaceRules(req.name(), req.rules().stream()
            .map(r -> new PolicyRule(r.priority(), r.category(), r.conditionType(), r.threshold(), r.decision(), r.rationale()))
            .toList(), CurrentUser.username().orElse("system"), clock.instant());
        policies.flush();
        int regenerated = recommendations.regenerateForOrganization(org);
        audit.record(org, "POLICY_UPDATED", "VotingPolicy", policy.getId(),
            Map.of("rules", req.rules().size(), "recommendationsRegenerated", regenerated));
        return toResponse(policy);
    }

    @Transactional
    public int recalculate() {
        Organization org = tenant.current();
        int n = recommendations.regenerateForOrganization(org);
        audit.record(org, "RECOMMENDATIONS_RECALCULATED", "VotingPolicy", load(org).getId(), Map.of("count", n));
        return n;
    }

    private VotingPolicy load(Organization org) {
        return policies.findByOrganizationId(org.getId()).orElseThrow(() -> new NotFoundException("entity.policy", org.getCode()));
    }

    private static PolicyResponse toResponse(VotingPolicy p) {
        return new PolicyResponse(p.getName(), p.getVersion(), p.getUpdatedAt(), p.getUpdatedBy(),
            p.getRules().stream().map(RuleDto::from).toList());
    }
}
