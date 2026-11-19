package com.moonligh.report.api.req;

import java.math.BigDecimal;

/** 单条指标人工修订。 */
public record CorrectionPatch(String indicatorId, BigDecimal normalizedValue, String reason) {
}
