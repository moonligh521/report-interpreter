package com.moonligh.report.api.res;

import java.util.List;

/**
 * 历史趋势响应。comparable=false 时仍可并列返回 points，并给出不可比原因码；
 * disclaimer 为固定免责声明，不得含诊断话术。
 */
public record TrendRes(
        String canonicalCode,
        boolean comparable,
        String incomparableReason,
        List<TrendPoint> points,
        String disclaimer) {

    public TrendRes {
        points = points == null ? List.of() : List.copyOf(points);
    }
}
