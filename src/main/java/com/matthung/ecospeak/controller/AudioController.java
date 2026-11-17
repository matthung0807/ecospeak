package com.matthung.ecospeak.controller;

import java.util.UUID;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.matthung.ecospeak.audio.AudioService;
import com.matthung.ecospeak.audio.AudioFailure;

@RestController
@RequestMapping("/api/dialogs/history/{id}/audio")
public class AudioController {
    private final AudioService audio;
    public AudioController(AudioService audio) { this.audio = audio; }
    private UUID uuid(String id) {
        if (!id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new AudioFailure(400, "invalid_uuid");
        return UUID.fromString(id);
    }
    @PostMapping public ResponseEntity<?> prepare(@PathVariable String id) {
        var result = audio.prepare(uuid(id));
        return ResponseEntity.status("ready".equals(result.status()) ? 200 : 202).cacheControl(CacheControl.noStore()).body(result);
    }
    @GetMapping("/status") public ResponseEntity<?> status(@PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(audio.status(uuid(id)));
    }
    @GetMapping public ResponseEntity<?> file(@PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("audio/wav"))
                .body(audio.read(uuid(id)));
    }
    @ExceptionHandler(AudioFailure.class) public ResponseEntity<?> failure(AudioFailure failure) {
        var response = ResponseEntity.status(failure.status()).cacheControl(CacheControl.noStore());
        if (failure.status() == 503) response.header("Retry-After", "5");
        return response.body(Map.of("code", failure.code()));
    }
    @ExceptionHandler(Exception.class) public ResponseEntity<?> unexpected(Exception failure) {
        return ResponseEntity.internalServerError().cacheControl(CacheControl.noStore()).body(Map.of("code", "audio_unavailable"));
    }
}
