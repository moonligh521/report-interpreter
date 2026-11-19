package com.moonligh.report.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.Result;
import com.moonligh.consult.security.AccountIds;
import com.moonligh.report.api.req.AskQuestionReq;
import com.moonligh.report.api.res.AskQuestionRes;
import com.moonligh.report.common.IdempotencyKeyFilter;
import com.moonligh.report.service.ReportQuestionService;

import jakarta.servlet.http.HttpServletRequest;

/** 追问接口。4107 表示 Guard 拦截改数等校验失败。 */
@RestController
@RequestMapping("/api/report/reports")
public class QuestionController {

    private final ReportQuestionService questions;

    public QuestionController(ReportQuestionService questions) {
        this.questions = questions;
    }

    @PostMapping("/{reportId}/questions")
    public Result<AskQuestionRes> ask(
            HttpServletRequest request,
            @RequestHeader(IdempotencyKeyFilter.HEADER) String idempotencyKey,
            @PathVariable String reportId,
            @RequestBody AskQuestionReq body) {
        String accountId = AccountIds.of(request);
        return toResult(questions.ask(accountId, reportId, idempotencyKey, body));
    }

    private static Result<AskQuestionRes> toResult(ReportQuestionService.QuestionView view) {
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
