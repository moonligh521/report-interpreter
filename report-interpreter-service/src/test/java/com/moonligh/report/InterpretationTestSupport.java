package com.moonligh.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.security.AuthTokens;

/** rag-guard 集成测试共用：KB 重建 / 清空、建单、触发与读取解读。 */
final class InterpretationTestSupport {

    private InterpretationTestSupport() {
    }

    static JsonNode reseed(MockMvc mvc) throws Exception {
        return reseed(mvc, "{\"source\":\"classpath:kb/lab-min\",\"dryRun\":false}");
    }

    static JsonNode reseed(MockMvc mvc, String body) throws Exception {
        return ReportTestSupport.JSON.readTree(mvc.perform(post("/internal/report/kb/reseed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(AuthTokens.INTERNAL_HEADER, "consult-internal")
                        .content(body))
                .andReturn().getResponse().getContentAsString());
    }

    static void clearKb(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM report_kb_chunk");
        jdbc.update("DELETE FROM report_kb_document");
    }

    /** 以期望姓名「张三」建单，返回 reportId。 */
    static String createReport(MockMvc mvc, String account, String fixture) throws Exception {
        return ReportTestSupport.createRules(mvc, account, fixture, "张三").path("data").path("reportId").asText();
    }

    static JsonNode trigger(MockMvc mvc, String account, String reportId, String key, String body) throws Exception {
        var b = post("/api/report/reports/" + reportId + "/interpretation")
                .header(AuthTokens.ACCOUNT_HEADER, account)
                .header("Idempotency-Key", key);
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return ReportTestSupport.JSON.readTree(mvc.perform(b).andReturn().getResponse().getContentAsString());
    }

    static JsonNode trigger(MockMvc mvc, String account, String reportId) throws Exception {
        return trigger(mvc, account, reportId, ReportTestSupport.uniq("itp"), null);
    }

    static JsonNode latest(MockMvc mvc, String account, String reportId) throws Exception {
        return ReportTestSupport.JSON.readTree(mvc.perform(get("/api/report/reports/" + reportId + "/interpretation")
                        .header(AuthTokens.ACCOUNT_HEADER, account))
                .andReturn().getResponse().getContentAsString());
    }

    static List<String> texts(JsonNode res) {
        List<String> out = new ArrayList<>();
        for (JsonNode b : res.path("data").path("blocks")) {
            out.add(b.path("text").asText());
        }
        return out;
    }

    static List<String> claimTypes(JsonNode res) {
        List<String> out = new ArrayList<>();
        for (JsonNode b : res.path("data").path("blocks")) {
            out.add(b.path("claimType").asText());
        }
        return out;
    }
}
