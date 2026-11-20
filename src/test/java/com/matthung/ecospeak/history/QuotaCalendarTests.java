package com.matthung.ecospeak.history;

import static org.junit.jupiter.api.Assertions.*;
import com.matthung.ecospeak.quota.QuotaCalendar;
import com.matthung.ecospeak.quota.QuotaProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

class QuotaCalendarTests {
    @Test void defaultsAndCalendarBoundaries() {
        var properties = new QuotaProperties();
        properties.validate();
        assertEquals(2, properties.getDailyLimit()); assertEquals(10, properties.getWeeklyLimit());
        var calendar = new QuotaCalendar(properties, Clock.fixed(Instant.parse("2026-11-15T15:59:59Z"), ZoneOffset.UTC));
        var before = calendar.current();
        var after = calendar.at(Instant.parse("2026-11-15T16:00:00Z"));
        assertEquals(Instant.parse("2026-11-15T16:00:00Z"), before.dailyReset());
        assertEquals(before.dailyReset(), before.weeklyReset());
        assertEquals(before.dailyReset(), after.dailyStart());
        assertEquals(before.weeklyReset(), after.weeklyStart());
        assertNotEquals(before.dailyStart(), after.dailyStart());
    }

    @Test void alternateZoneUsesCalendarArithmetic() {
        var properties = new QuotaProperties(); properties.setTimezone("America/New_York");
        properties.setDailyLimit(3); properties.setWeeklyLimit(12); properties.validate();
        var periods = new QuotaCalendar(properties, Clock.systemUTC()).at(Instant.parse("2026-03-08T12:00:00Z"));
        assertEquals(Instant.parse("2026-03-08T05:00:00Z"), periods.dailyStart());
        assertEquals(Instant.parse("2026-03-09T04:00:00Z"), periods.dailyReset());
    }

    @Test void invalidSettingsFailAtStartup() {
        var runner = new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);
        runner.withPropertyValues("ecospeak.quota.daily-limit=0").run(context -> assertNotNull(context.getStartupFailure()));
        runner.withPropertyValues("ecospeak.quota.weekly-limit=-1").run(context -> assertNotNull(context.getStartupFailure()));
        runner.withPropertyValues("ecospeak.quota.timezone=invalid-zone").run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(QuotaProperties.class)
    static class TestConfiguration {}
}
