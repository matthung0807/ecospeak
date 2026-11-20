package com.matthung.ecospeak.audio;

/** Safe public code only; never include provider payloads, text, paths, or credentials. */
public class AudioFailure extends RuntimeException {
    private final int status;
    private final String code;

    public AudioFailure(int status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
