package com.moonligh.report.domain.kb;

import java.util.List;

/**
 * classpath:kb/** 下一份 KB 夹具文档（JSON → 强类型）。status：DRAFT / PUBLISHED / RETIRED，
 * 仅 PUBLISHED 且在生效期内才会被检索。时间为 ISO-8601 UTC 字符串。
 */
public record KbFixtureDocument(
        String docId,
        String title,
        String kbVersion,
        String status,
        String authorityLevel,
        String url,
        String effectiveFrom,
        String effectiveTo,
        List<KbFixtureChunk> chunks) {

    /** 夹具分片。 */
    public record KbFixtureChunk(String chunkId, String content, String indicatorCode, String specialty) {
    }
}
