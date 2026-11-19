package com.moonligh.report.api.res;

/**
 * 一次解读钉死的五类版本：知识库 / 规则集 / 提示词 / 解析器 / 输出 schema。
 * 版本一经写入该次解读即不可变，后续 KB 或规则升级不改写历史解读。
 */
public record PinnedVersions(
        String kbVersion,
        String ruleVersion,
        String promptVersion,
        String parserVersion,
        String schemaVersion) {
}
