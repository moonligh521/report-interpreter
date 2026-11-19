package com.moonligh.report.api.msg;

import java.util.List;

import com.moonligh.report.api.res.IndicatorDto;

/**
 * 档案 Outbox 消息（序列化进 payload_json）。eventId 稳定（由解读 ID 派生），消费方按 eventId 幂等。
 * 仅在身份 MATCHED 且解读完成时才会生成；含完整指标，禁止写入日志。
 */
public record ArchiveOutboxMessage(
        String eventId,
        String schemaVersion,
        String reportId,
        String patientId,
        int parseVersion,
        String interpretationId,
        List<IndicatorDto> indicators,
        String identityStatus) {

    public ArchiveOutboxMessage {
        indicators = indicators == null ? List.of() : List.copyOf(indicators);
    }
}
