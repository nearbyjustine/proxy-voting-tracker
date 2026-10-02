package dev.justine.proxyvote.config;

import dev.justine.proxyvote.common.CurrentUser;
import dev.justine.proxyvote.common.ForbiddenException;
import dev.justine.proxyvote.meeting.Organization;
import dev.justine.proxyvote.meeting.OrganizationRepository;
import org.springframework.stereotype.Component;

/**
 * Resolves the caller's organisation (tenant) from the JWT "org" claim.
 * Every org-scoped query goes through this, so one investor can never read another's votes or policy.
 */
@Component
public class Tenant {
    private final OrganizationRepository organizations;

    public Tenant(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    public Organization current() {
        String code = CurrentUser.orgCode().orElseThrow(() -> new ForbiddenException("error.noTenant"));
        return organizations.findByCode(code).orElseThrow(() -> new ForbiddenException("error.noTenant"));
    }
}
