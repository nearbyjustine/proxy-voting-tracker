package dev.justine.proxyvote.voting;

import dev.justine.proxyvote.meeting.OrganizationRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** On startup, make sure every org has recommendations for open meetings (e.g. for seeded data). Idempotent. */
@Component
public class RecommendationBootstrap implements ApplicationRunner {
    private final OrganizationRepository organizations;
    private final RecommendationService recommendations;
    private final TransactionTemplate tx;

    public RecommendationBootstrap(OrganizationRepository organizations, RecommendationService recommendations,
                                   TransactionTemplate tx) {
        this.organizations = organizations;
        this.recommendations = recommendations;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        tx.executeWithoutResult(s -> organizations.findAll().forEach(recommendations::regenerateForOrganization));
    }
}
