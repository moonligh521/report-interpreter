package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;

/** AC-P3/P4：raw 不被 normalized 覆盖；分层置信度存在。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RawNormalizedTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void rawPreservedWhenNormalizedConverted() throws Exception {
        String account = ReportTestSupport.uniq("acc-rawnorm");
        JsonNode created = ReportTestSupport.create(
                mvc, account, ReportTestSupport.uniq("k"), null,
                ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-normalize.lab.txt")));
        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());

        JsonNode glu = ReportTestSupport.indicator(detail, "GLU");
        assertEquals("90", glu.path("rawValue").asText());
        assertEquals("mg/dL", glu.path("rawUnit").asText());
        assertEquals("GLU", glu.path("canonicalCode").asText());
        assertEquals("CONVERTED", glu.path("normalizationStatus").asText());
        assertEquals("mmol/L", glu.path("normalizedUnit").asText());
        assertEquals(0, new BigDecimal("4.995").compareTo(glu.path("normalizedValue").decimalValue()));

        JsonNode conf = glu.path("confidence");
        for (String layer : new String[] {"ocr", "structure", "extraction", "normalization",
                "name", "value", "unit", "refRange"}) {
            assertTrue(conf.has(layer), "missing confidence layer " + layer);
        }
    }

    @Test
    void unmappedNormalizationKeepsRaw() throws Exception {
        String account = ReportTestSupport.uniq("acc-unmapped");
        JsonNode created = ReportTestSupport.create(
                mvc, account, ReportTestSupport.uniq("k"), "LAB_PANEL",
                ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-normalize.lab.txt")));
        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());

        JsonNode xyz = ReportTestSupport.indicator(detail, "XYZ");
        assertEquals("1", xyz.path("rawValue").asText());
        assertEquals("u", xyz.path("rawUnit").asText());
        assertEquals("NAME_UNMAPPED", xyz.path("normalizationStatus").asText());
        assertTrue(xyz.path("normalizedValue").isNull());
        assertTrue(xyz.path("confidence").path("normalization").asDouble() < 0.5);
        // 单一分数不放行：整体置信度取各层最小值。
        assertTrue(detail.path("data").path("parseConfidence").asDouble() <= 0.5);
    }
}