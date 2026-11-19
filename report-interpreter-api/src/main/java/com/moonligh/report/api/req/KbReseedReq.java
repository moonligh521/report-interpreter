package com.moonligh.report.api.req;

/**
 * 知识库重建请求。source 为空时默认 classpath:kb/lab-min；dryRun 为空视为 false。
 */
public record KbReseedReq(String source, Boolean dryRun) {

    public boolean dryRunEnabled() {
        return Boolean.TRUE.equals(dryRun);
    }
}
