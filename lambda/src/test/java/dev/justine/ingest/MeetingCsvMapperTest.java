package dev.justine.ingest;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MeetingCsvMapperTest {

    static final String HEADER = String.join(",", MeetingCsvMapper.HEADER);

    @Test
    void groupsRowsIntoMeetingsSortedBySeq() {
        String csv = HEADER + "\n"
            + "M1,ACME,Acme Corp,US,2026-11-20,2026-11-18T21:00:00Z,America/New_York,AGM,2,SAY_ON_PAY,Approve pay,Pay plan,FOR,42,\n"
            + "M1,ACME,Acme Corp,US,2026-11-20,2026-11-18T21:00:00Z,America/New_York,AGM,1,DIRECTOR_ELECTION,Elect Jane,\"Jane, CFO\",FOR,,45\n";
        var result = MeetingCsvMapper.map(CsvParser.parse(csv));

        assertTrue(result.errors().isEmpty(), result.errors().toString());
        assertEquals(1, result.meetings().size());
        var m = result.meetings().get(0);
        assertEquals(2, m.proposals().size());
        assertEquals(1, m.proposals().get(0).seq());
        assertEquals(45, m.proposals().get(0).boardIndependencePct());
        assertEquals(42, m.proposals().get(1).payScore());
    }

    @Test
    void reportsBadRowsWithoutDroppingGoodOnes() {
        String csv = HEADER + "\n"
            + "M1,ACME,Acme Corp,US,2026-11-20,2026-11-18T21:00:00Z,America/New_York,AGM,1,AUDITOR,Ratify auditor,,FOR,,\n"
            + "M2,BETA,Beta Ltd,GB,2026-13-01,2026-11-18T21:00:00Z,Europe/London,AGM,1,AUDITOR,Ratify,,FOR,,\n"
            + "M3,GAMMA,Gamma,JP,2026-11-25,2026-11-22T08:00:00Z,Asia/Tokyo,AGM,1,BOGUS,Thing,,FOR,,\n";
        var result = MeetingCsvMapper.map(CsvParser.parse(csv));

        assertEquals(1, result.meetings().size());
        assertEquals(2, result.errors().size());
        assertTrue(result.errors().get(0).startsWith("row 3"));
        assertTrue(result.errors().get(1).contains("unknown category"));
    }

    @Test
    void rejectsWrongHeader() {
        var result = MeetingCsvMapper.map(CsvParser.parse("id,name\n1,x\n"));
        assertTrue(result.meetings().isEmpty());
        assertTrue(result.errors().get(0).startsWith("unexpected header"));
    }
}
