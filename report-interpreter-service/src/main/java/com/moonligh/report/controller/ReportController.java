package com.moonligh.report.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.moonligh.consult.framework.Result;
import com.moonligh.consult.security.AccountIds;
import com.moonligh.report.api.res.CreateReportRes;
import com.moonligh.report.api.res.ReportDetailRes;
import com.moonligh.report.common.IdempotencyKeyFilter;
import com.moonligh.report.service.ReportJobService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/report/reports")
public class ReportController {

    private final ReportJobService jobs;

    public ReportController(ReportJobService jobs) {
        this.jobs = jobs;
    }

    @PostMapping
    public Result<CreateReportRes> create(
            HttpServletRequest request,
            @RequestHeader(IdempotencyKeyFilter.HEADER) String idempotencyKey,
            @RequestParam("patientId") String patientId,
            @RequestParam(value = "reportType", required = false) String reportType,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        // foundation 不解析 file / reportType，仅校验身份与幂等后建单。
        String accountId = AccountIds.of(request);
        return Result.ok(jobs.create(accountId, patientId, idempotencyKey));
    }

    @GetMapping("/{reportId}")
    public Result<ReportDetailRes> get(HttpServletRequest request, @PathVariable String reportId) {
        AccountIds.of(request);
        return Result.ok(jobs.get(reportId));
    }
}
