package com.moonligh.report.domain.rule;

import com.moonligh.consult.framework.ErrorCode;

/**
 * 身份检查结果。detail 不得包含姓名等个人信息。
 */
public record IdentityCheckResult(IdentityStatus status, String detail) {

    /** CONFLICT→4108，MISSING→4110，MATCHED 无阻断码。 */
    public Integer blockCode() {
        return switch (status) {
            case CONFLICT -> ErrorCode.REPORT_IDENTITY_CONFLICT.getCode();
            case MISSING -> ErrorCode.REPORT_IDENTITY_MISSING.getCode();
            case MATCHED -> null;
        };
    }
}
