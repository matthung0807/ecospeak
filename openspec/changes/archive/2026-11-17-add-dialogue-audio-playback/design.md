# Design

## Context

See proposal.md for motivation and scope. Current `DialogueController` returns generated text with `historySaved` and `historyId`; a save failure still returns readable text. `DialogueService` uses the OpenAI Java SDK for structured Responses API output. No audio service exists.

`HistoryService` commits insertion and ten-record retention under a writer lock using a new transaction. `HistoryRepository.trim()` currently removes records without returning evicted IDs. H2 is intentionally in memory. Existing specs prohibit dialogue storage files; this change explicitly permits only the audio cache. The product roadmap identifies audio as a practice-experience extension.

The static practice page replaces results only after validating content; the detail page loads by UUID. Backend validation accepts nonblank speaker labels beyond A/B, so audio must reject unsupported speakers without changing existing text-generation validation.

## Goals / Non-Goals

**Goals:** Render text promptly, automatically prepare audio for the successfully saved displayed UUID, serve one complete reusable WAV, bound provider work, and keep cache availability consistent with history.

**Non-Goals:** Distributed jobs, persistent history, browser-to-provider connections, voice selection, automatic playback, and audio quality assessment by the implementation agent.

## Decisions

### 1. Separate preparation from text response

After rendering a complete generation response with `historySaved: true` and a canonical UUID, the practice page automatically POSTs `/api/dialogs/history/{id}/audio`. It then polls status while showing disabled grayed-out Play and `Generating audio…`. This is part of the Generate Dialogue flow and requires no additional click. A separate request avoids delaying text behind speech synthesis. The selected browser-mediated trigger means closing the page before that POST may leave audio ungenerated; the detail page offers Retry audio for this state. Server-triggered background creation was considered but would diverge from the reviewed explicit preparation flow.

Play only uses an existing audio URL. Readiness enables it without calling `play()`. The same preparation endpoint is used only for automatic preparation and explicit Retry audio. Detail-page loading only checks status; it does not generate. Text-only API consumers can call preparation explicitly and are not promised automatic audio by the unchanged generation endpoint.

### 2. Audio boundaries and API contract

Add an audio controller, orchestration service, provider adapter, and cache component. Load text through history lookup; accept no replacement speech text from the browser. Expose the three endpoints in the audio delta spec. Return JSON with `status`, optional `statusUrl`, optional `audioUrl`, and a safe failure `code`. Return 202 for accepted or pending work, 200 for ready preparation and valid status, and the specified 400/404/422/503 errors. Unexpected storage failures return non-success without leaking paths or provider details.

Status derives readiness from an existing complete file and retained history. A missing completed file becomes `not_generated`, allowing explicit recreation. Retrieval returns `audio/wav` with `Cache-Control: no-store`; it never synthesizes. Use canonical UUID-derived paths, reject links escaping the cache root, and keep the cache outside static resources. Stored bytes already fetched by a client cannot be revoked; the lifetime guarantee applies to subsequent server access.

### 3. Provider adapter and WAV assembly

Use OpenAI's supported audio workflow. Before implementing the adapter, confirm current official model, voice, SDK/transport support, PCM format, completion/error events, and account availability. This is a targeted compatibility check, not a redesign task. Do not assume the existing Responses API model can synthesize speech or pass a Realtime model to the legacy Speech endpoint. Keep model and A/B voice identifiers in backend configuration, requiring two different supported voices.

Request each turn separately, with fixed narration instructions to read exactly the saved text and no added conversation. Use a voice-specific request/session as required by the selected API; do not assume a voice can change within an active audio session. Normalize all turn output to one supported PCM sample format, concatenate PCM frames in order, insert approximately 300 ms of silence between turns, and write one WAV header around the complete PCM. Do not concatenate independent WAV headers. Reject empty, truncated, incompatible, or excessive output. Delete intermediate files on every terminal path. No external media-processing executable is required for PCM/WAV assembly.

Generative speech can deviate from text despite instructions. Automated provider-contract tests verify requested text and ordering; the user's listening acceptance verifies the actual rendition. Do not add transcription or quality-evaluation calls.

### 4. Shared jobs with finite resources

