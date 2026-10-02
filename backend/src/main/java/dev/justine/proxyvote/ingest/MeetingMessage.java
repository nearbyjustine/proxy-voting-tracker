package dev.justine.proxyvote.ingest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Same contract the Lambda produces. In a larger system this would live in a shared schema (JSON Schema/Avro). */
public record MeetingMessage(
    String externalId, String ticker, String companyName, String country,
    LocalDate meetingDate, Instant voteDeadline, String marketTimeZone, String meetingType,
    List<ProposalMessage> proposals) {

    public record ProposalMessage(int seq, String category, String title, String description,
                                  String boardRecommendation, Integer payScore, Integer boardIndependencePct) {}
}
