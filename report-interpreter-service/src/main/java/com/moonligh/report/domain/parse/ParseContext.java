package com.moonligh.report.domain.parse;

/**
 * 解析入参（强类型）。content 为已通过文件安全校验的原始字节。
 */
public record ParseContext(
        String reportId,
        ReportType reportType,
        String fileName,
        String contentType,
        byte[] content) {
}