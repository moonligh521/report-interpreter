package com.moonligh.report.controller;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.Result;
import com.moonligh.consult.security.AccountIds;
import com.moonligh.report.api.res.TrendRes;
import com.moonligh.report.service.TrendService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/report/patients")
public class TrendController {

    private final TrendService trends;

    public TrendController(TrendService trends) {
        this.trends = trends;
    }

    @GetMapping("/{patientId}/indicators/{canonicalCode}/trend")
    public Result<TrendRes> trend(
            HttpServletRequest request,
            @PathVariable String patientId,
            @PathVariable String canonicalCode,
            @RequestParam(value = "specimenType", required = false) String specimenType,
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to", required = false) Instant to,
            @RequestParam(value = "requireComparable", required = false, defaultValue = "false")
                    boolean requireComparable) {
        String accountId = AccountIds.of(request);
        return Result.ok(trends.trend(accountId, patientId, canonicalCode, specimenType, from, to, requireComparable));
    }
}
