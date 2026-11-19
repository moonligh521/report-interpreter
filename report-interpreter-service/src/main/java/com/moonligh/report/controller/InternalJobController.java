package com.moonligh.report.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.Result;
import com.moonligh.report.api.req.CompensateReq;
import com.moonligh.report.api.res.CompensateRes;
import com.moonligh.report.service.ArchiveOutboxService;

/**
 * 内部接口：档案 Outbox 补偿。鉴权由 /internal/** 内部令牌过滤器负责。
 * 天然幂等：已 PENDING 原样返回，SUCCESS 拒绝。
 */
@RestController
@RequestMapping("/internal/report/jobs")
public class InternalJobController {

    private final ArchiveOutboxService archive;

    public InternalJobController(ArchiveOutboxService archive) {
        this.archive = archive;
    }

    @PostMapping("/{id}/compensate")
    public Result<CompensateRes> compensate(@PathVariable String id, @RequestBody CompensateReq req) {
        return Result.ok(archive.compensate(id, req));
    }
}
