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
import com.matthung.ecospeak.service.DialogueValidation;
import com.matthung.ecospeak.dto.GenerationResponse;
import com.matthung.ecospeak.history.HistoryService;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/dialogs")
public class DialogueController {

    private final DialogueService dialogueService;
    private final HistoryService history;
    private static final Logger log = LoggerFactory.getLogger(DialogueController.class);

    public DialogueController(DialogueService dialogueService, HistoryService history) {
        this.dialogueService = dialogueService;
        this.history = history;
    }

    @PostMapping(value = "generate", consumes = "application/json")
    public ResponseEntity<?> generate(@RequestBody DialogueRequest request) {
        String scenario = request == null ? "" : request.trimmedScenario();
        if (scenario.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a scenario.");
        }
        var response = dialogueService.generate(scenario);
        DialogueValidation.requireComplete(response);
        Instant time = Instant.now();
        UUID id = null;
        try {
            id = history.save(scenario, response, time);
        } catch (RuntimeException exception) {
            // Do not log generated dialogue or exception messages that may contain SQL values.
            log.warn("Generated dialogue could not be saved to history ({})", exception.getClass().getSimpleName());
        }
        return ResponseEntity.ok(GenerationResponse.of(response, id));
    }

}
