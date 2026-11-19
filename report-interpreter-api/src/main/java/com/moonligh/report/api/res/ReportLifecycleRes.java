package com.moonligh.report.api.res;

/**
 * 三条互相独立的生命周期。
 * <ul>
 *   <li>interpretationStatus：NONE / COMPLETED / FAILED</li>
 *   <li>archiveStatus：NONE / BLOCKED / PENDING / FAILED / SUCCESS / DEAD</li>
 *   <li>criticalReadStatus：NONE / UNREAD / READ（READ 仅当用户确认，ACCEPTED 不算已读）</li>
 * </ul>
 */
public record ReportLifecycleRes(
        String interpretationStatus,
        String archiveStatus,
        String criticalReadStatus) {
}
