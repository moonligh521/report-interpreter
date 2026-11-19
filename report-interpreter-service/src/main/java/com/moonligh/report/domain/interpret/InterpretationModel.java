package com.moonligh.report.domain.interpret;

import java.util.List;

/**
 * 解读生成端口。真实 LLM 适配器在后续分支接入；本期默认实现为确定性模板
 * {@code FakeInterpretationModel}。模型输出永远只是草稿，必须经 Guard 才能外发。
 */
public interface InterpretationModel {

    /** attempt 从 1 开始；Guard 在 RETRYABLE 时会带着更大的 attempt 重新调用。 */
    List<DraftBlock> generate(InterpretationContext context, int attempt);
}
