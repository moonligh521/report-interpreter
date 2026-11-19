package com.moonligh.report.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.Result;
import com.moonligh.report.api.req.KbReseedReq;
import com.moonligh.report.api.res.KbReseedRes;
import com.moonligh.report.service.KbAdminService;

@RestController
@RequestMapping("/internal/report/kb")
public class InternalKbController {

    private final KbAdminService kbAdmin;

    public InternalKbController(KbAdminService kbAdmin) {
        this.kbAdmin = kbAdmin;
    }

    @PostMapping("/reseed")
    public Result<KbReseedRes> reseed(@RequestBody(required = false) KbReseedReq req) {
        return Result.ok(kbAdmin.reseed(req));
    }
}
