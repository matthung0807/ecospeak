package com.matthung.ecospeak.dto;

import java.util.List;

public record DialogueResponse(String topic,
        String targetPhrase,
        List<DialogueLine> dialogue) {

}
