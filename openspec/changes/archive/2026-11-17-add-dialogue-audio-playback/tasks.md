# Tasks

## 1. Provider and audio assembly

- [x] 1.1 Confirm current official OpenAI model, distinct voices, API workflow, Java SDK or transport support, audio format, and completion events; document selected configuration and verify it against official documentation before implementing the adapter.
- [x] 1.2 Add backend audio configuration, provider abstraction, finite resource defaults from design.md, and environment-only credentials; verify configuration binding and invalid-setting rejection with focused backend tests.
- [x] 1.3 Implement per-turn synthesis and PCM/WAV assembly with fixed A/B voice mapping and short pauses; verify ordered text/voice requests, WAV header/data lengths, nonempty output, and rejection of incomplete output using deterministic provider stubs without playback.
- [x] 1.4 Document provider settings, usage costs for every saved displayed generation, retry costs, and absence of automatic paid retries; verify the documented configuration matches actual property names.

## 2. Cache, jobs, and endpoints

- [x] 2.1 Implement managed UUID cache paths, safe temporary files, complete-file publication, and runtime ignore rules; verify temporary-directory tests prevent partial serving and path escape, and generated files are ignored by Git.
- [x] 2.2 Implement shared per-UUID jobs, bounded workers and queue, deadlines, cancellation, size limits, and explicit retry; verify concurrent requests share one job, capacity is released, and failures allow retry through focused backend tests.
- [x] 2.3 Add preparation, status, and audio retrieval endpoints with safe codes and no-store WAV responses; verify HTTP 200/202/400/404/422/503 behavior, saved-text-only input, missing-file status, and zero provider calls on status/retrieval using API tests.
- [x] 2.4 Document endpoint response examples and error behavior; verify examples against controller/API test fixtures.

## 3. History lifecycle

- [x] 3.1 Return or collect evicted UUIDs and coordinate cache publication with committed history retention; verify ten-record ordering, rollback preservation, eviction cancellation, and eviction/publication races with integration tests.
- [x] 3.2 Add post-commit cache cleanup, bounded reconciliation, startup orphan cleanup, and shutdown cancellation; verify cleanup is confined to managed files, retries deletion failures, and never converts a committed text save into a reported save failure.
- [x] 3.3 Document volatile history and audio-cache lifetime; verify existing history lifetime tests and new startup/eviction API tests agree with the documentation.

## 4. Practice and detail playback controls

- [x] 4.1 Add a shared playback script and accessible Play/Pause controls above dialogue lines with AI-generated voice disclosure; verify source review confirms native disabled controls and playback handlers never call preparation endpoints.
- [x] 4.2 Automatically request audio only after rendering a valid saved generation, poll preparation status, and enable Play only on ready without autoplay; verify source review covers saved/unsaved responses, failed saving, pending state, and disabled grayed-out styling.
- [x] 4.3 Add detail-page status lookup and separate Retry audio for missing/failed preparation, preserving existing text/navigation and separating audio errors; verify source review against every saved-dialogue scenario in the delta spec.
- [x] 4.4 Stop old playback and polling on replacement/navigation and guard late completions by UUID/view token; verify source review covers replacement during preparation, invalid subsequent generation, and media-error handling without synthesis.
- [x] 4.5 Deliver the manual acceptance checklist below and document controls and retry behavior; verify each UI/audio acceptance criterion is represented. Do not run browser acceptance testing or delegate it to a browser subagent for this change.

## 5. Integration verification and handoff

- [x] 5.1 Run the appropriate Maven backend/API suite with provider stubs and record results, verifying text-generation/history regressions and audio endpoint integration; do not play or assess audio.
- [x] 5.2 Verify the provider integration through an API-only smoke check that prepares a saved UUID, polls to ready, checks the UUID-named file, validates a WAV header and nonempty data, and retrieves it over HTTP; report results without playback, listening, browser testing, transcription, or audio quality assessment. Keep live generation out of routine automated test runs.
- [x] 5.3 Report implementation changes, API verification evidence, limitations, and the user-owned manual acceptance checklist; verify the report explicitly distinguishes automated/API results from acceptance checks not performed.

## Manual acceptance checklist (user-owned)

These are manual user checks, not implementation-agent tasks or automated completion gates. The implementation agent must not perform them.

- Generate Dialogue shows complete text before audio finishes and automatically starts preparation after a successful save.
- Play is above the lines, grayed out and disabled with `Generating audio…` while pending, and enabled only when ready.
- Audio never autoplays. Play only plays the existing file without starting synthesis.
- A/B voices are distinct and consistent; spoken text, order, pauses, and audio quality are acceptable.
- Pause, resume, replay, and cache reuse work on both dialogue views.
- Preparation errors leave text readable and offer separate Retry audio; save failures prevent preparation and explain unavailable playback.
- Replacing dialogue stops prior playback and ignores late old-UUID responses. Failed text generation preserves the prior result and audio association.
- Saved-dialogue pages reflect ready, pending, missing, failed, and expired audio correctly, without preparing merely on page load.
- Existing generation, validation, navigation, and history behavior remain usable.

## Workflow follow-up

- The user performs manual UI and listening acceptance and reviews the reported API evidence.
- Archive or sync this change only when separately requested; do not apply or archive during proposal creation.

