package com.moonligh.report.api.req;

/**
 * 档案 Outbox 补偿请求。force=true 允许把 FAILED 也拉回 PENDING；
 * force 不能越过身份门禁（非 MATCHED 永不投递档案）。
 */
public record CompensateReq(String outboxId, boolean force) {
}
