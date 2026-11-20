package com.moonligh.report.domain.parse;

/**
 * 文档抽取请求（同步 HTTP / Fake 共用）。
 */
public record ExtractionRequest(
        String requestId,
        String documentHash,
        String schemaVersion,
        String contentType,
        byte[] content,
        String fileName,
        String idempotencyKey) {

    public ExtractionRequest {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
        if (documentHash == null || documentHash.isBlank()) {
            throw new IllegalArgumentException("documentHash is required");
        }
        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalArgumentException("schemaVersion is required");
        }
        content = content == null ? new byte[0] : content;
        contentType = contentType == null ? "" : contentType;
        fileName = fileName == null ? "" : fileName;
        idempotencyKey = idempotencyKey == null ? requestId : idempotencyKey;
    }
}
