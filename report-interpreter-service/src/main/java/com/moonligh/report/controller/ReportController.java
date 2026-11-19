package com.moonligh.report.controller;

import java.io.IOException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.Result;
import com.moonligh.consult.security.AccountIds;
import com.moonligh.report.api.res.CreateReportRes;
import com.moonligh.report.api.res.CriticalEventsRes;
import com.moonligh.report.api.res.ReportDetailRes;
import com.moonligh.report.common.IdempotencyKeyFilter;
import com.moonligh.report.service.ReportParseService;
import com.moonligh.report.service.UploadedFile;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/report/reports")
public class ReportController {

    private final ReportParseService reports;

    public ReportController(ReportParseService reports) {
        this.reports = reports;
    }

    @PostMapping
    public Result<CreateReportRes> create(
            HttpServletRequest request,
            @RequestHeader(IdempotencyKeyFilter.HEADER) String idempotencyKey,
            @RequestParam("patientId") String patientId,
            @RequestParam(value = "reportType", required = false) String reportType,
            @RequestParam(value = "expectedPatientName", required = false) String expectedPatientName,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        String accountId = AccountIds.of(request);
        return Result.ok(reports.create(
                accountId, patientId, idempotencyKey, reportType, toUploaded(file), expectedPatientName));
    }

    @PostMapping("/{reportId}/reparse")
    public Result<CreateReportRes> reparse(
            HttpServletRequest request,
            @RequestHeader(IdempotencyKeyFilter.HEADER) String idempotencyKey,
            @PathVariable String reportId) {
        String accountId = AccountIds.of(request);
        return Result.ok(reports.reparse(accountId, reportId, idempotencyKey));
    }

    /**
     * 报告详情。身份 / 覆盖 / 规则存在阻断（4108/4110/4109/4106）时，Result.code 为对应平台错误码，
     * 同时仍随 data 返回详情（身份冲突时不含指标明细），让调用方能看到阻断原因。
     */
    @GetMapping("/{reportId}")
    public Result<ReportDetailRes> get(HttpServletRequest request, @PathVariable String reportId) {
        String accountId = AccountIds.of(request);
        ReportDetailRes detail = reports.get(accountId, reportId);
        if (detail.blockCode() == null) {
            return Result.ok(detail);
        }
        ErrorCode code = errorCodeOf(detail.blockCode());
        return new Result<>(code.getCode(), code.getMessage(), detail);
    }

    @GetMapping("/{reportId}/critical-events")
    public Result<CriticalEventsRes> criticalEvents(HttpServletRequest request, @PathVariable String reportId) {
        String accountId = AccountIds.of(request);
        return Result.ok(reports.criticalEvents(accountId, reportId));
    }

    private static ErrorCode errorCodeOf(int code) {
        for (ErrorCode c : ErrorCode.values()) {
            if (c.getCode() == code) {
                return c;
            }
        }
        throw new IllegalStateException("未知平台错误码：" + code);
    }

    private static UploadedFile toUploaded(MultipartFile file) {
        if (file == null) {
            return null;
        }
        try {
            return new UploadedFile(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文件读取失败");
        }
    }
}