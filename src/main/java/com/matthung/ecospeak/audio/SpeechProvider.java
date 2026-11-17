package com.matthung.ecospeak.audio;

import java.time.Duration;

public interface SpeechProvider {
    /** Complete mono PCM16LE at 24 kHz; must honor timeout/interruption and byte limit. */
    byte[] synthesize(String text, String voice, Duration timeout, int maxBytes) throws Exception;
}
