# Proposal

## Why

EcoSpeak currently provides text-only practice. Hearing the same conversation spoken by two distinct, natural-sounding voices would help users practice pronunciation, rhythm, and listening without generating another dialogue.

## What Changes

- Add Play/Pause above the dialogue lines on the practice and saved-dialogue pages, with an AI-generated voice disclosure.
- Automatically prepare audio after a successful dialogue generation is saved and displayed, using saved text and fixed, distinct voices for A and B; combine turns in order with short pauses.
- Keep Play disabled and grayed out with `Generating audio…` until audio is ready. Play only plays existing audio and never starts generation; playback never starts automatically.
- Offer a separate Retry audio action on preparation failure or missing audio; keep dialogue text readable.
- Cache one WAV file per dialogue UUID under `data/audio/` and reuse it for subsequent playback.
- Add backend preparation, status, and audio retrieval endpoints with shared in-flight work, bounded generation, retryable failures, and backend-only provider credentials.
- Disable playback with an explanation when no saved UUID is available; preserve existing text-generation and save-warning behavior.
- Remove cached audio when history expires, including restart cleanup, while keeping history itself in memory.
- Use OpenAI's supported audio workflow rather than introducing a new dependency on retiring Speech API models.

Out of scope: voice selection UI, voice cloning, microphone input, pronunciation assessment, downloads, persistent dialogue history, cloud storage, multi-instance coordination, and changes to text generation.

## Capabilities

### New Capabilities

- `dialogue-audio`: Prepare, cache, serve, and expire two-voice audio for retained dialogues, with controlled failure and concurrency behavior.

### Modified Capabilities

- `dialogue-practice-page`: Automatic preparation after successful saving and playback controls for the displayed UUID.
- `dialogue-history`: Playback on saved-dialogue detail and an explicit audio-cache exception to the existing no-generated-files requirement.

## Impact

Changes will affect static pages and a shared playback script, new audio controller/service/provider/storage components, history retention integration, application configuration, tests, and runtime-file ignore rules. Existing text API response fields and in-memory H2 history remain compatible. Every saved generation incurs audio usage even if never played. Preparation adds a wait before Play becomes available. No implementation or provider calls are part of this proposal.

The implementation agent may verify backend/API behavior, including generation of a valid nonempty UUID-named WAV file. Routine tests should use provider stubs. The user performs all manual UI and audio acceptance testing. The agent must not perform browser acceptance testing or play, listen to, or assess generated audio. This explicit task instruction overrides the repository's usual browser-testing workflow for this change.
