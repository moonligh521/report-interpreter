package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.report.api.msg.ArchiveOutboxMessageV2;
import com.moonligh.report.common.PayloadJson;
import com.moonligh.report.domain.archive.ArchiveOutboxStore;
import com.moonligh.report.domain.event.ReportEventStore;
import com.moonligh.report.infrastructure.persistence.ReportArchiveOutboxRow;
import com.moonligh.report.infrastructure.persistence.ReportEventRow;

/**
 * AC-D6 / D-R4：MATCHED 且解读完成 → 档案 Outbox 与解读、事件同一事务；任一环节失败整体回滚，不留半截数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArchiveOutboxTxTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private ArchiveOutboxStore archiveStore;

    @MockitoSpyBean
    private ReportEventStore eventStore;

    private int count(String table, String reportId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE report_id = ?", Integer.class, reportId);
    }

    @Test
    void outboxInterpretationAndEventsCommitTogether() throws Exception {
        String account = ReportTestSupport.uniq("acc-tx-ok");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);
        assertEquals(200, res.path("code").asInt());
        String interpretationId = res.path("data").path("interpretationId").asText();

        assertEquals(1, count("report_interpretation", reportId));
        assertEquals(1, count("report_archive_outbox", reportId));
        assertTrue(count("report_event", reportId) >= 3);

        ReportArchiveOutboxRow row = archiveStore.findByEventId("arc_" + interpretationId).orElseThrow();
        assertEquals("PENDING", row.status());
        assertEquals(0, row.attemptCount());
        assertEquals("2", row.schemaVersion());

        ArchiveOutboxMessageV2 msg = PayloadJson.read(row.payloadJson(), ArchiveOutboxMessageV2.class);
        assertEquals("arc_" + interpretationId, msg.eventId());
        assertEquals(reportId, msg.reportId());
        assertEquals("p-rules", msg.patientId());
        assertEquals(1, msg.parseVersion());
        assertEquals(interpretationId, msg.interpretationId());
        assertEquals("MATCHED", msg.identityStatus());
        assertEquals("LAB_PANEL", msg.reportType());
        assertEquals(1, msg.indicators().size());
        assertEquals("ALT", msg.indicators().get(0).canonicalCode());
        assertNotNull(msg.indicators().get(0).evidence());
        assertNotNull(msg.fhirHints());
    }

    @Test
    void archiveInsertFailureRollsBackInterpretationAndEvents() throws Exception {
        String account = ReportTestSupport.uniq("acc-tx-arc");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        doThrow(new IllegalStateException("simulated archive outbox failure")).when(archiveStore).insert(any());

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertNotEquals(200, res.path("code").asInt());
        assertEquals(0, count("report_interpretation", reportId));
        assertEquals(0, count("report_event", reportId));
        assertEquals(0, count("report_archive_outbox", reportId));

        // 故障恢复后同一报告可重新解读，且三者一起落库。
        Mockito.reset(archiveStore);
        assertEquals(200, InterpretationTestSupport.trigger(mvc, account, reportId).path("code").asInt());
        assertEquals(1, count("report_interpretation", reportId));
        assertEquals(1, count("report_archive_outbox", reportId));
        assertTrue(count("report_event", reportId) >= 3);
    }

    @Test
    void eventInsertFailureRollsBackTheArchiveOutboxToo() throws Exception {
        String account = ReportTestSupport.uniq("acc-tx-evt");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        doThrow(new IllegalStateException("simulated event store failure")).when(eventStore).insert(any(ReportEventRow.class));

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertNotEquals(200, res.path("code").asInt());
        // 档案 Outbox 先于事件写入；事件失败后它必须一起消失。
        assertEquals(0, count("report_archive_outbox", reportId));
        assertEquals(0, count("report_interpretation", reportId));
        assertEquals(0, count("report_event", reportId));
        assertFalse(InterpretationTestSupport.latest(mvc, account, reportId).path("code").asInt() == 200);
    }
}
