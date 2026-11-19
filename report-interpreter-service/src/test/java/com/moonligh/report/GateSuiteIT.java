package com.moonligh.report;

import static com.moonligh.report.GuardFixtures.ALT_HIGH;
import static com.moonligh.report.GuardFixtures.fact;
import static com.moonligh.report.GuardFixtures.factWith;
import static com.moonligh.report.GuardFixtures.plain;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.DeliveryTestSupport.SseEvent;
import com.moonligh.report.SwitchableModelConfig.SwitchableInterpretationModel;
import com.moonligh.report.domain.interpret.ClaimedStatus;

/**
 * P0-D 门禁包（薄套件）：把 deterministic / adversarial / critical / coverage 四类冒烟，
 * 与 delivery-gate 新增的关键不变量放进同一个端到端入口。细节断言在各自专项测试里，这里只守「不能破」的底线。
 * 技术闭环全绿 ≠ 临床放行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SwitchableModelConfig.class)
class GateSuiteIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SwitchableInterpretationModel model;

    @AfterEach
    void resetModel() {
        model.reset();
    }

    private int count(String table, String reportId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE report_id = ?", Integer.class, reportId);
    }

    private JsonNode lifecycle(String account, String reportId) throws Exception {
        return DeliveryTestSupport.events(mvc, account, reportId).path("data").path("lifecycle");
    }

    // ---- deterministic ----

    @Test
    void deterministicSameFactsGiveSameInterpretationAndStreamShape() throws Exception {
        String account = ReportTestSupport.uniq("acc-gate-det");
        String a = InterpretationTestSupport.createReport(mvc, account, "rag-k-high.lab.txt");
        String b = InterpretationTestSupport.createReport(mvc, account, "rag-k-high.lab.txt");

        JsonNode ra = InterpretationTestSupport.trigger(mvc, account, a);
        JsonNode rb = InterpretationTestSupport.trigger(mvc, account, b);

        assertEquals(ra.path("code").asInt(), rb.path("code").asInt());
        assertEquals(InterpretationTestSupport.texts(ra), InterpretationTestSupport.texts(rb));
        assertEquals(InterpretationTestSupport.claimTypes(ra), InterpretationTestSupport.claimTypes(rb));
        assertEquals(DeliveryTestSupport.types(DeliveryTestSupport.stream(mvc, account, a, null)),
                DeliveryTestSupport.types(DeliveryTestSupport.stream(mvc, account, b, null)));
        // 同键重放：同一份解读、不重复落事件 / 档案。
        String key = ReportTestSupport.uniq("itp");
        JsonNode first = InterpretationTestSupport.trigger(mvc, account, a, key, null);
        int events = count("report_event", a);
        JsonNode replay = InterpretationTestSupport.trigger(mvc, account, a, key, null);
        assertEquals(first.path("data").path("interpretationId").asText(),
                replay.path("data").path("interpretationId").asText());
        assertEquals(events, count("report_event", a));
    }

    // ---- adversarial ----

    @Test
    void adversarialModelOutputNeverReachesEventsArchiveOrLifecycleCompletion() throws Exception {
        String account = ReportTestSupport.uniq("acc-gate-adv");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        model.use((c, attempt) -> List.of(
                factWith(ALT_HIGH, ClaimedStatus.HIGH, new BigDecimal("58"), "U/L",
                        "ALT 检测值 58 U/L，高于参考范围（7–40 U/L）。"),
                plain("您已确诊为急性肝衰竭，无需就医，自行停药即可。")));

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals(ErrorCode.REPORT_INTERPRETATION_INVALID.getCode(), res.path("code").asInt());
        List<SseEvent> events = DeliveryTestSupport.stream(mvc, account, reportId, null);
        for (SseEvent e : events) {
            String json = e.data().toString();
            assertFalse(json.contains("58 U/L"), json);
            assertFalse(json.contains("确诊"), json);
            assertFalse(json.contains("自行停药"), json);
        }
        assertEquals("ERROR", events.get(events.size() - 1).event());
        assertFalse(DeliveryTestSupport.types(events).contains("DONE"));
        assertEquals(0, count("report_archive_outbox", reportId));
        JsonNode life = lifecycle(account, reportId);
        assertEquals("FAILED", life.path("interpretationStatus").asText());
        assertEquals("NONE", life.path("archiveStatus").asText());
        // 兜底：即便是一个合格块，也只通过 Guard 的才会出现。
        model.use((c, attempt) -> List.of(fact(ALT_HIGH, ClaimedStatus.HIGH,
                "ALT 检测值 85 U/L，高于参考范围（7–40 U/L）。")));
        String clean = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        assertEquals(200, InterpretationTestSupport.trigger(mvc, account, clean).path("code").asInt());
    }

    // ---- critical ----

    @Test
    void criticalAlertLeadsTheStreamAndStaysIndependentOfInterpretationAndArchive() throws Exception {
        String account = ReportTestSupport.uniq("acc-gate-crit");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-critical-k.lab.txt");

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals(4105, res.path("code").asInt());
        assertEquals("CRITICAL_ALERT", InterpretationTestSupport.claimTypes(res).get(0));
        List<String> types = DeliveryTestSupport.types(DeliveryTestSupport.stream(mvc, account, reportId, null));
        int alert = types.indexOf("CRITICAL_ALERT");
        assertTrue(alert >= 0, types.toString());
        assertTrue(types.indexOf("STRUCTURED_RESULT") < alert);
        if (types.contains("INTERPRETATION_DELTA")) {
            assertTrue(alert < types.indexOf("INTERPRETATION_DELTA"), "危急提醒必须先于普通解读块：" + types);
        }
        assertEquals("DONE", types.get(types.size() - 1));

        // 危急事件 / 通知独立落库，解读完成 + 档案入队都不会把它标记为已读或已送达。
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_critical_event WHERE report_id = ?", Integer.class, reportId));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT n.notification_status FROM report_critical_notification_outbox n "
                        + "JOIN report_critical_event e ON e.event_id = n.event_id WHERE e.report_id = ?",
                String.class, reportId));
        JsonNode life = lifecycle(account, reportId);
        assertEquals("COMPLETED", life.path("interpretationStatus").asText());
        assertEquals("PENDING", life.path("archiveStatus").asText());
        assertEquals("UNREAD", life.path("criticalReadStatus").asText());
    }

    // ---- coverage ----

    @Test
    void coverageUnknownNeverClaimsNormalAndNeverArchives() throws Exception {
        String account = ReportTestSupport.uniq("acc-gate-cov");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-coverage-unknown.lab.txt");

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals(200, res.path("code").asInt());
        for (String t : InterpretationTestSupport.texts(res)) {
            assertFalse(t.contains("处于参考范围内"), "覆盖未知不得出现「正常」结论：" + t);
        }
        assertTrue(InterpretationTestSupport.texts(res).contains("本报告的规则判定尚待人工复核，暂不下结论。"));
        assertEquals(0, count("report_archive_outbox", reportId));
        assertEquals("BLOCKED", lifecycle(account, reportId).path("archiveStatus").asText());
    }

    @Test
    void identityConflictAndMissingProduceNoInterpretationEventOrArchive() throws Exception {
        String account = ReportTestSupport.uniq("acc-gate-id");
        String conflict = ReportTestSupport.createRules(mvc, account, "rules-matched-normal.lab.txt", "李四")
                .path("data").path("reportId").asText();
        String missing = ReportTestSupport.createRules(mvc, account, "rules-critical-k-no-identity.lab.txt", "张三")
                .path("data").path("reportId").asText();

        assertEquals(4108, InterpretationTestSupport.trigger(mvc, account, conflict).path("code").asInt());
        assertEquals(4110, InterpretationTestSupport.trigger(mvc, account, missing).path("code").asInt());
        for (String id : List.of(conflict, missing)) {
            assertEquals(0, count("report_interpretation", id));
            assertEquals(0, count("report_event", id));
            assertEquals(0, count("report_archive_outbox", id));
        }
    }

    // ---- delivery-gate invariants ----

    @Test
    void deliveryInvariantsHoldEndToEnd() throws Exception {
        String account = ReportTestSupport.uniq("acc-gate-inv");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-mixed.lab.txt");
        assertEquals(200, InterpretationTestSupport.trigger(mvc, account, reportId).path("code").asInt());

        List<SseEvent> events = DeliveryTestSupport.stream(mvc, account, reportId, null);
        // 事件：落库一致、序号连续、delta 全 VALID、payload 强类型。
        assertEquals(count("report_event", reportId), events.size());
        for (int i = 0; i < events.size(); i++) {
            assertEquals(i + 1, events.get(i).data().path("sequence").asLong());
            assertEquals(events.get(i).event(), events.get(i).data().path("payload").path("payloadType").asText());
            if ("INTERPRETATION_DELTA".equals(events.get(i).event())) {
                assertEquals("VALID", events.get(i).data().path("payload").path("validationStatus").asText());
            }
        }
        // 续传：用中间事件续传不丢不重。
        List<SseEvent> tail = DeliveryTestSupport.stream(mvc, account, reportId, events.get(1).id());
        assertEquals(events.subList(2, events.size()).stream().map(SseEvent::id).toList(),
                tail.stream().map(SseEvent::id).toList());
        // 再鉴权：换账号不能续传。
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), ReportTestSupport.JSON.readTree(DeliveryTestSupport.streamRaw(
                mvc, ReportTestSupport.uniq("acc-x"), reportId, events.get(1).id()).getContentAsString())
                .path("code").asInt());
        // 双 Outbox：档案一条（MATCHED），与危急通知表互不相干。
        assertEquals(1, count("report_archive_outbox", reportId));
        assertEquals("MATCHED", ReportTestSupport.JSON.readTree(jdbc.queryForObject(
                "SELECT payload_json FROM report_archive_outbox WHERE report_id = ?", String.class, reportId))
                .path("identityStatus").asText());
        // 三生命周期互相独立。
        JsonNode life = lifecycle(account, reportId);
        assertEquals("COMPLETED", life.path("interpretationStatus").asText());
        assertNotEquals("SUCCESS", life.path("archiveStatus").asText());
        assertEquals("NONE", life.path("criticalReadStatus").asText());
    }
}
