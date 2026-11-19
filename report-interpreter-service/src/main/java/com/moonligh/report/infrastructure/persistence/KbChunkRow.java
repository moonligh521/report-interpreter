package com.moonligh.report.infrastructure.persistence;

/**
 * report_kb_chunk 行（联查 document 的 kbVersion / authorityLevel，仅读取时有值）。
 */
public record KbChunkRow(
        String chunkId,
        String docId,
        String content,
        String title,
        String indicatorCode,
        String specialty,
        String url,
        String kbVersion,
        String authorityLevel) {
}
