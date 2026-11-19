package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportKbDocumentMapper {

    int insert(KbDocumentRow row);

    int update(KbDocumentRow row);

    KbDocumentRow findById(@Param("docId") String docId);

    /** 当前生效的 PUBLISHED 文档的最大 kb_version；无则 null。 */
    String maxPublishedKbVersion(@Param("now") Instant now);

    int countAll();
}
