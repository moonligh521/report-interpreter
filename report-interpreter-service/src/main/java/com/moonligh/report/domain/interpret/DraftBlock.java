package com.moonligh.report.domain.interpret;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模型草稿块（未经 Guard，不可直接外发）。
 * indicatorCode / claimedStatus / claimedValue / claimedUnit 为结构化声明，供确定性比对。
 */
public record DraftBlock(
        String blockId,
        ClaimType claimType,
        String indicatorCode,
        ClaimedStatus claimedStatus,
        BigDecimal claimedValue,
        String claimedUnit,
        String text,
        List<String> evidenceIds) {

    public DraftBlock {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        claimedStatus = claimedStatus == null ? ClaimedStatus.NONE : claimedStatus;
    }
}
