package com.moonligh.report.domain.interpret;

import java.util.ArrayList;
import java.util.List;

/**
 * 输出闸门：只有通过 {@link InterpretationValidator} 的块才能外发。
 * <ul>
 *   <li>SAFETY_VIOLATION：立即终止，不重试，不外发任何模型块；</li>
 *   <li>RETRYABLE：在 maxAttempts 内整体重新生成，耗尽后丢弃问题块并保持 RETRYABLE；</li>
 *   <li>UNSUPPORTED / CONFLICT：不重试，丢弃问题块，其余通过的块保留。</li>
 * </ul>
 */
public class InterpretationOutputGuard {

    private final InterpretationValidator validator;

    public InterpretationOutputGuard(InterpretationValidator validator) {
        this.validator = validator;
    }

    public GuardOutcome guard(InterpretationModel model, InterpretationContext ctx, int maxAttempts) {
        int max = Math.max(1, maxAttempts);
        GuardOutcome last = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            List<DraftBlock> drafts;
            try {
                drafts = model.generate(ctx, attempt);
            } catch (RuntimeException e) {
                last = new GuardOutcome(List.of(), List.of(), ValidationStatus.RETRYABLE, attempt, false);
                continue;
            }
            List<BlockVerdict> verdicts = new ArrayList<>();
            for (DraftBlock b : drafts == null ? List.<DraftBlock>of() : drafts) {
                verdicts.add(validator.validate(b, ctx));
            }
            List<BlockVerdict> bad = verdicts.stream().filter(x -> !x.valid()).toList();

            if (bad.stream().anyMatch(x -> x.status() == ValidationStatus.SAFETY_VIOLATION)) {
                return new GuardOutcome(List.of(), bad, ValidationStatus.SAFETY_VIOLATION, attempt, true);
            }
            boolean needRetry = bad.stream().anyMatch(x -> x.status() == ValidationStatus.RETRYABLE);
            ValidationStatus overall = bad.stream()
                    .map(BlockVerdict::status)
                    .reduce(ValidationStatus.VALID, ValidationStatus::worst);
            List<DraftBlock> passed = verdicts.stream().filter(BlockVerdict::valid).map(BlockVerdict::block).toList();
            last = new GuardOutcome(passed, bad, overall, attempt, false);
            if (!needRetry) {
                return last;
            }
        }
        return last;
    }
}
