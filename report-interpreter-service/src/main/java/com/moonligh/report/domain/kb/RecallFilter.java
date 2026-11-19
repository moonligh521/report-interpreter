package com.moonligh.report.domain.kb;

/**
 * 结构化召回过滤：指标码 / 专科。任一非空即为「强制过滤」——
 * 此时检索失败或为空一律返回空列表，禁止退回全库（fail-closed）。
 */
public record RecallFilter(String indicatorCode, String specialty) {

    public static final RecallFilter NONE = new RecallFilter(null, null);

    public static RecallFilter ofIndicator(String indicatorCode, String specialty) {
        return new RecallFilter(blankToNull(indicatorCode), blankToNull(specialty));
    }

    public boolean hasFilter() {
        return indicatorCode != null && !indicatorCode.isBlank()
                || specialty != null && !specialty.isBlank();
    }

    /** 分片是否满足本过滤（防御式二次校验，不信任存储层已按条件过滤）。 */
    public boolean accepts(KnowledgeChunk chunk) {
        if (indicatorCode != null && !indicatorCode.isBlank() && !indicatorCode.equals(chunk.indicatorCode())) {
            return false;
        }
        return specialty == null || specialty.isBlank() || specialty.equals(chunk.specialty());
    }

    /** 日志 / 指标用的过滤表达式（不含任何患者数据）。 */
    public String expr() {
        StringBuilder sb = new StringBuilder();
        if (indicatorCode != null && !indicatorCode.isBlank()) {
            sb.append("indicatorCode==").append(indicatorCode);
        }
        if (specialty != null && !specialty.isBlank()) {
            if (sb.length() > 0) {
                sb.append(" && ");
            }
            sb.append("specialty==").append(specialty);
        }
        return sb.toString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
