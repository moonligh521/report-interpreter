package com.moonligh.report.api.res;

/**
 * 重要 SSE 事件类型；每种类型对应一个强类型 {@link ReportEventPayload}。
 */
public enum ReportEventType {
    STRUCTURED_RESULT,
    CRITICAL_ALERT,
    INTERPRETATION_DELTA,
    CORRECTION,
    DONE,
    ERROR
}
