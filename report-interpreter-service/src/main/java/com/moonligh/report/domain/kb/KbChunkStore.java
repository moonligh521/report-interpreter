package com.moonligh.report.domain.kb;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 知识库只读端口：仅返回 PUBLISHED 且在生效期内的分片。
 */
public interface KbChunkStore {

    /** indicatorCode / specialty 非空时下推为结构化过滤。 */
    List<KnowledgeChunk> listPublished(Instant now, String indicatorCode, String specialty);

    /** 当前生效的已发布文档最大 kbVersion；知识库为空时 empty。 */
    Optional<String> currentKbVersion(Instant now);
}
