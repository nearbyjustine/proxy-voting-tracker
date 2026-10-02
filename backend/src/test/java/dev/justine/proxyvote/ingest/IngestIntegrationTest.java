package dev.justine.proxyvote.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.justine.proxyvote.TestcontainersConfiguration;
import dev.justine.proxyvote.meeting.MeetingRepository;
import dev.justine.proxyvote.meeting.OrganizationRepository;
import dev.justine.proxyvote.voting.RecommendationRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.json.JsonMapper;

/** Real Postgres + real SQS (LocalStack): the consumer, idempotency and dead-lettering, end to end. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IngestIntegrationTest {

    @Autowired SqsClient sqs;
    @Autowired JsonMapper json;
    @Autowired MeetingRepository meetings;
    @Autowired RecommendationRepository recommendations;
    @Autowired OrganizationRepository organizations;
    @Autowired MeetingImportService importer;
    @Autowired TransactionTemplate tx;

    private static MeetingMessage message(String externalId, int payScore) {
        return new MeetingMessage(externalId, "TEST", "Test Corp", "US", LocalDate.now().plusDays(20),
            Instant.now().plus(Duration.ofDays(15)), "America/New_York", "AGM",
            List.of(new MeetingMessage.ProposalMessage(1, "SAY_ON_PAY", "Pay", "Pay plan.", "FOR", payScore, null)));
    }

    private String queueUrl(String name) {
        return sqs.getQueueUrl(b -> b.queueName(name)).queueUrl();
    }

    @Test
    void consumerImportsMeetingAndGeneratesRecommendationsForEveryOrg() {
        sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl("test-events"))
            .messageBody(json.writeValueAsString(message("IT-PAY-1", 20))).build());

        await().atMost(Duration.ofSeconds(30)).until(() -> meetings.findByExternalId("IT-PAY-1").isPresent());

        var steward = organizations.findByCode("STEWARD").orElseThrow();
        Long proposalId = tx.execute(s -> meetings.findByExternalId("IT-PAY-1").orElseThrow().getProposals().get(0).getId());
        var rec = recommendations.findByOrganizationIdAndProposalId(steward.getId(), proposalId).orElseThrow();
        assertThat(rec.getDecision().name()).isEqualTo("AGAINST");   // stewardship policy: pay score 20 < 50
    }

    @Test
    void duplicateDeliveryIsIgnoredAndReimportDoesNotDuplicateProposals() {
        assertThat(importer.importOnce("msg-dup-1", message("IT-DUP-1", 60))).isTrue();
        assertThat(importer.importOnce("msg-dup-1", message("IT-DUP-1", 60))).isFalse();      // same SQS message again
        assertThat(importer.importOnce("msg-dup-2", message("IT-DUP-1", 70))).isTrue();       // same meeting, new data
        int proposals = tx.execute(s -> meetings.findByExternalId("IT-DUP-1").orElseThrow().getProposals().size());
        assertThat(proposals).isEqualTo(1);
    }

    @Test
    void poisonMessageEndsUpInTheDeadLetterQueue() {
        sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl("test-events")).messageBody("{not json").build());

        await().atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofSeconds(1)).until(() -> {
            var attrs = sqs.getQueueAttributes(GetQueueAttributesRequest.builder().queueUrl(queueUrl("test-events-dlq"))
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES).build());
            return Integer.parseInt(attrs.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)) > 0;
        });
    }
}
