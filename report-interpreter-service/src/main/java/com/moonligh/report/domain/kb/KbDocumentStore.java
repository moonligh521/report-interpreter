package com.moonligh.report.domain.kb;

import java.util.List;

import com.moonligh.report.infrastructure.persistence.KbChunkRow;
import com.moonligh.report.infrastructure.persistence.KbDocumentRow;

/**
 * 知识库写端口（仅内部 reseed 使用）。
 */
public interface KbDocumentStore {

    /** 文档 upsert，并以给定分片整体替换该文档的旧分片。 */
    void upsertDocument(KbDocumentRow document, List<KbChunkRow> chunks);
}
