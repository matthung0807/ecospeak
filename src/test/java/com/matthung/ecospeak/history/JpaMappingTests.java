package com.matthung.ecospeak.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.matthung.ecospeak.entity.DialogueGenerationRequestEntity;
import com.matthung.ecospeak.entity.DialogueHistoryEntity;
import com.matthung.ecospeak.repository.DialogueGenerationRequestRepository;
import com.matthung.ecospeak.repository.DialogueHistoryJpaRepository;
import com.matthung.ecospeak.repository.DialogueQuotaLockRepository;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:jpa-mapping;DB_CLOSE_DELAY=0")
class JpaMappingTests {
    @Autowired DialogueHistoryJpaRepository history;

    @Autowired DialogueGenerationRequestRepository requests;

    @Autowired DialogueQuotaLockRepository locks;

    @Autowired EntityManager entityManager;

    @Test
    @Transactional
    void scalarFieldsRoundTrip() {
        String id = UUID.randomUUID().toString();
        OffsetDateTime time = OffsetDateTime.parse("2026-11-20T01:02:03.123456789Z");
        var row = new DialogueHistoryEntity();
        row.setId(id);
        row.setCreatedTime(time);
        row.setTopic("topic");
        row.setTargetPhrase("phrase");
        row.setScenario("scenario");
        String text = "dialogue".repeat(100);
        row.setDialogues(text);
        history.saveAndFlush(row);
        entityManager.clear();
        var saved = history.findById(id).orElseThrow();
        assertEquals(time, saved.getCreatedTime());
        assertEquals(text, saved.getDialogues());

        var request = new DialogueGenerationRequestEntity();
        request.setId(UUID.randomUUID().toString());
        request.setScenario("scenario");
        request.setReservedAt(time);
        request.setDailyPeriodStart(time);
        request.setWeeklyPeriodStart(time);
        request.setAttemptToken(UUID.randomUUID().toString());
        request.setState("reserved");
        requests.saveAndFlush(request);
        entityManager.clear();
        var savedRequest = requests.findById(request.getId()).orElseThrow();
        assertEquals(time, savedRequest.getReservedAt());
        assertNull(savedRequest.getIdempotencyKey());
        assertNull(savedRequest.getResponseSnapshot());
        assertTrue(locks.findById(1).isPresent());
    }
}
