package com.moonligh.report.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.Result;
import com.moonligh.consult.security.AccountIds;
import com.moonligh.report.api.res.ReportEventsRes;
import com.moonligh.report.api.res.ReportSseEventRes;
import com.moonligh.report.common.PayloadJson;
import com.moonligh.report.service.ReportEventService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 事件接口。stream 为 text/event-stream：每次请求都重新鉴权，并从 DB 回放 Last-Event-ID 之后的已落库事件；
 * 客户端断线重连带上 Last-Event-ID 即可续传。events 返回当前 epoch 的事件列表与三条生命周期。
 */
@RestController
@RequestMapping("/api/report/reports")
public class ReportEventController {

    public static final String LAST_EVENT_ID = "Last-Event-ID";
    private static final MediaType EVENT_STREAM_UTF8 = new MediaType("text", "event-stream", StandardCharsets.UTF_8);
    private static final long RETRY_MILLIS = 3000L;

    private final ReportEventService events;

    public ReportEventController(ReportEventService events) {
        this.events = events;
    }

    @GetMapping("/{reportId}/interpretation/stream")
    public ResponseEntity<?> stream(
            HttpServletRequest request,
            @PathVariable String reportId,
            @RequestHeader(value = LAST_EVENT_ID, required = false) String lastEventId) {
        List<ReportSseEventRes> replay;
        try {
            // 重连即再鉴权：账号校验与归属校验在每次请求里重新执行。
            String accountId = AccountIds.of(request);
            replay = events.replay(accountId, reportId, lastEventId);
        } catch (BusinessException e) {
            // 客户端 Accept: text/event-stream 时统一异常处理无法协商 JSON，这里显式以 JSON 返回 Result。
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(Result.fail(e.getCode(), e.getMessage()));
        }
        return ResponseEntity.ok()
                .contentType(EVENT_STREAM_UTF8)
                .cacheControl(CacheControl.noCache())
                .body(format(replay));
    }

    @GetMapping("/{reportId}/events")
    public Result<ReportEventsRes> list(HttpServletRequest request, @PathVariable String reportId) {
        return Result.ok(events.list(AccountIds.of(request), reportId));
    }

    /** SSE 线格式：retry 提示 + 每事件 id / event / data（data 为单行 JSON）。 */
    static String format(List<ReportSseEventRes> replay) {
        StringBuilder sb = new StringBuilder();
        sb.append("retry: ").append(RETRY_MILLIS).append("\n\n");
        for (ReportSseEventRes e : replay) {
            sb.append("id: ").append(e.eventId()).append('\n')
                    .append("event: ").append(e.eventType().name()).append('\n')
                    .append("data: ").append(PayloadJson.write(e)).append("\n\n");
        }
        return sb.toString();
    }
}
