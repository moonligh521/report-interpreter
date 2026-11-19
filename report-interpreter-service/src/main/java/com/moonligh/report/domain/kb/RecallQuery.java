package com.moonligh.report.domain.kb;

/**
 * 召回请求。text 为模糊词（可空）：仅用于关键词重排/收窄，绝不用来猜指标或专科类型；
 * limit 超界时收敛到 [1, MAX_LIMIT]。
 */
public record RecallQuery(String text, int limit) {

    public static final int DEFAULT_LIMIT = 5;
    public static final int MAX_LIMIT = 20;

    public static RecallQuery byFilterOnly() {
        return new RecallQuery(null, DEFAULT_LIMIT);
    }

    public boolean fuzzy() {
        return text != null && !text.isBlank();
    }

    public int effectiveLimit() {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
