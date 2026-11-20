package com.matthung.ecospeak.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "dialogue_generation_request")
@Getter
@Setter
public class DialogueGenerationRequestEntity {
    @Id private String id;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    private String scenario;

    @Column(name = "reserved_at")
    private OffsetDateTime reservedAt;

    @Column(name = "daily_period_start")
    private OffsetDateTime dailyPeriodStart;

    @Column(name = "weekly_period_start")
    private OffsetDateTime weeklyPeriodStart;

    @Column(name = "attempt_token")
    private String attemptToken;

    private String state;

    @Column(name = "history_id")
    private String historyId;

    @Column(name = "response_snapshot")
    private String responseSnapshot;
}
