package com.moonligh.report.domain.kb;

/**
 * 召回为空的稳定原因枚举（日志 / 指标用，不直接展示给 C 端）。
 */
public enum RecallEmptyReason {
    /** 有结果。 */
    NONE,
    /** 结构化过滤后无任何已发布分片。 */
    SCALAR_EMPTY,
    /** 过滤后有候选，但模糊词关键词无命中（不退回候选全集）。 */
    KEYWORD_EMPTY,
    /** 存储层异常；有无过滤都返回空，绝不退全库。 */
    STORE_UNAVAILABLE,
    /** 无过滤且已发布目录为空。 */
    NO_PUBLISHED_INTERSECT
}
