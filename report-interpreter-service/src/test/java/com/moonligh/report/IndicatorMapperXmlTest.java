package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.moonligh.report.infrastructure.persistence.ReportIndicatorMapper;
import com.moonligh.report.infrastructure.persistence.ReportIndicatorRow;
import com.moonligh.report.infrastructure.persistence.ReportParseResultMapper;
import com.moonligh.report.infrastructure.persistence.ReportParseResultRow;
import com.moonligh.report.infrastructure.persistence.ReportSourceEvidenceMapper;
import com.moonligh.report.infrastructure.persistence.ReportSourceEvidenceRow;

/** Mapper XML 契约与 H2 往返。 */
@SpringBootTest
@ActiveProfiles("test")
class IndicatorMapperXmlTest {

    @Autowired
    private ReportParseResultMapper parseMapper;

    @Autowired
    private ReportIndicatorMapper indicatorMapper;

    @Autowired
    private ReportSourceEvidenceMapper evidenceMapper;

    @Test
    void xmlFilesDeclareExpectedStatements() throws Exception {
        String parse = Files.readString(Path.of("src/main/resources/mapper/ReportParseResultMapper.xml"));
        String ind = Files.readString(Path.of("src/main/resources/mapper/ReportIndicatorMapper.xml"));
        String ev = Files.readString(Path.of("src/main/resources/mapper/ReportSourceEvidenceMapper.xml"));
        for (String id : List.of("insert", "deactivatePrevious", "findActive")) {
            assertTrue(parse.contains("id=\"" + id + "\""), id);
        }
        assertTrue(ind.contains("id=\"batchInsert\"") && ind.contains("id=\"listByParseResult\""));
        assertTrue(ev.contains("id=\"insert\"") && ev.contains("id=\"findByIndicator\""));
    }

    @Test
    void roundTripKeepsRawAndNormalizedSeparate() {
        String reportId = "rpt-map-1";
        parseMapper.insert(new ReportParseResultRow("prs-1", reportId, 1, true, "LAB_PANEL", 0.95, "PARSED", null, null, null));
        parseMapper.deactivatePrevious(reportId);
        assertEquals(null, parseMapper.findActive(reportId));
        parseMapper.insert(new ReportParseResultRow("prs-2", reportId, 2, true, "LAB_PANEL", 0.9, "PARSED", "k2", null, null));
        assertEquals(2, parseMapper.findActive(reportId).parseVersion());
        assertEquals(2, parseMapper.maxVersion(reportId));
        assertEquals("prs-2", parseMapper.findByRequestKey(reportId, "k2").id());

        indicatorMapper.batchInsert(List.of(new ReportIndicatorRow(
                "ind-1", "prs-2", reportId, 2, "GLU", "90", "mg/dL", "GLU", "SERUM",
                new BigDecimal("4.995"), "mmol/L", "CONVERTED", new BigDecimal("3.9"), new BigDecimal("6.1"),
                1.0, 0.95, 0.95, 0.9, 0.98, 0.98, 0.98, 0.98, null, null)));
        ReportIndicatorRow row = indicatorMapper.listByParseResult("prs-2").get(0);
        assertEquals("90", row.rawValue());
        assertEquals("mg/dL", row.rawUnit());
        assertEquals(0, new BigDecimal("4.995").compareTo(row.normalizedValue()));
        assertEquals(0.9, row.confNormalization(), 1e-9);

        evidenceMapper.insert(new ReportSourceEvidenceRow("evd-1", "ind-1", 3, "GLU 90 mg/dL", null));
        ReportSourceEvidenceRow e = evidenceMapper.findByIndicator("ind-1");
        assertNotNull(e);
        assertEquals(3, e.pageIndex());
        assertEquals("GLU 90 mg/dL", e.rawText());
    }
}