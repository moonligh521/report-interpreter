package com.moonligh.report.domain.interpret;

/**
 * 解读块声明类型。
 * FACT 复述报告数据与规则判定；GUIDELINE 引用知识库依据（必须带有效 evidenceId）；
 * CRITICAL_ALERT / NOTICE / DISCLAIMER 为 Java 模板块，不由模型生成。
 */
public enum ClaimType {
    FACT,
    GUIDELINE,
    CRITICAL_ALERT,
    NOTICE,
    DISCLAIMER
}
