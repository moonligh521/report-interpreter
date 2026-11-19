package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.moonligh.report.domain.critical.CriticalDetection;
import com.moonligh.report.domain.critical.CriticalEventStore;
import com.moonligh.report.domain.critical.CriticalNotificationManager;
import com.moonligh.report.domain.critical.CriticalNotificationSender;
import com.moonligh.report.domain.critical.DeliveryResult;
import com.moonligh.report.domain.critical.NotificationStatus;
import com.moonligh.report.domain.critical.RegisteredCriticalEvent;
import com.moonligh.report.infrastructure.persistence.CriticalNotificationOutboxRow;
import com.moonligh.report.service.CriticalNotificationService;

/** AC-R7/R8：通知状态机；FAILED 可 RETRYING；ACCEPTED ≠ 已读；超限升级人工。 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationRetryTest {

    private static final CriticalNotificationSender DOWN = m -> DeliveryResult.failed("CHANNEL_DOWN");
    private static final CriticalNotificationSender OK = m -> DeliveryResult.ok();

    @Autowired
    private CriticalNotificationManager manager;

    @Autowired
    private CriticalNotificationService service;

    @Autowired
    private CriticalEventStore store;

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private RegisteredCriticalEvent register(boolean deliverable) {
        String reportId = "rpt_" + UUID.randomUUID().toString().replace("-", "");
        return manager.register(new CriticalDetection(reportId, 1, "K", "HIGH", "6.8", now()), deliverable);
    }

    @Test
    void stateMachineAllowsOnlyDefinedTransitions() {
        assertTrue(NotificationStatus.FAILED.canTransitionTo(NotificationStatus.RETRYING));
        assertTrue(NotificationStatus.RETRYING.canTransitionTo(NotificationStatus.SENDING));
        assertTrue(NotificationStatus.SENDING.canTransitionTo(NotificationStatus.ACCEPTED));
        assertFalse(NotificationStatus.ACCEPTED.canTransitionTo(NotificationStatus.PENDING));
        assertFalse(NotificationStatus.ACCEPTED.canTransitionTo(NotificationStatus.RETRYING));
        assertFalse(NotificationStatus.PENDING_REVIEW.canTransitionTo(NotificationStatus.SENDING));
        assertTrue(NotificationStatus.ESCALATED.next().isEmpty());
    }

    @Test
    void failedDeliveryBecomesRetryingWithBackoffThenAccepted() {
        RegisteredCriticalEvent reg = register(true);
        String outboxId = reg.outbox().outboxId();
        assertEquals("PENDING", reg.outbox().notificationStatus());
        Instant t0 = now();

        CriticalNotificationOutboxRow failed = service.dispatch(outboxId, t0, DOWN);
        assertEquals("RETRYING", failed.notificationStatus());
        assertEquals(1, failed.attemptCount());
        assertEquals("CHANNEL_DOWN", failed.lastErrorCode());
        assertEquals(t0.plus(CriticalNotificationManager.backoff(1)), failed.nextRetryAt());

        // 未到期不在待重试列表；到期后出现。
        assertTrue(store.listRetryDue(t0.plusSeconds(5), 1000).stream().noneMatch(r -> r.outboxId().equals(outboxId)));
        assertThrows(IllegalStateException.class, () -> service.dispatch(outboxId, t0.plusSeconds(5), OK));
        Instant t1 = t0.plus(Duration.ofSeconds(31));
        assertTrue(store.listRetryDue(t1, 1000).stream().anyMatch(r -> r.outboxId().equals(outboxId)));

        CriticalNotificationOutboxRow accepted = service.dispatch(outboxId, t1, OK);
        assertEquals("ACCEPTED", accepted.notificationStatus());
        assertEquals(2, accepted.attemptCount());
        assertNull(accepted.lastErrorCode());
        assertNull(accepted.nextRetryAt());
        assertNotNull(accepted.deliveredAt());

        CriticalNotificationOutboxRow persisted = store.findOutboxById(outboxId).orElseThrow();
        assertEquals("ACCEPTED", persisted.notificationStatus());
        // ACCEPTED 之后不再进入重试队列，也不能被再次派发。
        assertTrue(store.listRetryDue(t1.plusSeconds(3600), 1000).stream().noneMatch(r -> r.outboxId().equals(outboxId)));
        assertThrows(IllegalStateException.class, () -> service.dispatch(outboxId, t1.plusSeconds(3600), OK));
    }

    @Test
    void exhaustedAttemptsEscalateToHuman() {
        RegisteredCriticalEvent reg = register(true);
        String outboxId = reg.outbox().outboxId();
        Instant t = now();
        CriticalNotificationOutboxRow row = null;
        for (int i = 0; i < 5; i++) {
            row = service.dispatch(outboxId, t, DOWN);
            if (row.nextRetryAt() != null) {
                t = row.nextRetryAt();
            }
        }
        assertEquals("ESCALATED", row.notificationStatus());
        assertEquals(5, row.attemptCount());
        final Instant late = t.plusSeconds(86400);
        assertTrue(store.listRetryDue(late, 1000).stream().noneMatch(r -> r.outboxId().equals(outboxId)));
    }

    @Test
    void pendingReviewIsNotDispatchableUntilReleased() {
        RegisteredCriticalEvent reg = register(false);
        String outboxId = reg.outbox().outboxId();
        assertEquals("PENDING_REVIEW", reg.outbox().notificationStatus());
        assertTrue(store.listRetryDue(now().plusSeconds(10), 1000).stream().noneMatch(r -> r.outboxId().equals(outboxId)));
        assertThrows(IllegalStateException.class, () -> service.dispatch(outboxId, now(), OK));

        CriticalNotificationOutboxRow released = service.releaseAfterReview(outboxId);
        assertEquals("PENDING", released.notificationStatus());
        assertEquals("ACCEPTED", service.dispatch(outboxId, now(), OK).notificationStatus());
    }

    @Test
    void registeringSameFactTwiceIsIdempotent() {
        String reportId = "rpt_" + UUID.randomUUID().toString().replace("-", "");
        CriticalDetection d = new CriticalDetection(reportId, 1, "K", "HIGH", "6.8", now());
        RegisteredCriticalEvent a = manager.register(d, true);
        RegisteredCriticalEvent b = manager.register(
                new CriticalDetection(reportId, 2, "K", "HIGH", "6.8", now()), true);
        assertTrue(a.created());
        assertFalse(b.created());
        assertEquals(a.event().eventId(), b.event().eventId());
        assertEquals(a.outbox().outboxId(), b.outbox().outboxId());
        assertEquals(1, store.listEventsByReport(reportId).size());
    }
}