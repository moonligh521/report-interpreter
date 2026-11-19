package com.moonligh.report.domain.archive;

import com.moonligh.report.api.msg.ArchiveOutboxMessage;

/**
 * 档案投递通道端口：由调度器 / 适配器实现。默认实现直连本服务的 {@link ArchiveConsumer}。
 */
@FunctionalInterface
public interface ArchiveSender {

    ArchiveDeliveryResult send(ArchiveOutboxMessage message);
}
