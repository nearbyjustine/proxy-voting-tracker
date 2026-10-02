package dev.justine.ingest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** The SQS message contract between this Lambda and the API (one message = one meeting). */
public record MeetingMessage(
    String externalId, String ticker, String companyName, String country,
    LocalDate meetingDate, Instant voteDeadline, String marketTimeZone, String meetingType,
    List<ProposalMessage> proposals) {

    public record ProposalMessage(int seq, String category, String title, String description,
                                  String boardRecommendation, Integer payScore, Integer boardIndependencePct) {}
}
