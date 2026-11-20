package com.moonligh.report.infrastructure.catalog;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.domain.rule.ClinicalCatalog;
import com.moonligh.report.domain.rule.ClinicalCatalogPort;
import com.moonligh.report.domain.rule.CriticalLevel;
import com.moonligh.report.domain.rule.CriticalRule;
import com.moonligh.report.domain.rule.CriticalRuleTable;
import com.moonligh.report.domain.rule.ReferenceRangeRule;
import com.moonligh.report.domain.rule.RuleCondition;

/**
 * Fake 临床目录：多版本可切换；未知版本 → 4106，禁止静默回退。
 */
public final class FakeClinicalCatalogAdapter implements ClinicalCatalogPort {

    public static final String VERSION_V1 = "builtin-v1";
    public static final String VERSION_V2 = "builtin-v2";

    private final Map<String, ClinicalCatalog> byVersion;

    public FakeClinicalCatalogAdapter() {
        Map<String, ClinicalCatalog> map = new LinkedHashMap<>();
        map.put(VERSION_V1, catalogV1());
        map.put(VERSION_V2, catalogV2());
        this.byVersion = Map.copyOf(map);
    }

    @Override
    public ClinicalCatalog load(String version) {
        if (version == null || version.isBlank()) {
            throw new BusinessException(ErrorCode.REPORT_RULE_PENDING_REVIEW, "clinical catalog version is blank");
        }
        ClinicalCatalog catalog = byVersion.get(version);
        if (catalog == null) {
            throw new BusinessException(
                    ErrorCode.REPORT_RULE_PENDING_REVIEW, "clinical catalog version not found: " + version);
        }
        return catalog;
    }

    private static ClinicalCatalog catalogV1() {
        return new ClinicalCatalog(VERSION_V1, CriticalRuleTable.builtIn().all(), referenceRulesV1());
    }

    /** v2：危急 K 额外要求 AGE（用于验证缺人口学不得 NOT_CRITICAL）。 */
    private static ClinicalCatalog catalogV2() {
        Set<RuleCondition> required = EnumSet.of(
                RuleCondition.CANONICAL, RuleCondition.VALUE, RuleCondition.UNIT,
                RuleCondition.SPECIMEN_TYPE, RuleCondition.FIELD_CONFIDENCE, RuleCondition.AGE);
        List<CriticalRule> critical = List.of(
                new CriticalRule("CRIT-K-HIGH", VERSION_V2, "K", "SERUM", "mmol/L", CriticalLevel.HIGH,
                        new BigDecimal("6.0"), true, required),
                new CriticalRule("CRIT-K-LOW", VERSION_V2, "K", "SERUM", "mmol/L", CriticalLevel.LOW,
                        new BigDecimal("2.5"), false, required));
        return new ClinicalCatalog(VERSION_V2, critical, referenceRulesV1());
    }

    private static List<ReferenceRangeRule> referenceRulesV1() {
        return List.of(
                new ReferenceRangeRule(
                        "REF-STD-K", VERSION_V1, "K", "SERUM", "mmol/L",
                        new BigDecimal("3.5"), new BigDecimal("5.3"), null, null, null),
                new ReferenceRangeRule(
                        "REF-STD-NA", VERSION_V1, "NA", "SERUM", "mmol/L",
                        new BigDecimal("135"), new BigDecimal("145"), null, null, null),
                new ReferenceRangeRule(
                        "REF-AGE-K-CHILD", VERSION_V1, "K", "SERUM", "mmol/L",
                        new BigDecimal("3.5"), new BigDecimal("5.0"), 0, 18, null),
                new ReferenceRangeRule(
                        "REF-AGE-K-ADULT", VERSION_V1, "K", "SERUM", "mmol/L",
                        new BigDecimal("3.5"), new BigDecimal("5.5"), 18, null, null));
    }
}
