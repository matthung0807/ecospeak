package com.matthung.ecospeak.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.matthung.ecospeak.dto.DialogueLine;
import com.matthung.ecospeak.dto.DialogueResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/dialogs")
public class DialogueController {

    @GetMapping("generate")
    public ResponseEntity<?> generate() {
        var response = new DialogueResponse(
                "Ordering Coffee",
                "I'll go with...",
                List.of(
                        new DialogueLine("A", "What are you getting?"),
                        new DialogueLine("B", "I think I'll go with the latte."),
                        new DialogueLine("A", "Good choice. I had that yesterday."),
                        new DialogueLine("B", "Nice. I'll give it a try.")));

        return ResponseEntity.ok(response);
    }

}
