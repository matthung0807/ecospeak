# Spec Delta

## ADDED Requirements

### Requirement: Play the displayed saved dialogue
The practice page SHALL show a keyboard-accessible Play button above complete dialogue lines and disclose `AI-generated voices`. Playback SHALL use the displayed result's valid `historyId` only when `historySaved` is true. Unsaved results SHALL keep text visible and show a disabled Play control with an explanation that audio requires saved history.

#### Scenario: Saved result
- **WHEN** a valid result with a saved UUID is displayed
- **THEN** the page displays the text immediately, shows disabled grayed-out Play above the lines, and automatically requests audio preparation for that UUID

#### Scenario: Unsaved or missing UUID
- **WHEN** valid dialogue text arrives with failed saving or without a usable saved UUID
- **THEN** the dialogue remains readable and Play is disabled with an explanation
- **AND** existing save warnings and generation behavior remain intact

### Requirement: Show audio preparation and playback states
Pending preparation SHALL show `Generating audio…` and disabled Play. Readiness SHALL enable Play without autoplay. Play SHALL only play existing audio with Pause, resume, and replay. Preparation failure SHALL keep Play disabled and offer separate Retry audio for the saved UUID. Audio errors SHALL have separate actionable messages without changing dialogue text or generation controls.

#### Scenario: First playback and replay
- **WHEN** preparation completes successfully
- **THEN** Play becomes enabled without autoplay; selecting it plays existing audio and offers Pause while playing
- **AND** subsequent replay uses ready audio without another paid generation

#### Scenario: Playback permission or audio failure
- **WHEN** audio preparation or playback fails
- **THEN** the page shows an audio-specific message and keeps Play disabled after preparation failure and offers separate Retry audio, or offers a playback retry for a playback-only failure
- **AND** complete dialogue text remains visible

#### Scenario: Saved record expires
- **WHEN** preparation or status reports that the displayed UUID is no longer retained
- **THEN** the page explains that audio is unavailable because history expired and stops polling
- **AND** the displayed dialogue text remains readable

### Requirement: Keep playback associated with the displayed result
A successful replacement dialogue SHALL stop old playback, clear old audio state, and ignore late preparation and playback completions for the previous UUID. While another text generation is pending or fails validation, retained result playback SHALL remain associated with the retained UUID. Leaving the page SHALL stop playback and polling.

#### Scenario: Replace while audio is pending
- **WHEN** a new valid dialogue replaces an earlier one while its audio is being prepared
- **THEN** the previous completion cannot start playback or update the new result's controls

#### Scenario: Text generation fails
- **WHEN** a subsequent text generation fails or returns invalid content
- **THEN** the previous valid result and its UUID association remain intact

