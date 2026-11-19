package com.moonligh.report.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moonligh.report.api.res.ReportLifecycleRes;
import com.moonligh.report.domain.archive.ArchiveOutboxStore;
import com.moonligh.report.domain.critical.CriticalEventStore;
import com.moonligh.report.domain.critical.NotificationStatus;
import com.moonligh.report.domain.interpret.InterpretationStatus;
import com.moonligh.report.domain.interpret.InterpretationStore;
import com.moonligh.report.infrastructure.persistence.CriticalNotificationOutboxRow;
import com.moonligh.report.infrastructure.persistence.ReportArchiveOutboxRow;
import com.moonligh.report.infrastructure.persistence.ReportCriticalEventRow;
import com.moonligh.report.infrastructure.persistence.ReportInterpretationRow;

/**
 * 三条互相独立的生命周期的读取视图：解读 / 档案 / 危急已读。
 * 三者各自来自不同的表与状态机，任何一条的状态都不推导另外两条。
 */
@Service
public class ReportLifecycleService {

    public static final String NONE = "NONE";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String BLOCKED = "BLOCKED";
    public static final String UNREAD = "UNREAD";
    public static final String READ = "READ";

    private final InterpretationStore interpretations;
    private final ArchiveOutboxStore archive;
    private final CriticalEventStore critical;

    public ReportLifecycleService(
            InterpretationStore interpretations, ArchiveOutboxStore archive, CriticalEventStore critical) {
        this.interpretations = interpretations;
        this.archive = archive;
        this.critical = critical;
    }

    @Transactional(readOnly = true)
    public ReportLifecycleRes of(String reportId) {
        Optional<ReportInterpretationRow> latest = interpretations.findLatest(reportId);
        boolean completed = latest.isPresent() && InterpretationStatus.valueOf(latest.get().status()).passed();
        return new ReportLifecycleRes(
                interpretationStatus(latest, completed), archiveStatus(reportId, latest.isPresent() && completed),
                criticalReadStatus(reportId));
    }

    private static String interpretationStatus(Optional<ReportInterpretationRow> latest, boolean completed) {
        if (latest.isEmpty()) {
            return NONE;
        }
        return completed ? COMPLETED : FAILED;
    }

    /** 已完成解读却没有档案 Outbox = 被门禁拦下（BLOCKED）；其余沿用 Outbox 自身状态。 */
    private String archiveStatus(String reportId, boolean interpretationCompleted) {
        Optional<ReportArchiveOutboxRow> row = archive.findLatestByReport(reportId);
        if (row.isPresent()) {
            return row.get().status();
        }
        return interpretationCompleted ? BLOCKED : NONE;
    }

    /** 仅用户确认（ACKNOWLEDGED）才算已读；ACCEPTED（通道已接受）仍是未读。 */
    public String criticalReadStatus(String reportId) {
        List<ReportCriticalEventRow> events = critical.listEventsByReport(reportId);
        if (events.isEmpty()) {
            return NONE;
        }
        boolean allRead = events.stream().allMatch(e -> critical.findOutboxByEvent(e.eventId())
                .map(CriticalNotificationOutboxRow::notificationStatus)
                .filter(s -> NotificationStatus.ACKNOWLEDGED.name().equals(s))
                .isPresent());
        return allRead ? READ : UNREAD;
    }
}
