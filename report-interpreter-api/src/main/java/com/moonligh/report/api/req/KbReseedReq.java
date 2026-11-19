package com.moonligh.report.api.req;

/**
 * 知识库重建请求。
 */
public record KbReseedReq(String source, boolean dryRun) {
}
