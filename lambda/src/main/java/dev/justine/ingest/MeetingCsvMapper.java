package dev.justine.ingest;

import dev.justine.ingest.MeetingMessage.ProposalMessage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Turns CSV rows (one row per proposal) into one MeetingMessage per meeting.
 * Bad rows are skipped and reported instead of failing the whole file: one typo shouldn't block 500 meetings.
 */
public final class MeetingCsvMapper {

    static final List<String> HEADER = List.of("externalId", "ticker", "companyName", "country", "meetingDate",
        "voteDeadline", "marketTimeZone", "meetingType", "seq", "category", "title", "description",
        "boardRecommendation", "payScore", "boardIndependencePct");

    static final Set<String> CATEGORIES = Set.of("DIRECTOR_ELECTION", "SAY_ON_PAY", "AUDITOR", "MERGER",
        "SHAREHOLDER_ENV", "SHAREHOLDER_SOCIAL", "OTHER");

    public record Result(List<MeetingMessage> meetings, List<String> errors) {}

    public static Result map(List<List<String>> rows) {
        List<String> errors = new ArrayList<>();
        if (rows.isEmpty()) return new Result(List.of(), List.of("empty file"));
        List<String> header = rows.get(0).stream().map(String::trim).toList();
        if (!header.equals(HEADER)) {
            return new Result(List.of(), List.of("unexpected header; expected " + String.join(",", HEADER)));
        }
        Map<String, List<List<String>>> byMeeting = new LinkedHashMap<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            String problem = validate(row);
            if (problem != null) {
                errors.add("row " + (r + 1) + ": " + problem);
                continue;
            }
            byMeeting.computeIfAbsent(row.get(0).trim(), k -> new ArrayList<>()).add(row);
        }
        List<MeetingMessage> meetings = new ArrayList<>();
        for (var entry : byMeeting.entrySet()) {
            List<String> first = entry.getValue().get(0);
            List<ProposalMessage> proposals = entry.getValue().stream()
                .map(row -> new ProposalMessage(Integer.parseInt(row.get(8).trim()), row.get(9).trim(), row.get(10).trim(),
                    row.get(11).trim(), row.get(12).trim(), intOrNull(row.get(13)), intOrNull(row.get(14))))
                .sorted(Comparator.comparingInt(ProposalMessage::seq))
                .toList();
            meetings.add(new MeetingMessage(entry.getKey(), first.get(1).trim(), first.get(2).trim(), first.get(3).trim(),
                LocalDate.parse(first.get(4).trim()), Instant.parse(first.get(5).trim()), first.get(6).trim(),
                first.get(7).trim(), proposals));
        }
        return new Result(meetings, errors);
    }

    private static String validate(List<String> row) {
        if (row.size() != HEADER.size()) return "expected " + HEADER.size() + " columns, got " + row.size();
        for (int i : new int[] {0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 12}) {
            if (row.get(i).isBlank()) return HEADER.get(i) + " is required";
        }
        try {
            LocalDate.parse(row.get(4).trim());
            Instant.parse(row.get(5).trim());
            ZoneId.of(row.get(6).trim());
            Integer.parseInt(row.get(8).trim());
            intOrNull(row.get(13));
            intOrNull(row.get(14));
        } catch (RuntimeException e) {
            return "invalid value (" + e.getMessage() + ")";
        }
        if (!CATEGORIES.contains(row.get(9).trim())) return "unknown category " + row.get(9);
        if (!Set.of("FOR", "AGAINST").contains(row.get(12).trim())) return "boardRecommendation must be FOR or AGAINST";
        return null;
    }

    private static Integer intOrNull(String s) {
        return s == null || s.isBlank() ? null : Integer.valueOf(s.trim());
    }
}
