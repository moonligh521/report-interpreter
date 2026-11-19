package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.moonligh.report.infrastructure.persistence.ReportJobMapper;
import com.moonligh.report.infrastructure.persistence.ReportJobRow;

@SpringBootTest
@ActiveProfiles("test")
class ReportJobMapperXmlTest {

    @Autowired
    private ReportJobMapper mapper;

    @Test
    void xmlHasNoHashMapResultType() throws Exception {
        Path xml = Path.of("src/main/resources/mapper/ReportJobMapper.xml");
        String text = Files.readString(xml);
        assertFalse(text.contains("java.util.HashMap"), text);
        assertFalse(text.contains("java.util.Map"), text);
        assertTrue(text.contains("ReportJobRow"), text);
        assertTrue(text.contains("id=\"insert\""), text);
        assertTrue(text.contains("id=\"findById\""), text);
        assertTrue(text.contains("id=\"findByIdempotency\""), text);
    }

    @Test
    void insertAndFindByIdempotency() {
        ReportJobRow row = new ReportJobRow(
                "rpt-mapper-1",
                "acc-m",
                "p-m",
                "idem-m-1",
                "CREATED",
                1L,
                "LAB_PANEL",
                "hash-m",
                null,
                "张三",
                null,
                null);
        mapper.insert(row);
        ReportJobRow found = mapper.findByIdempotency("acc-m", "idem-m-1");
        assertTrue(found != null);
        assertTrue(found.reportId().equals("rpt-mapper-1"));
        ReportJobRow byId = mapper.findById("rpt-mapper-1");
        assertTrue(byId != null);
        assertTrue(byId.patientId().equals("p-m"));
    }

    @Test
    void errorCodes41xxAreOnClasspath() {
        List.of(
                        com.moonligh.consult.framework.ErrorCode.REPORT_TYPE_UNSUPPORTED,
                        com.moonligh.consult.framework.ErrorCode.REPORT_PARSE_FAILED,
                        com.moonligh.consult.framework.ErrorCode.REPORT_PATIENT_INVALID,
                        com.moonligh.consult.framework.ErrorCode.REPORT_KB_UNAVAILABLE,
                        com.moonligh.consult.framework.ErrorCode.REPORT_CRITICAL_HIT,
                        com.moonligh.consult.framework.ErrorCode.REPORT_RULE_PENDING_REVIEW,
                        com.moonligh.consult.framework.ErrorCode.REPORT_INTERPRETATION_INVALID,
                        com.moonligh.consult.framework.ErrorCode.REPORT_IDENTITY_CONFLICT,
                        com.moonligh.consult.framework.ErrorCode.REPORT_CRITICAL_COVERAGE_UNKNOWN,
                        com.moonligh.consult.framework.ErrorCode.REPORT_IDENTITY_MISSING)
                .forEach(code -> assertTrue(code.getCode() >= 4101 && code.getCode() <= 4110));
    }
}
