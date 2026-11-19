package com.moonligh.report.api.res;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * SSE 事件载荷的强类型层次（sealed）。禁止使用 Map 承载 payload；
 * 序列化时以 payloadType 区分具体类型，落库 payload_json 与对外 data 使用同一形态。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "payloadType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StructuredResultPayload.class, name = "STRUCTURED_RESULT"),
        @JsonSubTypes.Type(value = CriticalAlertPayload.class, name = "CRITICAL_ALERT"),
        @JsonSubTypes.Type(value = InterpretationDeltaPayload.class, name = "INTERPRETATION_DELTA"),
        @JsonSubTypes.Type(value = CorrectionPayload.class, name = "CORRECTION"),
        @JsonSubTypes.Type(value = DonePayload.class, name = "DONE"),
        @JsonSubTypes.Type(value = ErrorPayload.class, name = "ERROR")
})
public sealed interface ReportEventPayload
        permits StructuredResultPayload, CriticalAlertPayload, InterpretationDeltaPayload, CorrectionPayload,
        DonePayload, ErrorPayload {

    /** 该载荷对应的事件类型。 */
    ReportEventType type();
}
