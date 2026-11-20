package com.matthung.ecospeak.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "dialogue_history")
@Getter
@Setter
public class DialogueHistoryEntity {
    @Id private String id;

    @Column(name = "created_time")
    private OffsetDateTime createdTime;

    private String topic;

    @Column(name = "target_phrase")
    private String targetPhrase;

    private String scenario;
    private String dialogues;
}
