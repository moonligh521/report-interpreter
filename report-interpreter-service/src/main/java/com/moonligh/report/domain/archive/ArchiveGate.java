package com.moonligh.report.domain.archive;

import com.moonligh.report.domain.rule.IdentityStatus;

/**
 * 档案门禁：仅「身份 MATCHED + 规则未阻断归档 + 解读已完成」才允许写入可投递档案 Outbox。
 * CONFLICT / MISSING 以及规则阻断（覆盖未知等）一律不写。
 */
public final class ArchiveGate {

    public enum Decision {
        ALLOWED,
        BLOCKED_IDENTITY,
        BLOCKED_RULES,
        INTERPRETATION_NOT_COMPLETED
    }

    private ArchiveGate() {
    }

    public static Decision decide(String identityStatus, boolean archiveBlocked, boolean interpretationCompleted) {
        if (!IdentityStatus.MATCHED.name().equals(identityStatus)) {
            return Decision.BLOCKED_IDENTITY;
        }
        if (archiveBlocked) {
            return Decision.BLOCKED_RULES;
        }
        if (!interpretationCompleted) {
            return Decision.INTERPRETATION_NOT_COMPLETED;
        }
        return Decision.ALLOWED;
    }
}
