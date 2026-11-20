# Design

## Context

Both `index.html` and `dialogue.html` instantiate the same controls and load `dialogue-audio.js`. The shared player creates a new `Audio` object when a retained dialogue is ready and disposes it when the dialogue changes. Preparation and retry use explicit POST requests; playback uses the ready audio URL.

## Goals / Non-Goals

**Goals:** Apply a user-selected rate to each current and replacement media object without interfering with preparation, playback, or lifecycle handling.

**Non-Goals:** Persist the choice across page loads, modify generated WAV files, change TTS settings, or add server APIs.

## Decisions

- Add the same labeled native select next to Play on both pages. A native select provides keyboard and assistive technology support with minimal custom interaction. A custom menu would add unnecessary interaction complexity.
- Set `Audio.playbackRate` when the selection changes and when a new media object is created. The select remains the page-local source of truth, so selection before readiness and dialogue replacement work without storage. Server-side resynthesis would incur generation and change the cached asset.
- Offer 0.75x, 1x, 1.25x, and 1.5x, defaulting to 1x. These bounded options cover slower and faster practice without requiring arbitrary input.

## Risks / Trade-offs

- Browser playback quality and pitch handling can vary at different rates → verify actual playback in the supported browser and use moderate rate choices.
- Recreating the audio object can lose its rate → apply the current selector value every time the player becomes ready.

## Migration Plan

Deploy the static changes together; no data migration is required. Roll back the static files together if needed.
