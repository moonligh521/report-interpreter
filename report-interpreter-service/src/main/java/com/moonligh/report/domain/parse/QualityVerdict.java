package com.moonligh.report.domain.parse;

import java.util.List;

/**
 * 页级汇总后的文档级质量结论。
 */
public record QualityVerdict(DocumentQualityStatus status, List<QualityIssue> issues) {

    public QualityVerdict {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean passed() {
        return status == DocumentQualityStatus.PASSED;
    }
}
