package dev.justine.proxyvote.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(List<String> corsAllowedOrigins, Aws aws, Ingest ingest, Ai ai, Voting voting) {

    public record Aws(String region, String endpoint, String publicEndpoint, String ingestBucket, String queueName, Duration presignTtl) {}

    public record Ingest(boolean consumerEnabled, int pollWaitSeconds) {}

    public record Ai(String anthropicApiKey, String model, Duration timeout) {}

    public record Voting(int closingSoonHours) {}
}
