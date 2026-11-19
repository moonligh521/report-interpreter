package com.moonligh.report.api.res;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * STRUCTURED_RESULT：解读开始前先落一份结构化事实（解析版本、身份结论、指标摘要）。
 */
public record StructuredResultPayload(
        int parseVersion,
        String identityStatus,
        List<IndicatorSummary> indicators) implements ReportEventPayload {

    public StructuredResultPayload {
        indicators = indicators == null ? List.of() : List.copyOf(indicators);
    }

    @Override
    @JsonIgnore
    public ReportEventType type() {
        return ReportEventType.STRUCTURED_RESULT;
    }
}
