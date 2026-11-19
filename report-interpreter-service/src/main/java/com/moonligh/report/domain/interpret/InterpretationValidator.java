package com.moonligh.report.domain.interpret;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.moonligh.report.domain.rule.ReferenceStatus;

/**
 * 确定性解读校验：不调用任何模型，只拿 Java 事实（解析值、规则结论、KB 召回）与草稿块逐项比对。
 * <ul>
 *   <li>数值 / 单位被改 → RETRYABLE</li>
 *   <li>降低危急程度、诊断 / 处方词 → SAFETY_VIOLATION</li>
 *   <li>假 evidenceId、无依据的指南口吻 → UNSUPPORTED</li>
 *   <li>与规则结论冲突（超区间说成正常等）→ CONFLICT</li>
 * </ul>
 * 多项命中时取最严重者。
 */
public class InterpretationValidator {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final Pattern NORMAL_PHRASE = Pattern.compile(
            "(?<!高于)(?<!低于)(?<!超过)(?<!超出)(?<!不)(?<!非)正常|范围内|未见异常");
    private static final Pattern DOWNGRADE_PHRASE = Pattern.compile(
            "无需紧急|不必紧急|不用紧急|无需就医|不必就医|无需处理|不用处理|不严重|问题不大|无大碍|不用担心|无需担心"
                    + "|略高|略低|轻度|轻微|可继续观察");
    private static final Pattern GUIDELINE_TONE = Pattern.compile("指南|共识|研究表明|研究显示|循证|权威|专家一致");

    public BlockVerdict validate(DraftBlock block, InterpretationContext ctx) {
        Verdict v = new Verdict();
        List<EvidenceItem> cited = new ArrayList<>();
        checkEvidence(block, ctx, cited, v);

        String text = block.text() == null ? "" : block.text();
        if (DiagnosisLexicon.containsForbidden(text)) {
            v.raise(ValidationStatus.SAFETY_VIOLATION, "DIAGNOSIS_OR_PRESCRIPTION");
        }
        String scrubbed = stripEvidence(text, cited);
        List<IndicatorFact> refs = referenced(block, scrubbed, ctx);

        checkCriticalDowngrade(block, scrubbed, refs, v);
        checkNumbers(block, text, scrubbed, refs, cited, ctx, v);
        checkConsistency(block, scrubbed, refs, ctx, v);

        return new BlockVerdict(block, v.status, v.reason);
    }

    // ---- 证据 ----

    private void checkEvidence(DraftBlock block, InterpretationContext ctx, List<EvidenceItem> cited, Verdict v) {
        for (String id : block.evidenceIds()) {
            EvidenceItem item = ctx.evidence().stream()
                    .filter(e -> e.evidenceId().equals(id))
                    .findFirst()
                    .orElse(null);
            if (item == null) {
                v.raise(ValidationStatus.UNSUPPORTED, "UNKNOWN_EVIDENCE_ID");
            } else {
                cited.add(item);
                if (item.indicatorCode() != null && block.indicatorCode() != null
                        && !item.indicatorCode().equals(block.indicatorCode())) {
                    v.raise(ValidationStatus.UNSUPPORTED, "EVIDENCE_INDICATOR_MISMATCH");
                }
            }
        }
        if (block.claimType() == ClaimType.GUIDELINE && block.evidenceIds().isEmpty()) {
            v.raise(ValidationStatus.UNSUPPORTED, "GUIDELINE_WITHOUT_EVIDENCE");
        }
        String text = block.text() == null ? "" : block.text();
        if (cited.isEmpty() && GUIDELINE_TONE.matcher(text).find()) {
            v.raise(ValidationStatus.UNSUPPORTED, "GUIDELINE_TONE_WITHOUT_EVIDENCE");
        }
    }

    // ---- 危急降级 ----

    private void checkCriticalDowngrade(DraftBlock block, String scrubbed, List<IndicatorFact> refs, Verdict v) {
        for (IndicatorFact fact : refs) {
            if (!fact.critical()) {
                continue;
            }
            ClaimedStatus c = block.claimedStatus();
            boolean statusDowngrade = c == ClaimedStatus.NORMAL || c == ClaimedStatus.HIGH || c == ClaimedStatus.LOW
                    || block.claimType() == ClaimType.FACT && c != ClaimedStatus.CRITICAL;
            if (statusDowngrade
                    || DOWNGRADE_PHRASE.matcher(scrubbed).find()
                    || NORMAL_PHRASE.matcher(scrubbed).find()) {
                v.raise(ValidationStatus.SAFETY_VIOLATION, "CRITICAL_DOWNGRADE");
            }
        }
    }

    // ---- 数值 / 单位 ----

