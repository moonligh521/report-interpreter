package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;

/**
 * report_kb_document 行。status 仅 PUBLISHED 可被检索。
 */
public record KbDocumentRow(
        String docId,
        String title,
        String kbVersion,
        String status,
        String authorityLevel,
        String url,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant createdAt,
        Instant updatedAt) {
}
