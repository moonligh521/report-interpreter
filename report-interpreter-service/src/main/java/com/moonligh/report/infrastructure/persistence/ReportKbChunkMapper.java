package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportKbChunkMapper {

    int insert(KbChunkRow row);

    int deleteByDoc(@Param("docId") String docId);

    int countByDoc(@Param("docId") String docId);

    /** 仅返回 PUBLISHED 且在生效期内的文档分片；indicatorCode / specialty 非空时下推为结构化过滤。 */
    List<KbChunkRow> listPublishedChunks(
            @Param("now") Instant now,
            @Param("indicatorCode") String indicatorCode,
            @Param("specialty") String specialty);
}
