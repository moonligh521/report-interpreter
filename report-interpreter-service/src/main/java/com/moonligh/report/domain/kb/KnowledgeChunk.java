package com.moonligh.report.domain.kb;

/**
 * 已发布知识库分片（强类型领域对象）。
 */
public record KnowledgeChunk(
        String chunkId,
        String docId,
        String title,
        String content,
        String indicatorCode,
        String specialty,
        String url,
        String kbVersion,
        String authorityLevel) {
}
