package com.moonligh.report.api.msg;

import java.util.List;

import com.moonligh.report.api.res.FhirHints;
import com.moonligh.report.api.res.IndicatorDto;
import com.moonligh.report.api.res.SectionDto;

/**
 * 档案 Outbox 消息 schemaVersion=2：在 V1 字段上增加 reportType、sections、可选 fhirHints。
 * 消费方按 eventId 幂等；含完整指标，禁止写入日志。
 */
public record ArchiveOutboxMessageV2(
        String eventId,
        String schemaVersion,
        String reportId,
        String patientId,
        int parseVersion,
        String interpretationId,
        List<IndicatorDto> indicators,
        String identityStatus,
        String reportType,
        List<SectionDto> sections,
        FhirHints fhirHints) {

    public ArchiveOutboxMessageV2 {
        indicators = indicators == null ? List.of() : List.copyOf(indicators);
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
