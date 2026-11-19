package com.moonligh.report.api.req;

import java.util.List;

/**
 * 触发解读请求。focusCanonicalCodes 可空：为空表示解读报告内全部可识别指标；
 * 非空时仅聚焦这些指标（危急指标无论是否被点名都会被纳入，不可被隐藏）。
 */
public record TriggerInterpretationReq(List<String> focusCanonicalCodes) {
}
