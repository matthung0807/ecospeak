# Spec Delta

## ADDED Requirements

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