Maintain one job per UUID within this single application instance, using atomic job admission and a bounded executor. Proposed configurable defaults: two workers, ten pending jobs, a 120-second overall job deadline, 8,000 total input characters, and 20 MiB total audio output. Validate configuration and input before provider work. Completion, failure, cancellation, and expiry release resources. Failed jobs allow explicit retries; no automatic paid retries or hidden SDK retries after partial generation. An explicit retry may regenerate all turns and incur usage again.

Use short provider request timeouts within the overall deadline and close provider connections on timeout or cancellation. Poll once per second, stop on ready/failed/404, and surface network failures without infinite silent polling. Do not persist job state in H2: startup begins with empty history and cleans the cache.

### 5. Cache publication and retention coordination

Write temporary output in the managed cache directory, then atomically rename to `<uuid>.wav` after checking history retention. Coordinate that final check/publication with the history writer lock through a narrow shared lifecycle boundary; never hold this lock during provider calls or audio assembly. This prevents a completed generation publishing after eviction. Ready reads check current history before serving bytes.

Have retention identify evicted UUIDs and notify the audio lifecycle only after successful commit, preserving the existing atomic ten-record transaction and ordering. Invalidate/cancel their jobs and remove their files after commit. Filesystem deletion failures must not undo committed history or make a valid text response appear unsaved. They are logged safely and retried by bounded periodic reconciliation. Reconciliation checks retained IDs under the same lifecycle coordination to avoid deleting newly published valid files. Rollback triggers no invalidation or cleanup.

At startup reconcile only application-owned UUID WAV and temporary filenames under the dedicated cache root, without following external symlinks or deleting unrelated files. Because history is empty, all managed audio is orphaned. A cleanup failure leaves preparation unavailable with a safe retryable error until reconciliation succeeds; text generation remains available. Graceful shutdown cancels jobs and closes the executor; restart cleanup handles crashes.

### 6. Shared client state

Use a shared static playback script for both pages. Keep current UUID and a monotonically increasing view token. Every asynchronous callback verifies that token before updating controls. A successful text replacement pauses/releases the previous audio element, stops polling, and initializes the new UUID. Failed text validation retains the prior audio association. Leaving the page aborts pending browser requests and polling and stops playback; server work may finish for a still-retained record.

Use native disabled buttons and accessible status/error text. Maintain Play/Pause labels from media events, resume paused playback, and reset completed playback for replay. A preparation failure disables Play and offers Retry audio. A playback-only failure offers playback retry without synthesis. Missing UUID or failed saving disables preparation and explains why, preserving the existing save warning. Audio errors are separate from text-generation errors.

### 7. Verification ownership

Routine backend tests use deterministic provider stubs and temporary cache directories. Check job sharing, requested voice/text sequence, WAV structure, cache reuse, failure/retry, limits, expiry, rollback, restart cleanup, and publication races. API verification may generate a real file and inspect its existence, UUID filename, WAV header, nonzero data, and HTTP retrieval without playing it. Keep paid checks explicit and outside routine test runs.

The user performs all manual UI and audio acceptance tests. The implementation agent must not run browser acceptance testing, delegate it to a Playwright tester, or play/listen to/assess generated audio. The user's explicit change-specific instruction overrides the usual AGENTS.md browser workflow. Provide a manual checklist and accurately report checks not performed.

## Risks / Trade-offs

- Every saved displayed generation starts paid audio work, even without playback -> reuse files and share jobs; document usage behavior.
- Separate synthesis can reduce conversational continuity or alter wording -> consistent instructions and voices; user listening acceptance.
- Provider/SDK capabilities change -> confirm official support before implementing the isolated adapter and document chosen configuration.
- Audio filesystem operations are not transactional with H2 -> commit-aware invalidation, coordinated publication, and reconciliation.
- The browser can close before automatic preparation -> saved detail exposes Retry audio; avoid claiming server-side automatic scheduling.
- In-memory history limits reuse to the current lifetime -> explicitly clean stale cache on restart; no persistence migration.

## Migration Plan

Add configuration, runtime ignore rules, audio components, history lifecycle integration, then both page integrations. No H2 schema change or baseline spec sync is performed during proposal creation. Keep the managed cache directory writable and credentials server-side. Rollback restores the text-only application; cache files can be removed within the dedicated managed directory. Existing text endpoints and response fields remain unchanged.
