package com.moonligh.report.api.res;

import java.util.List;

/**
 * 报告详情：当前 active 解析版本及其指标，外加身份、危急覆盖与规则结果。
 * blockCode 为阻断类平台错误码（4106/4108/4109/4110），无阻断时为 null；
 * archiveBlocked=true 表示默认禁止自动归档；safeNormalAllowed=false 表示不得对外表述为「安全正常」。
 */
public record ReportDetailRes(
        String reportId,
        String reportType,
        String status,
        int parseVersion,
        boolean activeParse,
        double parseConfidence,
        List<IndicatorDto> indicators,
        IdentityCheckRes identityCheck,
        CriticalCoverageRes criticalCoverage,
        List<AbnormalHitRes> abnormals,
        List<CriticalHitRes> criticalHits,
        boolean archiveBlocked,
        boolean safeNormalAllowed,
        Integer blockCode,
        String ruleVersion) {
}