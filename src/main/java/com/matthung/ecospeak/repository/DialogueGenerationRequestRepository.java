package com.matthung.ecospeak.repository;

import com.matthung.ecospeak.entity.DialogueGenerationRequestEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DialogueGenerationRequestRepository
        extends JpaRepository<DialogueGenerationRequestEntity, String> {
    Optional<DialogueGenerationRequestEntity> findByIdempotencyKey(String key);

    long countByDailyPeriodStartAndState(OffsetDateTime start, String state);

    long countByWeeklyPeriodStartAndState(OffsetDateTime start, String state);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    "UPDATE dialogue_generation_request SET state='failed' "
                            + "WHERE id=:id AND attempt_token=:token AND state='reserved'",
            nativeQuery = true)
    int release(@Param("id") String id, @Param("token") String token);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    "UPDATE dialogue_generation_request SET state='succeeded',"
                        + " history_id=:historyId, response_snapshot=:snapshot WHERE id=:id AND"
                        + " attempt_token=:token AND state='reserved'",
            nativeQuery = true)
    int finalizeAttempt(
            @Param("id") String id,
            @Param("token") String token,
            @Param("historyId") String historyId,
            @Param("snapshot") String snapshot);
}
