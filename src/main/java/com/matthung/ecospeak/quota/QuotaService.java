package com.matthung.ecospeak.quota;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class QuotaService {
    private final QuotaTransaction transaction;
    private final QuotaCalendar calendar;
    private final Set<Attempt> cleanup = ConcurrentHashMap.newKeySet();

    public QuotaService(QuotaTransaction transaction, QuotaCalendar calendar) {
        this.transaction = transaction;
        this.calendar = calendar;
    }

    public Admission admit(String key, String scenario) {
        var pendingCleanup = Set.copyOf(cleanup);
        var result = transaction.admit(key, scenario, pendingCleanup);
        cleanup.removeAll(pendingCleanup);
        return result;
    }

    public Snapshot snapshot() {
        var pendingCleanup = Set.copyOf(cleanup);
        var result = transaction.snapshot(pendingCleanup);
        cleanup.removeAll(pendingCleanup);
        return result;
    }

    public void finalizeAttempt(Attempt attempt, UUID historyId, String snapshot) {
        transaction.finalizeAttempt(attempt, historyId, snapshot);
    }

    public void release(Attempt attempt) {
        try {
            transaction.release(attempt);
            cleanup.remove(attempt);
        } catch (RuntimeException failure) {
            cleanup.add(attempt);
        }
    }

    public long retryAfter(Snapshot snapshot) {
        return Math.max(
                1,
                (Duration.between(calendar.current().capturedAt(), snapshot.nextAvailableAt())
                        .toMillis()
                        + 999)
                        / 1000);
    }

    public record Attempt(String id, String token) {
    }

    public record Admission(
            String outcome, Attempt attempt, String responseSnapshot, Snapshot quota) {
    }

    public record Period(int limit, int used, int reserved, int remaining, Instant resetsAt) {
    }

    public record Snapshot(
            String timezone,
            Period daily,
            Period weekly,
            Instant nextAvailableAt,
            List<String> exhaustedPeriods) {
    }
}
