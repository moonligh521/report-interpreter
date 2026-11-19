package com.moonligh.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.security.AuthTokens;

/**
 * parse 分支测试共用：金标夹具读取与 MockMvc 调用。
 */
final class ReportTestSupport {

    static final ObjectMapper JSON = new ObjectMapper();

    private ReportTestSupport() {
    }

    static String golden(String name) throws IOException {
        return new String(
                new ClassPathResource("golden/" + name).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    static MockMultipartFile labFile(String text) {
        return new MockMultipartFile("file", "sample.lab.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8));
    }

    static String uniq(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    static JsonNode create(MockMvc mvc, String account, String key, String reportType, MockMultipartFile file)
            throws Exception {
        MockMultipartHttpServletRequestBuilder b = multipart("/api/report/reports")
                .file(file)
                .param("patientId", "p-parse")
                .header(AuthTokens.ACCOUNT_HEADER, account)
                .header("Idempotency-Key", key);
        if (reportType != null) {
            b.param("reportType", reportType);
        }
        return read(mvc.perform(b).andReturn().getResponse().getContentAsString());
    }

    static JsonNode createWithExpected(
            MockMvc mvc, String account, String key, String expectedPatientName, MockMultipartFile file)
            throws Exception {
        MockMultipartHttpServletRequestBuilder b = multipart("/api/report/reports")
                .file(file)
                .param("patientId", "p-rules")
                .header(AuthTokens.ACCOUNT_HEADER, account)
                .header("Idempotency-Key", key);
        if (expectedPatientName != null) {
            b.param("expectedPatientName", expectedPatientName);
        }
        return read(mvc.perform(b).andReturn().getResponse().getContentAsString());
    }

    static JsonNode criticalEvents(MockMvc mvc, String account, String reportId) throws Exception {
        return read(mvc.perform(get("/api/report/reports/" + reportId + "/critical-events")
                        .header(AuthTokens.ACCOUNT_HEADER, account))
                .andReturn().getResponse().getContentAsString());
    }

    /** 以期望姓名建单并返回 reportId 与创建响应。 */
    static JsonNode createRules(MockMvc mvc, String account, String fixture, String expectedName) throws Exception {
        return createWithExpected(mvc, account, uniq("k"), expectedName, labFile(golden(fixture)));
    }

    static JsonNode reparse(MockMvc mvc, String account, String key, String reportId) throws Exception {
        return read(mvc.perform(post("/api/report/reports/" + reportId + "/reparse")
                        .header(AuthTokens.ACCOUNT_HEADER, account)
                        .header("Idempotency-Key", key))
                .andReturn().getResponse().getContentAsString());
    }

    static JsonNode detail(MockMvc mvc, String account, String reportId) throws Exception {
        return read(mvc.perform(get("/api/report/reports/" + reportId)
                        .header(AuthTokens.ACCOUNT_HEADER, account))
                .andReturn().getResponse().getContentAsString());
    }

    static JsonNode indicator(JsonNode detailRoot, String rawName) {
        for (JsonNode n : detailRoot.path("data").path("indicators")) {
            if (rawName.equals(n.path("rawName").asText())) {
                return n;
            }
        }
        throw new AssertionError("indicator not found: " + rawName);
    }

    private static JsonNode read(String body) throws IOException {
        return JSON.readTree(body);
    }
}