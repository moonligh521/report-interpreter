package com.moonligh.report.api.res;

/**
 * 解读引用的知识库依据。
 */
public record EvidenceRes(String evidenceId, String docId, String title, String snippet, String url) {
}
