package dev.justine.proxyvote.ingest;

import dev.justine.proxyvote.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Long-polls SQS and imports meetings.
 *
 * The contract: a message is DELETED only after it was imported (or recognised as a duplicate).
 * If import throws, the message is left alone: after the visibility timeout SQS redelivers it,
 * and after maxReceiveCount (3) failures SQS moves it to the dead-letter queue for a human to inspect.
 */
@Component
@ConditionalOnProperty(name = "app.ingest.consumer-enabled", havingValue = "true")
public class SqsMeetingConsumer {
    private static final Logger log = LoggerFactory.getLogger(SqsMeetingConsumer.class);

    private final SqsClient sqs;
    private final MeetingImportService importer;
    private final JsonMapper json;
    private final AppProperties props;
    private volatile String queueUrl;

    public SqsMeetingConsumer(SqsClient sqs, MeetingImportService importer, JsonMapper json, AppProperties props) {
        this.sqs = sqs;
        this.importer = importer;
        this.json = json;
        this.props = props;
    }

    @Scheduled(fixedDelay = 500, initialDelay = 3000)
    public void poll() {
        try {
            var response = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl())
                .maxNumberOfMessages(10)
                .waitTimeSeconds(props.ingest().pollWaitSeconds())   // long polling: fewer empty, billed requests
                .build());
            for (Message m : response.messages()) handle(m);
        } catch (RuntimeException e) {
            log.warn("SQS poll failed: {}", e.getMessage());
        }
    }

    void handle(Message m) {
        try {
            MeetingMessage msg = json.readValue(m.body(), MeetingMessage.class);
            importer.importOnce(m.messageId(), msg);
            sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl()).receiptHandle(m.receiptHandle()).build());
        } catch (RuntimeException e) {
            log.error("Failed to import message {} (will be retried, then dead-lettered): {}", m.messageId(), e.getMessage());
        }
    }

    private String queueUrl() {
        if (queueUrl == null) {
            queueUrl = sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(props.aws().queueName()).build()).queueUrl();
        }
        return queueUrl;
    }
}
