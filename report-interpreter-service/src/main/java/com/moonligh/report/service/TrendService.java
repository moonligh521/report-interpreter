package com.moonligh.report.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.res.TrendPoint;
import com.moonligh.report.api.res.TrendRes;
import com.moonligh.report.domain.trend.TrendComparability;
import com.moonligh.report.domain.trend.TrendDisclaimer;
import com.moonligh.report.infrastructure.persistence.ReportTrendMapper;
import com.moonligh.report.infrastructure.persistence.TrendPointRow;

/**
 * 历史趋势查询：可比性判定 + 固定免责声明；不做病因/诊断推断。
 */
@Service
public class TrendService {

    private final ReportTrendMapper trends;

    public TrendService(ReportTrendMapper trends) {
        this.trends = trends;
    }

    @Transactional(readOnly = true)
    public TrendRes trend(
            String accountId, String patientId, String canonicalCode, String specimenType,
            Instant from, Instant to, boolean requireComparable) {
        if (!StringUtils.hasText(patientId)) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "patientId 不能为空");
        }
        if (!StringUtils.hasText(canonicalCode)) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "canonicalCode 不能为空");
        }
        String code = canonicalCode.strip().toUpperCase(Locale.ROOT);
        String specimen = StringUtils.hasText(specimenType) ? specimenType.strip() : null;
        List<TrendPointRow> rows = trends.listTrendPoints(accountId, patientId, code, specimen, from, to);
        TrendComparability.Decision decision = TrendComparability.evaluate(rows);
        List<TrendPoint> points = rows.stream()
                .map(r -> new TrendPoint(
                        r.reportId(), r.specimenTime(), r.normalizedValue(), r.normalizedUnit(), r.parseVersion()))
                .toList();
        if (requireComparable && !decision.comparable()) {
            return new TrendRes(code, false, decision.reason(), List.of(), TrendDisclaimer.TEXT);
        }
        return new TrendRes(code, decision.comparable(), decision.reason(), points, TrendDisclaimer.TEXT);
    }
}
