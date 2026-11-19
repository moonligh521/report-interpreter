package com.moonligh.report.domain.rule;

import com.moonligh.report.domain.parse.ParsedIndicator;

/**
 * 把解析出的指标 + 人群事实装配成 RuleContext。只搬运已有事实，不猜测任何缺失条件。
 */
public final class RuleContextResolver {

    public RuleContext resolve(String indicatorId, ParsedIndicator indicator, PatientFacts facts) {
        PatientFacts f = facts == null ? PatientFacts.UNKNOWN : facts;
        // 原文区间的单位就是指标原始单位；与规范化单位不一致时引擎不会使用该区间。
        ReferenceRangeEvidence range = new ReferenceRangeEvidence(
                indicator.refLow(), indicator.refHigh(), indicator.rawUnit(), indicator.confidence().refRange());
        return new RuleContext(
                indicatorId,
                indicator.canonicalCode(),
                indicator.normalizedValue(),
                indicator.normalizedUnit(),
                indicator.specimenType(),
                f.age(),
                f.sex(),
                f.pregnancyStatus(),
                null,
                null,
                null,
                indicator.confidence(),
                range);
    }
}
