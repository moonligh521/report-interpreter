package com.moonligh.report.domain.rule;

import java.text.Normalizer;

/**
 * 身份一致性检查：报告自带姓名不得自动绑定患者，必须与调用方给出的期望姓名比对。
 * <ul>
 *   <li>报告无身份 → MISSING</li>
 *   <li>未提供期望姓名 → MISSING（无法核对，保守处理）</li>
 *   <li>两者不一致 → CONFLICT；一致 → MATCHED</li>
 * </ul>
 */
public final class ReportIdentityConsistencyChecker {

    public IdentityCheckResult check(String reportPatientName, String expectedPatientName) {
        if (isBlank(reportPatientName)) {
            return new IdentityCheckResult(IdentityStatus.MISSING, "报告中未识别到身份信息");
        }
        if (isBlank(expectedPatientName)) {
            return new IdentityCheckResult(IdentityStatus.MISSING, "未提供期望就诊人姓名，无法核对报告身份");
        }
        if (normalize(reportPatientName).equals(normalize(expectedPatientName))) {
            return new IdentityCheckResult(IdentityStatus.MATCHED, "报告身份与期望就诊人一致");
        }
        return new IdentityCheckResult(IdentityStatus.CONFLICT, "报告身份与期望就诊人不一致");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** NFKC 归一并去掉全部空白，避免全半角与空格造成误判。 */
    private static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFKC).replaceAll("\\s+", "");
    }
}
