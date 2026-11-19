package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.report.domain.rule.ApplicabilityResult;
import com.moonligh.report.domain.rule.CriticalLevel;
import com.moonligh.report.domain.rule.CriticalRuleTable;
import com.moonligh.report.domain.rule.CriticalStatus;
import com.moonligh.report.domain.rule.CriticalValueEngine;
import com.moonligh.report.domain.rule.CriticalValueResult;
import com.moonligh.report.domain.rule.ReferenceRangeEvidence;
import com.moonligh.report.domain.rule.RuleContext;
import com.moonligh.report.domain.rule.RuleSetVersion;
import com.moonligh.report.domain.rule.SafetyGate;

/** AC-R6/R7：危急事件独立持久化，无 RAG/LLM 也成立；K >= 6.0 HIGH；同一事实幂等。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CriticalIndependentTest {

    private final CriticalRuleTable table = CriticalRuleTable.builtIn();
    private final CriticalValueEngine engine = new CriticalValueEngine(table);
    private final SafetyGate gate = new SafetyGate();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private CriticalValueResult judge(String value) {
        RuleContext ctx = RuleTestSupport.ctx(
                "K", value, "mmol/L", "SERUM", RuleTestSupport.HIGH_CONF, ReferenceRangeEvidence.NONE);
        ApplicabilityResult a = gate.applicability(ctx, table.rulesFor("K"));
        return engine.evaluate(ctx, a);
    }

    @Test
    void potassiumThresholdsAreVersionedAndInclusiveAtSixPointZero() {
        CriticalValueResult at6 = judge("6.0");
        assertEquals(CriticalStatus.CRITICAL, at6.status());
        assertEquals(CriticalLevel.HIGH, at6.level());
        assertEquals("CRIT-K-HIGH", at6.ruleId());
        assertEquals(RuleSetVersion.CURRENT, at6.ruleVersion());
        assertEquals(CriticalStatus.NOT_CRITICAL, judge("5.99").status());
        assertEquals(CriticalStatus.CRITICAL, judge("2.4").status());
        assertEquals(CriticalLevel.LOW, judge("2.4").level());
        assertEquals(CriticalStatus.NOT_CRITICAL, judge("2.5").status());
        assertEquals("CRITICAL_HIGH", at6.code());
    }

    @Test
    void criticalEventPersistsWithoutAnyRagOrLlm() throws Exception {
        // 规则链路不引用 RAG / LLM / 解读任何组件：事件仅由规则表 + 解析结果产生。
        String account = ReportTestSupport.uniq("acc-crit");
        JsonNode created = ReportTestSupport.createRules(mvc, account, "rules-critical-k.lab.txt", "张三");
        String reportId = created.path("data").path("reportId").asText();
        assertEquals("RULE_CHECKED", created.path("data").path("status").asText());

        JsonNode events = ReportTestSupport.criticalEvents(mvc, account, reportId);
        assertEquals(200, events.path("code").asInt());
        JsonNode list = events.path("data").path("events");
        assertEquals(1, list.size());
        JsonNode e = list.get(0);
        assertEquals("K", e.path("canonicalCode").asText());
        assertEquals("CRITICAL_HIGH", e.path("criticalResult").asText());
        assertEquals("DETECTED", e.path("eventStatus").asText());
        assertEquals("PENDING", e.path("notificationStatus").asText());
        assertTrue(e.path("eventId").asText().startsWith("evt_"));

        JsonNode detail = ReportTestSupport.detail(mvc, account, reportId);
        assertEquals(200, detail.path("code").asInt());
        assertEquals(1, detail.path("data").path("criticalHits").size());
        assertFalse(detail.path("data").path("safeNormalAllowed").asBoolean());
        // 危急 ≠ 超区间 ≠ 解读：K=6.8 同时是 HIGH 超区间，但事件与 abnormals 是两套结果。
        assertEquals(1, detail.path("data").path("abnormals").size());

        Integer outbox = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_critical_notification_outbox o "
                        + "JOIN report_critical_event e ON e.event_id = o.event_id WHERE e.report_id = ?",
                Integer.class, reportId);
        assertEquals(1, outbox);
    }

    @Test
    void identityMissingKeepsCriticalEventButHoldsNotificationForReview() throws Exception {
        String account = ReportTestSupport.uniq("acc-crit-noid");
        JsonNode created = ReportTestSupport.createRules(mvc, account, "rules-critical-k-no-identity.lab.txt", "张三");
        String reportId = created.path("data").path("reportId").asText();

        JsonNode list = ReportTestSupport.criticalEvents(mvc, account, reportId).path("data").path("events");
        assertEquals(1, list.size());
        assertEquals("DETECTED", list.get(0).path("eventStatus").asText());
        assertEquals("PENDING_REVIEW", list.get(0).path("notificationStatus").asText());
    }

    @Test
    void reparseOfSameFactDoesNotDuplicateEvent() throws Exception {
        String account = ReportTestSupport.uniq("acc-crit-reparse");
        JsonNode created = ReportTestSupport.createRules(mvc, account, "rules-critical-k.lab.txt", "张三");
        String reportId = created.path("data").path("reportId").asText();
        String firstEventId = ReportTestSupport.criticalEvents(mvc, account, reportId)
                .path("data").path("events").get(0).path("eventId").asText();

        JsonNode second = ReportTestSupport.reparse(mvc, account, ReportTestSupport.uniq("re"), reportId);
        assertEquals(2, second.path("data").path("parseVersion").asInt());

        JsonNode list = ReportTestSupport.criticalEvents(mvc, account, reportId).path("data").path("events");
        assertEquals(1, list.size());
        assertEquals(firstEventId, list.get(0).path("eventId").asText());
    }

    @Test
    void normalPotassiumCreatesNoEvent() throws Exception {
        String account = ReportTestSupport.uniq("acc-crit-none");
        JsonNode created = ReportTestSupport.createRules(mvc, account, "rules-matched-normal.lab.txt", "张三");
        JsonNode list = ReportTestSupport.criticalEvents(
                mvc, account, created.path("data").path("reportId").asText()).path("data").path("events");
        assertEquals(0, list.size());
    }

    @Test
    void otherAccountCannotReadCriticalEvents() throws Exception {
        JsonNode created = ReportTestSupport.createRules(
                mvc, ReportTestSupport.uniq("acc-own"), "rules-critical-k.lab.txt", "张三");
        JsonNode res = ReportTestSupport.criticalEvents(
                mvc, ReportTestSupport.uniq("acc-intruder"), created.path("data").path("reportId").asText());
        assertNotEquals(200, res.path("code").asInt());
    }
}