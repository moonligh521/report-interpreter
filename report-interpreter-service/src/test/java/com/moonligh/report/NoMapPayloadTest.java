package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.moonligh.report.api.msg.ArchiveOutboxMessage;
import com.moonligh.report.api.req.CompensateReq;
import com.moonligh.report.api.res.CompensateRes;
import com.moonligh.report.api.res.CorrectionPayload;
import com.moonligh.report.api.res.CriticalAlertPayload;
import com.moonligh.report.api.res.DonePayload;
import com.moonligh.report.api.res.ErrorPayload;
import com.moonligh.report.api.res.IndicatorSummary;
import com.moonligh.report.api.res.InterpretationDeltaPayload;
import com.moonligh.report.api.res.ReportEventPayload;
import com.moonligh.report.api.res.ReportEventType;
import com.moonligh.report.api.res.ReportEventsRes;
import com.moonligh.report.api.res.ReportLifecycleRes;
import com.moonligh.report.api.res.ReportSseEventRes;
import com.moonligh.report.api.res.StructuredResultPayload;
import com.moonligh.report.common.PayloadJson;

/** AC-D11：事件 payload / 档案消息 / 补偿契约全部强类型（sealed + record），不出现 Map。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NoMapPayloadTest {

    private static final Pattern MAP_TYPE = Pattern.compile("\\bMap\\s*<|java\\.util\\.(Hash|LinkedHash|Tree)?Map|import\\s+java\\.util\\.Map");

    private static final List<ReportEventPayload> SAMPLES = List.of(
            new StructuredResultPayload(1, "MATCHED", List.of(new IndicatorSummary("K", null, "mmol/L", "HIGH", true))),
            new CriticalAlertPayload("itp_1", "ibk_1", "K", "危急值提醒"),
            new InterpretationDeltaPayload("itp_1", "ibk_2", "FACT", "文本", List.of("evd-1"), "VALID"),
            new CorrectionPayload("itp_0", "itp_1", "REINTERPRETED"),
            new DonePayload("itp_1", "COMPLETED", "PENDING", "NONE"),
            new ErrorPayload("itp_1", 4107, "RETRYABLE"));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static void assertNoMapComponents(Class<?> type) {
        assertTrue(type.isRecord(), type.getName() + " 必须是 record");
        for (RecordComponent c : type.getRecordComponents()) {
            String generic = c.getGenericType().getTypeName();
            assertFalse(generic.contains("java.util.Map"), type.getSimpleName() + "." + c.getName() + " -> " + generic);
            assertFalse(Object.class.equals(c.getType()), type.getSimpleName() + "." + c.getName() + " 不得是 Object");
        }
    }

    @Test
    void payloadHierarchyIsSealedAndCoversExactlyTheEventTypes() {
        assertTrue(ReportEventPayload.class.isSealed());
        Set<Class<?>> permitted = new LinkedHashSet<>(Arrays.asList(ReportEventPayload.class.getPermittedSubclasses()));
        assertEquals(ReportEventType.values().length, permitted.size());
        permitted.forEach(NoMapPayloadTest::assertNoMapComponents);

        Set<ReportEventType> covered = new LinkedHashSet<>();
        for (ReportEventPayload p : SAMPLES) {
            assertTrue(permitted.contains(p.getClass()), p.getClass().getName());
            covered.add(p.type());
        }
        assertEquals(Set.of(ReportEventType.values()), covered);
    }

    @Test
    void contractRecordsHaveNoMapComponents() {
        for (Class<?> c : List.of(
                ReportSseEventRes.class, ReportEventsRes.class, ReportLifecycleRes.class, ArchiveOutboxMessage.class,
                CompensateReq.class, CompensateRes.class, IndicatorSummary.class)) {
            assertNoMapComponents(c);
        }
        // payload 字段的声明类型是 sealed 接口，而不是 Map / Object。
        RecordComponent payload = Arrays.stream(ReportSseEventRes.class.getRecordComponents())
                .filter(c -> c.getName().equals("payload")).findFirst().orElseThrow();
        assertEquals(ReportEventPayload.class, payload.getType());
    }

    @Test
    void everyPayloadSerializesWithATypeDiscriminatorAndRoundTripsToTheSameType() {
        for (ReportEventPayload p : SAMPLES) {
            String json = PayloadJson.write(p);
            assertTrue(json.contains("\"payloadType\":\"" + p.type().name() + "\""), json);
            assertFalse(json.contains("\"payload\":{}"), json);
            ReportEventPayload back = PayloadJson.read(json, ReportEventPayload.class);
            assertEquals(p, back);
            assertEquals(p.getClass(), back.getClass());
        }
        ReportSseEventRes event = new ReportSseEventRes(
                "evt_1", "rpt_1", 1, ReportEventType.DONE, "2026-11-19T00:00:00Z", SAMPLES.get(4));
        String json = PayloadJson.write(event);
        assertFalse(json.contains("\"payload\":{}"), json);
        assertFalse(json.contains("\"payload\":null"), json);
        assertEquals(event, PayloadJson.read(json, ReportSseEventRes.class));
    }

    @Test
    void persistedAndStreamedPayloadsAreTypedForARealFlow() throws Exception {
        String account = ReportTestSupport.uniq("acc-nomap");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-critical-k.lab.txt");
        InterpretationTestSupport.trigger(mvc, account, reportId);

        Integer untyped = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_event WHERE report_id = ? AND payload_json NOT LIKE '%\"payloadType\"%'",
                Integer.class, reportId);
        assertEquals(0, untyped);
        Integer emptyObject = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_event WHERE report_id = ? AND payload_json IN ('{}', 'null')",
                Integer.class, reportId);
        assertEquals(0, emptyObject);
        for (DeliveryTestSupport.SseEvent e : DeliveryTestSupport.stream(mvc, account, reportId, null)) {
            assertNotNull(e.data().path("payload").path("payloadType").asText(null));
            assertEquals(e.event(), e.data().path("payload").path("payloadType").asText());
            assertTrue(e.data().path("payload").size() > 1, e.data().toString());
        }
        String archiveJson = jdbc.queryForObject(
                "SELECT payload_json FROM report_archive_outbox WHERE report_id = ?", String.class, reportId);
        ArchiveOutboxMessage msg = PayloadJson.read(archiveJson, ArchiveOutboxMessage.class);
        assertEquals(reportId, msg.reportId());
    }

    @Test
    void mainSourcesAndMapperXmlContainNoMapTypes() throws IOException {
        // 整个 API 契约模块 + delivery-gate 新增 / 改动的服务端源码：完全不出现 Map。
        for (String root : List.of(
                "../report-interpreter-api/src/main/java",
                "src/main/java/com/moonligh/report/domain/event", "src/main/java/com/moonligh/report/domain/archive")) {
            try (Stream<Path> s = Files.walk(Path.of(root))) {
                for (Path p : s.filter(x -> x.toString().endsWith(".java")).toList()) {
                    assertFalse(MAP_TYPE.matcher(Files.readString(p)).find(), p.toString());
                }
            }
        }
        for (String f : List.of(
                "common/PayloadJson.java", "config/ArchiveConfig.java", "service/ReportEventService.java",
                "service/ArchiveOutboxService.java", "service/ReportLifecycleService.java",
                "service/ReportInterpretationService.java", "service/IndicatorDtoAssembler.java",
                "controller/ReportEventController.java", "controller/InternalJobController.java",
                "infrastructure/persistence/ReportEventRow.java", "infrastructure/persistence/ReportEventMapper.java",
                "infrastructure/persistence/ReportEventStoreImpl.java",
                "infrastructure/persistence/ReportArchiveOutboxRow.java",
                "infrastructure/persistence/ReportArchiveOutboxMapper.java",
                "infrastructure/persistence/ArchiveOutboxStoreImpl.java",
                "infrastructure/persistence/ReportArchiveRecordRow.java",
                "infrastructure/persistence/ReportArchiveRecordMapper.java",
                "infrastructure/persistence/ArchiveRecordStoreImpl.java")) {
            assertFalse(MAP_TYPE.matcher(Files.readString(Path.of("src/main/java/com/moonligh/report/" + f))).find(), f);
        }
        for (String xml : List.of("ReportEventMapper.xml", "ReportArchiveOutboxMapper.xml", "ReportArchiveRecordMapper.xml")) {
            String text = Files.readString(Path.of("src/main/resources/mapper/" + xml));
            assertFalse(text.contains("java.util.HashMap"), xml);
            assertFalse(text.contains("java.util.Map"), xml);
            assertFalse(text.contains("parameterType=\"map\""), xml);
            assertFalse(text.contains("resultType=\"map\""), xml);
            assertTrue(text.contains("Row\""), xml);
        }
    }
}
