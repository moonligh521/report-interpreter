package com.moonligh.report.infrastructure.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.moonligh.report.domain.interpret.ClaimType;
import com.moonligh.report.domain.interpret.ClaimedStatus;
import com.moonligh.report.domain.interpret.DraftBlock;
import com.moonligh.report.domain.interpret.EvidenceItem;
import com.moonligh.report.domain.interpret.IndicatorFact;
import com.moonligh.report.domain.interpret.InterpretationContext;
import com.moonligh.report.domain.interpret.InterpretationModel;
import com.moonligh.report.domain.interpret.InterpretationTemplates;
import com.moonligh.report.domain.rule.ReferenceStatus;

/**
 * 确定性模板「模型」：不调用任何 LLM，同一输入永远得到同一输出。
 * 验收与回归以它驱动 Validator / Guard；真实 LLM 适配器在 delivery-gate 之后接入，同样只能产出草稿。
 */
@Component
public class FakeInterpretationModel implements InterpretationModel {

    /** 每个指标最多引用的依据条数。 */
    static final int MAX_EVIDENCE_PER_INDICATOR = 2;

    @Override
    public List<DraftBlock> generate(InterpretationContext context, int attempt) {
        List<DraftBlock> out = new ArrayList<>();
        int seq = 0;
        for (IndicatorFact f : context.facts()) {
            // 危急指标的状态陈述由 Java 的 CRITICAL_ALERT 模板块独占，模型不重复表述。
            if (!f.critical()) {
                out.add(new DraftBlock(
                        "draft-" + (++seq), ClaimType.FACT, f.canonicalCode(),
                        InterpretationTemplates.claimedStatusOf(f, context.safeNormalAllowed()),
                        f.value(), f.unit(),
                        InterpretationTemplates.factText(f, context.safeNormalAllowed()), List.of()));
            }
            if (f.critical() || f.reference() != ReferenceStatus.NORMAL) {
                List<EvidenceItem> evidence = InterpretationTemplates.evidenceFor(context, f);
                for (EvidenceItem e : evidence.stream().limit(MAX_EVIDENCE_PER_INDICATOR).toList()) {
                    out.add(new DraftBlock(
                            "draft-" + (++seq), ClaimType.GUIDELINE, f.canonicalCode(), ClaimedStatus.NONE,
                            null, null, InterpretationTemplates.guidelineText(e), List.of(e.evidenceId())));
                }
            }
        }
        return out;
    }
}
