package com.moonligh.report.domain.interpret;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 禁用词表：解读不下确诊、不开方。命中即 SAFETY_VIOLATION。
 * 免责声明里的「不构成诊断」等否定性表述不在词表内。
 */
public final class DiagnosisLexicon {

    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("确诊"),
            Pattern.compile("诊断为"),
            Pattern.compile("诊断是"),
            Pattern.compile("诊断结果为"),
            Pattern.compile("可诊断"),
            Pattern.compile("患有"),
            Pattern.compile("患上"),
            Pattern.compile("[你您]得了"),
            Pattern.compile("一定是"),
            Pattern.compile("肯定是"),
            Pattern.compile("可以确定为"),
            Pattern.compile("处方"),
            Pattern.compile("开药"),
            Pattern.compile("开具"),
            Pattern.compile("建议服用"),
            Pattern.compile("请服用"),
            Pattern.compile("服用\\s*\\d"),
            Pattern.compile("停药"),
            Pattern.compile("自行加量"),
            Pattern.compile("加大剂量"),
            Pattern.compile("减少剂量"));

    private DiagnosisLexicon() {
    }

    public static boolean containsForbidden(String text) {
        return text != null && FORBIDDEN.stream().anyMatch(p -> p.matcher(text).find());
    }

    /** 词表规模，供测试保证词表不被悄悄清空。 */
    public static int size() {
        return FORBIDDEN.size();
    }

    /** 各词条的字面形式（供测试逐条覆盖）。 */
    public static List<String> samples() {
        return List.of(
                "确诊", "诊断为", "诊断是", "诊断结果为", "可诊断", "患有", "患上", "你得了", "您得了", "一定是", "肯定是",
                "可以确定为", "处方", "开药", "开具", "建议服用", "请服用", "服用 5", "停药", "自行加量", "加大剂量",
                "减少剂量");
    }
}
