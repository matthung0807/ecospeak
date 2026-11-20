package com.matthung.ecospeak.history;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.*;
import com.matthung.ecospeak.quota.QuotaService;
import com.matthung.ecospeak.quota.QuotaCalendar;
import com.matthung.ecospeak.quota.QuotaProperties;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:quota-integration;DB_CLOSE_DELAY=0")
class QuotaIntegrationTests {
    @Autowired QuotaService quota;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired QuotaCalendar calendar;
    @Autowired QuotaProperties properties;
    @MockitoBean Clock clock;
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM dialogue_generation_request");
        when(clock.instant()).thenReturn(Instant.parse("2026-11-15T15:59:59Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }
    @Test void reservationRetryFencingAndCalendarCharging() {
        var first = quota.admit("key", "scenario");
        assertEquals("generation_in_progress", quota.admit("key", "scenario").outcome());
        assertEquals("idempotency_conflict", quota.admit("key", "other").outcome());
        assertEquals(1, quota.snapshot().daily().reserved());
        quota.release(first.attempt());
        var second = quota.admit("key", "scenario");
        quota.release(first.attempt());
        assertEquals(1, quota.snapshot().daily().reserved());
        when(clock.instant()).thenReturn(Instant.parse("2026-11-15T16:00:00Z"));
        assertEquals(0, quota.snapshot().daily().reserved());
        assertEquals(0, quota.snapshot().weekly().reserved());
        quota.release(second.attempt());
    }
    @Test void simultaneousFinalSlotIsReservedOnce() throws Exception {
        quota.admit(null, "first");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> quota.admit(null, "a"));
            var b = pool.submit(() -> quota.admit(null, "b"));
            assertEquals(java.util.Set.of("reserved", "dialogue_quota_exceeded"), java.util.Set.of(a.get().outcome(), b.get().outcome()));
        }
        assertEquals(2, quota.snapshot().daily().reserved());
    }
    @Test void finalizationIsFencedAndSuccessfulReplaySurvivesHistoryDeletion() {
        var first = quota.admit("key", "scenario");
        quota.release(first.attempt());
        var second = quota.admit("key", "scenario");
        var tx = new TransactionTemplate(manager);
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> quota.finalizeAttempt(first.attempt(), UUID.randomUUID(), "old")));
        tx.executeWithoutResult(s -> quota.finalizeAttempt(second.attempt(), UUID.randomUUID(), "snapshot"));
        assertEquals(1, quota.snapshot().daily().used());
        jdbc.update("DELETE FROM dialogue_history");
        assertEquals("snapshot", quota.admit("key", "scenario").responseSnapshot());
        assertEquals(1, quota.snapshot().daily().used());
    }
    @Test void failedReleaseIsRetriedBeforeNextAdmission() {
        JdbcTemplate flaky = spy(jdbc);
        var service = new QuotaService(flaky, manager, calendar, properties);
        var first = service.admit("key", "scenario");
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"))
            .when(flaky).update(startsWith("UPDATE dialogue_generation_request SET state='failed'"), any(Object[].class));
        service.release(first.attempt());
        doCallRealMethod().when(flaky).update(startsWith("UPDATE dialogue_generation_request SET state='failed'"), any(Object[].class));
        var second = service.admit("key", "scenario");
        assertEquals("reserved", second.outcome());
        assertNotEquals(first.attempt().token(), second.attempt().token());
        assertEquals(1, service.snapshot().daily().reserved());
    }
    @Test void dailyWeeklyAndBothExhaustionUseLaterReset() {
        var p = calendar.current();
        for (int i = 0; i < 10; i++) {
            Instant day = i < 2 ? p.dailyStart() : p.dailyStart().minusSeconds(86400);
            jdbc.update("INSERT INTO dialogue_generation_request (id, scenario, reserved_at, daily_period_start, weekly_period_start, attempt_token, state) VALUES (?, 's', ?, ?, ?, ?, 'succeeded')",
                UUID.randomUUID().toString(), p.capturedAt().atOffset(ZoneOffset.UTC), day.atOffset(ZoneOffset.UTC), p.weeklyStart().atOffset(ZoneOffset.UTC), UUID.randomUUID().toString());
        }
        var both = quota.snapshot();
        assertEquals(java.util.List.of("daily", "weekly"), both.exhaustedPeriods());
        assertEquals(p.weeklyReset(), both.nextAvailableAt());
        jdbc.update("DELETE FROM dialogue_generation_request WHERE daily_period_start=?", p.dailyStart().atOffset(ZoneOffset.UTC));
        assertEquals(java.util.List.of(), quota.snapshot().exhaustedPeriods());
        for (int i = 0; i < 2; i++) quota.admit(null, "new");
        assertEquals(java.util.List.of("daily", "weekly"), quota.snapshot().exhaustedPeriods());
        jdbc.update("DELETE FROM dialogue_generation_request WHERE state='reserved'");
        jdbc.update("UPDATE dialogue_generation_request SET weekly_period_start=?", p.weeklyStart().minusSeconds(604800).atOffset(ZoneOffset.UTC));
        quota.admit(null, "one"); quota.admit(null, "two");
        assertEquals(java.util.List.of("daily"), quota.snapshot().exhaustedPeriods());
        jdbc.update("UPDATE dialogue_generation_request SET daily_period_start=? WHERE state='reserved'", p.dailyStart().minusSeconds(86400).atOffset(ZoneOffset.UTC));
        for (int i = 0; i < 8; i++) jdbc.update("INSERT INTO dialogue_generation_request (id, scenario, reserved_at, daily_period_start, weekly_period_start, attempt_token, state) VALUES (?, 's', ?, ?, ?, ?, 'succeeded')", UUID.randomUUID().toString(), p.capturedAt().atOffset(ZoneOffset.UTC), p.dailyStart().minusSeconds(86400).atOffset(ZoneOffset.UTC), p.weeklyStart().atOffset(ZoneOffset.UTC), UUID.randomUUID().toString());
        assertEquals(java.util.List.of("weekly"), quota.snapshot().exhaustedPeriods());
    }
}
