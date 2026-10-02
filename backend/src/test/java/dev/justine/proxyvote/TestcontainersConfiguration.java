package dev.justine.proxyvote;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    /** SQS queue + DLQ with maxReceiveCount=2 and a 1s visibility timeout, so dead-lettering happens fast in tests. */
    @Bean
    LocalStackContainer localStackContainer() throws Exception {
        LocalStackContainer ls = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.9")).withServices("sqs", "s3");
        ls.start();
        ls.execInContainer("awslocal", "sqs", "create-queue", "--queue-name", "test-events-dlq");
        ls.execInContainer("awslocal", "sqs", "create-queue", "--queue-name", "test-events", "--attributes",
            "{\"VisibilityTimeout\":\"1\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"arn:aws:sqs:"
                + ls.getRegion() + ":000000000000:test-events-dlq\\\",\\\"maxReceiveCount\\\":\\\"2\\\"}\"}");
        return ls;
    }

    @Bean
    DynamicPropertyRegistrar awsProperties(LocalStackContainer localstack) {
        return registry -> {
            registry.add("app.aws.endpoint", () -> localstack.getEndpoint().toString());
            registry.add("app.aws.public-endpoint", () -> localstack.getEndpoint().toString());
            registry.add("app.aws.region", localstack::getRegion);
            registry.add("app.aws.queue-name", () -> "test-events");
            registry.add("app.ingest.poll-wait-seconds", () -> "1");
        };
    }
}
