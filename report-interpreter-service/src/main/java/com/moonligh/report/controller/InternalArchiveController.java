package com.moonligh.report.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.Result;
import com.moonligh.report.api.res.ArchiveOutboxRes;
import com.moonligh.report.service.ArchiveOutboxService;

/**
 * 内部接口：档案 Outbox 查询。鉴权由 /internal/** 内部令牌过滤器负责。
 */
@RestController
@RequestMapping("/internal/report/archive")
public class InternalArchiveController {

    private final ArchiveOutboxService archive;

    public InternalArchiveController(ArchiveOutboxService archive) {
        this.archive = archive;
    }

    @GetMapping("/outbox/{id}")
    public Result<ArchiveOutboxRes> get(@PathVariable String id) {
        return Result.ok(archive.getOutbox(id));
    }
}
