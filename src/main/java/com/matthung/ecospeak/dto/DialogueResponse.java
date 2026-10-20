package com.matthung.ecospeak.dto;

import java.util.List;

import lombok.Data;

@Data 
public class DialogueResponse {
    String topic;
    String targetPhrase;
    List<DialogueLine> dialogue;
}
