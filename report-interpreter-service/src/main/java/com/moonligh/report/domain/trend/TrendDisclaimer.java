package com.moonligh.report.domain.trend;

/**
 * 趋势接口固定免责声明（T-R3）：无诊断话术、无病因推断。
 */
public final class TrendDisclaimer {

    public static final String TEXT =
            "本趋势仅基于已解析指标的时间序列并列展示，不构成诊断意见，亦不对病因作推断；请结合临床医生判断。";

    private TrendDisclaimer() {
    }

    /** 防御：声明文本不得含确诊式病因措辞。 */
    public static boolean looksDiagnostic(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.toLowerCase();
        return t.contains("确诊") || t.contains("诊断为") || t.contains("病因是") || t.contains("diagnosis");
    }
}
