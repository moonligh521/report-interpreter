package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.moonligh.consult.framework.ErrorCode;

/** AC-P7：仅 LAB_PANEL，其它 → 4101。 */
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
}