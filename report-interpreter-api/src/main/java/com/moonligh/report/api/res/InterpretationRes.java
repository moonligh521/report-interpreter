package com.moonligh.report.api.res;

import java.util.List;

/**
 * 解读结果。status：VALID / DEGRADED / RETRYABLE / UNSUPPORTED / CONFLICT / SAFETY_VIOLATION。
 * 非 VALID/DEGRADED 时 Result.code 为 4107，blocks 仅含通过 Guard 的块。
 */
public record InterpretationRes(
        String interpretationId,
        String status,
        PinnedVersions pinnedVersions,
        List<InterpretationBlockRes> blocks,
        List<EvidenceRes> evidence) {
}
