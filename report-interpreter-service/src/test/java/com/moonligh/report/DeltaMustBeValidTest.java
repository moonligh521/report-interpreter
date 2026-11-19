package com.moonligh.report;

import static com.moonligh.report.GuardFixtures.ALT_HIGH;
import static com.moonligh.report.GuardFixtures.fact;
import static com.moonligh.report.GuardFixtures.factWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

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
import com.moonligh.report.api.res.InterpretationDeltaPayload;
import com.moonligh.report.domain.interpret.ClaimedStatus;
import com.moonligh.report.domain.interpret.ValidationStatus;

/** AC-D4 / D-R2：INTERPRETATION_DELTA 只承载 validationStatus=VALID 的块；未过 Guard 的内容永不进入事件。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SwitchableModelConfig.class)
class DeltaMustBeValidTest {

    private static final String FAITHFUL = "ALT 检测值 85 U/L，高于参考范围（7–40 U/L）。";
    private static final String TAMPERED = "ALT 检测值 58 U/L，高于参考范围（7–40 U/L）。";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SwitchableInterpretationModel model;

    @Test
    void deltaPayloadRefusesEveryNonValidStatusAtConstruction() {
        for (ValidationStatus s : ValidationStatus.values()) {
            if (s == ValidationStatus.VALID) {
                InterpretationDeltaPayload ok =
                        new InterpretationDeltaPayload("itp", "blk", "FACT", "文本", List.of("evd-1"), s.name());
                assertEquals("VALID", ok.validationStatus());
            } else {
                assertThrows(IllegalArgumentException.class,
                        () -> new InterpretationDeltaPayload("itp", "blk", "FACT", "文本", List.of(), s.name()), s.name());
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> new InterpretationDeltaPayload("itp", "blk", "FACT", "文本", List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new InterpretationDeltaPayload("itp", "blk", "FACT", "文本", List.of(), "valid"));
    }

    @Test
    void allEmittedDeltasAreValidInTheHappyPath() throws Exception {
        String account = ReportTestSupport.uniq("acc-delta-ok");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        assertEquals(200, InterpretationTestSupport.trigger(mvc, account, reportId).path("code").asInt());

        List<SseEvent> events = DeliveryTestSupport.stream(mvc, account, reportId, null);
        List<SseEvent> deltas = events.stream().filter(e -> "INTERPRETATION_DELTA".equals(e.event())).toList();
        assertFalse(deltas.isEmpty());
        for (SseEvent d : deltas) {
            assertEquals("VALID", d.data().path("payload").path("validationStatus").asText());
            assertNotNull(d.data().path("payload").path("blockId").asText());
        }
    }

    @Test
    void tamperedBlockNeverBecomesADeltaButFaithfulOnesStillDo() throws Exception {
        String account = ReportTestSupport.uniq("acc-delta-bad");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        try {
            model.use((c, attempt) -> List.of(
                    fact(ALT_HIGH, ClaimedStatus.HIGH, FAITHFUL),
                    factWith(ALT_HIGH, ClaimedStatus.HIGH, new BigDecimal("58"), "U/L", TAMPERED)));
            JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);
            assertEquals(ErrorCode.REPORT_INTERPRETATION_INVALID.getCode(), res.path("code").asInt());

            List<SseEvent> events = DeliveryTestSupport.stream(mvc, account, reportId, null);
            for (SseEvent e : events) {
                assertFalse(e.data().toString().contains("58 U/L"), e.data().toString());
                if ("INTERPRETATION_DELTA".equals(e.event())) {
                    assertEquals("VALID", e.data().path("payload").path("validationStatus").asText());
                }
            }
            assertTrue(events.stream().anyMatch(e -> "INTERPRETATION_DELTA".equals(e.event())
                    && e.data().path("payload").path("text").asText().contains("85")));
            // 解读没通过：以 ERROR 收尾，不会有 DONE。
            assertEquals("ERROR", events.get(events.size() - 1).event());
            assertFalse(DeliveryTestSupport.types(events).contains("DONE"));
            assertEquals(ErrorCode.REPORT_INTERPRETATION_INVALID.getCode(),
                    events.get(events.size() - 1).data().path("payload").path("code").asInt());

            // 落库的 payload_json 同样不含被篡改的文本。
            Integer leaked = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM report_event WHERE report_id = ? AND payload_json LIKE '%58 U/L%'",
                    Integer.class, reportId);
            assertEquals(0, leaked);
        } finally {
            model.reset();
        }
    }

    @Test
    void persistedDeltaRowsAreAllValid() throws Exception {
        String account = ReportTestSupport.uniq("acc-delta-db");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-k-high.lab.txt");
        InterpretationTestSupport.trigger(mvc, account, reportId);
        Integer nonValid = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_event WHERE report_id = ? AND event_type = 'INTERPRETATION_DELTA'"
                        + " AND payload_json NOT LIKE '%\"validationStatus\":\"VALID\"%'", Integer.class, reportId);
        assertEquals(0, nonValid);
    }
}
