package com.moonligh.report.domain.interpret;

import java.util.List;

/**
 * Guard 结论。passed 仅含 VALID 块（唯一允许外发 / 作为 INTERPRETATION_DELTA 的来源）；
 * overall 为本次所有被拒块中最严重者（全部通过则为 VALID）；terminated 表示因 SAFETY_VIOLATION 立即终止。
 */
public record GuardOutcome(
        List<DraftBlock> passed,
        List<BlockVerdict> rejected,
        ValidationStatus overall,
        int attempts,
        boolean terminated) {
}
