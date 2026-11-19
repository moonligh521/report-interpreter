package com.moonligh.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.security.AuthTokens;

/** delivery-gate 集成测试共用：SSE 流读取 / 解析、事件列表、补偿接口。 */
final class DeliveryTestSupport {

    static final String INTERNAL_TOKEN = "consult-internal";

    private DeliveryTestSupport() {
    }

    /** 一条解析后的 SSE 事件（data 为强类型事件 JSON）。 */
    record SseEvent(String id, String event, JsonNode data) {
    }

    static MockHttpServletResponse streamRaw(MockMvc mvc, String account, String reportId, String lastEventId)
            throws Exception {
        MockHttpServletRequestBuilder b = get("/api/report/reports/" + reportId + "/interpretation/stream")
                .accept(MediaType.TEXT_EVENT_STREAM);
        if (account != null) {
            b.header(AuthTokens.ACCOUNT_HEADER, account);
        }
        if (lastEventId != null) {
            b.header("Last-Event-ID", lastEventId);
        }
        return mvc.perform(b).andReturn().getResponse();
    }

    static List<SseEvent> stream(MockMvc mvc, String account, String reportId, String lastEventId) throws Exception {
        return parseSse(streamRaw(mvc, account, reportId, lastEventId).getContentAsString());
    }

    static List<SseEvent> parseSse(String body) throws Exception {
        List<SseEvent> out = new ArrayList<>();
        for (String block : body.split("\n\n")) {
            String id = null;
            String event = null;
            String data = null;
            for (String line : block.split("\n")) {
                if (line.startsWith("id: ")) {
                    id = line.substring(4);
                } else if (line.startsWith("event: ")) {
                    event = line.substring(7);
                } else if (line.startsWith("data: ")) {
                    data = line.substring(6);
                }
            }
            if (id != null) {
                out.add(new SseEvent(id, event, ReportTestSupport.JSON.readTree(data)));
            }
        }
        return out;
    }

    static JsonNode events(MockMvc mvc, String account, String reportId) throws Exception {
        return ReportTestSupport.JSON.readTree(mvc.perform(get("/api/report/reports/" + reportId + "/events")
                        .header(AuthTokens.ACCOUNT_HEADER, account))
                .andReturn().getResponse().getContentAsString());
    }

    static JsonNode compensate(MockMvc mvc, String token, String jobId, String body) throws Exception {
        var b = post("/internal/report/jobs/" + jobId + "/compensate")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            b.header(AuthTokens.INTERNAL_HEADER, token);
        }
        return ReportTestSupport.JSON.readTree(mvc.perform(b).andReturn().getResponse().getContentAsString());
    }

    static String compensateBody(String outboxId, boolean force) {
        return "{\"outboxId\":\"" + outboxId + "\",\"force\":" + force + "}";
    }

    static List<String> types(List<SseEvent> events) {
        return events.stream().map(SseEvent::event).toList();
    }
}
