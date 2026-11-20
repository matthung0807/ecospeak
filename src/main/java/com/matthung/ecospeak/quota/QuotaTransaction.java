package com.matthung.ecospeak.quota;

import com.matthung.ecospeak.entity.DialogueGenerationRequestEntity;
import com.matthung.ecospeak.repository.DialogueGenerationRequestRepository;
import com.matthung.ecospeak.repository.DialogueQuotaLockRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuotaTransaction {
    private final DialogueGenerationRequestRepository requests;
    private final DialogueQuotaLockRepository locks;
    private final QuotaCalendar calendar;
    private final QuotaProperties properties;

    public QuotaTransaction(
            DialogueGenerationRequestRepository requests,
            DialogueQuotaLockRepository locks,
            QuotaCalendar calendar,
            QuotaProperties properties) {
        this.requests = requests;
        this.locks = locks;
        this.calendar = calendar;
        this.properties = properties;
    }

    private static OffsetDateTime sql(Instant time) {
        return time.atOffset(ZoneOffset.UTC);
    }

    private void cleanup(Set<QuotaService.Attempt> pending) {
        pending.forEach(attempt -> requests.release(attempt.id(), attempt.token()));
    }

    private void lock() {
        locks.lockRow().orElseThrow(() -> new IllegalStateException("Quota lock row missing"));
    }

    @Transactional
    public QuotaService.Admission admit(
            String key, String scenario, Set<QuotaService.Attempt> pending) {
        lock();
        cleanup(pending);
        var existing = key == null ? null : requests.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            if (!existing.getScenario().equals(scenario)) {
                return new QuotaService.Admission("idempotency_conflict", null, null, null);
            }
            if (existing.getState().equals("succeeded")) {
                return new QuotaService.Admission(
                        "replay", null, existing.getResponseSnapshot(), null);
            }
            if (existing.getState().equals("reserved")) {
                return new QuotaService.Admission("generation_in_progress", null, null, null);
            }
        }
        var periods = calendar.current();
        var snapshot = snapshotLocked(periods);
        if (snapshot.nextAvailableAt() != null) {
            return new QuotaService.Admission("dialogue_quota_exceeded", null, null, snapshot);
        }
        var row = existing == null ? new DialogueGenerationRequestEntity() : existing;
        if (existing == null) {
            row.setId(UUID.randomUUID().toString());
            row.setIdempotencyKey(key);
            row.setScenario(scenario);
        }
        String token = UUID.randomUUID().toString();
        row.setReservedAt(sql(periods.capturedAt()));
        row.setDailyPeriodStart(sql(periods.dailyStart()));
        row.setWeeklyPeriodStart(sql(periods.weeklyStart()));
        row.setAttemptToken(token);
        row.setState("reserved");
        row.setHistoryId(null);
        row.setResponseSnapshot(null);
        requests.save(row);
        return new QuotaService.Admission(
                "reserved", new QuotaService.Attempt(row.getId(), token), null, null);
    }

    @Transactional
    public QuotaService.Snapshot snapshot(Set<QuotaService.Attempt> pending) {
        lock();
        cleanup(pending);
        return snapshotLocked(calendar.current());
    }

    private QuotaService.Snapshot snapshotLocked(QuotaCalendar.Periods p) {
        var daily = period(p.dailyStart(), p.dailyReset(), properties.getDailyLimit(), true);
        var weekly = period(p.weeklyStart(), p.weeklyReset(), properties.getWeeklyLimit(), false);
        Instant next = daily.remaining() == 0 ? daily.resetsAt() : null;
        if (weekly.remaining() == 0 && (next == null || weekly.resetsAt().isAfter(next))) {
            next = weekly.resetsAt();
        }
        return new QuotaService.Snapshot(
                properties.getTimezone(),
                daily,
                weekly,
                next,
                Stream.of(
                                daily.remaining() == 0 ? "daily" : null,
                                weekly.remaining() == 0 ? "weekly" : null)
                        .filter(Objects::nonNull)
                        .toList());
    }

    private QuotaService.Period period(Instant start, Instant reset, int limit, boolean daily) {
        int used =
                Math.toIntExact(
                        daily
                                ? requests.countByDailyPeriodStartAndState(sql(start), "succeeded")
                                : requests.countByWeeklyPeriodStartAndState(
                                        sql(start), "succeeded"));
        int reserved =
                Math.toIntExact(
                        daily
                                ? requests.countByDailyPeriodStartAndState(sql(start), "reserved")
                                : requests.countByWeeklyPeriodStartAndState(
                                        sql(start), "reserved"));
        return new QuotaService.Period(
                limit, used, reserved, Math.max(0, limit - used - reserved), reset);
    }

    @Transactional
    public void finalizeAttempt(QuotaService.Attempt attempt, UUID historyId, String snapshot) {
        lock();
        if (requests.finalizeAttempt(attempt.id(), attempt.token(), historyId.toString(), snapshot)
                != 1) {
            throw new IllegalStateException("Reservation no longer active");
        }
    }

    @Transactional
    public void release(QuotaService.Attempt attempt) {
        lock();
        requests.release(attempt.id(), attempt.token());
    }
}
