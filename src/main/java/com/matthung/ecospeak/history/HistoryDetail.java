package com.matthung.ecospeak.history;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.matthung.ecospeak.dto.DialogueLine;

public record HistoryDetail(UUID id, Instant createdTime, String topic, String targetPhrase,
        String scenario, List<DialogueLine> dialogue) {}
