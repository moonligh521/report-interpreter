package com.moonligh.report.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.Result;
import com.moonligh.report.api.req.CreateReviewReq;
import com.moonligh.report.api.res.CreateReviewRes;
import com.moonligh.report.common.IdempotencyKeyFilter;
import com.moonligh.report.service.ReviewService;

/**
 * 内部接口：创建人工复核任务。鉴权由 /internal/** 内部令牌过滤器负责。
 */
@RestController
@RequestMapping("/internal/report/reviews")
public class InternalReviewController {

    private final ReviewService reviews;

    public InternalReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @PostMapping
    public Result<CreateReviewRes> create(
            @RequestHeader(value = IdempotencyKeyFilter.HEADER, required = false) String idempotencyKey,
            @RequestBody CreateReviewReq req) {
        return Result.ok(reviews.create(req, idempotencyKey));
    }
}