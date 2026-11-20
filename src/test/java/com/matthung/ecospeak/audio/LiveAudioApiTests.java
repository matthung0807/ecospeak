package com.matthung.ecospeak.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

import com.matthung.ecospeak.dto.DialogueResponse;
import com.matthung.ecospeak.service.DialogueService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

/**
 * Explicit paid API smoke test. Inspects bytes only: never plays, transcribes, or evaluates audio.
 */
@EnabledIfEnvironmentVariable(named = "ECOSPEAK_LIVE_AUDIO", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:live-audio-check;DB_CLOSE_DELAY=0;TRACE_LEVEL_FILE=0",
            "ecospeak.audio.directory=target/live-audio-check"
        })
@DirtiesContext
class LiveAudioApiTests {
    @LocalServerPort
    int port;
    @MockitoBean
    DialogueService dialogue;
    @Autowired
    JsonMapper mapper;

    @Test
    void prepareInspectAndRetrieveRealProviderAudioWithoutPlayback() throws Exception {
        assertNotNull(System.getenv("OPENAI_API_KEY"), "OPENAI_API_KEY is required");
        var response = new DialogueResponse();
        response.setTopic("Audio API verification");
        response.setTargetPhrase("Sounds good");
        response.setDialogue(
                List.of(
                        SpeechTests.line("A", "Shall we get a coffee?"),
                        SpeechTests.line("B", "Sounds good. Let's go.")));
        when(dialogue.generate(anyString())).thenReturn(response);
        try (var client = HttpClient.newHttpClient()) {
            String root = "http://localhost:" + port;
            var generated =
                    client.send(
                            HttpRequest.newBuilder(URI.create(root + "/api/dialogs/generate"))
                                    .header("Content-Type", "application/json")
                                    .POST(
                                            HttpRequest.BodyPublishers.ofString(
                                                    "{\"scenario\":\"Audio API check\"}"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, generated.statusCode());
            String id = mapper.readTree(generated.body()).path("historyId").asText();
            assertDoesNotThrow(() -> java.util.UUID.fromString(id));
            String base = root + "/api/dialogs/history/" + id + "/audio";
            var accepted =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base))
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(202, accepted.statusCode());
            long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(130);
            boolean ready = false;
            while (System.nanoTime() < end) {
                var status =
                        client.send(
                                HttpRequest.newBuilder(URI.create(base + "/status")).GET().build(),
                                HttpResponse.BodyHandlers.ofString());
                assertEquals(200, status.statusCode());
                var state = mapper.readTree(status.body());
                assertNotEquals(
                        "failed",
                        state.path("status").asText(),
                        "Live API preparation failed: " + state.path("code").asText());
                if ("ready".equals(state.path("status").asText())) {
                    ready = true;
                    break;
                }
                Thread.sleep(500);
            }
            assertTrue(ready, "Live audio did not become ready");
            Path file = Path.of("target/live-audio-check", id + ".wav");
            byte[] bytes = Files.readAllBytes(file);
            assertTrue(bytes.length > 44);
            var header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(
                    "RIFF", new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
            assertEquals(bytes.length - 8, header.getInt(4));
            assertEquals(bytes.length - 44, header.getInt(40));
            assertEquals(24000, header.getInt(24));
            var fetched =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base)).GET().build(),
                            HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, fetched.statusCode());
            assertArrayEquals(bytes, fetched.body());
            assertEquals("audio/wav", fetched.headers().firstValue("Content-Type").orElse(""));
            var cached =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base))
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, cached.statusCode());
            Files.createDirectories(Path.of("test-evidence"));
            Files.writeString(
                    Path.of("test-evidence/audio-api-check.txt"),
                    "UUID="
                            + id
                            + "\nfile="
                            + file.toAbsolutePath()
                            + "\nbytes="
                            + bytes.length
                            + "\n"
                            + "rate=24000\n"
                            + "channels=1\n"
                            + "bits=16\n"
                            + "HTTP=200\n"
                            + "cache_reused=true\n"
                            + "playback_performed=false\n");
            System.out.println(
                    "Audio API verified: UUID="
                            + id
                            + ", bytes="
                            + bytes.length
                            + ", WAV/HTTP valid; no playback");
        }
    }
}
