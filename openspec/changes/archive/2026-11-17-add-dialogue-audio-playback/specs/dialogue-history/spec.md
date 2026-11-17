# Spec Delta

## MODIFIED Requirements

### Requirement: Store complete dialogue records in memory
The system SHALL store UUID, UTC generation-completion timestamp, topic, target phrase, normalized user scenario, and ordered speaker/text lines for each saved generation in in-memory H2. Dialogue records SHALL NOT be written to files; synthesized WAV audio and temporary assembly files SHALL be allowed only in the managed audio cache. History SHALL be discarded on application shutdown. History SHALL have no user partitioning or authentication requirement.

#### Scenario: Save complete content
- **WHEN** a valid dialogue is saved for a normalized scenario
- **THEN** its record contains all specified fields and preserves every line and its position

#### Scenario: Restart clears history
- **GIVEN** saved records exist
- **WHEN** the application shuts down and starts again
- **THEN** history is empty and earlier record UUIDs are not found
- **AND** no dialogue-record storage files were created and orphaned audio cache files are cleaned at startup

## ADDED Requirements

### Requirement: Play saved dialogue audio
After valid detail content loads, `dialogue.html` SHALL show Play above dialogue lines with `AI-generated voices` and check audio status without starting preparation or playback. Ready status SHALL enable Play. Pending status SHALL show `Generating audio…` and disabled grayed-out Play. Failed or missing audio SHALL keep Play disabled and offer separate Retry audio.

#### Scenario: Ready or pending audio
- **WHEN** a retained detail page reports ready or generating audio
- **THEN** it enables Play or displays the disabled preparation state respectively
- **AND** pending preparation is polled without starting duplicate work or autoplay

#### Scenario: Missing or failed audio
- **WHEN** a retained detail page reports not_generated or failed audio
- **THEN** it offers Retry audio while keeping Play disabled
- **AND** selecting Retry audio prepares the existing UUID without regenerating dialogue text

#### Scenario: Playback controls
- **WHEN** the user selects enabled Play
- **THEN** existing audio plays with Pause, resume, and replay available without preparation requests
- **AND** leaving the page stops playback and polling

#### Scenario: Detail or audio failure
- **WHEN** detail content is unavailable or invalid
- **THEN** no active playback controls or audio preparation requests are provided
- **AND** existing navigation and detail-error behavior remain available

#### Scenario: Record expires after loading
- **WHEN** audio status or retrieval reports a missing record
- **THEN** the page disables Play, stops polling, and explains that audio is unavailable
- **AND** navigation and any already displayed dialogue text remain available
