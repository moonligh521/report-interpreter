package com.moonligh.report.api.req;

import java.time.Instant;

/** 危急通知患者端已读确认。acknowledgedAt 可空，服务端用当前时间。 */
public record AckCriticalReq(Instant acknowledgedAt) {
}
