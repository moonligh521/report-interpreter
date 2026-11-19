package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.security.AuthTokens;
import com.moonligh.report.domain.interpret.InterpretationVersions;
import com.moonligh.report.domain.rule.RuleSetVersion;

/** AC-G2 / G-R2：一次解读钉死 kb/rule/prompt/parser/schema 五类版本（强类型对象，非 Map），后续升级不改写历史。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PinVersionsTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() throws Exception {
        InterpretationTestSupport.clearKb(jdbc);
        assertEquals(200, InterpretationTestSupport.reseed(mvc).path("code").asInt());
    }

    @Test
    void responseCarriesAllFivePinnedVersions() throws Exception {
        String account = ReportTestSupport.uniq("acc-pin");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-k-high.lab.txt");

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);
        JsonNode pinned = res.path("data").path("pinnedVersions");

        assertEquals("kb-lab-min-v1", pinned.path("kbVersion").asText());
        assertEquals(RuleSetVersion.CURRENT, pinned.path("ruleVersion").asText());
        assertEquals(InterpretationVersions.PROMPT_VERSION, pinned.path("promptVersion").asText());
        assertEquals(InterpretationVersions.PARSER_VERSION, pinned.path("parserVersion").asText());
        assertEquals(InterpretationVersions.SCHEMA_VERSION, pinned.path("schemaVersion").asText());
        assertEquals(5, pinned.size());
        for (String f : new String[] {"kbVersion", "ruleVersion", "promptVersion", "parserVersion", "schemaVersion"}) {
            assertFalse(pinned.path(f).asText().isBlank(), f);
        }
    }

    @Test
    void pinnedVersionsArePersistedOnTheInterpretationRow() throws Exception {
        String account = ReportTestSupport.uniq("acc-pin-row");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-k-high.lab.txt");
        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);
        String id = res.path("data").path("interpretationId").asText();

        assertEquals("kb-lab-min-v1", column("kb_version", id));
        assertEquals(RuleSetVersion.CURRENT, column("rule_version", id));
        assertEquals(InterpretationVersions.PROMPT_VERSION, column("prompt_version", id));
        assertEquals(InterpretationVersions.PARSER_VERSION, column("parser_version", id));
        assertEquals(InterpretationVersions.SCHEMA_VERSION, column("schema_version", id));
    }

    private String column(String name, String interpretationId) {
        return jdbc.queryForObject(
                "SELECT " + name + " FROM report_interpretation WHERE interpretation_id = ?",
                String.class, interpretationId);
    }

    @Test
    void laterKbUpgradeDoesNotRewriteHistoricalInterpretation() throws Exception {
        String account = ReportTestSupport.uniq("acc-pin-hist");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-k-high.lab.txt");
        JsonNode first = InterpretationTestSupport.trigger(mvc, account, reportId);
        assertEquals("kb-lab-min-v1", first.path("data").path("pinnedVersions").path("kbVersion").asText());

        // KB 升级到 v9：新解读钉新版本；历史解读读回仍是 v1。
        jdbc.update("UPDATE report_kb_document SET kb_version = 'kb-lab-min-v9' WHERE status = 'PUBLISHED'");
        JsonNode second = InterpretationTestSupport.trigger(mvc, account, reportId);
        assertEquals("kb-lab-min-v9", second.path("data").path("pinnedVersions").path("kbVersion").asText());

        String firstId = first.path("data").path("interpretationId").asText();
        String kbOfFirst = jdbc.queryForObject(
                "SELECT kb_version FROM report_interpretation WHERE interpretation_id = ?", String.class, firstId);
        assertEquals("kb-lab-min-v1", kbOfFirst);
        assertTrue(!firstId.equals(second.path("data").path("interpretationId").asText()));

        // GET 读最新一次，且版本与其落库一致。
        JsonNode latest = InterpretationTestSupport.latest(mvc, account, reportId);
        assertEquals(second.path("data").path("interpretationId").asText(),
                latest.path("data").path("interpretationId").asText());
        assertEquals("kb-lab-min-v9", latest.path("data").path("pinnedVersions").path("kbVersion").asText());
    }

    @Test
    void sameIdempotencyKeyReplaysTheSameInterpretation() throws Exception {
        String account = ReportTestSupport.uniq("acc-pin-idem");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-k-high.lab.txt");
        String key = ReportTestSupport.uniq("itp");

        JsonNode a = InterpretationTestSupport.trigger(mvc, account, reportId, key, null);
        jdbc.update("UPDATE report_kb_document SET kb_version = 'kb-lab-min-v8' WHERE status = 'PUBLISHED'");
        JsonNode b = InterpretationTestSupport.trigger(mvc, account, reportId, key, null);

        assertEquals(a.path("data").path("interpretationId").asText(), b.path("data").path("interpretationId").asText());
        assertEquals("kb-lab-min-v1", b.path("data").path("pinnedVersions").path("kbVersion").asText());
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_interpretation WHERE report_id = ?", Integer.class, reportId);
        assertEquals(1, n);
    }

    @Test
    void otherAccountCannotReadInterpretation() throws Exception {
        String account = ReportTestSupport.uniq("acc-pin-own");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-k-high.lab.txt");
        InterpretationTestSupport.trigger(mvc, account, reportId);

        JsonNode res = ReportTestSupport.JSON.readTree(mvc.perform(get("/api/report/reports/" + reportId + "/interpretation")
                        .header(AuthTokens.ACCOUNT_HEADER, ReportTestSupport.uniq("acc-intruder")))
                .andReturn().getResponse().getContentAsString());
        assertFalse(res.path("code").asInt() == 200);
    }
}
