package com.moonligh.report;

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

import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.security.AuthTokens;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthGuardTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectWriteWithoutIdentity() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lab.pdf", "application/pdf", "%PDF-1.4".getBytes());
        mockMvc.perform(multipart("/api/report/reports")
                        .file(file)
                        .param("patientId", "p1")
                        .header("Idempotency-Key", "idem-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.LOGIN_REQUIRED.getCode()));
    }

    @Test
    void acceptWriteWithAccountHeader() throws Exception {
        MockMultipartFile file = ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-basic.lab.txt"));
        mockMvc.perform(multipart("/api/report/reports")
                        .file(file)
                        .param("patientId", "p1")
                        .header(AuthTokens.ACCOUNT_HEADER, "acc-auth-ok")
                        .header("Idempotency-Key", "idem-auth-ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reportId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").isNotEmpty());
    }
}
