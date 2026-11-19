package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.domain.interpret.InterpretationVersions;

/** AC-G1 / G-R1：知识库为空 → 降级话术，无指南口吻、无伪造依据。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KbEmptyDegradeTest {

    private static final Pattern GUIDELINE_TONE = Pattern.compile("指南|共识|研究表明|研究显示|循证|权威|专家一致");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyKb() {
        InterpretationTestSupport.clearKb(jdbc);
    }

    @Test
    void emptyKbDegradesWithoutGuidelineTone() throws Exception {
        String account = ReportTestSupport.uniq("acc-kb-empty");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals(200, res.path("code").asInt());
        assertEquals("DEGRADED", res.path("data").path("status").asText());
        assertEquals(InterpretationVersions.KB_EMPTY, res.path("data").path("pinnedVersions").path("kbVersion").asText());
        assertEquals(0, res.path("data").path("evidence").size());

        List<String> types = InterpretationTestSupport.claimTypes(res);
        assertFalse(types.contains("GUIDELINE"), types.toString());
        for (String text : InterpretationTestSupport.texts(res)) {
            assertFalse(GUIDELINE_TONE.matcher(text).find(), text);
        }
        List<String> texts = InterpretationTestSupport.texts(res);
        assertTrue(texts.stream().anyMatch(t -> t.contains("暂无可引用的依据")), texts.toString());
        assertTrue(texts.stream().anyMatch(t -> t.contains("ALT") && t.contains("高于参考范围")), texts.toString());
        for (JsonNode b : res.path("data").path("blocks")) {
            assertEquals(0, b.path("evidenceIds").size());
        }
    }

    @Test
    void degradedInterpretationIsPersistedAndReadable() throws Exception {
        String account = ReportTestSupport.uniq("acc-kb-empty-get");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-matched-normal.lab.txt");
        JsonNode created = InterpretationTestSupport.trigger(mvc, account, reportId);
        JsonNode read = InterpretationTestSupport.latest(mvc, account, reportId);

        assertEquals(200, read.path("code").asInt());
        assertEquals(created.path("data").path("interpretationId").asText(),
                read.path("data").path("interpretationId").asText());
        assertEquals("DEGRADED", read.path("data").path("status").asText());
        assertEquals(InterpretationTestSupport.texts(created), InterpretationTestSupport.texts(read));
    }

    @Test
    void criticalAlertStillPresentWhenKbEmpty() throws Exception {
        String account = ReportTestSupport.uniq("acc-kb-empty-crit");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-critical-k.lab.txt");
        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        // 危急值不依赖 KB：仍给出危急提醒，Result.code=4105，状态为降级。
        assertEquals(ErrorCode.REPORT_CRITICAL_HIT.getCode(), res.path("code").asInt());
        assertEquals("DEGRADED", res.path("data").path("status").asText());
        assertEquals("CRITICAL_ALERT", InterpretationTestSupport.claimTypes(res).get(0));
    }
}
