package com.moonligh.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.security.AuthTokens;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalTokenTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectInternalWithoutToken() throws Exception {
        mockMvc.perform(post("/internal/report/kb/reseed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"classpath:kb/lab-min\",\"dryRun\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.LOGIN_REQUIRED.getCode()));
    }

    @Test
    void acceptInternalWithToken() throws Exception {
        mockMvc.perform(post("/internal/report/kb/reseed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(AuthTokens.INTERNAL_HEADER, "consult-internal")
                        .content("{\"source\":\"classpath:kb/lab-min\",\"dryRun\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.kbVersion").isNotEmpty());
    }
}
