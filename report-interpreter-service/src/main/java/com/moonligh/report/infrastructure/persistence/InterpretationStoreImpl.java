package com.moonligh.report.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.moonligh.report.domain.interpret.InterpretationStore;

@Component
public class InterpretationStoreImpl implements InterpretationStore {

    private final ReportInterpretationMapper interpretations;
    private final ReportInterpretationBlockMapper blocks;
    private final ReportInterpretationEvidenceMapper evidence;

    public InterpretationStoreImpl(
            ReportInterpretationMapper interpretations,
            ReportInterpretationBlockMapper blocks,
            ReportInterpretationEvidenceMapper evidence) {
        this.interpretations = interpretations;
        this.blocks = blocks;
        this.evidence = evidence;
    }

    @Override
    public void save(
            ReportInterpretationRow row,
            List<ReportInterpretationBlockRow> blockRows,
            List<ReportInterpretationEvidenceRow> evidenceRows) {
        interpretations.insert(row);
        if (!blockRows.isEmpty()) {
            blocks.batchInsert(blockRows);
        }
        if (!evidenceRows.isEmpty()) {
            evidence.batchInsert(evidenceRows);
        }
    }

    @Override
    public Optional<ReportInterpretationRow> findByIdempotency(String reportId, String idempotencyKey) {
        return Optional.ofNullable(interpretations.findByIdempotency(reportId, idempotencyKey));
    }

    @Override
    public Optional<ReportInterpretationRow> findLatest(String reportId) {
        return Optional.ofNullable(interpretations.findLatestByReport(reportId));
    }

    @Override
    public List<ReportInterpretationBlockRow> listBlocks(String interpretationId) {
        return blocks.listByInterpretation(interpretationId);
    }

    @Override
    public List<ReportInterpretationEvidenceRow> listEvidence(String interpretationId) {
        return evidence.listByInterpretation(interpretationId);
    }
}
