package com.moonligh.report.domain.interpret;

import java.math.BigDecimal;
import java.util.List;

import com.moonligh.report.domain.rule.ReferenceStatus;

/**
 * 受控模板：解读文本只由这些模板拼装，措辞固定、无诊断词、无指南口吻（除非带有效依据）。
 * 所有模板产物都必须能通过 {@link InterpretationValidator}（测试保证）。
 */
public final class InterpretationTemplates {

    public static final String DISCLAIMER = "本解读仅复述检验数据与规则判定，供参考，不能替代医生面诊。";
    public static final String DEGRADE_NOTICE =
            "当前知识库暂无可引用的依据，以下内容仅复述报告数据与规则判定结果，不引用外部资料。";
    public static final String PENDING_REVIEW_NOTICE = "本报告的规则判定尚待人工复核，暂不下结论。";

    private InterpretationTemplates() {
    }

    /** 报告事实块文本（模型模板与 Validator 的口径一致）。 */
    public static String factText(IndicatorFact f, boolean safeNormalAllowed) {
        String head = f.name() + " 检测值 " + num(f.value()) + " " + unitOf(f);
        String range = rangeOf(f);
        if (f.critical()) {
            return head + "，达到危急值水平，请立即联系医生或前往急诊处理。";
        }
        return switch (f.reference()) {
            case HIGH -> head + "，高于参考范围" + range + "。";
            case LOW -> head + "，低于参考范围" + range + "。";
            case NORMAL -> safeNormalAllowed
                    ? head + "，处于参考范围内" + range + "。"
                    : head + "，本次结果的判定待人工复核，暂不下结论。";
            case UNKNOWN -> head + "，缺少可用的参考范围，暂不判定高低。";
        };
    }

    public static ClaimedStatus claimedStatusOf(IndicatorFact f, boolean safeNormalAllowed) {
        if (f.critical()) {
            return ClaimedStatus.CRITICAL;
        }
        return switch (f.reference()) {
            case HIGH -> ClaimedStatus.HIGH;
            case LOW -> ClaimedStatus.LOW;
            case NORMAL -> safeNormalAllowed ? ClaimedStatus.NORMAL : ClaimedStatus.NONE;
            case UNKNOWN -> ClaimedStatus.NONE;
        };
    }

    /** 纠正 / 重解析后当前版本不再危急，但已登记的危急事件不撤销，仍须提示。 */
    public static String historicalCriticalNotice(String canonicalCode) {
        return "本报告曾登记 " + canonicalCode + " 的危急值事件，该事件不会因解析更正而撤销，请与医生核实。";
    }

    public static String guidelineText(EvidenceItem e) {
        return "参考资料《" + e.title() + "》：" + e.snippet();
    }

    public static String criticalAlertText(IndicatorFact f) {
        return "危急值提醒：" + f.name() + " 检测值 " + num(f.value()) + " " + unitOf(f)
                + " 达到危急值水平，请立即联系医生或前往急诊处理。";
    }

    public static String num(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        return s.toPlainString();
    }

    private static String unitOf(IndicatorFact f) {
        return f.unit() == null ? "" : f.unit();
    }

    private static String rangeOf(IndicatorFact f) {
        if (f.refLow() == null || f.refHigh() == null) {
            return "";
        }
        return "（" + num(f.refLow()) + "–" + num(f.refHigh()) + " " + unitOf(f) + "）";
    }

    /** 方便测试：事实是否会被模板表述为「处于范围内」。 */
    public static boolean saysWithinRange(IndicatorFact f, boolean safeNormalAllowed) {
        return !f.critical() && f.reference() == ReferenceStatus.NORMAL && safeNormalAllowed;
    }

    /** 与事实相关联的依据（按指标码）。 */
    public static List<EvidenceItem> evidenceFor(InterpretationContext ctx, IndicatorFact f) {
        return ctx.evidence().stream()
                .filter(e -> f.canonicalCode() != null && f.canonicalCode().equals(e.indicatorCode()))
                .toList();
    }
}
