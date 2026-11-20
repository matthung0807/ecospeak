package com.matthung.ecospeak.controller;

import com.matthung.ecospeak.history.HistoryService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dialogs/history")
public class HistoryController {
    private final HistoryService history;

    public HistoryController(HistoryService history) {
        this.history = history;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.list());
        } catch (RuntimeException exception) {
            return ResponseEntity.internalServerError()
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable String id) {
        if (!id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();
        }
        try {
            var record = history.find(UUID.fromString(id));
            return record.<ResponseEntity<?>>map(
                            value ->
                                    ResponseEntity.ok()
                                            .cacheControl(CacheControl.noStore())
                                            .body(value))
                    .orElseGet(
                            () ->
                                    ResponseEntity.status(HttpStatus.NOT_FOUND)
                                            .cacheControl(CacheControl.noStore())
                                            .build());
        } catch (RuntimeException exception) {
            return ResponseEntity.internalServerError()
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
    }
}
