package com.matthung.ecospeak.controller;

import com.matthung.ecospeak.dto.DialogueRequest;
import com.matthung.ecospeak.dto.GenerationResponse;
import com.matthung.ecospeak.history.HistoryService;
import com.matthung.ecospeak.quota.QuotaService;
import com.matthung.ecospeak.service.DialogueService;
import com.matthung.ecospeak.service.DialogueValidation;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/dialogs")
public class DialogueController {

    private final DialogueService dialogueService;
    private final HistoryService history;
    private final QuotaService quota;
    private final JsonMapper mapper;

    public DialogueController(
            DialogueService dialogueService,
            HistoryService history,
            QuotaService quota,
            JsonMapper mapper) {
        this.dialogueService = dialogueService;
        this.history = history;
        this.quota = quota;
        this.mapper = mapper;
    }

    @GetMapping("quota")
    public ResponseEntity<?> quota() {
        try {
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(quota.snapshot());
        } catch (RuntimeException e) {
            return ResponseEntity.status(503).header("Cache-Control", "no-store").build();
        }
    }

    @PostMapping(value = "generate", consumes = "application/json")
    public ResponseEntity<?> generate(
            @RequestBody DialogueRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        String scenario = request == null ? "" : request.trimmedScenario();
        if (scenario.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a scenario.");
        }
        if (key != null && (key.isBlank() || key.length() > 128)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key");
        }
        QuotaService.Admission admission;
        try {
            admission = quota.admit(key, scenario);
        } catch (RuntimeException e) {
            return ResponseEntity.status(503).build();
        }
        switch (admission.outcome()) {
            case "replay":
                return ResponseEntity.ok(
                        mapper.readValue(admission.responseSnapshot(), GenerationResponse.class));
            case "idempotency_conflict":
                return ResponseEntity.status(409).body(Map.of("code", "idempotency_conflict"));
            case "generation_in_progress":
                return ResponseEntity.status(409)
                        .header("Retry-After", "1")
                        .body(Map.of("code", "generation_in_progress"));
            case "dialogue_quota_exceeded":
                return ResponseEntity.status(429)
                        .header("Cache-Control", "no-store")
                        .header("Retry-After", Long.toString(quota.retryAfter(admission.quota())))
                        .body(
                                Map.of(
                                        "code",
                                        "dialogue_quota_exceeded",
                                        "quota",
                                        admission.quota()));
            default:
                break;
        }
        try {
            var response = dialogueService.generate(scenario);
            DialogueValidation.requireComplete(response);
            UUID id =
                    history.save(
                            scenario,
                            response,
                            Instant.now(),
                            saved ->
                                    quota.finalizeAttempt(
                                            admission.attempt(),
                                            saved,
                                            mapper.writeValueAsString(
                                                    GenerationResponse.of(response, saved))));
            return ResponseEntity.ok(GenerationResponse.of(response, id));
        } catch (RuntimeException exception) {
            quota.release(admission.attempt());
            throw exception;
        }
    }
}
