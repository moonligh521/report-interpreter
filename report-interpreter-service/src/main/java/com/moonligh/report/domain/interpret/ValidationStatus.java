package com.moonligh.report.domain.interpret;

/**
 * 单块校验结果。严重度：SAFETY_VIOLATION > CONFLICT > UNSUPPORTED > RETRYABLE > VALID。
 */
public enum ValidationStatus {
    VALID(0),
    /** 可通过重新生成修复（如数值被改）。 */
    RETRYABLE(1),
    /** 缺少可验证依据（假 evidenceId、无依据的指南口吻）；丢弃该块，不重试。 */
    UNSUPPORTED(2),
    /** 与 Java 规则结论冲突（如把超区间说成正常）；丢弃该块，不重试。 */
    CONFLICT(3),
    /** 涉及诊断 / 处方 / 降低危急；立即终止，不外发任何模型块。 */
    SAFETY_VIOLATION(4);

    private final int severity;

    ValidationStatus(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    public static ValidationStatus worst(ValidationStatus a, ValidationStatus b) {
        return a.severity >= b.severity ? a : b;
    }
}
