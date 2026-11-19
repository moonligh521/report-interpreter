package com.moonligh.report.api.res;

import java.util.List;

/**
 * GET /api/report/reports/{reportId}/events：当前 epoch 下已落库事件（按 sequence 升序）与三条生命周期。
 */
public record ReportEventsRes(
        String reportId,
        List<ReportSseEventRes> events,
        ReportLifecycleRes lifecycle) {
}