    private void checkNumbers(
            DraftBlock block, String text, String scrubbed, List<IndicatorFact> refs, List<EvidenceItem> cited,
            InterpretationContext ctx, Verdict v) {
        IndicatorFact byCode = block.indicatorCode() == null ? null : factOf(ctx, block.indicatorCode());
        if (block.indicatorCode() != null && byCode == null) {
            v.raise(ValidationStatus.UNSUPPORTED, "UNKNOWN_INDICATOR");
        }
        if (byCode != null) {
            if (block.claimedValue() != null && block.claimedValue().compareTo(byCode.value()) != 0) {
                v.raise(ValidationStatus.RETRYABLE, "NUMBER_TAMPER");
            }
            if (block.claimedUnit() != null && byCode.unit() != null
                    && !block.claimedUnit().equalsIgnoreCase(byCode.unit())) {
                v.raise(ValidationStatus.RETRYABLE, "UNIT_TAMPER");
            }
        }

        List<BigDecimal> allowed = new ArrayList<>();
        for (IndicatorFact f : refs) {
            addIfPresent(allowed, f.value());
            addIfPresent(allowed, f.refLow());
            addIfPresent(allowed, f.refHigh());
        }
        for (EvidenceItem e : cited) {
            if (e.snippet() != null) {
                Matcher m = NUMBER.matcher(e.snippet());
                while (m.find()) {
                    allowed.add(new BigDecimal(m.group()));
                }
            }
        }
        String numericText = stripNames(scrubbed, ctx);
        Matcher m = NUMBER.matcher(numericText);
        while (m.find()) {
            BigDecimal n = new BigDecimal(m.group());
            if (allowed.stream().noneMatch(a -> a.compareTo(n) == 0)) {
                v.raise(ValidationStatus.RETRYABLE, "NUMBER_TAMPER");
                break;
            }
        }
    }

    // ---- 与规则结论一致性 ----

    private void checkConsistency(
            DraftBlock block, String scrubbed, List<IndicatorFact> refs, InterpretationContext ctx, Verdict v) {
        IndicatorFact byCode = block.indicatorCode() == null ? null : factOf(ctx, block.indicatorCode());
        if (byCode != null) {
            switch (block.claimedStatus()) {
                case NORMAL -> {
                    if (byCode.reference() != ReferenceStatus.NORMAL || byCode.critical() || !ctx.safeNormalAllowed()) {
                        v.raise(ValidationStatus.CONFLICT, "NORMAL_CLAIM_CONFLICT");
                    }
                }
                case HIGH -> {
                    if (byCode.reference() != ReferenceStatus.HIGH) {
                        v.raise(ValidationStatus.CONFLICT, "HIGH_CLAIM_CONFLICT");
                    }
                }
                case LOW -> {
                    if (byCode.reference() != ReferenceStatus.LOW) {
                        v.raise(ValidationStatus.CONFLICT, "LOW_CLAIM_CONFLICT");
                    }
                }
                case CRITICAL -> {
                    if (!byCode.critical()) {
                        v.raise(ValidationStatus.CONFLICT, "CRITICAL_CLAIM_CONFLICT");
                    }
                }
                case NONE -> {
                    // 不下结论
                }
            }
        }
        if (NORMAL_PHRASE.matcher(scrubbed).find()) {
            boolean anyNotSafe = refs.stream().anyMatch(f -> f.reference() != ReferenceStatus.NORMAL || f.critical());
            if (anyNotSafe || !refs.isEmpty() && !ctx.safeNormalAllowed()) {
                v.raise(ValidationStatus.CONFLICT, "NORMAL_TEXT_CONFLICT");
            }
        }
    }

    // ---- 工具 ----

    private static List<IndicatorFact> referenced(DraftBlock block, String scrubbed, InterpretationContext ctx) {
        Set<String> seen = new HashSet<>();
        List<IndicatorFact> out = new ArrayList<>();
        for (IndicatorFact f : ctx.facts()) {
            boolean byCode = f.canonicalCode() != null && f.canonicalCode().equals(block.indicatorCode());
            boolean byName = f.name() != null && f.name().length() >= 2 && scrubbed.contains(f.name());
            if ((byCode || byName) && seen.add(f.indicatorId())) {
                out.add(f);
            }
        }
        return out;
    }

    private static IndicatorFact factOf(InterpretationContext ctx, String code) {
        return ctx.facts().stream().filter(f -> code.equals(f.canonicalCode())).findFirst().orElse(null);
    }

    private static String stripEvidence(String text, List<EvidenceItem> cited) {
        String out = text;
        for (EvidenceItem e : cited) {
            if (e.snippet() != null && !e.snippet().isBlank()) {
                out = out.replace(e.snippet(), " ");
            }
            if (e.title() != null && !e.title().isBlank()) {
                out = out.replace(e.title(), " ");
            }
        }
        return out;
    }

    /** 去掉指标名 / 码里自带的数字（如 HbA1c），避免误判为被篡改的数值。 */
    private static String stripNames(String text, InterpretationContext ctx) {
        List<String> names = new ArrayList<>();
        for (IndicatorFact f : ctx.facts()) {
            if (f.name() != null && !f.name().isBlank()) {
                names.add(f.name());
            }
            if (f.canonicalCode() != null && !f.canonicalCode().isBlank()) {
                names.add(f.canonicalCode());
            }
        }
        names.sort(Comparator.comparingInt(String::length).reversed());
        String out = text;
        for (String n : names) {
            out = out.replace(n, " ");
        }
        return out;
    }

    private static void addIfPresent(List<BigDecimal> list, BigDecimal v) {
        if (v != null) {
            list.add(v);
        }
    }

    private static final class Verdict {
        private ValidationStatus status = ValidationStatus.VALID;
        private String reason = "OK";

        void raise(ValidationStatus s, String why) {
            if (s.severity() > status.severity()) {
                status = s;
                reason = why;
            }
        }
    }
}
