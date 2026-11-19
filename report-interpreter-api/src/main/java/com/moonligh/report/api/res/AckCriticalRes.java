package com.moonligh.report.api.res;

/** 危急 ACK 结果。notificationStatus 应为 ACKNOWLEDGED。 */
public record AckCriticalRes(String eventId, String notificationStatus) {
}
