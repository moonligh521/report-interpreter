package com.moonligh.report.api.res;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * CRITICAL_ALERT：Java 模板产出的危急提醒块（不依赖模型）。
 */
public record CriticalAlertPayload(
        String interpretationId,
        String blockId,
        String canonicalCode,
        String text) implements ReportEventPayload {

    @Override
    @JsonIgnore
    public ReportEventType type() {
        return ReportEventType.CRITICAL_ALERT;
    }
}
