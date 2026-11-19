package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;

import com.moonligh.report.infrastructure.persistence.ReportArchiveOutboxMapper;
import com.moonligh.report.infrastructure.persistence.ReportArchiveOutboxRow;
import com.moonligh.report.infrastructure.persistence.ReportArchiveRecordMapper;
import com.moonligh.report.infrastructure.persistence.ReportArchiveRecordRow;
import com.moonligh.report.infrastructure.persistence.ReportEventMapper;
import com.moonligh.report.infrastructure.persistence.ReportEventRow;

/** SQL 仅在 xml、行对象强类型：report_event / report_archive_outbox / report_archive_record。 */
@SpringBootTest
@ActiveProfiles("test")
class MapperXmlDeliveryTest {

    private static final Pattern SQL_LITERAL = Pattern.compile(
            "\"\\s*(?i:select\\s+.+\\s+from\\s|insert\\s+into\\s|update\\s+\\w+\\s+set\\s|delete\\s+from\\s)");

    @Autowired
    private ReportEventMapper events;

    @Autowired
    private ReportArchiveOutboxMapper outbox;

    @Autowired
    private ReportArchiveRecordMapper records;

    private static ReportEventRow event(String id, String report, long seq, long epoch) {
        return new ReportEventRow(id, report, report, seq, "DONE", epoch, "{\"payloadType\":\"DONE\"}", null);
    }

    private static ReportArchiveOutboxRow outboxRow(String id, String report, String eventId, String status) {
        return new ReportArchiveOutboxRow(id, report, eventId, "archive-schema-v1", status, 0, null, "{}", null, null);
    }

    @Test
    void xmlFilesAreTypedAndCarryTheDocumentedStatements() throws Exception {
        String eventXml = Files.readString(Path.of("src/main/resources/mapper/ReportEventMapper.xml"));
        for (String id : List.of("insert", "listAfterSequence", "maxSequence", "findByEventId")) {
            assertTrue(eventXml.contains("id=\"" + id + "\""), id);
        }
        String outboxXml = Files.readString(Path.of("src/main/resources/mapper/ReportArchiveOutboxMapper.xml"));
        for (String id : List.of("insert", "updateStatus", "findByEventId", "findById", "findLatestByReport", "listDue")) {
            assertTrue(outboxXml.contains("id=\"" + id + "\""), id);
        }
        assertTrue(Files.readString(Path.of("src/main/resources/mapper/ReportArchiveRecordMapper.xml"))
                .contains("id=\"insert\""));
    }

    @Test
    void newPersistenceSourcesHoldNoSqlLiterals() throws Exception {
        for (String f : List.of(
                "infrastructure/persistence/ReportEventStoreImpl.java",
                "infrastructure/persistence/ArchiveOutboxStoreImpl.java",
                "infrastructure/persistence/ArchiveRecordStoreImpl.java",
                "service/ReportEventService.java", "service/ArchiveOutboxService.java",
                "service/ReportLifecycleService.java", "domain/archive/ArchiveOutboxManager.java",
                "domain/archive/ArchiveConsumer.java", "controller/ReportEventController.java",
                "controller/InternalJobController.java")) {
            assertFalse(SQL_LITERAL.matcher(Files.readString(Path.of("src/main/java/com/moonligh/report/" + f))).find(), f);
        }
    }

    @Test
    void eventRoundTripOrderingAndEpochFilter() {
        String report = ReportTestSupport.uniq("rpt-map-ev");
        events.insert(event(report + "-3", report, 3, 2));
        events.insert(event(report + "-1", report, 1, 1));
        events.insert(event(report + "-2", report, 2, 2));

        assertEquals(3L, events.maxSequence(report));
        assertEquals(0L, events.maxSequence("rpt-none"));
        assertEquals(List.of(1L, 2L, 3L),
                events.listAfterSequence(report, 0, 1).stream().map(ReportEventRow::sequence).toList());
        assertEquals(List.of(2L, 3L),
                events.listAfterSequence(report, 1, 1).stream().map(ReportEventRow::sequence).toList());
        // 旧 epoch 事件被过滤。
        assertEquals(List.of(2L, 3L),
                events.listAfterSequence(report, 0, 2).stream().map(ReportEventRow::sequence).toList());
        ReportEventRow found = events.findByEventId(report, report + "-2");
        assertEquals(2L, found.sequence());
        assertEquals(2L, found.epoch());
        assertEquals("DONE", found.eventType());
        assertNotNull(found.createdAt());
        assertNull(events.findByEventId("other-report", report + "-2"));
    }

    @Test
    void jobSequenceAndEventIdAreUnique() {
        String report = ReportTestSupport.uniq("rpt-map-uk");
        events.insert(event(report + "-a", report, 1, 1));
        assertThrows(DuplicateKeyException.class, () -> events.insert(event(report + "-b", report, 1, 1)));
        assertThrows(DuplicateKeyException.class, () -> events.insert(event(report + "-a", report, 2, 1)));
    }

    @Test
    void archiveOutboxRoundTripStatusUpdateAndDueList() {
        String report = ReportTestSupport.uniq("rpt-map-ob");
        String ev = ReportTestSupport.uniq("arc");
        outbox.insert(outboxRow(report + "-o1", report, ev, "PENDING"));

        ReportArchiveOutboxRow found = outbox.findByEventId(ev);
        assertEquals(report + "-o1", found.outboxId());
        assertEquals("PENDING", found.status());
        assertEquals("archive-schema-v1", found.schemaVersion());
        assertEquals(report + "-o1", outbox.findById(report + "-o1").outboxId());
        assertEquals(report + "-o1", outbox.findLatestByReport(report).outboxId());
        assertTrue(outbox.listDue(1000).stream().anyMatch(r -> r.outboxId().equals(report + "-o1")));

        assertEquals(1, outbox.updateStatus(report + "-o1", "DEAD", 5, "ARCHIVE_DOWN"));
        ReportArchiveOutboxRow dead = outbox.findById(report + "-o1");
        assertEquals("DEAD", dead.status());
        assertEquals(5, dead.attemptCount());
        assertEquals("ARCHIVE_DOWN", dead.lastErrorCode());
        assertTrue(outbox.listDue(1000).stream().noneMatch(r -> r.outboxId().equals(report + "-o1")));
        assertNull(outbox.findById("aob_none"));
        assertNull(outbox.findLatestByReport("rpt-none"));
    }

    @Test
    void archiveOutboxEventIdIsUnique() {
        String report = ReportTestSupport.uniq("rpt-map-ou");
        String ev = ReportTestSupport.uniq("arc");
        outbox.insert(outboxRow(report + "-o1", report, ev, "PENDING"));
        assertThrows(DuplicateKeyException.class, () -> outbox.insert(outboxRow(report + "-o2", report, ev, "PENDING")));
    }

    @Test
    void archiveRecordIsKeyedByEventId() {
        String report = ReportTestSupport.uniq("rpt-map-rec");
        String ev = ReportTestSupport.uniq("arc");
        ReportArchiveRecordRow row = new ReportArchiveRecordRow(ev, report, "itp_1", "p-1", 1, "MATCHED", 2, null);
        assertEquals(1, records.insert(row));
        assertThrows(DuplicateKeyException.class, () -> records.insert(row));
        ReportArchiveRecordRow found = records.findByEventId(ev);
        assertEquals("MATCHED", found.identityStatus());
        assertEquals(2, found.indicatorCount());
        assertNotNull(found.createdAt());
        assertEquals(1, records.countByReport(report));
        assertNull(records.findByEventId("arc_none"));
    }
}
