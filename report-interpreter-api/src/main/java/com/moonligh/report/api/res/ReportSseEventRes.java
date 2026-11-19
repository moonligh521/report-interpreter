package com.moonligh.report.api.res;

/**
 * 一条已落库的 SSE 事件。payload 为按 eventType 分发的强类型载荷（sealed），不是 Map。
 * eventId 同时用作 SSE 的 id: 字段，客户端重连用 Last-Event-ID 续传；timestamp 为 ISO-8601 UTC 文本。
 */
public record ReportSseEventRes(
        String eventId,
        String reportId,
        long sequence,
        ReportEventType eventType,
        String timestamp,
        ReportEventPayload payload) {
}
