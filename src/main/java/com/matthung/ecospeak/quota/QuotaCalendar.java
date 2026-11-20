package com.matthung.ecospeak.quota;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
public class QuotaCalendar {
    private final QuotaProperties properties;
    private final Clock clock;

    public QuotaCalendar(QuotaProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public Periods current() { return at(clock.instant()); }

    public Periods at(Instant instant) {
        var zone = ZoneId.of(properties.getTimezone());
        var date = instant.atZone(zone).toLocalDate();
        var monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new Periods(instant, date.atStartOfDay(zone).toInstant(),
                monday.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant(),
                monday.plusWeeks(1).atStartOfDay(zone).toInstant());
    }

    public record Periods(Instant capturedAt, Instant dailyStart, Instant weeklyStart,
                          Instant dailyReset, Instant weeklyReset) {}

    @Configuration(proxyBeanMethods = false)
    static class ClockConfiguration {
        @Bean Clock quotaClock() { return Clock.systemUTC(); }
    }
}
