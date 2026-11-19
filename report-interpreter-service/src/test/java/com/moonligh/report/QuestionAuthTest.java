package com.moonligh.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.security.AuthTokens;

/** 追问接口鉴权与幂等头：与其它写接口一致。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuestionAuthTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void rejectWithoutAccount() throws Exception {
        mvc.perform(post("/api/report/reports/rpt_x/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", ReportTestSupport.uniq("q"))
                        .content("{\"question\":\"ALT 什么意思？\",\"focusCanonicalCodes\":[\"ALT\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.LOGIN_REQUIRED.getCode()));
    }

    @Test
    void rejectWithoutIdempotencyKey() throws Exception {
        mvc.perform(post("/api/report/reports/rpt_x/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(AuthTokens.ACCOUNT_HEADER, "acc-q")
                        .content("{\"question\":\"ALT 什么意思？\",\"focusCanonicalCodes\":[\"ALT\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_MISSING.getCode()));
    }
}
