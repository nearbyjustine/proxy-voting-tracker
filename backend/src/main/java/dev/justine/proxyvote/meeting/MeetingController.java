package dev.justine.proxyvote.meeting;

import dev.justine.proxyvote.meeting.MeetingDtos.*;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class MeetingController {
    private final MeetingService meetings;
    private final OrganizationRepository organizations;

    public MeetingController(MeetingService meetings, OrganizationRepository organizations) {
        this.meetings = meetings;
        this.organizations = organizations;
    }

    @GetMapping("/meetings")
    public List<MeetingSummary> list() {
        return meetings.list();
    }

    @GetMapping("/meetings/{id}")
    public MeetingDetail detail(@PathVariable Long id) {
        return meetings.detail(id);
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt, JwtAuthenticationToken auth) {
        String org = jwt.getClaimAsString("org");
        return Map.of(
            "username", jwt.getClaimAsString("preferred_username"),
            "name", String.valueOf(jwt.getClaimAsString("name")),
            "org", org == null ? "" : org,
            "orgName", org == null ? "" : organizations.findByCode(org).map(Organization::getName).orElse(""),
            "roles", auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).sorted().toList());
    }
}
