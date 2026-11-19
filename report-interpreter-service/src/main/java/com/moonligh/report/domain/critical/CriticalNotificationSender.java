package com.moonligh.report.domain.critical;

/**
 * 危急通知通道端口（站内/短信/电话等由 infrastructure 适配）。实现须按 idempotencyKey 幂等。
 */
public interface CriticalNotificationSender {

    DeliveryResult send(CriticalNotificationMessage message);
}