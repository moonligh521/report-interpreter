package com.moonligh.report.api.res;

/**
 * 知识库重建占位响应。
 */
public record KbReseedRes(int documentsUpserted, int chunksUpserted, String kbVersion) {
}
