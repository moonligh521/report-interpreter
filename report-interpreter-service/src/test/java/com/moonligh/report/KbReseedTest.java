package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

/** POST /internal/report/kb/reseed：把 classpath:kb/lab-min 夹具幂等加载进 DB。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KbReseedTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        InterpretationTestSupport.clearKb(jdbc);
    }

    private int count(String table) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    void reseedLoadsFixturesIntoTheDatabase() throws Exception {
        JsonNode res = InterpretationTestSupport.reseed(mvc);

        assertEquals(200, res.path("code").asInt());
        assertEquals(3, res.path("data").path("documentsUpserted").asInt());
        assertEquals(4, res.path("data").path("chunksUpserted").asInt());
        assertEquals("kb-lab-min-v1", res.path("data").path("kbVersion").asText());
        assertEquals(3, count("report_kb_document"));
        assertEquals(4, count("report_kb_chunk"));
        String draft = jdbc.queryForObject(
                "SELECT status FROM report_kb_document WHERE doc_id = 'doc-k-draft'", String.class);
        assertEquals("DRAFT", draft);
    }

    @Test
    void reseedIsIdempotent() throws Exception {
        InterpretationTestSupport.reseed(mvc);
        JsonNode again = InterpretationTestSupport.reseed(mvc);

        assertEquals(200, again.path("code").asInt());
        assertEquals(3, count("report_kb_document"));
        assertEquals(4, count("report_kb_chunk"));
    }

    @Test
    void reseedOverwritesLocalDriftFromFixtures() throws Exception {
        InterpretationTestSupport.reseed(mvc);
        jdbc.update("UPDATE report_kb_document SET status = 'RETIRED', title = '被改过' WHERE doc_id = 'doc-k-guide'");
        jdbc.update("UPDATE report_kb_chunk SET content = '被改过' WHERE chunk_id = 'chunk-k-1'");
        jdbc.update("DELETE FROM report_kb_chunk WHERE chunk_id = 'chunk-k-2'");

        InterpretationTestSupport.reseed(mvc);

        assertEquals("PUBLISHED", jdbc.queryForObject(
                "SELECT status FROM report_kb_document WHERE doc_id = 'doc-k-guide'", String.class));
        assertNotEquals("被改过", jdbc.queryForObject(
                "SELECT content FROM report_kb_chunk WHERE chunk_id = 'chunk-k-1'", String.class));
        assertEquals(4, count("report_kb_chunk"));
    }

    @Test
    void dryRunValidatesButWritesNothing() throws Exception {
        JsonNode res = InterpretationTestSupport.reseed(mvc, "{\"source\":\"classpath:kb/lab-min\",\"dryRun\":true}");

        assertEquals(200, res.path("code").asInt());
        assertEquals(3, res.path("data").path("documentsUpserted").asInt());
        assertEquals(0, count("report_kb_document"));
        assertEquals(0, count("report_kb_chunk"));
    }

    @Test
    void defaultSourceIsLabMin() throws Exception {
        JsonNode res = InterpretationTestSupport.reseed(mvc, "{}");
        assertEquals(200, res.path("code").asInt());
        assertEquals(4, count("report_kb_chunk"));
    }

    @Test
    void onlyClasspathKbSourcesAreAccepted() throws Exception {
        for (String source : new String[] {
            "file:/etc", "http://evil.invalid/kb", "classpath:kb/../application", "classpath:mapper", "classpath:kb"}) {
            JsonNode res = InterpretationTestSupport.reseed(mvc, "{\"source\":\"" + source + "\",\"dryRun\":false}");
            assertEquals(ErrorCode.PARAM_INVALID.getCode(), res.path("code").asInt(), source);
        }
        assertEquals(0, count("report_kb_document"));
    }

    @Test
    void emptyOrMissingSourceDirectoryIsRejected() throws Exception {
        JsonNode res = InterpretationTestSupport.reseed(mvc, "{\"source\":\"classpath:kb/not-there\"}");
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), res.path("code").asInt());
    }
}
