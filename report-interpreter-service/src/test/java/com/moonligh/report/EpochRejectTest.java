package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.report.DeliveryTestSupport.SseEvent;
import com.moonligh.report.api.res.DonePayload;
import com.moonligh.report.api.res.ReportEventPayload;
import com.moonligh.report.api.res.ReportSseEventRes;
import com.moonligh.report.domain.event.StaleEpochException;
import com.moonligh.report.service.ReportEventService;

/** AC-D9 / D-R8：旧 epoch 的写入被拒绝且不落库；重解析开启新一代，旧一代事件不再回放。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EpochRejectTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReportEventService events;

    private long epochOf(String reportId) {
        return jdbc.queryForObject("SELECT epoch FROM report_job WHERE report_id = ?", Long.class, reportId);
    }

    private int eventCount(String reportId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM report_event WHERE report_id = ?", Integer.class, reportId);
    }

    private static List<ReportEventPayload> done(String tag) {
        return List.of(new DonePayload("itp_" + tag, "COMPLETED", "PENDING", "NONE"));
    }

    @Test
    void newReportStartsAtEpochOneAndAcceptsOnlyThatEpoch() throws Exception {
        String account = ReportTestSupport.uniq("acc-epoch-1");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        assertEquals(1L, epochOf(reportId));

        List<ReportSseEventRes> saved = events.append(reportId, 1L, done("a"));
        assertEquals(1, saved.size());
        assertEquals(1L, saved.get(0).sequence());

        // 比当前 epoch 新（未来）的写入同样不被接受。
        assertThrows(StaleEpochException.class, () -> events.append(reportId, 2L, done("future")));
        assertEquals(1, eventCount(reportId));
    }

    @Test
    void reparseBumpsEpochAndRejectsInFlightWritesOfTheOldEpoch() throws Exception {
        String account = ReportTestSupport.uniq("acc-epoch-2");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        events.append(reportId, 1L, done("old-1"));
        int before = eventCount(reportId);

        assertEquals(200, ReportTestSupport.reparse(mvc, account, ReportTestSupport.uniq("rp"), reportId)
                .path("code").asInt());
        assertEquals(2L, epochOf(reportId));

        StaleEpochException ex = assertThrows(StaleEpochException.class, () -> events.append(reportId, 1L, done("late")));
        assertEquals(1L, ex.getEventEpoch());
        assertEquals(2L, ex.getCurrentEpoch());
        assertEquals(before, eventCount(reportId));

        List<ReportSseEventRes> fresh = events.append(reportId, 2L, done("new"));
        assertEquals(before + 1L, fresh.get(0).sequence());
        assertEquals(before + 1, eventCount(reportId));
    }

    @Test
    void replayedReparseKeyDoesNotStartAnotherGeneration() throws Exception {
        String account = ReportTestSupport.uniq("acc-epoch-3");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        String key = ReportTestSupport.uniq("rp");

        ReportTestSupport.reparse(mvc, account, key, reportId);
        ReportTestSupport.reparse(mvc, account, key, reportId);

        assertEquals(2L, epochOf(reportId));
    }

    @Test
    void oldGenerationEventsAreNotReplayedAfterReparse() throws Exception {
        String account = ReportTestSupport.uniq("acc-epoch-4");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        assertEquals(200, InterpretationTestSupport.trigger(mvc, account, reportId).path("code").asInt());
        List<SseEvent> gen1 = DeliveryTestSupport.stream(mvc, account, reportId, null);
        assertFalse(gen1.isEmpty());

        ReportTestSupport.reparse(mvc, account, ReportTestSupport.uniq("rp"), reportId);
        // 新一代还没有任何事件：旧一代的内容不会冒充当前结果。
        assertTrue(DeliveryTestSupport.stream(mvc, account, reportId, null).isEmpty());
        // 就算客户端拿着旧一代的 Last-Event-ID 重连，也拿不到旧事件。
        assertTrue(DeliveryTestSupport.stream(mvc, account, reportId, gen1.get(0).id()).isEmpty());

        JsonNode second = InterpretationTestSupport.trigger(mvc, account, reportId);
        assertEquals(200, second.path("code").asInt());
        List<SseEvent> gen2 = DeliveryTestSupport.stream(mvc, account, reportId, null);
        assertFalse(gen2.isEmpty());
        // 序号跨代连续（(jobId, sequence) 全局唯一），新一代从旧序号之后继续。
        assertEquals(gen1.size() + 1L, gen2.get(0).data().path("sequence").asLong());
        Integer epochs = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT epoch) FROM report_event WHERE report_id = ?", Integer.class, reportId);
        assertEquals(2, epochs);
        Integer oldInReplay = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_event WHERE report_id = ? AND epoch = 1", Integer.class, reportId);
        assertEquals(gen1.size(), oldInReplay);
        for (SseEvent e : gen2) {
            assertEquals(2L, jdbc.queryForObject(
                    "SELECT epoch FROM report_event WHERE event_id = ?", Long.class, e.id()));
        }
    }

    @Test
    void rejectedAppendWritesNothingAtAll() throws Exception {
        String account = ReportTestSupport.uniq("acc-epoch-5");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        ReportTestSupport.reparse(mvc, account, ReportTestSupport.uniq("rp"), reportId);

        assertThrows(StaleEpochException.class, () -> events.append(reportId, 1L, List.of(
                new DonePayload("itp_x", "COMPLETED", "PENDING", "NONE"),
                new DonePayload("itp_y", "COMPLETED", "PENDING", "NONE"))));

        assertEquals(0, eventCount(reportId));
    }
}
