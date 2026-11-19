package com.moonligh.report.domain.interpret;

import java.util.List;
import java.util.Optional;

import com.moonligh.report.infrastructure.persistence.ReportInterpretationBlockRow;
import com.moonligh.report.infrastructure.persistence.ReportInterpretationEvidenceRow;
import com.moonligh.report.infrastructure.persistence.ReportInterpretationRow;

/**
 * 解读仓储端口。
 */
public interface InterpretationStore {

    /** 一次写入解读、块与依据快照（同事务）。 */
    void save(
            ReportInterpretationRow row,
            List<ReportInterpretationBlockRow> blocks,
            List<ReportInterpretationEvidenceRow> evidence);

    Optional<ReportInterpretationRow> findByIdempotency(String reportId, String idempotencyKey);

    Optional<ReportInterpretationRow> findLatest(String reportId);

    List<ReportInterpretationBlockRow> listBlocks(String interpretationId);

    List<ReportInterpretationEvidenceRow> listEvidence(String interpretationId);
}
