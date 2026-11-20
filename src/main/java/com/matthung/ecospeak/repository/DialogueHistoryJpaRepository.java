package com.matthung.ecospeak.repository;

import com.matthung.ecospeak.entity.DialogueHistoryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DialogueHistoryJpaRepository extends JpaRepository<DialogueHistoryEntity, String> {
    List<DialogueHistoryEntity> findAllByOrderByCreatedTimeDescIdDesc();
}
