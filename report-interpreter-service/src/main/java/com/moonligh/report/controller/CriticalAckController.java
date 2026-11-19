package com.moonligh.report.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.Result;
import com.moonligh.consult.security.AccountIds;
import com.moonligh.report.api.req.AckCriticalReq;
import com.moonligh.report.api.res.AckCriticalRes;
import com.moonligh.report.common.IdempotencyKeyFilter;
import com.moonligh.report.service.CriticalAckService;

import jakarta.servlet.http.HttpServletRequest;

/** 危急通知患者端 ACK。 */
@RestController
@RequestMapping("/api/report/reports")
public class CriticalAckController {

    private final CriticalAckService acks;

    public CriticalAckController(CriticalAckService acks) {
        this.acks = acks;
    }

    @PostMapping("/{reportId}/critical-events/{eventId}/ack")
    public Result<AckCriticalRes> ack(
            HttpServletRequest request,
            @RequestHeader(IdempotencyKeyFilter.HEADER) String idempotencyKey,
            @PathVariable String reportId,
            @PathVariable String eventId,
            @RequestBody(required = false) AckCriticalReq body) {
        String accountId = AccountIds.of(request);
        return Result.ok(acks.ack(accountId, reportId, eventId, body == null ? new AckCriticalReq(null) : body));
    }
}
