# Proposal

## Why

Learners need to hear dialogue at a pace that suits their listening practice. The current player only offers normal-speed playback.

## What Changes

- Add an accessible playback-speed selector beside Play on the practice and saved-dialogue pages.
- Apply the selected rate to the existing audio in the browser, including while playing, without generating new TTS audio.
- Default to normal speed and retain the selection for the current page session.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dialogue-audio`: Provide browser-side speed selection for the shared dialogue player.

## Impact

The shared player script, its styles, and the two pages containing its controls are affected. Audio APIs, generated WAV files, provider calls, and server storage do not change. The original product proposal excluded TTS from its MVP; the current application already implements dialogue audio, which this change extends.
