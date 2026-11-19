package com.moonligh.report.api.res;

import java.util.List;

/**
 * 通过 Guard 的解读块。validationStatus 对外只会是 VALID（被拦截的块不外发）。
 */
public record InterpretationBlockRes(
        String blockId,
        String claimType,
        String text,
        List<String> evidenceIds,
        String validationStatus) {
}
