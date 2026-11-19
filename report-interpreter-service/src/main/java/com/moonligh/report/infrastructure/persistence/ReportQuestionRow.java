package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;

/** report_question 行。responseJson 用于幂等回放强类型答复。 */
public record ReportQuestionRow(
        String answerId,
        String reportId,
        String question,
        String focusCodesJson,
        String idempotencyKey,
        String responseJson,
        Integer resultCode,
        Instant createdAt) {
}
