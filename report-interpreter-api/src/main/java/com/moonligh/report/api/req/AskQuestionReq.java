package com.moonligh.report.api.req;

import java.util.List;

/**
 * 追问请求。仅传问题与聚焦规范码；不得携带可改写的检测数值。
 */
public record AskQuestionReq(String question, List<String> focusCanonicalCodes) {
}
