package com.matthung.ecospeak.quota;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class QuotaService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final QuotaCalendar calendar;
    private final QuotaProperties properties;
    private final Set<Attempt> cleanup = ConcurrentHashMap.newKeySet();

    public QuotaService(JdbcTemplate jdbc, PlatformTransactionManager manager, QuotaCalendar calendar, QuotaProperties properties) {
        this.jdbc = jdbc; this.tx = new TransactionTemplate(manager); this.calendar = calendar; this.properties = properties;
    }
    private void lock() { jdbc.queryForObject("SELECT id FROM dialogue_quota_lock WHERE id=1 FOR UPDATE", Integer.class); }
    private static java.time.OffsetDateTime sql(Instant time) { return time.atOffset(ZoneOffset.UTC); }
    public Admission admit(String key, String scenario) {
        var pendingCleanup = Set.copyOf(cleanup);
        var result = tx.execute(status -> {
            lock(); pendingCleanup.forEach(this::releaseLocked);
            if (key != null) {
                var existing = jdbc.query("SELECT id, scenario, state, response_snapshot FROM dialogue_generation_request WHERE idempotency_key=?", (rs, n) -> new Existing(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), key);
                if (!existing.isEmpty()) {
                    var row = existing.getFirst();
                    if (!row.scenario().equals(scenario)) return new Admission("idempotency_conflict", null, null, null);
                    if (row.state().equals("succeeded")) return new Admission("replay", null, row.snapshot(), null);
                    if (row.state().equals("reserved")) return new Admission("generation_in_progress", null, null, null);
                }
            }
            var periods = calendar.current(); var snapshot = snapshotLocked(periods);
            if (snapshot.nextAvailableAt() != null) return new Admission("dialogue_quota_exceeded", null, null, snapshot);
            String id = UUID.randomUUID().toString(), token = UUID.randomUUID().toString();
            if (key != null) {
                var existing = jdbc.queryForList("SELECT id FROM dialogue_generation_request WHERE idempotency_key=?", String.class, key);
                if (!existing.isEmpty()) {
                    id = existing.getFirst();
                    jdbc.update("UPDATE dialogue_generation_request SET reserved_at=?, daily_period_start=?, weekly_period_start=?, attempt_token=?, state='reserved', history_id=NULL, response_snapshot=NULL WHERE id=?", sql(periods.capturedAt()), sql(periods.dailyStart()), sql(periods.weeklyStart()), token, id);
                    return new Admission("reserved", new Attempt(id, token), null, null);
                }
            }
            jdbc.update("INSERT INTO dialogue_generation_request (id,idempotency_key,scenario,reserved_at,daily_period_start,weekly_period_start,attempt_token,state) VALUES (?,?,?,?,?,?,?,'reserved')", id, key, scenario, sql(periods.capturedAt()), sql(periods.dailyStart()), sql(periods.weeklyStart()), token);
            return new Admission("reserved", new Attempt(id, token), null, null);
        });
        cleanup.removeAll(pendingCleanup); return result;
    }
    public Snapshot snapshot() {
        var pendingCleanup = Set.copyOf(cleanup);
        var result = tx.execute(status -> { lock(); pendingCleanup.forEach(this::releaseLocked); return snapshotLocked(calendar.current()); });
        cleanup.removeAll(pendingCleanup); return result;
    }
    private Snapshot snapshotLocked(QuotaCalendar.Periods p) {
        var daily = period("daily_period_start", p.dailyStart(), p.dailyReset(), properties.getDailyLimit());
        var weekly = period("weekly_period_start", p.weeklyStart(), p.weeklyReset(), properties.getWeeklyLimit());
        Instant next = daily.remaining() == 0 ? daily.resetsAt() : null;
        if (weekly.remaining() == 0 && (next == null || weekly.resetsAt().isAfter(next))) next = weekly.resetsAt();
        return new Snapshot(properties.getTimezone(), daily, weekly, next,
                java.util.stream.Stream.of(daily.remaining() == 0 ? "daily" : null, weekly.remaining() == 0 ? "weekly" : null).filter(java.util.Objects::nonNull).toList());
    }
    private Period period(String column, Instant start, Instant reset, int limit) {
        var counts = jdbc.queryForMap("SELECT COALESCE(SUM(CASE WHEN state='succeeded' THEN 1 ELSE 0 END),0) AS used, COALESCE(SUM(CASE WHEN state='reserved' THEN 1 ELSE 0 END),0) AS reserved FROM dialogue_generation_request WHERE " + column + "=?", sql(start));
        int used = ((Number) counts.get("USED")).intValue(), reserved = ((Number) counts.get("RESERVED")).intValue();
        return new Period(limit, used, reserved, Math.max(0, limit-used-reserved), reset);
    }
    public void finalizeAttempt(Attempt attempt, UUID historyId, String snapshot) {
        lock();
        if (jdbc.update("UPDATE dialogue_generation_request SET state='succeeded', history_id=?, response_snapshot=? WHERE id=? AND attempt_token=? AND state='reserved'", historyId.toString(), snapshot, attempt.id(), attempt.token()) != 1)
            throw new IllegalStateException("Reservation no longer active");
    }
    public void release(Attempt attempt) {
        try { tx.executeWithoutResult(status -> { lock(); releaseLocked(attempt); }); cleanup.remove(attempt); }
        catch (RuntimeException failure) { cleanup.add(attempt); }
    }
    private void releaseLocked(Attempt attempt) { jdbc.update("UPDATE dialogue_generation_request SET state='failed' WHERE id=? AND attempt_token=? AND state='reserved'", attempt.id(), attempt.token()); }
    public long retryAfter(Snapshot snapshot) { return Math.max(1, (Duration.between(calendar.current().capturedAt(), snapshot.nextAvailableAt()).toMillis() + 999) / 1000); }
    private record Existing(String id, String scenario, String state, String snapshot) {}
    public record Attempt(String id, String token) {}
    public record Admission(String outcome, Attempt attempt, String responseSnapshot, Snapshot quota) {}
    public record Period(int limit, int used, int reserved, int remaining, Instant resetsAt) {}
    public record Snapshot(String timezone, Period daily, Period weekly, Instant nextAvailableAt, List<String> exhaustedPeriods) {}
}
