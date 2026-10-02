package dev.justine.proxyvote.policy;

import dev.justine.proxyvote.meeting.Organization;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
public class VotingPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(nullable = false, length = 100)
    private String name;

    @Version
    private long version;

    @Column(nullable = false)
    private Instant updatedAt;
    @Column(nullable = false, length = 50)
    private String updatedBy;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("priority")
    private List<PolicyRule> rules = new ArrayList<>();

    protected VotingPolicy() {}

    /** Replace all rules at once (the editor saves the whole list); priorities are renumbered 1..n. */
    public void replaceRules(String name, List<PolicyRule> newRules, String by, Instant at) {
        this.name = name;
        rules.clear();
        int priority = 1;
        for (PolicyRule r : newRules.stream().sorted(Comparator.comparingInt(PolicyRule::getPriority)).toList()) {
            r.attach(this, priority++);
            rules.add(r);
        }
        this.updatedBy = by;
        this.updatedAt = at;
    }

    public Long getId() { return id; }
    public Organization getOrganization() { return organization; }
    public String getName() { return name; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public List<PolicyRule> getRules() { return rules; }
}
