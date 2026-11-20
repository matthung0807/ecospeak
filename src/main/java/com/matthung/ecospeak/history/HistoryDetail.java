package com.matthung.ecospeak.history;

import com.matthung.ecospeak.dto.DialogueLine;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HistoryDetail(
        UUID id,
        Instant createdTime,
        String topic,
        String targetPhrase,
        String scenario,
        List<DialogueLine> dialogue) {
}
