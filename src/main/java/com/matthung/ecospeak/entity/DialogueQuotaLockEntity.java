package com.matthung.ecospeak.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dialogue_quota_lock")
public class DialogueQuotaLockEntity {
    @Id private Integer id;
}
