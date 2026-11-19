package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.security.AuthTokens;

/**
 * LoadSmokeIT：轻量并发冒烟（非正式压测门禁）。
 * 诚实说明：本测试仅验证短并发下 create+trend 不崩溃且业务 code=200，
 * 不替代真实压测放行清单（QPS/延迟/资源阈值）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoadSmokeIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void shortConcurrentCreateAndTrendStayHealthy() throws Exception {
        String account = ReportTestSupport.uniq("acc-load");
        String patient = "p-load-smoke";
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int idx = i;
                tasks.add(() -> {
                    String body = """
                            REPORT_TYPE=LAB_PANEL
                            PATIENT_NAME=张三
                            SPECIMEN_TIME=2026-04-%02dT08:00:00Z
                            ALT|%d|U/L|7|40|ALT %d U/L|0
                            """.formatted(Math.min(idx + 1, 28), 40 + idx, 40 + idx);
                    MockMultipartFile file = new MockMultipartFile(
                            "file", "l.lab.txt", "text/plain", body.getBytes(StandardCharsets.UTF_8));
                    JsonNode created = ReportTestSupport.JSON.readTree(mvc.perform(
                                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                            .multipart("/api/report/reports")
                                            .file(file)
                                            .param("patientId", patient)
                                            .param("expectedPatientName", "张三")
                                            .header(AuthTokens.ACCOUNT_HEADER, account)
                                            .header("Idempotency-Key", ReportTestSupport.uniq("lk")))
                            .andReturn().getResponse().getContentAsString());
                    return created.path("code").asInt();
                });
            }
            List<Future<Integer>> futures = pool.invokeAll(tasks);
            for (Future<Integer> f : futures) {
                assertEquals(200, f.get());
            }
            JsonNode trend = ReportTestSupport.JSON.readTree(mvc.perform(get(
                            "/api/report/patients/" + patient + "/indicators/ALT/trend")
                            .header(AuthTokens.ACCOUNT_HEADER, account))
                    .andReturn().getResponse().getContentAsString());
            assertEquals(200, trend.path("code").asInt());
            assertTrue(trend.path("data").path("points").size() >= n);
        } finally {
            pool.shutdownNow();
        }
    }
}
