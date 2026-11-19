package com.moonligh.report;

import static com.moonligh.report.GuardFixtures.ALT_HIGH;
import static com.moonligh.report.GuardFixtures.fact;
import static com.moonligh.report.GuardFixtures.factWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.moonligh.report.SwitchableModelConfig.SwitchableInterpretationModel;
import com.moonligh.report.domain.archive.ArchiveDeliveryResult;
import com.moonligh.report.domain.archive.ArchiveSender;
import com.moonligh.report.domain.interpret.ClaimedStatus;
import com.moonligh.report.service.ArchiveOutboxService;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;

/**
 * D-R9：日志不得出现指标全文 / 解读文本 / 证件号 / 姓名。两道防线：
 * 1) 运行期：跑完整条链路（含 Guard 拒绝、身份冲突、投递失败、补偿被拒）后断言捕获的日志不含敏感内容；
 * 2) 编码规则：扫描 main 源码里所有日志调用，禁止把载荷 / 文本 / 姓名 / 证件字段作为参数。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SwitchableModelConfig.class)
class NoSensitiveLogTest {

    private static final String ID_NUMBER = "110101199003078888";
    private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");
    private static final List<String> FORBIDDEN = List.of(
            "ALT 检测值", "85 U/L", "58 U/L", "K 6.8 mmol/L", "6.8 mmol/L", "危急值提醒", "请立即联系医生",
            "张三", ID_NUMBER, "ALT 85 U/L 7-40");

    private static final Pattern LOG_CALL =
            Pattern.compile("(?s)\\b(?:LOG|log|logger)\\.(?:trace|debug|info|warn|error)\\((.*?)\\);");
    private static final Pattern FORBIDDEN_ARG = Pattern.compile(
            "rawText|payloadJson|payload\\(|\\.text\\(\\)|\\.content\\(\\)|snippet|idCard|idNo|identityNo|"
                    + "patientName|expectedPatientName|sourceText|ArchiveOutboxMessage|indicators\\(");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SwitchableInterpretationModel model;

    @Autowired
    private ArchiveOutboxService archive;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger root;

    @BeforeEach
    void attach() {
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void detach() {
        root.detachAppender(appender);
        appender.stop();
        model.reset();
    }

    private String allLogText() {
        StringBuilder sb = new StringBuilder();
        for (ILoggingEvent e : appender.list) {
            sb.append(e.getFormattedMessage()).append('\n');
            for (IThrowableProxy t = e.getThrowableProxy(); t != null; t = t.getCause()) {
                sb.append(t.getClassName()).append(": ").append(t.getMessage()).append('\n');
            }
        }
        return sb.toString();
    }

    @Test
    void fullFlowLogsNeverCarryIndicatorTextInterpretationTextOrIdentifiers() throws Exception {
        // 1) Guard 拒绝（篡改数值）：会打 warn，但只含机器码与报告号。
        String account = ReportTestSupport.uniq("acc-log");
        String tampered = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        model.use((c, attempt) -> List.of(
                fact(ALT_HIGH, ClaimedStatus.HIGH, "ALT 检测值 85 U/L，高于参考范围（7–40 U/L）。"),
                factWith(ALT_HIGH, ClaimedStatus.HIGH, new BigDecimal("58"), "U/L",
                        "ALT 检测值 58 U/L，高于参考范围（7–40 U/L）。")));
        InterpretationTestSupport.trigger(mvc, account, tampered);
        model.reset();

        // 2) 危急报告：解读 + 流 + 事件列表 + 档案投递失败 / 成功。
        String critical = InterpretationTestSupport.createReport(mvc, account, "rules-critical-k.lab.txt");
        InterpretationTestSupport.trigger(mvc, account, critical);
        DeliveryTestSupport.stream(mvc, account, critical, null);
        DeliveryTestSupport.events(mvc, account, critical);
        String outboxId = jdbc.queryForObject(
                "SELECT outbox_id FROM report_archive_outbox WHERE report_id = ?", String.class, critical);
        archive.dispatch(outboxId, m -> ArchiveDeliveryResult.failed("ARCHIVE_DOWN"));
        archive.dispatch(outboxId, m -> {
            throw new IllegalStateException("downstream blew up");
        });
        ArchiveSender ok = m -> ArchiveDeliveryResult.ok();
        archive.dispatch(outboxId, ok);

        // 3) 业务异常路径：身份冲突（期望姓名是证件号）、越权重连、补偿被拒。
        String conflict = ReportTestSupport.createRules(mvc, account, "rules-matched-normal.lab.txt", ID_NUMBER)
                .path("data").path("reportId").asText();
        InterpretationTestSupport.trigger(mvc, account, conflict);
        DeliveryTestSupport.streamRaw(mvc, ReportTestSupport.uniq("acc-other"), critical, null);
        DeliveryTestSupport.compensate(mvc, DeliveryTestSupport.INTERNAL_TOKEN, critical,
                DeliveryTestSupport.compensateBody(outboxId, true));

        String logs = allLogText();
        // 链路确实在打日志（防止断言空转）：Guard 拒绝、业务异常都应出现。
        assertTrue(logs.contains("interpretation guard rejected"), "expected guard warn in logs");
        assertTrue(logs.contains("业务异常"), "expected business-exception log");
        for (String forbidden : FORBIDDEN) {
            assertFalse(logs.contains(forbidden), "log leaked: " + forbidden);
        }
        assertFalse(ID_CARD.matcher(logs).find(), "log contains an ID-card-like number");
    }

    @Test
    void noLogCallInMainSourcesPassesPayloadTextOrIdentifiers() throws IOException {
        int inspected = 0;
        try (Stream<Path> s = Files.walk(Path.of("src/main/java"))) {
            for (Path p : s.filter(x -> x.toString().endsWith(".java")).toList()) {
                Matcher m = LOG_CALL.matcher(Files.readString(p));
                while (m.find()) {
                    inspected++;
                    assertFalse(FORBIDDEN_ARG.matcher(m.group(1)).find(), p + " -> " + m.group(0));
                }
            }
        }
        // 规则本身有效：至少扫到了现有的日志调用。
        assertTrue(inspected >= 2, "scanned log calls: " + inspected);
        assertEquals(true, FORBIDDEN_ARG.matcher("LOG.warn(x, row.payloadJson())").find());
    }
}
