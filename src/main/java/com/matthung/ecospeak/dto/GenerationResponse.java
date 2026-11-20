package com.matthung.ecospeak.dto;

import java.util.List;
import java.util.UUID;

public record GenerationResponse(
        String topic,
        String targetPhrase,
        List<DialogueLine> dialogue,
        boolean historySaved,
        UUID historyId) {
    public static GenerationResponse of(DialogueResponse response, UUID id) {
        return new GenerationResponse(
                response.getTopic(),
                response.getTargetPhrase(),
                response.getDialogue(),
                id != null,
                id);
    }
}
