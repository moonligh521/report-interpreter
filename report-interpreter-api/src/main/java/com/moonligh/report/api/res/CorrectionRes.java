package com.moonligh.report.api.res;

/** 修订结果：新解析版本，并要求重跑规则。 */
public record CorrectionRes(int newParseVersion, boolean reRuleRequired) {
}
