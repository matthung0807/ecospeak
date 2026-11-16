package com.matthung.ecospeak.history;

import java.time.Instant;
import java.util.UUID;

public record HistorySummary(UUID id, Instant createdTime, String topic) {}
