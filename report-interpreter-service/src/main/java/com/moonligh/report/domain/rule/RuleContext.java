package com.moonligh.report.domain.rule;

import java.math.BigDecimal;
import java.time.Instant;

import com.moonligh.report.api.res.FieldConfidence;

/**
 * 规则上下文（强类型，R-R10）。快照 JSON 由本 record 序列化，读回仍反序列化为本 record。
 * age/sex/pregnancyStatus/institution/method/specimenTime 当前无数据源时为 null，
 * 规则必要条件缺失会得到 UNKNOWN，不会被猜测填充。
 */
public record RuleContext(
        String indicatorId,
        String canonicalCode,
        BigDecimal value,
        String unit,
        String specimenType,
        Integer age,
        String sex,
        String pregnancyStatus,
        String institution,
        String method,
        Instant specimenTime,
        FieldConfidence fieldConfidence,
        ReferenceRangeEvidence referenceRangeEvidence) {
}
