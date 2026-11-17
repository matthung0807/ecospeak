package com.matthung.ecospeak.audio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import com.matthung.ecospeak.dto.DialogueLine;
import tools.jackson.databind.json.JsonMapper;

class SpeechTests {
    static DialogueLine line(String speaker, String text) {
        var line = new DialogueLine(); line.setSpeaker(speaker); line.setText(text); return line;
    }
    @Test void configurationBindsAndRejectsInvalidSettings() {
        var source = new MapConfigurationPropertySource(java.util.Map.of("ecospeak.audio.workers", "3", "ecospeak.audio.deadline", "25s"));
        var config = new Binder(source).bind("ecospeak.audio", AudioProperties.class).get();
        config.validate(); assertEquals(3, config.getWorkers()); assertEquals(25, config.getDeadline().toSeconds());
        config.setVoiceB(config.getVoiceA()); assertThrows(IllegalArgumentException.class, config::validate);
        config.setVoiceB("cedar"); config.setWorkers(0); assertThrows(IllegalArgumentException.class, config::validate);
    }
    @Test void orderedVoicesPauseAndOneWavHeader() throws Exception {
        var calls = new ArrayList<String>();
        SpeechProvider provider = (text, voice, timeout, limit) -> { calls.add(voice + ":" + text); return new byte[]{1, 2}; };
        byte[] wav = WavAssembler.assemble(List.of(line("A", "First"), line("B", "Second"), line("A", "Third")),
                provider, new AudioProperties(), System.nanoTime() + 1_000_000_000L);
        assertEquals(List.of("marin:First", "cedar:Second", "marin:Third"), calls);
        var bytes = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals("RIFF", new String(wav, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(wav.length - 8, bytes.getInt(4)); assertEquals(24000, bytes.getInt(24));
        assertEquals(6 + 2 * 14400, bytes.getInt(40)); assertEquals(1, wav[44]); assertEquals(0, wav[46]);
    }
    @Test void incompleteOversizeAndDeadlineRejected() {
        var lines = List.of(line("A", "Hi")); var cfg = new AudioProperties();
        assertThrows(AudioFailure.class, () -> WavAssembler.assemble(lines, (t,v,d,l) -> new byte[0], cfg, Long.MAX_VALUE));
        assertThrows(AudioFailure.class, () -> WavAssembler.assemble(lines, (t,v,d,l) -> new byte[3], cfg, Long.MAX_VALUE));
        assertThrows(AudioFailure.class, () -> WavAssembler.assemble(lines, (t,v,d,l) -> new byte[cfg.getMaxOutputBytes()], cfg, Long.MAX_VALUE));
        assertThrows(AudioFailure.class, () -> WavAssembler.assemble(lines, (t,v,d,l) -> new byte[2], cfg, System.nanoTime() - 1));
    }
    @Test void realtimeProtocolRequiresCompleteAudioAndDoesNotLeakProviderError() {
        var ws = mock(WebSocket.class);
        when(ws.sendText(any(), eq(true))).thenReturn(CompletableFuture.completedFuture(ws));
        var events = new OpenAiSpeechProvider.Events(JsonMapper.builder().build(), "Hello", "marin", 100);
        events.onText(ws, "{\"type\":\"session.created\"}", true);
        events.onText(ws, "{\"type\":\"session.updated\"}", true);
        verify(ws, times(2)).sendText(any(), eq(true));
        events.onText(ws, "{\"type\":\"response.output_audio.delta\",\"delta\":\"AQI=\"}", true);
        assertFalse(events.result.isDone());
        events.onText(ws, "{\"type\":\"response.output_audio.done\"}", true);
        events.onText(ws, "{\"type\":\"response.done\",\"response\":{\"status\":\"completed\"}}", true);
        assertArrayEquals(new byte[]{1,2}, events.result.join());
        var incomplete = new OpenAiSpeechProvider.Events(JsonMapper.builder().build(), "Hello", "marin", 100);
        incomplete.onText(ws, "{\"type\":\"response.done\",\"response\":{\"status\":\"incomplete\"}}", true);
        assertTrue(incomplete.result.isCompletedExceptionally());
    }
}
