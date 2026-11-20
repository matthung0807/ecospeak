package com.matthung.ecospeak.repository;

import com.matthung.ecospeak.entity.DialogueQuotaLockEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DialogueQuotaLockRepository
        extends JpaRepository<DialogueQuotaLockEntity, Integer> {
    @Query(value = "SELECT id FROM dialogue_quota_lock WHERE id = 1 FOR UPDATE", nativeQuery = true)
    Optional<Integer> lockRow();
}
