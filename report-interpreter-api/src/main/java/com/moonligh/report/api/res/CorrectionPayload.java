package com.moonligh.report.api.res;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * CORRECTION：新解读取代同报告早先的解读；客户端应以 interpretationId 对应的内容为准。
 * 不影响已登记的危急事件。
 */
public record CorrectionPayload(
        String supersededInterpretationId,
        String interpretationId,
        String reason) implements ReportEventPayload {

    @Override
    @JsonIgnore
    public ReportEventType type() {
        return ReportEventType.CORRECTION;
    }
}
