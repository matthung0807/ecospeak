package com.matthung.ecospeak.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.matthung.ecospeak.service.DialogueService;

@RestController
@RequestMapping("/api/dialogs")
public class DialogueController {

    private final DialogueService dialogueService;

    public DialogueController(DialogueService dialogueService) {
        this.dialogueService = dialogueService;
    }

    @GetMapping("generate")
    public ResponseEntity<?> generate() {
        var response = dialogueService.generate();
        System.out.println("response=" + response);
        return ResponseEntity.ok(response);
    }

}
