package com.moonligh.report.api.res;

/**
 * 补偿结果：补偿后的 Outbox 状态与已用投递次数（补偿会重置次数）。
 */
public record CompensateRes(String outboxId, String status, int attemptCount) {
}
