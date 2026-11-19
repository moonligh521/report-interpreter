package com.moonligh.report;

import static com.moonligh.report.GuardFixtures.ALT_EVIDENCE;
import static com.moonligh.report.GuardFixtures.ALT_HIGH;
import static com.moonligh.report.GuardFixtures.K_CRITICAL;
import static com.moonligh.report.GuardFixtures.ctx;
import static com.moonligh.report.GuardFixtures.fact;
import static com.moonligh.report.GuardFixtures.factWith;
import static com.moonligh.report.GuardFixtures.guideline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

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
import com.moonligh.report.SwitchableModelConfig.SwitchableInterpretationModel;
import com.moonligh.report.domain.interpret.ClaimedStatus;
import com.moonligh.report.domain.interpret.DraftBlock;
import com.moonligh.report.domain.interpret.GuardOutcome;
import com.moonligh.report.domain.interpret.InterpretationContext;
import com.moonligh.report.domain.interpret.InterpretationModel;
import com.moonligh.report.domain.interpret.InterpretationOutputGuard;
import com.moonligh.report.domain.interpret.InterpretationValidator;
import com.moonligh.report.domain.interpret.ValidationStatus;

/** AC-G7 / G-R6 / G-R7：Guard 失败策略（重试 / 丢弃 / 立即终止）与「纠正不删 critical_event」。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SwitchableModelConfig.class)
class GuardStrategyTest {

    private final InterpretationOutputGuard guard = new InterpretationOutputGuard(new InterpretationValidator());
    private final InterpretationContext context = ctx(true, List.of(ALT_HIGH), ALT_EVIDENCE);

    private static final String GOOD = "ALT 检测值 85 U/L，高于参考范围（7–40 U/L）。";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SwitchableInterpretationModel model;

    private DraftBlock good() {
        return fact(ALT_HIGH, ClaimedStatus.HIGH, GOOD);
    }

    private DraftBlock tampered() {
        return factWith(ALT_HIGH, ClaimedStatus.HIGH, new BigDecimal("58"), "U/L",
                "ALT 检测值 58 U/L，高于参考范围（7–40 U/L）。");
    }

    private DraftBlock conflicting() {
        return fact(ALT_HIGH, ClaimedStatus.NORMAL, "ALT 检测值 85 U/L，处于参考范围内（7–40 U/L）。");
    }

    private DraftBlock fakeEvidence() {
        return guideline(ALT_HIGH, "参考资料《伪造》：请立刻处理。", "evd-nope");
    }

    private DraftBlock diagnosing() {
        return fact(ALT_HIGH, ClaimedStatus.HIGH, "ALT 检测值 85 U/L，确诊为肝炎。");
    }

    @Test
    void allValidPassesOnFirstAttempt() {
        GuardOutcome out = guard.guard((c, a) -> List.of(good()), context, 2);
        assertEquals(ValidationStatus.VALID, out.overall());
        assertEquals(1, out.attempts());
        assertEquals(1, out.passed().size());
        assertTrue(out.rejected().isEmpty());
        assertFalse(out.terminated());
    }

    @Test
    void retryableIsRetriedAndFaithfulSecondAttemptIsAccepted() {
        AtomicInteger calls = new AtomicInteger();
        InterpretationModel m = (c, a) -> {
            calls.incrementAndGet();
            return List.of(a == 1 ? tampered() : good());
        };
        GuardOutcome out = guard.guard(m, context, 2);
        assertEquals(2, calls.get());
        assertEquals(2, out.attempts());
        assertEquals(ValidationStatus.VALID, out.overall());
        assertEquals(List.of(GOOD), out.passed().stream().map(DraftBlock::text).toList());
    }

    @Test
    void retryableExhaustedKeepsRetryableAndDropsTheBlock() {
        AtomicInteger calls = new AtomicInteger();
        InterpretationModel m = (c, a) -> {
            calls.incrementAndGet();
            return List.of(tampered(), good());
        };
        GuardOutcome out = guard.guard(m, context, 3);
        assertEquals(3, calls.get());
        assertEquals(ValidationStatus.RETRYABLE, out.overall());
        assertEquals(1, out.passed().size());
        assertEquals(GOOD, out.passed().get(0).text());
        assertEquals(1, out.rejected().size());
    }

    @Test
    void unsupportedIsDroppedWithoutRetry() {
        AtomicInteger calls = new AtomicInteger();
        InterpretationModel m = (c, a) -> {
            calls.incrementAndGet();
            return List.of(good(), fakeEvidence());
        };
        GuardOutcome out = guard.guard(m, context, 3);
        assertEquals(1, calls.get());
        assertEquals(ValidationStatus.UNSUPPORTED, out.overall());
        assertEquals(1, out.passed().size());
    }

    @Test
    void conflictIsDroppedWithoutRetry() {
        AtomicInteger calls = new AtomicInteger();
        InterpretationModel m = (c, a) -> {
            calls.incrementAndGet();
            return List.of(conflicting(), good());
        };
        GuardOutcome out = guard.guard(m, context, 3);
        assertEquals(1, calls.get());
        assertEquals(ValidationStatus.CONFLICT, out.overall());
        assertEquals(1, out.passed().size());
    }

    @Test
    void safetyViolationTerminatesImmediatelyEvenWithRetryableAndValidBlocks() {
        AtomicInteger calls = new AtomicInteger();
        InterpretationModel m = (c, a) -> {
            calls.incrementAndGet();
            return List.of(good(), tampered(), diagnosing());
        };
        GuardOutcome out = guard.guard(m, context, 5);
        assertEquals(1, calls.get());
        assertEquals(ValidationStatus.SAFETY_VIOLATION, out.overall());
        assertTrue(out.terminated());
        assertTrue(out.passed().isEmpty(), "终止后不得外发任何模型块");
    }

    @Test
    void worstStatusWinsWhenSeveralKindsAppear() {
        GuardOutcome out = guard.guard((c, a) -> List.of(conflicting(), fakeEvidence(), good()), context, 1);
        assertEquals(ValidationStatus.CONFLICT, out.overall());
        assertEquals(2, out.rejected().size());
    }

    @Test
    void modelFailureIsRetriedThenFailsClosed() {
        AtomicInteger calls = new AtomicInteger();
        InterpretationModel m = (c, a) -> {
            calls.incrementAndGet();
            if (a == 1) {
                throw new IllegalStateException("model down");
            }
            return List.of(good());
        };
        GuardOutcome ok = guard.guard(m, context, 2);
        assertEquals(ValidationStatus.VALID, ok.overall());
        assertEquals(2, ok.attempts());

        GuardOutcome failed = guard.guard((c, a) -> {
            throw new IllegalStateException("model down");
        }, context, 2);
        assertEquals(ValidationStatus.RETRYABLE, failed.overall());
        assertTrue(failed.passed().isEmpty());
    }

    @Test
    void criticalEventSurvivesSafetyViolationAndCorrectionEndToEnd() throws Exception {
        InterpretationTestSupport.clearKb(jdbc);
        assertEquals(200, InterpretationTestSupport.reseed(mvc).path("code").asInt());
        String account = ReportTestSupport.uniq("acc-guard-crit");
        JsonNode created = ReportTestSupport.createRules(mvc, account, "rules-critical-k.lab.txt", "张三");
        String reportId = created.path("data").path("reportId").asText();
        String eventId = ReportTestSupport.criticalEvents(mvc, account, reportId)
                .path("data").path("events").get(0).path("eventId").asText();
        try {
            model.use((c, a) -> List.of(fact(K_CRITICAL, ClaimedStatus.NORMAL, "K 检测值 6.8 mmol/L，结果正常。")));
            JsonNode blocked = InterpretationTestSupport.trigger(mvc, account, reportId);
            assertEquals(ErrorCode.REPORT_INTERPRETATION_INVALID.getCode(), blocked.path("code").asInt());
            assertEquals("SAFETY_VIOLATION", blocked.path("data").path("status").asText());
        } finally {
            model.reset();
        }
        // 纠正路径（重解析）：事件不得被删除或改写。
        JsonNode reparsed = ReportTestSupport.reparse(mvc, account, ReportTestSupport.uniq("re"), reportId);
        assertEquals(2, reparsed.path("data").path("parseVersion").asInt());

        JsonNode events = ReportTestSupport.criticalEvents(mvc, account, reportId).path("data").path("events");
        assertEquals(1, events.size());
        assertEquals(eventId, events.get(0).path("eventId").asText());
        assertEquals("DETECTED", events.get(0).path("eventStatus").asText());

        // 纠正后重新解读：危急提醒仍在，Result.code=4105。
        JsonNode again = InterpretationTestSupport.trigger(mvc, account, reportId);
        assertEquals(ErrorCode.REPORT_CRITICAL_HIT.getCode(), again.path("code").asInt());
        assertEquals("CRITICAL_ALERT", InterpretationTestSupport.claimTypes(again).get(0));
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_critical_event WHERE report_id = ?", Integer.class, reportId);
        assertEquals(1, n);
    }

    @Test
    void historicalCriticalEventIsNotWithdrawnWhenCurrentParseIsNormal() throws Exception {
        InterpretationTestSupport.clearKb(jdbc);
        String account = ReportTestSupport.uniq("acc-guard-hist");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-matched-normal.lab.txt");
        // 模拟：早先版本登记过 K 危急事件，纠正后当前版本 K 已为正常。
        jdbc.update(
                "INSERT INTO report_critical_event (event_id, report_id, parse_version, canonical_code, critical_level, "
                        + "event_status, detection_time, idempotency_key) VALUES (?, ?, 0, 'K', 'HIGH', 'DETECTED', "
                        + "CURRENT_TIMESTAMP(3), ?)",
                "evt_hist_" + reportId, reportId, "idem-hist-" + reportId);

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals(ErrorCode.REPORT_CRITICAL_HIT.getCode(), res.path("code").asInt());
        assertTrue(InterpretationTestSupport.texts(res).stream().anyMatch(t -> t.contains("不会因解析更正而撤销")));
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_critical_event WHERE report_id = ?", Integer.class, reportId);
        assertEquals(1, n);
    }

    @Test
    void noMapperDeletesCriticalEvents() throws Exception {
        try (Stream<Path> s = Files.walk(Path.of("src/main/resources/mapper"))) {
            for (Path p : s.filter(x -> x.toString().endsWith(".xml")).toList()) {
                String text = Files.readString(p).toLowerCase();
                assertFalse(text.contains("delete from report_critical_event"), p.toString());
                assertFalse(text.contains("delete from report_critical_notification_outbox"), p.toString());
            }
        }
    }
}
