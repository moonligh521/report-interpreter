package com.moonligh.report.api.res;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * DONE：解读流程结束时刻三条生命周期的快照。三者互相独立：
 * interpretationStatus=COMPLETED 不代表 archiveStatus=SUCCESS，也不代表危急已读。
 */
public record DonePayload(
        String interpretationId,
        String interpretationStatus,
        String archiveStatus,
        String criticalReadStatus) implements ReportEventPayload {

    @Override
    @JsonIgnore
    public ReportEventType type() {
        return ReportEventType.DONE;
    }
}
