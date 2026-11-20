# dialogue-audio Specification

## Purpose

Let users listen to retained practice dialogues with distinct AI voices and reuse locally cached audio throughout the dialogue's history lifetime.

## Requirements

### Requirement: Prepare audio from retained dialogue text
The system SHALL expose `POST /api/dialogs/history/{id}/audio` without a text body. It SHALL prepare audio from the retained record only. Malformed UUIDs SHALL return 400 and missing records SHALL return 404 without provider calls. Unsupported speaker labels SHALL return 422 without modifying text history.

#### Scenario: First preparation
- **WHEN** preparation is requested for a retained dialogue with A/B speakers and no cached audio
- **THEN** the server accepts generation and returns 202 with `status: generating` and a status URL
- **AND** the saved text and UUID remain unchanged

#### Scenario: Invalid or expired identifier
- **WHEN** preparation uses a malformed or unknown UUID
- **THEN** the server returns 400 or 404 respectively and makes no provider request

#### Scenario: Unsupported saved speaker
- **WHEN** a saved line has a speaker other than A or B
- **THEN** preparation returns 422 without provider calls and the dialogue remains readable

### Requirement: Produce ordered two-voice speech
Audio SHALL speak each saved line's text in order, using one fixed voice for A and a distinct fixed voice for B, with short pauses between turns. It SHALL omit topic, target-phrase headings, and speaker labels. Output SHALL be one valid WAV file. Provider credentials SHALL remain on the backend.

#### Scenario: Alternating and repeated speakers
- **WHEN** saved lines contain A, B, B, and A turns
- **THEN** audio preserves that order and uses the same corresponding voice for every occurrence of each speaker
- **AND** no explanatory narration or new dialogue is requested

### Requirement: Reuse completed audio and share pending work
Repeated preparation requests for one UUID SHALL share at most one active generation job. A ready cache SHALL return 200 with `status: ready` and an audio URL without provider calls. Merely loading a page or requesting status SHALL NOT generate audio.

#### Scenario: Concurrent preparation
- **WHEN** two clients request audio for the same UUID while generation is pending
- **THEN** both receive the same generating state and only one generation job runs

#### Scenario: Replay cached audio
- **WHEN** audio for the UUID is ready and preparation is requested again
- **THEN** the server returns the existing audio URL without regenerating any turn

### Requirement: Report status and recover from failure
`GET /api/dialogs/history/{id}/audio/status` SHALL return 200 with `status` equal to `not_generated`, `generating`, `ready`, or `failed` for retained records. Ready status SHALL include `audioUrl`; failed status SHALL include a safe error code. Invalid and missing identifiers SHALL return 400 and 404. Failed jobs SHALL allow explicit retry by POST.

#### Scenario: Provider timeout or file failure
- **WHEN** generation exceeds its configured deadline or provider, decoding, or file writing fails
- **THEN** status becomes failed, no partial audio is served, and the text remains available
- **AND** a later POST can start a new attempt without an automatic paid retry loop

#### Scenario: Read-only status
- **WHEN** status is requested before any preparation
- **THEN** the server returns `not_generated` and makes no provider call

#### Scenario: Missing cached file
- **WHEN** status is requested for a retained UUID whose completed cache file is missing
- **THEN** it reports `not_generated` rather than ready, and a later explicit preparation can recreate it

### Requirement: Bound audio generation resources
The system SHALL enforce finite configurable job concurrency, pending capacity, generation duration, input size, and output size. Capacity exhaustion SHALL return 503 with a retryable indication; oversized dialogue input SHALL return 422 before provider calls. Failure SHALL release job capacity.

#### Scenario: Capacity or input limit reached
- **WHEN** a new preparation exceeds capacity or its saved text exceeds configured input limits
- **THEN** it returns 503 or 422 respectively without starting a new provider request
- **AND** existing jobs and readable dialogues remain available

### Requirement: Serve complete cached audio by UUID
`GET /api/dialogs/history/{id}/audio` SHALL serve only complete audio for a retained record with `Content-Type: audio/wav`. Invalid UUIDs SHALL return 400, absent records or files SHALL return 404, and retrieval SHALL NOT generate audio. Responses SHALL prevent persistent browser caching of expired history audio.

#### Scenario: Retrieve ready file
- **WHEN** ready audio is requested for a retained UUID
- **THEN** a playable WAV is returned without invoking the provider

#### Scenario: Retrieve before preparation
- **WHEN** a retained UUID has no complete cached file
- **THEN** retrieval returns 404 without generating audio or exposing a temporary file

### Requirement: Couple local cache lifetime to history
Completed audio SHALL be stored as `data/audio/<uuid>.wav` by default under the project working directory. Files SHALL be published only while the record remains retained. Eviction SHALL make audio immediately inaccessible and trigger file cleanup after commit. Startup SHALL clean orphaned managed audio and temporary files. Cleanup SHALL remain confined to the managed audio directory.

#### Scenario: Eviction during generation
- **WHEN** a dialogue is evicted before its audio finishes
- **THEN** its result is discarded, its audio endpoints return 404, and no completed cache is published

#### Scenario: Retention transaction rollback
- **WHEN** a history save and eviction transaction rolls back
- **THEN** previously retained audio remains available and is not removed by that attempted eviction

#### Scenario: Restart or cleanup failure
- **WHEN** startup finds files from a previous in-memory history lifetime
- **THEN** those managed files are removed before audio preparation becomes available
- **AND** deletion failures are reported safely and retried through reconciliation without exposing expired audio

### Requirement: Adjust dialogue playback speed
The practice and saved-dialogue pages SHALL provide a labeled, keyboard-accessible speed selector near Play with 0.75x, 1x, 1.25x, and 1.5x options. It SHALL default to 1x on page load and retain the selected rate for the current page lifetime, including when the displayed dialogue changes.

#### Scenario: Change speed during playback
- **WHEN** the user selects another speed while audio is playing
- **THEN** the existing playback changes to that rate without restarting or issuing a preparation request

#### Scenario: Select speed before playback
- **WHEN** the user selects a speed before audio becomes ready or while it is paused
- **THEN** playback uses the selected speed when started or resumed

#### Scenario: Replace or replay dialogue
- **WHEN** the user replays audio or a new dialogue replaces the current one on the same page
- **THEN** the selected speed applies to the next playback
- **AND** changing speed does not generate new TTS audio or alter the cached audio

#### Scenario: Open a page
- **WHEN** the user loads either page
- **THEN** the speed selector initially displays 1x
