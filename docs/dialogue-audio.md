# Dialogue audio

The practice page automatically requests audio after a valid dialogue is saved and displayed. Text appears first. Play stays disabled until ready and never generates audio or autoplays. Retry audio is a separate action. Detail pages read status on load; missing or failed audio requires Retry audio.

## Provider

The adapter uses the JDK HTTP client's server-side WebSocket transport, avoiding a dependency on newer SDK helper APIs. Default model: `gpt-realtime-2.1-mini`; voice A: `marin`; voice B: `cedar`. Credentials come only from `OPENAI_API_KEY` in the server environment. Account/model access is verified by the opt-in live API check, not assumed from documentation.

Official API references checked during implementation:

- https://developers.openai.com/api/docs/models/gpt-realtime-2.1-mini
- https://developers.openai.com/api/docs/guides/realtime-conversations
- https://developers.openai.com/api/docs/guides/voice-websockets?voice-api=realtime

Connect to `wss://api.openai.com/v1/realtime?model=...`, wait for `session.created`, send `session.update` with audio output, PCM at 24 kHz and the chosen voice, then wait for `session.updated`. Send the saved text and `response.create`. Collect base64 PCM from `response.output_audio.delta`; require `response.output_audio.done` and successful `response.done`. Use a fresh session per turn so voices and content cannot bleed between turns. All turns are 24 kHz mono PCM16 little endian, combined with 300 ms silence and one WAV header. No microphone, transcription, or playback is involved on the server.

Every saved displayed dialogue incurs audio-generation usage even if never played. Explicit retries may regenerate all turns and incur usage again. There are no automatic paid retries. Closing the page before its preparation request can leave audio missing; Retry audio handles that case. Direct text API consumers must explicitly call preparation.

## Configuration

Spring properties (environment equivalents use uppercase and underscores) and defaults:

| Property | Default |
|---|---|
| `ecospeak.audio.directory` | `data/audio` relative to process working directory |
| `ecospeak.audio.model` | `gpt-realtime-2.1-mini` |
| `ecospeak.audio.voice-a` | `marin` |
| `ecospeak.audio.voice-b` | `cedar` |
| `ecospeak.audio.workers` | `2` |
| `ecospeak.audio.pending` | `10` |
| `ecospeak.audio.deadline` | `120s` including queue time |
| `ecospeak.audio.request-timeout` | `40s` per turn, bounded by overall deadline |
| `ecospeak.audio.max-input-characters` | `8000` total saved text characters |
| `ecospeak.audio.max-output-bytes` | `20971520` including WAV header |

Use different valid voices; both must be supported by the configured model. Invalid local settings fail startup. Provider-level invalid settings fail preparation with a safe error. No provider payload, key, generated text, or local filesystem path is sent in errors.

## API

After `POST /api/dialogs/generate` returns a valid saved `historyId`, call `POST /api/dialogs/history/<uuid>/audio` with no text body. Accepted/pending work returns 202:

```json
{"status":"generating","statusUrl":"/api/dialogs/history/<uuid>/audio/status","audioUrl":null,"code":null}
```

Poll `GET /api/dialogs/history/<uuid>/audio/status`. Status returns 200 with `not_generated`, `generating`, `ready`, or `failed`. Ready status and an already-ready preparation return:

```json
{"status":"ready","statusUrl":"/api/dialogs/history/<uuid>/audio/status","audioUrl":"/api/dialogs/history/<uuid>/audio","code":null}
```

Failures include a safe code, e.g. `{"status":"failed","statusUrl":"...","audioUrl":null,"code":"audio_generation_failed"}`. Explicit POST retries failed jobs. `GET /api/dialogs/history/<uuid>/audio` returns complete `audio/wav` bytes and `Cache-Control: no-store`. Status/retrieval never synthesize.

Malformed UUID: 400 (`invalid_uuid`); expired record: 404 (`dialogue_not_found`); absent file: 404 (`audio_not_found`); unsupported speaker or excessive input: 422; exhausted capacity or unavailable cache: 503 with `Retry-After: 5`. Unexpected errors return safe 500 responses. All audio responses use no-store caching.

## Lifetime

History remains in-memory H2 with at most ten records. Completed files are `data/audio/<uuid>.wav`; UUID temporary files are not served. Eviction cancels work and deletes audio after database commit. Rollback preserves existing audio. Failed deletions are retried by reconciliation every 30 seconds; endpoints still reject expired records. Startup cleans managed files from prior history lifetimes while leaving unrelated files alone. The managed directory and its ancestors must not be symlinks. Cleanup failure temporarily disables preparation, but not text generation. Restart does not restore history or audio access.

## Manual acceptance (user-owned)

- Generate text and confirm automatic audio preparation, disabled gray Play, then enabled Play without autoplay.
- Listen manually for distinct consistent voices, correct wording/order, suitable pauses, and acceptable quality.
- Check Pause/resume/replay, both pages, separate Retry audio, unavailable audio after failed saving, expired history, and preservation of text/navigation.
- Replace a dialogue during preparation/playback and confirm stale responses cannot update the replacement.
- Confirm ordinary text generation, validation, history, and navigation still work.

The implementation agent performs backend/API verification only. It does not run browser acceptance, play/listen to audio, transcribe, or assess audio quality. Routine tests use stubs; the opt-in API smoke test may generate a real file without playing it.

## API-only live verification

The live test is disabled in routine Maven runs. To opt in with server-side `OPENAI_API_KEY` already set, run in PowerShell:

```powershell
$env:ECOSPEAK_LIVE_AUDIO='true'
mvn '-Dtest=LiveAudioApiTests' test
Remove-Item Env:ECOSPEAK_LIVE_AUDIO
```

This makes paid requests for two short turns, using a stub for text generation and the real audio provider. It starts a temporary local HTTP server, prepares audio by UUID, polls ready, checks PCM/WAV metadata and nonempty data, retrieves identical bytes over HTTP, and confirms cache reuse. It never plays audio. Files go under the isolated `target/live-audio-check` cache rather than touching an application's normal cache; its history disappears at test shutdown. Safe metadata is written to ignored `test-evidence/audio-api-check.txt`.
