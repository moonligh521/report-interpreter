package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.moonligh.consult.framework.ErrorCode;

/** AC-P7 / V0.2：IMAGING_FILM 与未知类型 → 4101；CHECKUP/ECG/US 可接受。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportTypeRejectTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void imagingFilmRejected() throws Exception {
        var res = ReportTestSupport.create(
                mvc, ReportTestSupport.uniq("acc"), ReportTestSupport.uniq("k"), "IMAGING_FILM",
                ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-basic.lab.txt")));
        assertEquals(4101, res.path("code").asInt());
        assertEquals(ErrorCode.REPORT_TYPE_UNSUPPORTED.getCode(), res.path("code").asInt());
    }

    @Test
    void otherTypeRejected() throws Exception {
        var res = ReportTestSupport.create(
                mvc, ReportTestSupport.uniq("acc"), ReportTestSupport.uniq("k"), "OTHER",
                ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-basic.lab.txt")));
        assertEquals(4101, res.path("code").asInt());
    }

    @Test
    void documentDeclaringImagingFilmRejected() throws Exception {
        var res = ReportTestSupport.create(
                mvc, ReportTestSupport.uniq("acc"), ReportTestSupport.uniq("k"), "LAB_PANEL",
                ReportTestSupport.labFile(ReportTestSupport.golden("imaging-film.lab.txt")));
        assertEquals(4101, res.path("code").asInt());
    }

    @Test
    void checkupBookAccepted() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "sample.checkup.txt", "text/plain",
                ReportTestSupport.golden("checkup-basic.checkup.txt")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var res = ReportTestSupport.create(
                mvc, ReportTestSupport.uniq("acc"), ReportTestSupport.uniq("k"), "CHECKUP_BOOK", file);
        assertEquals(200, res.path("code").asInt(), res.toString());
    }

    @Test
    void ecgAndUsAccepted() throws Exception {
        var ecg = new org.springframework.mock.web.MockMultipartFile(
                "file", "sample.ecg.txt", "text/plain",
                ReportTestSupport.golden("ecg-basic.ecg.txt")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var us = new org.springframework.mock.web.MockMultipartFile(
                "file", "sample.us.txt", "text/plain",
                ReportTestSupport.golden("us-basic.us.txt")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(200, ReportTestSupport.create(
                mvc, ReportTestSupport.uniq("acc"), ReportTestSupport.uniq("k"), "ECG_REPORT", ecg)
                .path("code").asInt());
        assertEquals(200, ReportTestSupport.create(
                mvc, ReportTestSupport.uniq("acc"), ReportTestSupport.uniq("k"), "US_REPORT", us)
                .path("code").asInt());
    }

    @Test
    void dischargeAndEndoscopyAccepted() throws Exception {
        var discharge = new org.springframework.mock.web.MockMultipartFile(
                "file", "sample.discharge.txt", "text/plain",
                ReportTestSupport.golden("discharge-basic.discharge.txt")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var endo = new org.springframework.mock.web.MockMultipartFile(
                "file", "sample.endo.txt", "text/plain",
                ReportTestSupport.golden("endoscopy-basic.endo.txt")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(200, ReportTestSupport.create(
                mvc, ReportTestSupport.uniq("acc"), ReportTestSupport.uniq("k"), "DISCHARGE_SUMMARY", discharge)
                .path("code").asInt());
        assertEquals(200, ReportTestSupport.create(
                mvc, ReportTestSupport.uniq("acc"), ReportTestSupport.uniq("k"), "ENDOSCOPY_REPORT", endo)
                .path("code").asInt());
    }
}