package com.matthung.ecospeak.audio;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import lombok.Data;

@Data
@Component
@ConfigurationProperties("ecospeak.audio")
public class AudioProperties {
    private Path directory = Path.of("data", "audio");
    private String model = "gpt-realtime-2.1-mini";
    private String voiceA = "marin";
    private String voiceB = "cedar";
    private int workers = 2;
    private int pending = 10;
    private Duration deadline = Duration.ofSeconds(120);
    private Duration requestTimeout = Duration.ofSeconds(40);
    private int maxInputCharacters = 8000;
    private int maxOutputBytes = 20 * 1024 * 1024;

    @PostConstruct
    public void validate() {
        if (directory == null || model == null || !model.matches("[a-zA-Z0-9._-]+")
                || voiceA == null || voiceA.isBlank() || voiceB == null || voiceB.isBlank()
                || voiceA.equals(voiceB) || workers < 1 || pending < 1
                || deadline == null || deadline.isNegative() || deadline.isZero()
                || requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()
                || maxInputCharacters < 1 || maxOutputBytes < 14446) {
            throw new IllegalArgumentException("Invalid audio configuration");
        }
    }
}
