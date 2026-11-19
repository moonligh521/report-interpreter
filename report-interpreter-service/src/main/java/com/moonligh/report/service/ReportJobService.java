package com.moonligh.report.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.res.CreateReportRes;
import com.moonligh.report.api.res.ReportDetailRes;
import com.moonligh.report.domain.job.ReportJobStore;
import com.moonligh.report.infrastructure.persistence.ReportJobRow;

/**
 * foundation：幂等建单骨架，不做解析。
 */
@Service
public class ReportJobService {

    private static final String STATUS_CREATED = "CREATED";

    private final ReportJobStore jobs;

    public ReportJobService(ReportJobStore jobs) {
        this.jobs = jobs;
    }

    @Transactional
    public CreateReportRes create(String accountId, String patientId, String idempotencyKey) {
        if (!StringUtils.hasText(patientId)) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "patientId 不能为空");
        }
        return jobs.findByIdempotency(accountId, idempotencyKey)
                .map(existing -> new CreateReportRes(existing.reportId(), existing.status()))
                .orElseGet(() -> insertNew(accountId, patientId, idempotencyKey));
    }

    public ReportDetailRes get(String reportId) {
        ReportJobRow row = jobs.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "找不到该报告任务"));
        return new ReportDetailRes(row.reportId(), "UNKNOWN", row.status(), 0, false);
    }

    private CreateReportRes insertNew(String accountId, String patientId, String idempotencyKey) {
        String reportId = "rpt_" + UUID.randomUUID().toString().replace("-", "");
        ReportJobRow row = new ReportJobRow(
                reportId, accountId, patientId, idempotencyKey, STATUS_CREATED, 1L, null, null);
        jobs.insert(row);
        return new CreateReportRes(reportId, STATUS_CREATED);
    }
}
