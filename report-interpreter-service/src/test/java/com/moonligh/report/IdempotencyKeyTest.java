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
class IdempotencyKeyTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectWriteWithoutIdempotencyKey() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lab.pdf", "application/pdf", "%PDF-1.4".getBytes());
        mockMvc.perform(multipart("/api/report/reports")
                        .file(file)
                        .param("patientId", "p1")
                        .header(AuthTokens.ACCOUNT_HEADER, "acc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_MISSING.getCode()));
    }
}
