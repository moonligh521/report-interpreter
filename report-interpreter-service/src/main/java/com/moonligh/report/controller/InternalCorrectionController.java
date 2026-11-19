package com.moonligh.report.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.Result;
import com.moonligh.report.api.req.CorrectionReq;
import com.moonligh.report.api.res.CorrectionRes;
import com.moonligh.report.common.IdempotencyKeyFilter;
import com.moonligh.report.service.ReportCorrectionService;

/** 内部接口：普通字段修订（与危急复核分离）。 */
@RestController
@RequestMapping("/internal/report/corrections")
public class InternalCorrectionController {

    private final ReportCorrectionService corrections;

    public InternalCorrectionController(ReportCorrectionService corrections) {
        this.corrections = corrections;
    }

    @PostMapping
    public Result<CorrectionRes> correct(
            @RequestHeader(value = IdempotencyKeyFilter.HEADER, required = false) String idempotencyKey,
            @RequestBody CorrectionReq req) {
        return Result.ok(corrections.correct(req, idempotencyKey));
    }
}
