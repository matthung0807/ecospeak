# Implementation verification

## Delivered behavior

After saved dialogue text renders, the practice page automatically requests preparation. Play is disabled and gray until ready; readiness does not autoplay. Both dialogue views share Play/Pause/resume/replay and separate Retry audio. Unsaved/expired results cannot prepare or retrieve audio. Text generation and history remain compatible.

The backend uses OpenAI Realtime over a JDK server WebSocket with `gpt-realtime-2.1-mini`, `marin` and `cedar`, collecting 24 kHz mono PCM16LE and assembling one UUID WAV. Jobs share work per UUID and enforce queue, input, output, per-turn timeout, and overall deadline limits. Committed history eviction cancels jobs and removes cached files; rollback preserves them. Startup and periodic reconciliation handle orphans and failed cleanup.

## Checks performed

- `mvn -q test`: 46 backend/API tests passed before the opt-in live test was added. Provider calls in routine tests use stubs. Coverage includes configuration binding, provider completion events, PCM/WAV assembly, job sharing, cache reuse, deadlines, capacity, HTTP contracts, startup cleanup, eviction, rollback, cancellation, and existing text/history behavior.
- `ECOSPEAK_LIVE_AUDIO=true` with `mvn -q '-Dtest=LiveAudioApiTests' test`: one live API integration test passed. Text generation was stubbed; both speech turns used the real configured OpenAI model and distinct configured voices.
- Live UUID: `e04685e2-1a22-4b72-b1c1-5ab209f46c6d`.
- Generated file: `target/live-audio-check/e04685e2-1a22-4b72-b1c1-5ab209f46c6d.wav`, 175,244 bytes.
- WAV header and nonempty data validated: 24 kHz, mono, PCM16LE. HTTP retrieval returned 200 and identical bytes with `audio/wav`. Repeated preparation returned ready from cache.
- `node --check src/main/resources/static/dialogue-audio.js`: passed. UI behavior was reviewed in source only.
- `git check-ignore data/audio/00000000-0000-0000-0000-000000000000.wav`: confirms runtime files are ignored.

## User-owned acceptance and limitations

No browser acceptance testing, playback, listening, transcription, pronunciation check, or audio quality assessment was performed. Follow the manual checklist in tasks.md or docs/dialogue-audio.md. Generative audio can deviate from requested wording; the user verifies rendition and voice quality manually. Symbolic-link creation coverage is conditional on OS permission; path guards are implemented regardless.

The browser triggers preparation after receiving and rendering a saved generation. Closing it before that POST can leave missing audio; the detail page offers Retry audio. Direct text API callers must explicitly prepare audio. Cache and jobs are single-process and tied to volatile history. The live test's history has been shut down; its generated file is evidence, not an application history record.
