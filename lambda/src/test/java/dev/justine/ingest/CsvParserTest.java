package dev.justine.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvParserTest {

    @Test
    void handlesQuotedCommasEscapedQuotesAndCrlf() {
        String csv = "a,b,c\r\n1,\"Approve merger, as amended\",\"say \"\"hi\"\"\"\r\n\r\n2,,x";
        var rows = CsvParser.parse(csv);
        assertEquals(List.of("a", "b", "c"), rows.get(0));
        assertEquals(List.of("1", "Approve merger, as amended", "say \"hi\""), rows.get(1));
        assertEquals(List.of("2", "", "x"), rows.get(2));
        assertEquals(3, rows.size());
    }

    @Test
    void stripsExcelByteOrderMark() {
        assertEquals("a", CsvParser.parse("﻿a,b\n").get(0).get(0));
    }

    @Test
    void keepsNewlinesInsideQuotes() {
        var rows = CsvParser.parse("x,\"line1\nline2\"\n");
        assertEquals("line1\nline2", rows.get(0).get(1));
    }
}
