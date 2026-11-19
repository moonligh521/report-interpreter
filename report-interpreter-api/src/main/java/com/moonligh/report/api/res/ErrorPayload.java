package com.moonligh.report.api.res;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * ERROR：解读校验未通过等终止性错误。code 为平台 Result 错误码，reason 为机器码，不含患者数据。
 */
public record ErrorPayload(
        String interpretationId,
        int code,
        String reason) implements ReportEventPayload {

    @Override
    @JsonIgnore
    public ReportEventType type() {
        return ReportEventType.ERROR;
    }
}
