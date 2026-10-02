package dev.justine.proxyvote.ingest;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class ProcessedMessage {
    @Id
    private String messageId;
    private Instant processedAt;

    protected ProcessedMessage() {}

    public ProcessedMessage(String messageId, Instant processedAt) {
        this.messageId = messageId;
        this.processedAt = processedAt;
    }
}
