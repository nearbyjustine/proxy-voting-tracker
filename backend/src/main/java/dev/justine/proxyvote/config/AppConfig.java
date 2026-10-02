package dev.justine.proxyvote.config;

import java.net.URI;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SqsClient sqsClient(AppProperties props) {
        var b = SqsClient.builder().region(Region.of(props.aws().region())).credentialsProvider(credentials(props));
        if (StringUtils.hasText(props.aws().endpoint())) b.endpointOverride(URI.create(props.aws().endpoint()));
        return b.build();
    }

    /** Pre-signed PUT URLs are signed for the host the BROWSER uploads to. */
    @Bean
    S3Presigner s3Presigner(AppProperties props) {
        var b = S3Presigner.builder().region(Region.of(props.aws().region())).credentialsProvider(credentials(props));
        String endpoint = StringUtils.hasText(props.aws().publicEndpoint()) ? props.aws().publicEndpoint() : props.aws().endpoint();
        if (StringUtils.hasText(endpoint)) {
            b.endpointOverride(URI.create(endpoint))
             .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return b.build();
    }

    private static AwsCredentialsProvider credentials(AppProperties props) {
        if (StringUtils.hasText(props.aws().endpoint())) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
