package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportCriticalNotificationOutboxMapper {

    int insert(CriticalNotificationOutboxRow row);

    int updateStatus(CriticalNotificationOutboxRow row);

    List<CriticalNotificationOutboxRow> listRetryDue(@Param("now") Instant now, @Param("limit") int limit);

    CriticalNotificationOutboxRow findByEvent(@Param("eventId") String eventId);

    CriticalNotificationOutboxRow findById(@Param("outboxId") String outboxId);
}