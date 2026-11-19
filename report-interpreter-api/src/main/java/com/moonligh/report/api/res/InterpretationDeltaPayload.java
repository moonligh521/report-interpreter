package com.moonligh.report.api.res;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * INTERPRETATION_DELTA：一个已通过校验的解读块。只允许 validationStatus=VALID；
 * 未通过 Guard 的块永远不会成为 DELTA（构造即拒绝）。
 */
public record InterpretationDeltaPayload(
        String interpretationId,
        String blockId,
        String claimType,
        String text,
        List<String> evidenceIds,
        String validationStatus) implements ReportEventPayload {

    /** 唯一允许外发的校验状态。 */
    public static final String VALID = "VALID";

    public InterpretationDeltaPayload {
        if (!VALID.equals(validationStatus)) {
            throw new IllegalArgumentException("INTERPRETATION_DELTA 仅允许 validationStatus=VALID");
        }
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }

    @Override
    @JsonIgnore
    public ReportEventType type() {
        return ReportEventType.INTERPRETATION_DELTA;
    }
}
