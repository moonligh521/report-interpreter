package com.moonligh.report.domain.interpret;

/**
 * 解读整体状态。VALID / DEGRADED 对应 Result.code 200（或 4105 危急）；其余为 4107。
 */
public enum InterpretationStatus {
    VALID,
    /** 知识库无可引用依据：仅复述报告数据与规则结论，无指南口吻。 */
    DEGRADED,
    RETRYABLE,
    UNSUPPORTED,
    CONFLICT,
    SAFETY_VIOLATION;

    public boolean passed() {
        return this == VALID || this == DEGRADED;
    }

    public static InterpretationStatus from(ValidationStatus v) {
        return switch (v) {
            case VALID -> VALID;
            case RETRYABLE -> RETRYABLE;
            case UNSUPPORTED -> UNSUPPORTED;
            case CONFLICT -> CONFLICT;
            case SAFETY_VIOLATION -> SAFETY_VIOLATION;
        };
    }
}
