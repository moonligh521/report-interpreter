package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.domain.parse.UploadGuard;

/** AC-P8：MIME+魔数、空文件、超大、夹具开关。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileSecurityTest {

    private static final long MAX = 20L * 1024 * 1024;

    @Autowired
    private MockMvc mvc;

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static void assertRejected(String name, String mime, byte[] content, long max, boolean fixture) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> UploadGuard.check(name, mime, content, max, fixture));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), ex.getCode());
    }

    @Test
    void exeDisguisedAsPdfRejected() {
        assertRejected("evil.pdf", "application/pdf", bytes("MZ\u0090\u0000\u0003"), MAX, true);
    }

    @Test
    void exeMimeRejected() {
        assertRejected("a.exe", "application/x-msdownload", bytes("MZ\u0090"), MAX, true);
    }

    @Test
    void emptyRejected() {
        assertRejected("a.pdf", "application/pdf", new byte[0], MAX, true);
    }

    @Test
    void oversizeRejected() {
        assertRejected("a.pdf", "application/pdf", bytes("%PDF-1.7 padding"), 4, true);
    }

    @Test
    void textFixtureRejectedWhenDisabled() {
        assertRejected("a.lab.txt", "text/plain", bytes("REPORT_TYPE=LAB_PANEL"), MAX, false);
    }

    @Test
    void validMagicAccepted() {
        assertEquals(UploadGuard.Kind.PDF,
                UploadGuard.check("a.pdf", "application/pdf", bytes("%PDF-1.7"), MAX, false));
        assertEquals(UploadGuard.Kind.IMAGE,
                UploadGuard.check("a.png", "image/png", bytes("\u0089PNG\r\n\u001a\n"), MAX, false));
        assertEquals(UploadGuard.Kind.IMAGE,
                UploadGuard.check("a.jpg", "image/jpeg", bytes("\u00ff\u00d8\u00ff\u00e0"), MAX, false));
        assertEquals(UploadGuard.Kind.TEXT_FIXTURE,
                UploadGuard.check("a.lab.txt", "text/plain", bytes("REPORT_TYPE=LAB_PANEL"), MAX, true));
    }

    @Test
    void httpFakeExeRejected() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("file", "evil.pdf", "application/pdf", bytes("MZ\u0090\u0000"));
        var res = ReportTestSupport.create(mvc, ReportTestSupport.uniq("acc"), ReportTestSupport.uniq("k"), "LAB_PANEL", fake);
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), res.path("code").asInt());
    }

    @Test
    void httpEmptyRejected() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        var res = ReportTestSupport.create(mvc, ReportTestSupport.uniq("acc"), ReportTestSupport.uniq("k"), "LAB_PANEL", empty);
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), res.path("code").asInt());
    }
}