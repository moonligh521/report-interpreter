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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.DeliveryTestSupport.SseEvent;

/**
 * AC-D1 / D2 / D3：重要事件落库；Last-Event-ID 从 DB 续传；重连再鉴权；(jobId, sequence) 唯一。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SseReplayTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String interpretedReport(String account) throws Exception {
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-k-high.lab.txt");
        assertEquals(200, InterpretationTestSupport.trigger(mvc, account, reportId).path("code").asInt());
        return reportId;
    }

    @Test
    void importantEventsArePersistedAndStreamedInSequenceOrder() throws Exception {
        String account = ReportTestSupport.uniq("acc-sse");
        String reportId = interpretedReport(account);

        MockHttpServletResponse raw = DeliveryTestSupport.streamRaw(mvc, account, reportId, null);
        assertTrue(raw.getContentType().startsWith("text/event-stream"), raw.getContentType());
        List<SseEvent> events = DeliveryTestSupport.parseSse(raw.getContentAsString());

        List<String> types = DeliveryTestSupport.types(events);
        assertEquals("STRUCTURED_RESULT", types.get(0));
        assertEquals("DONE", types.get(types.size() - 1));
        assertTrue(types.contains("INTERPRETATION_DELTA"), types.toString());
        for (int i = 0; i < events.size(); i++) {
            assertEquals(i + 1, events.get(i).data().path("sequence").asLong());
            assertEquals(events.get(i).id(), events.get(i).data().path("eventId").asText());
            assertEquals(events.get(i).event(), events.get(i).data().path("eventType").asText());
            assertEquals(reportId, events.get(i).data().path("reportId").asText());
        }

        // DB 为准：流里的每条事件都能在 report_event 找到，数量一致，且 payload_json 是强类型形态。
        Integer persisted = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_event WHERE report_id = ?", Integer.class, reportId);
        assertEquals(events.size(), persisted);
        for (SseEvent e : events) {
            String payloadJson = jdbc.queryForObject(
                    "SELECT payload_json FROM report_event WHERE event_id = ?", String.class, e.id());
            assertTrue(payloadJson.contains("\"payloadType\":\"" + e.event() + "\""), payloadJson);
        }
    }

    @Test
    void lastEventIdResumesFromTheDatabase() throws Exception {
        String account = ReportTestSupport.uniq("acc-sse-resume");
        String reportId = interpretedReport(account);
        List<SseEvent> all = DeliveryTestSupport.stream(mvc, account, reportId, null);
        assertTrue(all.size() >= 4, all.toString());

        // 以 eventId 续传
        List<SseEvent> afterThird = DeliveryTestSupport.stream(mvc, account, reportId, all.get(2).id());
        assertEquals(all.subList(3, all.size()).stream().map(SseEvent::id).toList(),
                afterThird.stream().map(SseEvent::id).toList());
        // 以序号续传
        List<SseEvent> afterSecond = DeliveryTestSupport.stream(mvc, account, reportId, "2");
        assertEquals(all.get(2).id(), afterSecond.get(0).id());
        // 已是最后一条：无新事件
        assertTrue(DeliveryTestSupport.stream(mvc, account, reportId, all.get(all.size() - 1).id()).isEmpty());
        // 不认识的 ID：从头回放（客户端按 eventId 去重），而不是报错或丢事件。
        assertEquals(all.size(), DeliveryTestSupport.stream(mvc, account, reportId, "evt_unknown").size());
    }

    @Test
    void replayDoesNotDependOnAnyInMemoryState() throws Exception {
        String account = ReportTestSupport.uniq("acc-sse-db");
        String reportId = interpretedReport(account);
        List<SseEvent> first = DeliveryTestSupport.stream(mvc, account, reportId, null);
        // 多次独立重连得到相同序列：事件只来自 report_event。
        List<SseEvent> second = DeliveryTestSupport.stream(mvc, account, reportId, null);
        assertEquals(first.stream().map(SseEvent::id).toList(), second.stream().map(SseEvent::id).toList());
    }

    @Test
    void reconnectIsReauthenticated() throws Exception {
        String account = ReportTestSupport.uniq("acc-sse-auth");
        String reportId = interpretedReport(account);

        // 同样带着合法 Last-Event-ID，别的账号 / 未登录都不能续传。
        List<SseEvent> all = DeliveryTestSupport.stream(mvc, account, reportId, null);
        String lastId = all.get(0).id();

        JsonNode other = ReportTestSupport.JSON.readTree(
                DeliveryTestSupport.streamRaw(mvc, ReportTestSupport.uniq("acc-other"), reportId, lastId)
                        .getContentAsString());
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), other.path("code").asInt());

        JsonNode anonymous = ReportTestSupport.JSON.readTree(
                DeliveryTestSupport.streamRaw(mvc, null, reportId, lastId).getContentAsString());
        assertEquals(ErrorCode.LOGIN_REQUIRED.getCode(), anonymous.path("code").asInt());

        JsonNode unknown = ReportTestSupport.JSON.readTree(
                DeliveryTestSupport.streamRaw(mvc, account, "rpt_not_exist", lastId).getContentAsString());
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), unknown.path("code").asInt());

        assertFalse(DeliveryTestSupport.stream(mvc, account, reportId, lastId).isEmpty());
    }

    @Test
    void sequenceIsUniquePerJob() throws Exception {
        String account = ReportTestSupport.uniq("acc-sse-uk");
        String reportId = interpretedReport(account);
        assertThrows(DuplicateKeyException.class, () -> jdbc.update(
                "INSERT INTO report_event (event_id, report_id, job_id, sequence, event_type, epoch, payload_json,"
                        + " created_at) VALUES (?, ?, ?, 1, 'DONE', 1, '{}', CURRENT_TIMESTAMP(3))",
                ReportTestSupport.uniq("evt-dup"), reportId, reportId));
    }

    @Test
    void eventsListEndpointReturnsTheSameEventsWithLifecycle() throws Exception {
        String account = ReportTestSupport.uniq("acc-sse-list");
        String reportId = interpretedReport(account);
        List<SseEvent> streamed = DeliveryTestSupport.stream(mvc, account, reportId, null);

        JsonNode res = DeliveryTestSupport.events(mvc, account, reportId);
        assertEquals(200, res.path("code").asInt());
        JsonNode data = res.path("data");
        assertEquals(reportId, data.path("reportId").asText());
        assertEquals(streamed.size(), data.path("events").size());
        for (int i = 0; i < streamed.size(); i++) {
            assertEquals(streamed.get(i).id(), data.path("events").get(i).path("eventId").asText());
            assertFalse(data.path("events").get(i).path("payload").isMissingNode());
        }
        assertEquals("COMPLETED", data.path("lifecycle").path("interpretationStatus").asText());
        assertEquals("PENDING", data.path("lifecycle").path("archiveStatus").asText());
        assertEquals("NONE", data.path("lifecycle").path("criticalReadStatus").asText());

        JsonNode denied = DeliveryTestSupport.events(mvc, ReportTestSupport.uniq("acc-other"), reportId);
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), denied.path("code").asInt());
    }

    @Test
    void nothingToReplayBeforeAnyInterpretation() throws Exception {
        String account = ReportTestSupport.uniq("acc-sse-empty");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-k-high.lab.txt");
        assertTrue(DeliveryTestSupport.stream(mvc, account, reportId, null).isEmpty());
        assertEquals("NONE", DeliveryTestSupport.events(mvc, account, reportId)
                .path("data").path("lifecycle").path("interpretationStatus").asText());
    }
}
