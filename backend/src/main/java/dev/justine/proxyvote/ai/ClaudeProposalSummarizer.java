package dev.justine.proxyvote.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import dev.justine.proxyvote.config.AppProperties;
import dev.justine.proxyvote.meeting.Proposal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Summarizes a proposal with Claude. Any failure (rate limit, refusal, outage, timeout) falls back to
 * the extractive summary, so a third-party API problem degrades the feature instead of breaking the page.
 */
public class ClaudeProposalSummarizer implements ProposalSummarizer {
    private static final Logger log = LoggerFactory.getLogger(ClaudeProposalSummarizer.class);

    private static final String SYSTEM = """
        You summarize shareholder-meeting proposals for institutional investors.
        Write at most two plain sentences: what the proposal asks for, and the single most decision-relevant fact.
        Be neutral. Do not recommend how to vote. No preamble, no markdown.""";

    private final AnthropicClient client;
    private final AppProperties props;
    private final ProposalSummarizer fallback;

    public ClaudeProposalSummarizer(AnthropicClient client, AppProperties props, ProposalSummarizer fallback) {
        this.client = client;
        this.props = props;
        this.fallback = fallback;
    }

    @Override
    public Summary summarize(Proposal p) {
        String input = "Category: " + p.getCategory() + "\nTitle: " + p.getTitle()
            + "\nDescription: " + (p.getDescription() == null ? "(none)" : p.getDescription())
            + (p.getPayScore() == null ? "" : "\nPay-for-performance score (0-100): " + p.getPayScore())
            + (p.getBoardIndependencePct() == null ? "" : "\nBoard independence: " + p.getBoardIndependencePct() + "%")
            + "\nBoard recommendation: " + p.getBoardRecommendation();
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                .model(props.ai().model())
                .maxTokens(4000L)
                .system(SYSTEM)
                .addUserMessage(input)
                // Short summarization: low effort keeps latency and cost down without hurting quality here.
                .outputConfig(com.anthropic.models.beta.messages.BetaOutputConfig.builder()
                    .effort(com.anthropic.models.beta.messages.BetaOutputConfig.Effort.LOW).build())
                // Server-side refusal fallback: if this model's safety classifiers decline the request,
                // the API re-runs it on Anthropic's recommended fallback model instead of returning a refusal.
                .addBeta("server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();
            BetaMessage response = client.beta().messages().create(params);

            if (response.stopReason().map(r -> r.toString().equals("refusal")).orElse(false)) {
                log.warn("Claude declined to summarize proposal {}; using extractive summary", p.getId());
                return fallback.summarize(p);
            }
            String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .reduce("", String::concat)
                .trim();
            return text.isEmpty() ? fallback.summarize(p) : new Summary(text, "claude");
        } catch (RateLimitException e) {
            log.warn("Claude rate limit hit; using extractive summary");
        } catch (AnthropicServiceException e) {
            log.warn("Claude API error {}: {}", e.statusCode(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Claude call failed ({}); using extractive summary", e.toString());
        }
        return fallback.summarize(p);
    }
}
