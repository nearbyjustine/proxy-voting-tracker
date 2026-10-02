package dev.justine.proxyvote.ingest;

import dev.justine.proxyvote.meeting.*;
import dev.justine.proxyvote.voting.RecommendationService;
import java.time.Clock;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent import. Two layers of protection against duplicate delivery (SQS is at-least-once):
 *  1. the message ID is recorded in processed_message in the same transaction as the import;
 *  2. meetings/proposals are UPSERTED by business key (externalId, seq), so even a re-sent file is safe.
 */
@Service
public class MeetingImportService {
    private static final Logger log = LoggerFactory.getLogger(MeetingImportService.class);

    private final CompanyRepository companies;
    private final MeetingRepository meetings;
    private final ProcessedMessageRepository processed;
    private final RecommendationService recommendations;
    private final Clock clock;

    public MeetingImportService(CompanyRepository companies, MeetingRepository meetings, ProcessedMessageRepository processed,
                                RecommendationService recommendations, Clock clock) {
        this.companies = companies;
        this.meetings = meetings;
        this.processed = processed;
        this.recommendations = recommendations;
        this.clock = clock;
    }

    /** @return false if this message was already processed (duplicate delivery) */
    @Transactional
    public boolean importOnce(String messageId, MeetingMessage msg) {
        if (processed.existsById(messageId)) {
            log.info("Duplicate delivery of message {} ignored", messageId);
            return false;
        }
        ZoneId.of(msg.marketTimeZone());   // validate early: a bad zone should fail (and DLQ), not be stored
        Company company = companies.findByTicker(msg.ticker())
            .orElseGet(() -> companies.save(new Company(msg.ticker(), msg.companyName(), msg.country())));
        company.rename(msg.companyName(), msg.country());

        Meeting meeting = meetings.findByExternalId(msg.externalId())
            .orElseGet(() -> new Meeting(msg.externalId(), company));
        meeting.schedule(msg.meetingDate(), msg.voteDeadline(), msg.marketTimeZone(), msg.meetingType(), clock.instant());
        for (var p : msg.proposals()) {
            meeting.upsertProposal(p.seq()).update(ProposalCategory.valueOf(p.category()), p.title(), p.description(),
                VoteDecision.valueOf(p.boardRecommendation()), p.payScore(), p.boardIndependencePct());
        }
        meetings.saveAndFlush(meeting);
        recommendations.regenerateForMeeting(meeting);
        processed.save(new ProcessedMessage(messageId, clock.instant()));
        log.info("Imported meeting {} ({} proposals)", msg.externalId(), msg.proposals().size());
        return true;
    }
}
