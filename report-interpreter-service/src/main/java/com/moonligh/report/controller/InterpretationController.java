package com.moonligh.report.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.Result;
import com.moonligh.consult.security.AccountIds;
import com.moonligh.report.api.req.TriggerInterpretationReq;
import com.moonligh.report.api.res.InterpretationRes;
import com.moonligh.report.common.IdempotencyKeyFilter;
import com.moonligh.report.service.ReportInterpretationService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 解读接口。Result.code：200 成功；4105 报告含危急值（data 仍返回，含危急提醒块）；
 * 4107 解读校验未通过（data 仅含通过 Guard 的块）；4104 知识库无依据且不允许降级。
 */
@RestController
@RequestMapping("/api/report/reports")
public class InterpretationController {

    private final ReportInterpretationService interpretations;

    public InterpretationController(ReportInterpretationService interpretations) {
        this.interpretations = interpretations;
    }

    @PostMapping("/{reportId}/interpretation")
    public Result<InterpretationRes> trigger(
            HttpServletRequest request,
            @RequestHeader(IdempotencyKeyFilter.HEADER) String idempotencyKey,
            @PathVariable String reportId,
            @RequestBody(required = false) TriggerInterpretationReq body) {
        String accountId = AccountIds.of(request);
        return toResult(interpretations.trigger(accountId, reportId, idempotencyKey, body));
    }

    @GetMapping("/{reportId}/interpretation")
    public Result<InterpretationRes> get(HttpServletRequest request, @PathVariable String reportId) {
        String accountId = AccountIds.of(request);
        return toResult(interpretations.latest(accountId, reportId));
    }

    private static Result<InterpretationRes> toResult(ReportInterpretationService.InterpretationView view) {
        if (view.code() == null) {
            return Result.ok(view.data());
        }
        ErrorCode code = errorCodeOf(view.code());
        return new Result<>(code.getCode(), code.getMessage(), view.data());
    }

    private static ErrorCode errorCodeOf(int code) {
        for (ErrorCode c : ErrorCode.values()) {
            if (c.getCode() == code) {
                return c;
            }
        }
        throw new IllegalStateException("未知平台错误码：" + code);
    }
}
