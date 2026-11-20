package com.matthung.ecospeak.audio;

import com.matthung.ecospeak.dto.DialogueLine;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public final class WavAssembler {
    public static final int RATE = 24000;
    private static final byte[] PAUSE = new byte[RATE * 2 * 300 / 1000];

    private WavAssembler() {
    }

    public static byte[] assemble(
            List<DialogueLine> lines,
            SpeechProvider provider,
            AudioProperties config,
            long deadlineNanos)
            throws Exception {
        var pcm = new ByteArrayOutputStream();
        for (var line : lines) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) {
                throw new AudioFailure(504, "audio_timeout");
            }
            if (pcm.size() > 0) {
                append(pcm, PAUSE, config.getMaxOutputBytes() - 44);
            }
            var timeout = Duration.ofNanos(Math.min(remaining, config.getRequestTimeout().toNanos()));
            byte[] turn = provider.synthesize(
                    line.getText(),
                    "A".equals(line.getSpeaker()) ? config.getVoiceA() : config.getVoiceB(),
                    timeout,
                    config.getMaxOutputBytes() - 44 - pcm.size());
            if (turn == null || turn.length == 0 || turn.length % 2 != 0) {
                throw new AudioFailure(502, "invalid_audio");
            }
            append(pcm, turn, config.getMaxOutputBytes() - 44);
        }
        return wrap(pcm.toByteArray());
    }

    private static void append(ByteArrayOutputStream target, byte[] bytes, int limit) {
        if ((long) target.size() + bytes.length > limit) {
            throw new AudioFailure(502, "audio_too_large");
        }
        target.writeBytes(bytes);
    }

    public static byte[] wrap(byte[] pcm) {
        if (pcm.length == 0 || pcm.length % 2 != 0) {
            throw new AudioFailure(502, "invalid_audio");
        }
        var out = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length);
        out.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        out.putShort((short) 1).putShort((short) 1).putInt(RATE).putInt(RATE * 2);
        out.putShort((short) 2).putShort((short) 16);
        out.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);
        return out.array();
    }
}
