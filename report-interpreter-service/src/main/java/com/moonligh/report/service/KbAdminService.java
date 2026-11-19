package com.moonligh.report.service;

import org.springframework.stereotype.Service;

import com.moonligh.report.api.req.KbReseedReq;
import com.moonligh.report.api.res.KbReseedRes;

/**
 * foundation：知识库重建占位，不落真实 KB。
 */
@Service
public class KbAdminService {

    public KbReseedRes reseed(KbReseedReq req) {
        String source = req == null || req.source() == null || req.source().isBlank()
                ? "classpath:kb/lab-min"
                : req.source();
        return new KbReseedRes(0, 0, "foundation-stub:" + source);
    }
}
