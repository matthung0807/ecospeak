# Spec Delta

## ADDED Requirements

### Requirement: Display generation allowance
The practice page SHALL load quota status on entry, after generation outcomes, on returning to the foreground, and at the reported next availability time. It SHALL display daily and weekly used counts and limits near Generate Dialogue, explain active reservations, and disable generation while quota is unknown or either allowance is exhausted. Exhaustion SHALL show when generation becomes available again in the configured timezone.

#### Scenario: Available allowance
- **WHEN** status reports one daily use and six weekly uses without reservations
- **THEN** the page displays Today: 1 of 2 used and This week: 6 of 10 used and enables generation.

#### Scenario: Exhaustion and recovery
- **WHEN** either allowance is exhausted
- **THEN** generation is disabled and next availability is displayed
- **AND** at that time the page refreshes quota and enables generation only if allowance is confirmed.

#### Scenario: Status unavailable
- **WHEN** quota status cannot be loaded
- **THEN** generation remains disabled with an actionable quota retrieval error and retry control
- **AND** existing content, playback, and history navigation remain available.

### Requirement: Reuse keys for generation retries
The page SHALL send an Idempotency-Key for each logical generation. Transport retries with the same scenario SHALL reuse the key, including after reload while an outcome is unresolved. Explicit new generation or an edited scenario SHALL use a new key. Pending duplicate responses SHALL show progress and retry using the same key without presenting a new success.

#### Scenario: Lost response
- **WHEN** a request's response is lost and the user retries the unresolved request
- **THEN** the same key and scenario are submitted and a completed dialogue is recovered without new usage.
- **AND** a separate Recover pending result action remains available even when quota disables new generation.

#### Scenario: New dialogue
- **WHEN** the user chooses Generate Another after success or changes the scenario
- **THEN** the page creates a new key for the new logical generation.

## MODIFIED Requirements

### Requirement: Report failures and allow another attempt
The page SHALL treat non-success statuses and invalid dialogue responses as failures and catch fetch, JSON parsing, and rendering exceptions. Ordinary failures SHALL log the exception and show Something went wrong. Please try again. HTTP 429 SHALL instead show quota exhaustion and reset information. After completion loading SHALL stop; generation SHALL be enabled only when current quota permits. Validation SHALL precede result updates so failures preserve previous results.

#### Scenario: Request fails before rendering
- **GIVEN** an earlier result is visible
- **WHEN** the next fetch fails or returns a non-success status other than quota exhaustion or pending duplicate
- **THEN** the generic error is shown and the earlier result remains visible
- **AND** loading stops and retry is available when quota permits.

#### Scenario: Response processing fails
- **WHEN** JSON parsing or rendering throws an exception
- **THEN** the page shows the generic error and allows another attempt when quota permits
- **AND** parsing failure leaves the previous result unchanged and rendering exceptions remain caught without requiring rollback of valid-response rendering.

#### Scenario: Invalid response preserves previous success
- **GIVEN** a valid result is visible
- **WHEN** a subsequent response fails validation
- **THEN** topic, target phrase, every dialogue entry, result visibility, and generation button label remain unchanged
- **AND** the generic error is shown, loading stops, the input is retained, and generation follows current quota availability.

#### Scenario: Invalid first response
- **WHEN** the first response fails validation
- **THEN** the result remains hidden and the button retains its initial label
- **AND** the error is shown, loading stops, and retry follows current quota availability.

#### Scenario: Retry succeeds after invalid response
- **GIVEN** a response failed validation and allowance remains
- **WHEN** a retry receives a valid response
- **THEN** the error is hidden, complete content is displayed, loading stops, and generation follows refreshed quota.

#### Scenario: Server rejects stale allowance
- **WHEN** the page submits while another tab has consumed the remaining quota and receives HTTP 429
- **THEN** it shows the returned exhaustion and reset information, disables generation, and preserves the previous dialogue and audio association.

## REMOVED Requirements

### Requirement: Warn when generated content could not be saved
**Reason**: Successful generation now requires history and usage to commit together; unsaved content is no longer returned as success.
**Migration**: Remove the save-warning success path. Treat saving failures as generation errors, preserve previous results, and refresh quota before allowing retry. Existing playback fallback for missing saved metadata remains defensive behavior.
