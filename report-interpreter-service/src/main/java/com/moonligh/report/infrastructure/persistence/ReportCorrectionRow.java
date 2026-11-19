package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;

/** report_correction 行。 */
public record ReportCorrectionRow(
        String id,
        String reportId,
        int parseVersionBase,
        int newParseVersion,
        String reviewerId,
        String patchesJson,
        String idempotencyKey,
        Instant createdAt) {
}
