package com.moonligh.report.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.moonligh.report.domain.archive.ArchiveConsumer;
import com.moonligh.report.domain.archive.ArchiveOutboxManager;
import com.moonligh.report.domain.archive.ArchiveOutboxStore;
import com.moonligh.report.domain.archive.ArchiveRecordStore;
import com.moonligh.report.domain.archive.ArchiveSender;

/**
 * 档案 Outbox 域对象装配（domain 类保持无 Spring 注解）。
 */
@Configuration
public class ArchiveConfig {

    @Bean
    public ArchiveOutboxManager archiveOutboxManager(ArchiveOutboxStore store, ReportProperties properties) {
        return new ArchiveOutboxManager(store, properties.getArchive().getMaxAttempts());
    }

    @Bean
    public ArchiveConsumer archiveConsumer(ArchiveRecordStore records) {
        return new ArchiveConsumer(records);
    }

    /** 默认投递通道：直连本服务的幂等消费方；生产可替换为消息总线适配器。 */
    @Bean
    public ArchiveSender archiveSender(ArchiveConsumer consumer) {
        return consumer::deliver;
    }
}
