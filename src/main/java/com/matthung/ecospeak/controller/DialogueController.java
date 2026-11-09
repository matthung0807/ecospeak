package com.matthung.ecospeak.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.matthung.ecospeak.dto.DialogueRequest;
import com.matthung.ecospeak.service.DialogueService;

@RestController
@RequestMapping("/api/dialogs")
public class DialogueController {

    private final DialogueService dialogueService;

    public DialogueController(DialogueService dialogueService) {
        this.dialogueService = dialogueService;
    }

    @PostMapping(value = "generate", consumes = "application/json")
    public ResponseEntity<?> generate(@RequestBody DialogueRequest request) {
        String scenario = request.trimmedScenario();
        if (scenario.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a scenario.");
        }
        var response = dialogueService.generate(scenario);
        System.out.println("response=" + response);
        return ResponseEntity.ok(response);
    }

}
