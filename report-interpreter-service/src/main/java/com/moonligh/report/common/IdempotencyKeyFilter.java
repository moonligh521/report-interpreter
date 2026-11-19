package com.moonligh.report.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.Result;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * /api/report/** 写接口必须带 Idempotency-Key。
 */
@Component
public class IdempotencyKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/report/")) {
            return true;
        }
        return !WRITE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank() || key.length() > 64) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            Result<Void> body = Result.fail(ErrorCode.PARAM_MISSING.getCode(), "缺少或非法 Idempotency-Key");
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
