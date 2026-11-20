package com.moonligh.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.security.AuthTokens;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResultContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createResponseIsTypedResultWithCode200() throws Exception {
        MockMultipartFile file = ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-basic.lab.txt"));
        MvcResult result = mockMvc.perform(multipart("/api/report/reports")
                        .file(file)
                        .param("patientId", "p-contract")
                        .header(AuthTokens.ACCOUNT_HEADER, "acc-contract")
                        .header("Idempotency-Key", "idem-contract"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.data.reportId").isString())
                .andExpect(jsonPath("$.data.status").isString())
                .andExpect(jsonPath("$.data").isMap())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        // 禁止把业务字段塞进自由 Map 扩展键；契约字段固定。
        data.fieldNames().forEachRemaining(name -> {
            if (!name.equals("reportId") && !name.equals("status") && !name.equals("parseVersion")) {
                throw new AssertionError("unexpected field: " + name);
            }
        });
    }

    @Test
    void getDetailReturnsTypedShape() throws Exception {
        MockMultipartFile file = ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-basic.lab.txt"));
        MvcResult created = mockMvc.perform(multipart("/api/report/reports")
                        .file(file)
                        .param("patientId", "p-get")
                        .header(AuthTokens.ACCOUNT_HEADER, "acc-get")
                        .header("Idempotency-Key", "idem-get"))
                .andExpect(status().isOk())
                .andReturn();
        String reportId = objectMapper
                .readTree(created.getResponse().getContentAsString())
                .path("data")
                .path("reportId")
                .asText();

        mockMvc.perform(get("/api/report/reports/" + reportId)
                        .header(AuthTokens.ACCOUNT_HEADER, "acc-get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(reportId))
                .andExpect(jsonPath("$.data.status").isNotEmpty())
                .andExpect(jsonPath("$.data.reportType").value("LAB_PANEL"));
    }
}
