package com.moonligh.report.api.req;

/**
 * 创建人工复核任务。priority 取 NORMAL / HIGH / URGENT。
 */
public record CreateReviewReq(String reportId, String reason, String priority) {
}