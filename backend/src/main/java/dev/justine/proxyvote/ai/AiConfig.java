package dev.justine.proxyvote.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import dev.justine.proxyvote.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

/**
 * Picks the summarizer at startup: Claude when an API key is configured, otherwise the offline one.
 * The key comes from the environment (ANTHROPIC_API_KEY), never from source code or git.
 */
@Configuration
public class AiConfig {
    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    @Primary
    ProposalSummarizer proposalSummarizer(AppProperties props, ExtractiveSummarizer extractive) {
        if (!StringUtils.hasText(props.ai().anthropicApiKey())) {
            log.info("No ANTHROPIC_API_KEY set: proposal summaries use the offline extractive summarizer");
            return extractive;
        }
        AnthropicClient client = AnthropicOkHttpClient.builder()
            .apiKey(props.ai().anthropicApiKey())
            .timeout(props.ai().timeout())
            .maxRetries(2)          // the SDK retries 408/409/429/5xx with backoff
            .build();
        log.info("Proposal summaries use Claude ({})", props.ai().model());
        return new ClaudeProposalSummarizer(client, props, extractive);
    }
}
